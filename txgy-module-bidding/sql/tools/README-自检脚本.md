# 自检脚本（sql/tools）

模块 SQL 包与前端页面之间的一致性由脚本把关——它们都只做静态比对，不需要连数据库，可随时跑、可进 CI。

### 事件：响应类型全量链接（71 → 99 配对）＋门禁自身的注释解析 bug（2026-09-25）

同一套方法用到**响应类**接口上：实测 30 个 `*Item/*Row/*Detail` 形态的接口没链到 `RespVO`，
逐个按 **controller 方法返回类型**（`CommonResult<XxxRespVO>` / `PageResult<XxxRespVO>`）核对后补注释。
过程中踩到并修掉一个**门禁自身的 bug**（比新增覆盖更值得记）：

```
原正则：/(\/\*\*[\s\S]{0,300}?\*\/\s*)?export\s+interface\s+(\w+)\s*(\{)/g
问题：惰性量词可以从**更早的 /** 一路吞到目标声明前**，把中间整个接口的代码和别人的注释都当成
      本接口的注释 → 取到错误的 VO 名。
实例：SourcePayload 的注释里因此混进了上一个接口字段注释中的 CrawlSourceRespVO，
      于是被链到 CrawlSourceRespVO（27 字段，报缺 createTime/updateTime），而它真正的契约是
      CrawlSourceSaveReqVO（25 字段，完全吻合）。
修法：注释块内不允许出现 */ —— /(\/\*\*(?:(?!\*\/)[\s\S]){0,400}?\*\/\s*)?/g
```

修完这一处，门禁**自己**多认出 4 个配对（疑似误链 7 → 1），并暴露出两个此前被"误链剔除"掩盖的缺口：

| 缺口 | 说明 |
|---|---|
| `QualificationAlertRespVO ← Alert` 缺 `version` | **正是卡住"预警处理"接线的那个字段**——白名单原写着"清单出参未回传 version"，实测 VO 里**有** version，只是前端 interface 没声明。已补类型；白名单理由同步更正为"前端待接线"（性质与后端缺口不同，接线后即删） |
| `QualificationPermitRespVO ← Permit` 缺 `remark` | 已补类型 |

同时发现并修掉一个**真 bug**：`CertFileVersion` 前端读的是 `sha256`，后端字段是 `fileHash`
（写入侧一直用的是 `fileHash` ✅，读取展示侧却绑 `sha256` ❌）→ 证书文件列表的「SHA-256」列**永远是空的**，
数据其实一直存着、也一直返回着。已改类型与列绑定。
另把 `SourceItem` 缺的 11 个 API 直取列补成类型（页面仍走 apiConfigJson，注释里写明"仅补类型、未接 UI"），
**类型对等基线因此清零**：99 配对 / 完整 99 / 既知不完整 0 / 反向差异 1（resultRaws，属前端内部参数）。

> 教训同上一轮：**解析型门禁的绿，先要问"我的解析器有没有骗我"**。这次它骗了我两轮（误链剔除掩盖了缺口），
> 而修好之后，它立刻报出了两个真缺口和一个真 bug。

### 事件：一次"测量后决定不建门禁"的记录 + 类型对等覆盖扩到参数类（2026-09-25）

想做一道新门禁：**查询参数名 ↔ 目标接口入参 VO 对账**（起因：内部大厅的时间筛选差点被我写成
`beginTime/endTime` 发给只认 `publishTimeStart` 的 VO——**Spring 对不认识的多余参数是静默忽略的**，
筛选会"看着在工作、实际空转"）。按"先测量、再设门禁"的规矩先写探针，结论是**不建**：

1. 写接口的参数大多通过**类型化对象**下发（`fetchXxx(params)`），不走字符串键构造，
   能扫到的构造器参数名只有 11 个 → 门禁覆盖率极低；
2. 唯一命中 `pageNo` 还是**我探针自己的假阳性**（字段池只扫了 bidding 模块，
   漏掉框架层 `PageParam`）——一个当场就误报的检查，加进去只会消耗信任；
3. 这一类风险其实**已经被既有门禁覆盖**：参数类接口 ↔ ReqVO 的双向对等检查，
   参数名写错/凭空字段都会被反向检查抓住。

> 记这一笔是为了留证据：**"没建门禁"和"没想到"看起来一样，但只有前者是判断**。

