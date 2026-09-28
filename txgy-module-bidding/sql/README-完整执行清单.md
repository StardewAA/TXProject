# bidding 模块 SQL 索引（初始状态包 + 历史增量 + 工具）

> 业务库：`ruoyi-vue-pro`（bidding 全部业务表；本环境 system_menu / system_role_menu 同库）。
> **口径（2026-09-11 整理）**：SQL 只保留**表结构 + 最基础数据**——能建表、能进菜单、能启动爬虫；
> 任务/公告等业务数据不入脚本。全部脚本幂等（`IF NOT EXISTS` / `INSERT IGNORE`），
> 包内**无 DROP / TRUNCATE / DELETE**，重复执行不改动既有行、不清空任何业务数据。

## 1. 初始状态包（新环境一键初始化）

**`sql/master-bidding-sync.sql`**

| 段 | 内容 | 规模 |
| --- | --- | --- |
| ① | 业务表结构（CREATE TABLE IF NOT EXISTS） | **21 张** |
| ② | 采集源配置（EBIDDING / QYZB / GX）——**爬虫可启动的最小数据** | **3 条** |
| ③ | 原文下载限速闸位（GLOBAL 单行，合规频控 2 分钟/次） | **1 行** |
| ④ | 菜单：招投标 6735 子树（含子菜单与按钮权限） | **29 条** |
| ⑤ | 角色菜单授权（system_role_menu） | **以包尾校验输出为准** |

> **段序是有意的（2026-09-11 事故复盘）**：`mysql` 客户端**遇到第一个错误就中止**执行。
> 菜单属 system 服务库——目标库若没有 `system_menu`，④ 段必然报错中止。旧版把限速闸/审计表排在菜单之后，
> 于是「表没建 → 爬虫每个任务都在闸位处抛错 → 退避后进死信 → 下一轮再造任务」表现为**无限重试**。
> 现在表结构全部集中在 ①、爬虫必需项（②③）在菜单（④⑤）之前：**即使菜单段失败，爬虫依然可用**。
> 实测：无 `system_menu` 的库执行本包 → ④ 段报错中止，但 21 张表 / 3 采集源 / 1 闸位行均已落地。

执行（包尾自带校验段，执行后直接打印期望值）：

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < sql/master-bidding-sync.sql
```

实测：空库一次执行通过（结构 21 / 采集源 3 / 闸位 1 / 菜单与授权条数以包尾校验输出为准）；**连续执行两遍结果完全一致**（幂等）。

**不包含**（按「不要数据」口径）：采集任务/批次、公告/附件/正文、主体授权与档案、AI 渠道与密钥、演示公告。

**也不含智慧标书审查域**（7 张 `bidding_bid_review_*` 表 + `bid_review_*` 字典 + 管理端菜单）：该域单独成包放在 `sql/history/`，**新库与老库都要单独执行**，见 §2「智慧标书审查建表（单独执行）」。

### 1.1 电子资质域包（新环境第二包，独立执行）

**`sql/master-credential-sync.sql`**（2026-09-13 整理：把电子资质域 16 个增量脚本合并为一个一键包）

| 段 | 内容 | 规模 |
| --- | --- | --- |
| ① | 业务表结构：电子证照台账/文件版本/验真/到期预警/变更审计、企业资质与资质标准/要素/达标核查、员工资质（档案/证书/履历/社保/劳动合同）、证件全生命周期、规则配置中心与字典、安许 A/B/C 风险联动 | **29 张** |
| ② | 默认规则阈值（证照预警窗口/档位、安许风险参数、宽限期等，可后台动态改） | **26 行** |
| ③ | 枚举字典（cert_type / holder_type / review_result / material_category / material_type / material_result / material_status / safety_risk_flag / person_risk_flag / risk_level） | **109 行** |
| ④ | 菜单与权限点（**60005000 段**：证照台账 · 企业资质 · 员工资质 · 证件生命周期 · 安许风险联动） | **45 行** |

执行（包尾自带校验段，执行后直接打印期望值）：

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < sql/master-credential-sync.sql
```

