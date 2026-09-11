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
| ② | 菜单：招投标 6735 子树（含子菜单与按钮权限） | **37 条** |
| ③ | 角色菜单授权（system_role_menu） | **135 条** |
| ④ | 采集源配置（EBIDDING / QYZB / GX）——**爬虫可启动的最小数据** | **3 条** |
| ⑤ | 原文下载限速闸位（GLOBAL 单行，合规频控 2 分钟/次） | **1 行** |

执行（包尾自带校验段，执行后直接打印期望值）：

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < sql/master-bidding-sync.sql
```

实测：空库一次执行通过（结构 21 / 菜单 37 / 授权 135 / 采集源 3 / 闸位 1）；**连续执行两遍结果完全一致**（幂等）。

**不包含**（按「不要数据」口径）：采集任务/批次、公告/附件/正文、主体授权与档案、AI 渠道与密钥、演示公告。

### 1.1 电子资质域包（新环境第二包，独立执行）

**`sql/master-credential-sync.sql`**（2026-09-13 整理：把电子资质域 16 个增量脚本合并为一个一键包）

| 段 | 内容 | 规模 |
| --- | --- | --- |
| ① | 业务表结构：电子证照台账/文件版本/验真/到期预警/变更审计、企业资质与资质标准/要素/达标核查、员工资质（档案/证书/履历/社保/劳动合同）、证件全生命周期、规则配置中心与字典、安许 A/B/C 风险联动 | **29 张** |
| ② | 默认规则阈值（证照预警窗口/档位、安许风险参数、宽限期等，可后台动态改） | **28 行** |
| ③ | 枚举字典（cert_type / holder_type / review_result / material_category / material_type / safety_risk_flag / person_risk_flag / risk_level） | **104 行** |
| ④ | 菜单与权限点（60004000 段：证照台账 · 企业资质 · 员工资质 · 证件生命周期 · 安许风险联动） | **46 行** |

执行（包尾自带校验段，执行后直接打印期望值）：

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < sql/master-credential-sync.sql
```

- 与 `master-bidding-sync.sql` **无先后依赖**（本包不引用招投标主流程的表），两者合起来覆盖代码里全部 49 张 DO 表；
- 全部语句幂等（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE`），**包内无 DROP / TRUNCATE / DELETE**，可重复执行；
- 菜单号段使用 **60004000 段**（旧 60002xxx 段与外部公告采集域冲突已废弃，本包直接落终态，不再需要执行号段迁移脚本）；
- **不包含**角色授权（授权属业务决策）：超管默认放行，其他角色见包尾「授权指引」按需授权；
- 不含演示/业务数据、文件实体（file_id 由 FileApi 上传产生）、AI 渠道与密钥。

## 2. 历史增量（老库追平用；新库直接跑第 1 节即可）

`sql/history/`：按日期归档的增量脚本（建表 / 字段 / 菜单 / 配置 / 修复 / 数据补偿）。

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