测量过程本身暴露了真问题，并已修复：类型对等的覆盖**依赖"接口注释点名 VO"**——
没点名的接口会被静默跳过。实测 12 个参数类接口没点名（`CertificateSaveReqVO`/`CertFileRegisterReqVO`/
`CertLifecycle*ReqVO`/`NoticeSaveReqVO`/`PersonnelCertificate*ReqVO`/`SafetyGraceRegisterReqVO`/
`KeyPostAssignmentCreateReqVO` 等）。逐个**按 controller 方法签名核对**（不是只凭字段重合度）后补上
"契约来源"注释，覆盖随即扩大：

| | 补注释前 | 补注释后 |
|---|---|---|
| 配对 interface | 59 | **71** |
| 完整镜像 | 58 | **70** |
| 既知不完整 | 1 | 1（`CrawlSourceRespVO ← SourceItem`，②类后端保留字段） |

顺带给门禁加了 `--pairs`：列出**全部**配对（VO ← interface、重合度、字段规模、缺/多计数）。
此前 `--list` 只打印有问题的配对，"某接口到底链到了哪个 VO"无从查看——排查同名接口
（`StatusChangeReq` 在 personnel 的两个 namespace 各有一个）时当场就需要它。

### 事件：AI 复核列接线，字典覆盖率门禁当场报警（2026-09-25）

外部公告管理页新增「AI 复核」列（用 `aiReviewStatus`+`aiReviewReason`+`aiReviewHitWords`+`aiReviewAt` 与
审核留痕 6 字段，清掉类型对等最后一条既知不完整基线：完整镜像 57 → 58、既知不完整 2 → 1）。
接线时引用了字典 `ai_review_status`（尚未注册）→ **`check-dict-coverage` 立刻 FAIL**，这正是它该有的行为。
处理方式体现门禁设计意图：不是去改门禁，也不是写死一张新的 code→中文映射表，
而是在它既有的 ALLOW 白名单里登记一条"待注册"（含契约文件 13 的指向），
前端同时采用"字典优先、未下发显示后端原始 code 并注明来源"的降级——字典一注册即自动变中文。
硬编码棘轮因此**零增长**（`data.ts` 仍是 7 张表，不新增第 8 张）。
另记两条业务语义（来自 sql/2026-09-20-notice-audit-source.sql，已在代码注释里复述）：
· AI 判 REJECT **不等于驳回**——该条保持 UNREVIEWED 转人工复核，只有人工能驳回；
· `auditStageName` 是后端合成的展示名（VO 注明"前端直接显示"），优先用它可避免前后端各写一版措辞。

### 脚本 13：类型对等（`check-type-parity.js`）—— 2026-09-25 升级为**双向**

原来只查「VO 有、前端没有」（缺口 → 界面永远拿不到某个字段）。本轮补上反向：
**「前端有、VO 没有」→ 界面读它必然是空的/拼写错位**，同样基线冻结、只减不增、陈旧条目 FAIL。
反向检查上线当天就抓到一处真实漂移：`OperateLogItem.success`——后端 VO 在同一天被同事迭代时删掉了该字段，
前端还留着声明和「结果」列（永远显示「—」）。**这类问题只有反向检查能发现**。

为做对这件事，解析器修了三个坑（都会造成假阳性/假阴性，踩过才算数）：
1. **继承**：VO 常 `extends PageParam`，`pageNo/pageSize` 不在本类体内 → 需扫全仓 Java 建索引、
   再递归合并父类字段（带环保护）；
2. **带初始化的字段**：`private Integer pageNo = PAGE_NO;` 早期正则要求"名字后紧跟分号"，
   整个 `PageParam` 的字段都读不到 → 正则须容忍 `= …`；
3. **同名类跨模块**：`QualificationRespVO` / `NoticeRespVO` 在别的模块也有同名类，
   早期按**简单类名**建索引会被覆盖 → 前端类型被拿去和"别的模块的同名 VO"比对，
   重合度骤降而被误判为"疑似误链"剔除（**掩盖了真缺口**）。
   改为**全限定名**索引 + 解析父类时优先同模块/同包/唯一候选后，
   一次性多认出 3 个配对，并暴露真缺口：门户 `InternalNoticeParams` 缺 6 个后端支持的查询字段。