- 与 `master-bidding-sync.sql` **无先后依赖**（本包不引用招投标主流程的表），两者合起来覆盖代码里全部 49 张 DO 表；
- 全部语句幂等（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE`），**包内无 DROP / TRUNCATE / DELETE**，可重复执行；
- 菜单号段使用 **60005000 段**。两段变更史：① 旧 60002xxx 段与外部公告采集域冲突 → 迁至 60004xxx；
  ② 60004xxx（父级 60004000 + 页面 60004200 + 按钮 60004201~203）已被 `docs/前端规范-全域接口说明/投标流程跟踪功能设计方案.md` §8.3 **预约**给投标流程跟踪，
  按"先登记再占用"本域让出 → 终态改为 **60005000 段**（登记时 SQL 侧 60005000~60005999 无占用）。
  本包直接落终态，**不需要**再执行任何号段迁移脚本；流程跟踪落地时按原预约使用 60004xxx 段即可，不会再撞；
- **不包含**角色授权（授权属业务决策）：超管默认放行，其他角色见包尾「授权指引」按需授权；
- 不含演示/业务数据、文件实体（file_id 由 FileApi 上传产生）、AI 渠道与密钥。

## 2. 历史增量（老库追平用；新库直接跑第 1 节即可）

`sql/history/`：按日期归档的增量脚本（建表 / 字段 / 菜单 / 配置 / 修复 / 数据补偿）。

- **评审收尾：删除空壳目录 6759「主体信息与授权管理」（2026-09-23）**：`history/2026-09-23-remove-empty-subject-auth-dir.sql`。
  其子项已全部迁出/删除（6762/6763 于 09-16 删、6760/6761 于 09-20 迁入资质与证书管理），
  6759 成为无权限码空壳，按评审口径物理删除（先删 system_role_menu 绑定再删菜单，幂等可重跑）。
  执行后须清 `menu_role_ids:*` 与 `permission_menu_ids:*` 缓存；并在库追平后用
  `build-init-from-db.js` 重新导出 `master-bidding-sync.sql`（否则初始包仍含 6770/6772/6759 旧结构）。

- **⚠️ 本地库 id 错位版：公告/公示管理 + 主体域空壳删除（2026-09-24）**：`history/2026-09-24-remove-notice-and-subject-auth-local-ids.sql`。
  本地库菜单 id 与 dev 号段错位（本地 6753~6763 是 **MES 项目管理按钮**），dev 版三个脚本
  （09-16 remove-notice、09-20 subject-profile-relocate、09-23 remove-empty-subject-auth-dir）**严禁在本地库执行**。
  本脚本按本地真实 id 操作：删公告模块 6783/6784/6785~6788（先按权限码 1:1 把角色绑定转给 60006223~60006227，
  否则角色 1/2/60346/60352 失去公告五码、公告工作台 403）；删平台授权 6792/6793（沿用 dev 先例不转移角色 1/2 绑定）；
  主体档案 6790/6791 迁入 60005000 首位；删空壳 6789；归位 60006100/60006101/60006300/60006301（此前误挂 MES 6760）。
  执行后须清 `menu_role_ids:*` 与 `permission_menu_ids:*` 缓存（Redis 容器名为 `deploy-redis-1`，非 txgy-redis）。

- **项目台账提升 + 标段台账退役（2026-09-24 本地库补跑）**：`history/2026-09-16-bidding-promote-project-ledger-menu.sql` +
  `history/2026-09-26-bidding-retire-bid-section-menu.sql`。两者 677x 号段与本地一致、幂等可重跑；
  此前本地库只执行了 retire 的第②步（6777 已建），6770 目录与 6772 标段台账仍在 → 点「标段台账」404。
  补跑后 6771 提升为 6735 一级、6770/6772 硬删、6775/6776 迁入 6771、标段查询绑定转至 6777。

- **⚠️ 智慧标书审查建表（单独执行）：`history/2026-09-16-bid-review.sql`**——两个 master 包 **都不含**这个域，
  新库、老库都要单独跑一次。内容：7 张表（`bidding_bid_review_task` / `_rule_set` / `_rule_item` / `_issue` /
  `_report` / `_content` / `_ai_log`）+ `bid_review_*` 枚举字典 + 管理端菜单；全部 `CREATE TABLE IF NOT EXISTS` /
  `INSERT IGNORE`，可重复执行。
  - **执行顺序：必须在 `master-credential-sync.sql` 之后**——脚本会往 `bidding_dict_data` 写字典，而该表由资质包创建；
    `mysql` 批处理**遇错即中止**，顺序反了会连表带字典一起没建。
  - 命令：`docker exec -i deploy-mysql-1 mysql -uroot -proot --default-character-set=utf8mb4 -D ruoyi-vue-pro < sql/history/2026-09-16-bid-review.sql`
  - 漏执行的症状（2026-09-20 实测）：门户「智慧标书审查」点「开始审查」只弹一句通用错误（该接口只做「插入任务 +
    异步入队」，插入失败即整单失败；前端 catch 落到兜底文案「提交失败」，看不出真因）。复核：
    `show tables like 'bidding_bid_review%';` 应有 7 张。

- **⚠️ 权限体系根治（2026-09-20，两个脚本必须按序执行）**：
  `history/2026-09-20-perm-carrier-and-tenant-fix.sql` → `history/2026-09-20-perm-carrier-role-backfill.sql`。
  修「租户/租户管理员用不了招投标」的根因，**不改任何接口权限码**（口径：接口权限不能改，改为补齐承载与数据）。
  - ① 修正 `system_role_menu.tenant_id = 0`（1838 条，角色却属各租户）→ 缓存键 `menu_role_ids:{租户}:{菜单}` 命中不到，
    已授权限**全部失效**。症状：个人用户调招投标接口一律 403，但 `get-permission-info` 里权限码齐全。
  - ③ 为 37 个「接口要求但无菜单承载」的权限码补建载体菜单——菜单是权限码的**唯一承载体**
    （`hasAnyPermission` 先按 `permission_menu_ids:{码}` 找菜单，再按 `menu_role_ids:{租户}:{菜单}` 找角色），
    无承载 → 该码永远无法授予任何角色 → 接口永久 403。**super_admin 绕过权限校验，故该缺陷在超管视角不可见。**
  - 第二轮修上一脚本自身缺陷：**载体归位**（`bidding:process-track:cancel` 从根目录 6735 挪到页面 6748，
    否则角色授权树的级联勾选覆盖不到它）+ **授权回填**（此前只授 `tenant_admin`，导致 `common` 个人用户
    能建档、能报名却**永远不能取消投标**）。
  - 并恢复被误删的超管总览载体菜单 `60006222`（`bidding:process-track:overview`）：该码缺失会让
    `BiddingProcessTrackController.isSuperAdmin()` **恒为 false**，**平台超管也拿不到跨租户总览**。
  - **执行后必须清 Redis 权限缓存**，否则缓存不刷新、修复不生效（两脚本只改库，不触发 `@CacheEvict`）：
    ```bash
    docker exec -i txgy-redis redis-cli EVAL "local ks=redis.call('keys',ARGV[1]); if #ks>0 then return redis.call('del',unpack(ks)) end; return 0" 0 'menu_role_ids:*'
    docker exec -i txgy-redis redis-cli EVAL "local ks=redis.call('keys',ARGV[1]); if #ks>0 then return redis.call('del',unpack(ks)) end; return 0" 0 'permission_menu_ids:*'
    ```
  - 复核：两脚本末尾自带校验段——覆盖漏洞应为 0 行；`bidding:process-track:*` 六项权限角色数应齐平（当前各 35）。

- **个人用户（保留租户 0）的门户关注权限（2026-09-21）**：`history/2026-09-21-个人租户关注权限.sql`。
  症状：个人账号点「＋跟踪该项目」→「跟踪：租户不存在（浏览不受影响）」。
  - 根因 A（**部署**，前端/DB 无法绕过）：框架对保留租户 0 的豁免（`TenantSecurityWebFilter.PERSONAL_TENANT_ID=0`）
    由提交 `90ed16bc7` 引入，**system 服务已含、bidding 服务没有** ⇒ 个人账号调招投标接口一律
    `1002015000 租户不存在`（实测：`/system/auth/get-permission-info` code=0，`/bidding/ca/seal/check` 报租户不存在）
    ⇒ **bidding-server 需按当前框架重建+重启**。
  - 根因 B（**权限模型**）：`system_role WHERE tenant_id=0` 原本 0 条角色、`registerUser` 也不分配角色
    ⇒ 个人用户没有任何权限码，而关注域要求 `bidding:notice-external:query/update`。
  - 脚本动作：租户 0 建「个人门户用户」角色（`personal_portal`，id 70004）→ 授予
    **父目录 6735 + 承载 60006260（query）+ 6736（update）** → 绑定存量个人用户。
  - **⚠️ 父目录必须一起授**：`filterDisableMenus().isMenuDisabled()` 沿 parentId 向上递归，
    父菜单不在角色菜单集合里即判为"禁用"并剔除该菜单（`parent == null ⇒ true`）。
    实测只授叶子时 `get-permission-info` 权限码为 0，补 6735 后正常。
  - 复核：个人账号实测权限码 `["", "bidding:notice-external:query", "bidding:notice-external:update"]`。
  - 遗留（需后端二选一）：注册时自动绑定默认角色；或把 notice-follow 关注类端点改 `isAuthenticated()`（见 §34/§35）。

- **2026-09-29 权限审计批次（统一脚本）**：`history/2026-09-29-权限审计批次-菜单与授权统一.sql`。
  一个文件收敛四件事，替代原 `2026-09-29-bidding-missing-perm-menus.sql` 与 `2026-09-29-菜单祖先授权补齐.sql`（已合并删除）：
  ① **4 个缺失权限点登记**（`bidding:subject-auth:query|update`、`bidding:tender-doc:query|download`——审计发现代码里
     `@PreAuthorize` 有、`system_menu` 无承载 ⇒ 非超管恒 403）；② 角色授权（沿用所属页面既有授权）；
  ③ **菜单祖先授权断链修复**；④ **三台账重构后的菜单项去重**（条件执行）。
  **父菜单不写死**：`bidding:subject-auth:*` 优先挂三台账重构后的新主体档案页 `60006100`（设置 `60006000` 下），
  重构未执行时回落旧页 `6760` —— 两种库都能正确挂载，重构落地后**重跑本脚本自动归位**。
  **号段归属（先登记再占用）**：`60006300~60006303` = 本批次；`60006000~60006101 / 60006200 / 60006400~60006501 /
  60006601~60006603` = 三台账重构批次；`600062xx` 其余 = 流程子域批次。**禁止再向 600063xx 登记其他语义**
  （三台账重构脚本已按此让位，见其「号段让位」注释——同段不同义会导致菜单名与权限静默错位）。
  实测：4 个权限码各 **1 个承载**（无同码多承载）；断链角色 31 → **0**。
  **已并入一键包**：本脚本登记进 `sql/tools/build-credential-sync.js` 的 `MENU_EXTRA_SOURCES` 后重建
  `master-credential-sync.sql`（新装库不再缺这 4 个权限点）。改动菜单脚本后必须重跑
  `build-credential-sync.js` → `check-master-mirror.js`（否则下次重建会把这段**静默抹掉**）。
  ⚠️ 踩坑记录：打包器用 `indexOf` 找「角色授权 / 校验」两个节标题来截断，**注释里写标记原文也会被当成截断点** ——
  本文件首版把标记原文写进了说明注释，结果正文被"在注释里"截断、菜单注册整段被丢弃（镜像门禁报「缺 4 项」）。
  故本文件说明一律用「」描述节标题，不复制标记原文。

- **原「给了投标流程管理菜单权限，用户却没拿到权限」（2026-09-29）修复记录**：已并入上条统一脚本③。
  症状：角色授权界面勾选「投标流程管理」（菜单 6748）保存成功，但该角色登录后左侧看不到该菜单。
  - **根因（框架语义，不是用户操作问题）**：`MenuServiceImpl.filterDisableMenus()` → `isMenuDisabled()` 沿 `parentId`
    向上递归，且判断依据是**该角色自己的菜单集合**（`menuMap.get(parentId) == null` 即判为禁用并**从菜单树剔除**）——
    「只有子菜单、没有祖先菜单」时子菜单被静默丢弃。实测：**31 个角色持有 6748 却缺祖先 6735**。
  - 脚本动作：按角色已持有的招投标子树节点反向补齐缺失祖先（三条语句覆盖 3 层：根 6735 → 页面 → 按钮）。
    仅补**无权限码的目录**（6735 的 permission 为空）：实测执行后 6748/6749/6750/6751/6752 授权行数均**仍为 38（未变）**，
    6735 授权行由 9 → 41，即**零权限扩大、只恢复菜单可见性**；断链角色数 31 → **0**。
  - 执行后同样必须清 `permission_menu_ids:* ` 与 `menu_role_ids:*`（实测清了 5+5 个键）。
  - **同类遗留（未自动修，需授权决策）**：爬虫域 6739/6740 各 33 个角色、5805（4 个）、5700（1 个）同样"有子无父"；
    但这些父节点**携带权限码**（如 `bidding:crawl-source:query`），补授=授出一项权限，须由管理员在角色授权界面决定，未纳入本脚本。

- **门户「关注 / 跟踪公告」权限修复（2026-09-21）**：`history/2026-09-21-门户关注权限承载修复.sql`。
  症状：门户公告详情页「＋关注 / 跟踪该项目」报「无操作权限（需要 bidding:notice-external:query/update）」，
  关注按钮恒显示「需登录」——任何普通租户用户都复现。
  - **根因①**：`bidding:notice-external:query` **全库没有菜单承载**（只有 `:update` 有 6736「外部公告审核管理」）。
    实测不对称：`POST /notice-follow/follow` → code=0（有承载），`GET /notice-follow/status` 与 `/item/page` → 403。
  - **根因②**：后端把"查不到承载菜单"的结果也缓存了
    （`permission_menu_ids:bidding:notice-external:query = ["java.util.ArrayList",[]]`）⇒ **只补菜单、不清缓存仍然 403**。
  - 脚本动作：补承载菜单 `60006260 外部公告关注查询`（type=3，挂 6735 下）→ 授予**所有已持有 `:update`(6736) 的 34 个角色**
    （`tenant_id` 跟随来源行复制，避免重演上文 ① 的 `tenant_id=0` 失效）→ 并入含 6736 的租户套餐（119 / 120）。
  - 执行后同样必须清 `permission_menu_ids:*` 与 `menu_role_ids:*`（命令见上）。
  - 复核（修复后同账号实测）：`/notice-follow/status` → code=0；`/notice-follow/item/page` → code=0。
  - **遗留（需后端改，前端/DB 无法两全）**：门户关注域与「外部公告审核管理」**共用** `bidding:notice-external:update`，
    而该码的承载菜单正是审核工作台（`/manage/page`、`/approve`、`/reject`）——
    想让"普通门户用户能关注"就只能把审核工作台一起给出去。建议改 `isAuthenticated()` 或拆独立门户权限码，
    见《后端变更通知-给前端》§34。

- `2026-09-11-master-sync-removed-seeds.sql`：**归档文件**——原同步包中被剔除的业务数据种子
  （原 §⑤ 主体授权与档案、§⑥ AI 链路、§⑦ 演示公告、§⑧ AI 启用状态），确需时人工取用，不在初始包内执行；
- 其余为模块演进记录（爬虫合规频控、项目域、采集源模板、公告域字段等）。
- **电子资质域（2026-09-04 ~ 09-19，共 16 个）**：已合并进 `master-credential-sync.sql`，原脚本归档备查：
  `2026-09-04-certificate`、`2026-09-05-cert-alert`、`2026-09-06-cert-verify-history`、`2026-09-07-enterprise-qualification`、
  `2026-09-08-certificate-menu`（旧号段·**废弃**）、`2026-09-09-qualification-compliance`、`2026-09-10-qual-expiry-alert`、
  `2026-09-11-qualification-ops`（旧号段·**废弃**）、`2026-09-12-certificate-file-menu`（旧号段·**废弃**）、
  `2026-09-13-personnel-qualification`、`2026-09-14-cert-lifecycle`、`2026-09-15-personnel-lifecycle-menu`（旧号段·**废弃**）、
  `2026-09-16-rule-config-center`、`2026-09-17-safety-permit-risk`、`2026-09-18-cert-menu-renumber`（号段迁移·**新库不需要**）、
  `2026-09-19-cert-dict-register`。
  新库直接跑 `master-credential-sync.sql`；老库未追平时可继续按顺序执行这些历史脚本，或直接跑新包（幂等，等价于"追平到终态"）。

## 3. 工具（`sql/tools/`）

| 文件 | 用途 |
| --- | --- |
| `validate-sql-structure.js` | 纯文本自检：括号/引号闭合、语句截断、建表重名、**意外 DROP/TRUNCATE** |
| `build-init-from-db.js` | 从开发库重新导出种子（菜单/授权/采集源/闸位）→ `sql/.tmp/` |
| `build-init.js` | 用导出的种子重新拼装初始状态包（表结构原样保留、不改写） |
| `build-credential-sync.js` | 由 `sql/history/` 里的电子资质域源脚本重新生成 `master-credential-sync.sql`（菜单段取终态、校验段期望值自动推导） |
| `check-do-tables.js` | 一致性校验：代码里全部 `@TableName` 表是否都被两个初始化包覆盖（当前 49/49） |
| `check-do-columns.js` | 字段级校验：电子资质域 DO 的每个字段在合并包里都能找到同名列（当前 28 表 / 401 字段全通过） |
| `reset-crawl-to-zero.sql` / `.sh` | 采集域归零（**破坏性脚本**，需人工确认后执行；含 Redis 布隆与 ES 清理） |
| `restore-external-from-es.js` / `fallback-content-from-es.js` | 从 ES 恢复公告正文的应急脚本 |
| `README-数据恢复.md` | 数据恢复说明 |

校验用法：`node sql/tools/validate-sql-structure.js`（缺省校验初始状态包）。

> **程序侧兜底**：应用启动时会做库表自检（`BiddingSchemaHealthCheck`）——齐备打 OK；
> 缺表打 ERROR 并列出缺哪些表与修复命令；若缺的是爬虫关键表（源/任务/批次/限速闸/审计），
> **自动停掉爬虫调度**并拒绝人工触发，避免"任务反复重试且毫无进展"。
> 补表后重启服务即恢复；需要严格 fail-fast 的环境可置 `bidding.schema.strict=true`。

## 4. 其他域的脚本

电子资质域已收敛为 `sql/master-credential-sync.sql`（见 §1.1），不再于 `sql/` 根目录散落增量文件。
公告采集、主体、项目等域的脚本由各自负责人维护；`sql/` 根目录只保留两个一键初始化包 + 本 README + `tools/`。
`master-bidding-sync.sql` **不包含**电子资质域的表与菜单，以免与本包互相覆盖——两者可独立执行，无先后依赖。

## 5. 已移除的纯数据文件（2026-09-11 整理）

按「不要数据」口径，以下文件已从工作区移除（**均在 git 历史中，可 `git checkout <commit> -- <file>` 取回**）：

| 文件 | 原大小 | 说明 |
| --- | --- | --- |
| `history/2026-09-10-export-bidding-data.sql` | 14.9 MB | 全库数据导出（公告/任务等业务数据） |
| `history/2026-09-10-export-bidding-tables.sql` | 33 KB | bidding 业务表数据导出 |
| `history/2026-09-10-demo-data.sql` | 79 KB | 演示公告数据 |

需要重新导出时（示例）：

```bash
docker exec txgy-mysql mysqldump -uroot -p123456 --default-character-set=utf8mb4 \
  --single-transaction --no-create-info ruoyi-vue-pro bidding_notice_external > export.sql
