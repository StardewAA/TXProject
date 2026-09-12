# 自检脚本（sql/tools）

模块 SQL 包与前端页面之间的一致性由脚本把关——它们都只做静态比对，不需要连数据库，可随时跑、可进 CI。

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

**棘轮口径**：既有欠债按文件登记在 `KNOWN_HARDCODED`（逐条写明理由）——只许减少、不许增加；
新文件出现即 FAIL，在册文件变多也 FAIL。实测基线：**15 个文件 / 33 处**。迁移到字典后把处数改小
（`--update-baseline` 可打印新基线），文件彻底清干净后从表里删除。
`--list` 列出每处命中的常量名与项数。

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