> 教训：解析型门禁的"绿"只说明"按我理解的世界观没问题"。凡是靠名字/正则去理解另一种语言的代码，
> 都要先问一句"同名/继承/初始化这些常见的写法，我读全了吗"——否则假阳性会让人去修不存在的问题，
> 假阴性则会安静地掩盖真问题（本例中掩盖了 6 个字段整整若干轮）。

### 事件：枚举取值全量核对（2026-09-25）

把棘轮基线里"因字典未注册而冻结"的 16 处映射表取值，逐条与后端权威来源对照（枚举文件 / VO 字段注释），
结论：**全部一致，未发现错值或漏值**。其中两处极易误判、已固化证据备查：
· `QualificationSerialEnum` 的 `TESTING`（检测）写在**最后一行且无逗号**，按行解析容易漏掉 → 曾误判"前端多了一个值"；
· `RuleConfigStatusEnum` 有 4 个值，前端选项只列 3 个是**有意排除 ROLLED_BACK**（"只能由回滚接口产生"），不是漏值。
另发现 4 类值域**后端没有权威来源**（批次状态 / 触发方式 / 命中来源 / 预警引用类型），已在
`docs/前端规范-全域接口说明/13-枚举字典注册请求-资质规则采集公告域-2026-09-25.md` 中列为 B 类，请后端补枚举或书面确认——
前端不臆造值域。

## 一键跑全部

```bash
cd txgy-module-bidding
node sql/tools/check-all.js            # 汇总（失败项打印尾部日志）
node sql/tools/check-all.js --verbose  # 打印每项完整输出
```

退出码 0 = 全部通过；1 = 有失败项（可直接作为提交/发版门禁）。CI 里只需一行：

```yaml
- run: node sql/tools/check-all.js
```

## 各脚本职责与失败含义

| 脚本 | 校验什么 | 失败代表 |
| --- | --- | --- |
| `validate-sql-structure.js` | 语句数/建表数统计、括号与分号闭合 | SQL 被改坏（漏分号、括号不配对），导入会中途报错 |
| `verify-credential-sync.js` | 一键包内不得残留 `ALTER`/`DROP`/`TRUNCATE`/`DELETE` | 迁移语句没折叠进基表，包不可重复执行 |
| `check-do-tables.js` | 每个 `*DO` 都能在任一一键包中找到建表 | DO 有表无 DDL（启动即缺表），或表名写错 |
| `check-do-columns.js` | DO 字段在合并包中有对应列 | DO 与 DDL 字段漂移，运行期报 Unknown column |
| `check-menu-pages.js` | `type=2` 菜单的 `component` 必须有对应 `.vue` | **死菜单**：侧边栏能点、点进去 404 |
| `check-dict-coverage.js` | 前端引用的字典类型已在 `bidding_dict_data` 注册 | **英文码直出**：页面拿不到中文名与标签色 |
| `check-api-coverage.js` | 正向：后端 `/bidding` 端点都已在前端接线；反向：前端请求路径在后端都存在 | 正向失败 = **功能静默缺失**（后端有端点、前端无人调用）；反向失败 = **运行期 404**（前端调了不存在的路径） |
| `check-permission-coverage.js` | 前端判权码、后端 `@PreAuthorize`、`system_menu` 注册三处一致 | **按钮永远不可见**：码没注册就无法在「角色授权」里勾选；或**拼错码**：前端判的码后端不认 |
| `check-master-mirror.js` | `sql/history` 脚本内容是否真的进了 `master-*.sql`（按业务键比对） | **重建一键包会静默抹掉**：新装库缺字典/权限点，功能直接没了 |

`check-api-coverage.js` 的选项与口径：

- 默认汇总（未接线项逐条列出后端归属）；`--list` 全量清单（`✅` 精确命中 / `≈` 后缀命中 / `⚪` 白名单 / `❌` 待接线）；`--json` 机器可读
- 判定：先精确匹配，再按"前端路径是端点路径的后缀且 ≥2 段"匹配——后者用于应对
  `const API = \`\${baseUrl}\${apiPrefix}/notice-follow\`` 这类**配置常量拼前缀**的写法（门户站就是这么写的，
  字面量里根本没有 `/bidding`，只做精确比对会把 5 个已接线的关注端点误报成未接线）
