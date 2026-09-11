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
| `check-api-coverage.js` | 后端 `/bidding` 端点都已在前端接线 | **功能静默缺失**：后端有端点、前端无人调用 |

`check-api-coverage.js` 的选项与口径：

- 默认汇总（未接线项逐条列出后端归属）；`--list` 全量清单（`✅` 精确命中 / `≈` 后缀命中 / `⚪` 白名单 / `❌` 待接线）；`--json` 机器可读
- 判定：先精确匹配，再按"前端路径是端点路径的后缀且 ≥2 段"匹配——后者用于应对
  `const API = \`\${baseUrl}\${apiPrefix}/notice-follow\`` 这类**配置常量拼前缀**的写法（门户站就是这么写的，
  字面量里根本没有 `/bidding`，只做精确比对会把 5 个已接线的关注端点误报成未接线）
- `ALLOW` 白名单：只应放"公开接口/程序化接口/他人在飞功能"，并在理由里说明替代路径或跟进条件

`check-dict-coverage.js` 的选项：

- 默认口径 = `sql/*.sql` + `sql/history/*.sql`（判"整仓是否注册过"）
- `--masters-only`：只校验一键包（发版前用——确保**新装库**不缺字典）
- `--list`：打印全量清单（每个类型的注册行数 / 前端引用次数），排查"注册了但没人用"或"用了但没注册"
- `ALLOW` 白名单：确属有意不登记的类型写进脚本常量并注明理由（现仅 `qual_grade`：国标表110 等级无稳定 code 口径）

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

## 新增校验器的约定

- 只读、零副作用；不连数据库
- 失败必须**能被看见**：打印具体文件/类型/菜单 id，并给出修复方向
- 退出码 1，便于 CI 拦截
- 新增校验器后：加进 `check-all.js` 的 `CHECKS`，并补到本文件表格