```

## 6. 全量结构与数据快照（交付 / 备份用，2026-09-20 新增）

> 与 §1「初始状态包」（只留结构 + 最小启动数据、无业务数据）**刻意区分**：
> 以下两个文件是**运行库的完整快照**，面向交付基线、环境复现与数据迁移；
> **新环境初始化仍建议优先走 §1 的两个同步包**（口径干净、可重复执行）。

**`sql/bidding-full-schema-data.sql`**（约 1.5 MB）

| 项 | 内容 |
| --- | --- |
| 表结构 | **76 张** ＝ 61 张现有表 + 15 张最新脚本已定义、库中尚未创建的表（CA 签章 / 合同 / 异议 / 投标保证金 / 资格预审 / 招标文件下载 / 评标报告 / 智能评标） |
| 表数据 | 35 张有数据的表（字典、菜单与授权、采集源、规则阈值等；**不含**爬虫抓取大表的数据） |
| 来源 | 运行库 `ruoyi-vue-pro` 的 `mysqldump --single-transaction --complete-insert` 一致性快照 |
| 安全 | 全文**无** `DROP DATABASE` / `DROP TABLE` / `TRUNCATE`（已脚本自检：仅 CREATE TABLE IF NOT EXISTS + INSERT） |

导入：`mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < sql/bidding-full-schema-data.sql`

**`sql/bidding-full-with-crawl-data.sql.gz`**（约 11.3 MB；未压缩 126 MB）

- 在上一文件基础上**额外包含外部公告抓取数据**：`bidding_fetch_task` 1730 行、`bidding_notice_external` 1622 行等（占未压缩体积的 150 MB）；
- 使用方式：

```bash
gunzip -c sql/bidding-full-with-crawl-data.sql.gz | mysql -uroot -p123456 ruoyi-vue-pro
```

- 该 `.gz` 入库原因：未压缩 126 MB 超出代码托管单文件限制，gzip 后 11.3 MB（9%）可安全入库；
- 若只需干净结构 + 最小启动数据，请用 §1 的两个同步包，不要用本文件（会带入业务/抓取数据）。