- `ALLOW` 白名单：只应放"公开接口/程序化接口/他人在飞功能"，并在理由里说明替代路径或跟进条件
- `--orphans`：只跑反向检查（前端请求调用点 → 后端是否存在），排查"改后端路径忘了改前端"
- 反向检查**只认请求调用点**（`requestClient.get('...')`、`authedFetch(\`...\`)` 的第一个字符串参数）。
  若把任意字符串引用都算进去，`#/views/bidding/vo/...` 这类 import 路径与路由 `path` 会被误判成 56 个"孤儿"——
  实测该口径下真正的孤儿为 0，说明"只认调用点"既能查问题又不误报

`check-dict-coverage.js` 的选项：

- 默认口径 = `sql/*.sql` + `sql/history/*.sql`（判"整仓是否注册过"）
- `--masters-only`：只校验一键包（发版前用——确保**新装库**不缺字典）
- `--list`：打印全量清单（每个类型的注册行数 / 前端引用次数），排查"注册了但没人用"或"用了但没注册"
- `ALLOW` 白名单：确属有意不登记的类型写进脚本常量并注明理由（现为**空**——原 `qual_grade` 条目经核实是陈旧死配置，已删除）
- **白名单只对"当前真实引用"生效**：条目一旦不再被引用就是死配置，门禁直接 FAIL 要求删除。
  陈旧白名单比没有白名单更危险——将来前端真的引用该类型时，门禁会**静默放行**。
  汇总行也只打印"本轮命中的白名单"，不再无条件列出 ALLOW 键（那会让人误以为确有引用）

## 这些脚本抓到过的真实问题

1. **死菜单**：`master-bidding-sync.sql` 里菜单 6739 仍以 `type=2` 指向已删除的 `bidding/crawlExternal/task/index`，
   而退役动作只存在于 `sql/history/2026-09-10-menu-retire-crawl-task-page.sql` → 新装库会重建一个点进去 404 的菜单。
   修复：终态包里直接把 6739 落成工作台下的按钮（保留 `bidding:crawl-task:query` 权限码，避免授权失效），6744 重挂，6745 逻辑删除并清授权。
2. **字典名写错**：资质页读 `apply_type`/`apply_status`，而注册名是 `qual_apply_type`/`qual_apply_status`。
   写错不报错——页面只是静默显示 `DRAFT`/`APPROVED` 英文码，靠人工比对才发现。修复见 `2026-09-20-bidding-dict-register-personnel.sql`。
3. **漏注册**：员工档案页请求 `personnel_status`/`social_pay_status`/`gender`/`edu_level`/`qual_serial` 五类字典，
   库里一条种子都没有 → 下拉框空白、列表显示英文码。修复同上（22 行，逐条标注取值出处）。

4. **误判与自纠**：写 `check-api-coverage.js` 时，第一版只按字面量精确匹配，把门户站用
   `const FOLLOW_API = \`\${baseUrl}\${apiPrefix}/notice-follow\`` 拼出来的 5 个关注端点误判成"未接线"。
   加上"模板常量尾段 + 内层变量归一 + 后缀匹配"后自纠；同一轮还发现 `notice/public/detail` 其实由门户站的
   `INTERNAL_API/public/detail` 调用——**校验器的假阳性会把"已实现"误报成"缺失"，所以每次新增校验器都要做反向自测**。

## 一键包是生成物：history 必须被镜像（check-master-mirror.js）

`master-*.sql` 由 `sql/tools/build-credential-sync.js` 按**写死的来源清单**拼接。新增 `sql/history` 脚本却没加进清单时，
下次重建就会把这段内容静默抹掉——实测发生过两次（2026-09-20 字典 22 行、2026-09-21 规则配置权限点 2 行），
都是"新装库缺功能"而运行中的库看不出来。

该脚本按**业务键**比对而非自增 id（号段迁移会换 id，按 id 比会全是假阳性）：
`bidding_dict_data` 用 (dict_type, dict_value)，`system_menu` 用 permission 码，`system_role_menu` 跳过（授权属部署态）。
实测 57 个脚本 / 279 个键，零假阳性。文件头写了「新库不需要」的迁移脚本自动跳过。

## 接口接线覆盖率的白名单口径（必须逐条写理由）

`check-api-coverage.js` 的 ALLOW 里每条都要写清"为什么不需要前端调用"或"为什么暂时接不了"，并按下列约定维护：

| 端点 | 理由 | 何时删除 |
|---|---|---|
| `/bidding/notice-external/ingest` | 入参是采集侧 DTO，属爬虫/脚本程序化入库，后台不应提供人工表单 | 长期（设计使然） |
| `/bidding/qualification/alert/handle` | **后端契约缺口**：请求 VO 要求 `version` 做 CAS，但清单出参 `QualificationAlertRespVO` 不回传 version、也无 detail 端点 → 前端拿不到版本号，接了只能发假版本（恒冲突或绕过 CAS） | 后端出参补上 `version` 后**立即删除并接线** |

**白名单不是垃圾桶**：本轮就清掉了一条已过期的 `/bidding/safety-permit/project/key-post-assignment`（该端点早先因"另一开发者在飞、语义未稳定"入表，后来前端已接线）。过期的白名单项会**掩盖将来重新断线**，所以接线完成后要同步删除。

## 出参消费审计（audit-respvo-fields.js，只报告、不入 CI）

审的是"**后端算了、页面没接**"的字段：用户看不到的数据，而且编译期完全静默（前端类型若也没声明该字段，
连 vue-tsc 都不报）。跑法：`node sql/tools/audit-respvo-fields.js`（加 `--all` 连"已确认有意为之"一并列出，
加 `--strict` 时有未归类命中则退出 1，供将来按需接入 CI）。

实测口径（2026-09-22 复审基线）：后端 63 个 RespVO / 860 个字段，未被前端引用 **18** 个；其中 17 个已确认有意为之
（采集源 api* 扁平字段、结构化解析诊断量、AI 抽取诊断量、字典复合键），**1 个待确认**：`CrawlParseTestRespVO.urlIncomplete`
（解析测试回执里的"附件 URL 不完整"标记；该页目前只渲染解析状态与错误，附件候选/匹配元数据/正文预览都未渲染，
属"整份诊断载荷没用上"，见下条待办）。历史轨迹：首版审计 55 → 纳入门户后 28 → 本轮修复后 18。

两条踩过的坑，写在这里免得重犯：
1. **扫描范围必须覆盖整个前端 monorepo**：只扫 `apps/web-antd` 时报 55 个，把门户应用一并纳入后降到 28 个
   —— `contentHtml` / `bidSectionNames` 等是门户消费的，管理端用 `contentHtmlSafe` / `bodyBlocksJson`。
2. **前端类型漏字段会让缺口彻底静默**：`KeyPostAssignmentResp` 曾只声明 7 个字段、漏掉后端实际返回的
   7 个风险字段（含 `exceeded`/`windowChangeCount`/`threshold`），于是"达阈值当场提示挂靠嫌疑"这条
   后端明写的需求在页面上根本没实现，而编译与 lint 全绿。补齐类型后 vue-tsc 立刻报出 12 处引用错误。

## 接入 CI 与本地钩子

门禁不接自动化就等于没有。三处已就位（**均零依赖**，只用 Node 内置模块，不需要 npm install）：

| 位置 | 文件 | 触发时机 |
|---|---|---|
| GitHub / 镜像仓库 | `.github/workflows/bidding-gates.yml` | push、PR、手动 workflow_dispatch |
| **Gitee Go（本仓库远端）** | `.workflow/bidding-gates.yaml` | push（含 `feat/*`）、PR |
| 本地提交前（可选） | `.githooks/pre-commit` | 仅当暂存改动涉及 SQL / 前端 bidding / 后端 bidding 控制器时 |

本地启用钩子（每仓库一次）：`git config core.hooksPath .githooks`；确需跳过：`git commit --no-verify`。
若在 Linux/macOS 上提示 `Permission denied`（Windows 提交时 `core.fileMode=false`，执行位可能没进索引）：
`chmod +x .githooks/pre-commit` 或 `git update-index --chmod=+x .githooks/pre-commit`。
钩子对无关改动（如纯文档）直接放行，不会拖慢日常提交。

CI 与本地命令完全一致：`node txgy-module-bidding/sql/tools/check-all.js`，**退出码非 0 即失败**。

> 脚本刻意兼容 **Node 14**：Gitee Go 的 `nodeVersion` 仅支持 8.16.2 / 10.17.0 / 12.16.1 / 14.16.0 / 15.12.0。
> 因此门禁脚本内不使用 `String.replaceAll`、`Array.at`、`??=`、`Object.hasOwn`、`structuredClone` 等新特性
> （曾因 `check-dict-coverage.js` 用了 `replaceAll` 而在 Gitee Go 上会直接崩，已改为 `split('\\').join('/')`）。

## 枚举不得硬编码（check-hardcoded-enums.js，棘轮式）

字典门禁只能发现"引用了未注册的字典"，**看不见"前端干脆自己写死一张 code→中文 映射表"**——这类硬编码在页面上
看不出异常，却让后台改字典文案不生效、多租户/多语言差异无从落地（用户硬约束：状态码/枚举值必须支持动态配置）。

检测两类形态（要求 SCREAMING_SNAKE code 与中文文案成对出现，避免误报）：对象映射
`const X = { CODE: '中文' }` / `{ CODE: { text|label: '中文' } }`，以及选项数组 `[{ label: '中文', value: 'CODE' }]`。

**已知盲区**：只认"具名常量"——模板内联的枚举映射（如 `<Select.Option value="BIDDER">投标人</Select.Option>`、
`:options="[{ label: '投标人 BIDDER', value: 'BIDDER' }]"`）不会被识别。实测 `subjectAuth/index.vue` 就有两处，
是人工迁移时才发现并收口的。约定替代：**枚举类文案一律收口到具名常量或字典**（收口后本门禁即可见）。

**扫描根（2026-09-25 扩面）**：原只扫 `views/bidding` + `api/bidding`，**漏掉门户前台**。
门户与后台是同一批值域（公告状态/内容形态/公告类型/关注类型/跟踪阶段），后台走字典、门户写死中文 ⇒ 字典改名即前后台不一致，
而门禁看不见。纳入 `views/portal/bidding` 后一次抓出 **6 个文件 / 10 处**，逐条登记（含"可迁移"与"演示数据永久豁免"两类）；
全量基线因此从 15 文件/33 处变为 **21 文件/43 处**（新增部分全部来自门户，非新欠债产生）。
教训与类型对等门禁那条同源：**门禁的扫描范围本身就是口径的一部分，范围缺口=口径缺口**。

**棘轮口径**：既有欠债按文件登记在 `KNOWN_HARDCODED`（逐条写明理由）——只许减少、不许增加；
新文件出现即 FAIL，在册文件变多也 FAIL。实测基线：**15 个文件 / 33 处**。迁移到字典后把处数改小
（`--update-baseline` 可打印新基线），文件彻底清干净后从表里删除。
`--list` 列出每处命中的常量名与项数。

## 类型对等（check-type-parity.js）

**同一类缺陷咬过三次**：`KeyPostAssignmentResp` 只声明一半字段（挂靠嫌疑告警拿不到数据）、
`ParseAttachment` 漏 `urlIncomplete`、`ParseEntry` 漏 `date`/`category`（解析诊断面板做不出来）。
`audit-respvo-fields.js` 只能按"字段名是否在全仓出现"比对，`date`/`category` 这种**通用词**会被别处同名用法掩盖
而漏报；本门禁**按类型对类型**比对，没有这个盲区。

判定口径（先量化后定阈值）：
· 只认**注释里点名了 VO 的前端 interface**，不猜、不按名字硬凑；
· 候选 = 被点名的 VO ∪ 它的嵌套类（前端类型常对应嵌套行）；取**字段重合度最高**者，且必须 ≥ 0.6 —
  实测 < 0.6 的基本都是误链（如 `QualificationPermitPageReqVO ← Permit` 重合 0.10）；
· 重合度 = 前端字段中能在该 VO 找到同名的比例；1.00 = 前端类型是后端 VO 的严格子集（可信）；
· 缺失字段超基线即 FAIL；基线 `KNOWN_INCOMPLETE` **只许减少**，补全后不删条目同样 FAIL（陈旧基线会掩盖重现的缺口）。

**实测（2026-09-25）**：后端 VO（含嵌套）152 个、前端可链接 interface 57 个 → **完整镜像 53 个**、
不完整 4 个（均已按 VO←前端 逐条冻结并写明"真缺口/后端保留字段"）：
`CrawlSourceRespVO ← SourceItem`（11，API 直取展开列，页面走 apiConfigJson 整体配置）、
`NoticeExternalManageRespVO ← ManageItem`（10，审核留痕 + AI 复核命中词——真缺口）、
`PersonnelProfileSaveReqVO ← SaveReq`（4，岗位名/档案号/乐观锁 version——真缺口）、
`NoticeRespVO ← InternalNoticeItem`（1，internalFlag 内部公告标记——真缺口）。

## 字典可读性（check-dict-permission.js）

「页面用字典」与「用它的角色读得到字典」是两件事：`/bidding/meta/dicts|configs` 的 `@PreAuthorize` 只列了
5 个电子证照/资质/人员域权限，而这两个接口是招投标全域页面共用的前置元数据。只持有其它页面权限的角色调用会 403，
页面**静默回落英文 code**——界面上看不出坏，只是中文变英文。

判定口径：只看调用 `useCertMeta()` 的文件；其"可读性证据" = 文件内引用的 bidding 权限码 **∪ 该页面菜单的页权限**
（从 sql 的 `system_menu` 解析 component → permission）。**只看文件内的 `can(...)` 会误判**：
`certificate/lifecycle` 页自身只引用 `lifecycle:apply/audit`，而它的菜单页权限 `bidding:certificate:lifecycle:query`
本就在可读集合内。两者与可读集合无交集即 FAIL；纯子组件（无权限码）跟随父页面，跳过。

实测：15 个消费字典的文件 → 5 个页面自身命中、9 个子组件跟随、**1 个真缺口**（`project/index.vue`：页权限
`bidding:project:query` 不在集合内），已登记 KNOWN_GAP 并注明契约出处
（`docs/前端规范-全域接口说明/12-字典元数据读取权限契约-2026-09-25.md`）。
**前端只提契约，不自行改权限**；后端放开后更新脚本常量 `META_READABLE` 并清空 `KNOWN_GAP`。
与字典门禁同理，KNOWN_GAP 陈旧（缺口已消失）也 FAIL。

## 权限码三处一致（check-permission-coverage.js）

前端 `can('bidding:xxx:yyy')` 里的码要真的可用，必须同时满足：后端接口 `@PreAuthorize` 认它、`system_menu` 注册了它。
缺前者 = 点下去 403；缺后者 = 「角色授权」界面里没有这一项 → 任何角色都授不出去 → 按钮永远不渲染。两种都不报错。
该脚本实测抓到过：`bidding:project:delete`（后端根本没有这个码，删除端点复用 `bidding:project:update`）、
`bidding:rule-config:query/update`（后端与前端都在用，但 `system_menu` 从未注册 → ⑤ 规则配置面板对普通角色整块不可见）。

## 事故记录（每条都对应一次真实故障/缺口）

| 日期 | 现象 | 根因 | 现在的守门人 |
|---|---|---|---|
| 09-13 | 字典名写错 `apply_type`（实际 `qual_apply_type`），前端下拉空白 | 前端凭记忆写字典名 | `check-dict-coverage.js` |
| 09-20 | 5 类员工域字典未注册，状态/学历/社保只能显示英文 code | 新页面引用了没登记的枚举 | `check-dict-coverage.js` |
| 09-20/21 | 一键包生成器来源清单漏登记 → 重建时**静默抹掉**22 行字典与 2 个权限点（实测连抹两次） | `build-credential-sync.js` 写死的 SOURCES 清单 | `check-master-mirror.js` |
| 09-21 | `bidding:project:delete` 后端根本不存在；`bidding:rule-config:*` 从未注册 → 按钮永远不可见 | 判权码三处（前端/后端/菜单）不一致 | `check-permission-coverage.js` |
| 09-21 | 清理 Windows 下提交的 `100644` 钩子（Linux 上 Permission denied，钩子静默不跑） | `core.fileMode=false` 不记录执行位 | `git update-index --chmod=+x` + README 说明 |
| 09-22 | `KeyPostAssignmentResp` 前端类型漏 7 个风险字段 → "达阈值当场提示挂靠嫌疑"这条后端明写的需求根本没实现，而编译/lint/门禁全绿 | 前端出参类型是后端 VO 的不完整镜像 | 补类型后 vue-tsc 报 12 处；`audit-respvo-fields.js` 可复审 |
| 09-22 | 到期预警"闭环处理"缺出口：端点已加但前端接不了 | 出参缺 `version`（请求侧 CAS 必填） | 覆盖率白名单登记理由，待后端补字段 |
| 09-24 | 字典门禁白名单里的 `qual_grade` 是**陈旧死配置**：前端全仓并无任何 `dictLabel('qual_grade')` 调用（仅两行注释提及），而后端 `/bidding/qualification/standard/options` 明确"取自现行标准库，不固化字典" | 汇总行无条件打印 ALLOW 键，看起来像"确有引用"；陈旧白名单会成为将来真实引用的静默盲区 | 删除条目 + 门禁新增"陈旧白名单即 FAIL"；前端两处注释由"未注册/仍缺"改为"不是字典（值域由标准库数据决定）" |

## 新增校验器的约定

- 只读、零副作用；不连数据库
- 失败必须**能被看见**：打印具体文件/类型/菜单 id，并给出修复方向
- 退出码 1，便于 CI 拦截
- 新增校验器后：加进 `check-all.js` 的 `CHECKS`，并补到本文件表格

## 单位语义对等（check-unit-semantics.js，第 14 道）

**由来**：类型对等门禁只验"字段在不在"，不验"这个数是什么单位、管什么事"。用户 2026-09-25 直接问到
"下载器的时间间隔是列表的爬取间隔还是详细页的爬取间隔"——后端 DO 注释里写着"一个源一把尺子（同时管列表轮询与原文下载）"，
但 VO 的 `@Schema` 只写"抓取间隔（秒，频控；默认 3600）"，界面 5 种写法、无一处说归属 ⇒ **接口含义没对齐**，
而门禁看不见。实测：后端带单位语义的数值字段 **22 个**，前端有单位注释的只有 5 个。

**口径**：只认数值类型字段（布尔排除——"是否存在覆盖**今天**的劳动合同"会被"天"误匹配）；
单位词限定 秒/毫秒/分钟/小时/天/字节/KB/MB；前端同名声明（`api/bidding/**` + 门户 `views/portal/bidding/types.ts`）
必须有 3 行内的单位注释，缺即 FAIL。**无基线**：这条门禁要求"零缺注释"，因为缺单位的数字无法判断量纲。

**反向验证（必须有）**：临时删掉 `api/bidding/crawlExternal/index.ts` 里 `fetchIntervalSeconds` 的单位注释
→ 门禁 FAIL 并点名该文件行号；恢复后转绿。新增门禁不做反向验证等于没门禁。

**顺带发现（结构性）**：门户 `views/portal/bidding/types.ts` 自建了一套与 `api/bidding` 并行的类型，
所以它同时逃过类型对等与单位语义两道门禁——本门禁已把该文件纳入扫描。

## 内部术语直出（check-internal-terms.js，第 15 道）

**由来**（用户 2026-09-25"数据胡乱展示"）：采集控制台把 `scope`/`worker` 直接当列名、规则配置面板把
`traceId` 当列名、资质页把 6 个英文业务码（NEW/UPGRADE/RENEW/ADDITION/CHANGE/CANCEL）直出为筛选项文案，
甚至用**手输英文 code 的输入框**筛申报状态 —— 业务人员在中文界面里看到的是开发者的字段名。

**与第 11 道的关系**：枚举棘轮只认**具名常量**里的 code→中文映射，README 早已记录"内联在模板里的枚举映射"
是它的盲区（当时只能靠约定）；本门禁正是补这块：扫 `title:'…'`/`label:'…'`/`<Descriptions.Item label>`/`<th>`，
**不含中文即命中**，除非在 ALLOW（标准术语 URL/JSON/SHA-256/robots/API…）。

**实测**：全 bidding 域 + 门户共 80 个文件，命中 16 处 → 甄别后 **5 处为真问题**（scope/worker×2/code/traceId）
+ 资质页 6 个英文码（改具名常量后由第 11 道接手）+ 若干标准术语（入 ALLOW）。修完输出
`[OK] 未发现内部术语直出`。

**反向验证**：把 `source-detail.vue` 的"执行节点"临时改回 `worker` → 门禁 FAIL 并点名行号；恢复后转绿。

**顺带修掉的交互矛盾**：资质页申报状态原为"手输状态 code（如 SUBMITTED）"输入框 → 改中文下拉
（值域取自后端 `QualificationApplyPageReqVO.status` 的 @Schema）；申报类型原 6 个英文码 → 只留
RENEW 延续（UPGRADE 升级置 disabled 并注明"后端暂未支持"）；其余 4 码后端 Schema 未收录，已写进契约 13 第八节请后端确认。
