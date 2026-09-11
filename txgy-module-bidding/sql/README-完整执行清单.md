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

## 2. 历史增量（老库追平用；新库直接跑第 1 节即可）

`sql/history/`：按日期归档的增量脚本（建表 / 字段 / 菜单 / 配置 / 修复 / 数据补偿）。

- `2026-09-11-master-sync-removed-seeds.sql`：**归档文件**——原同步包中被剔除的业务数据种子
  （原 §⑤ 主体授权与档案、§⑥ AI 链路、§⑦ 演示公告、§⑧ AI 启用状态），确需时人工取用，不在初始包内执行；
- 其余为模块演进记录（爬虫合规频控、项目域、采集源模板、公告域字段等）。

## 3. 工具（`sql/tools/`）

| 文件 | 用途 |
| --- | --- |
| `validate-sql-structure.js` | 纯文本自检：括号/引号闭合、语句截断、建表重名、**意外 DROP/TRUNCATE** |
| `build-init-from-db.js` | 从开发库重新导出种子（菜单/授权/采集源/闸位）→ `sql/.tmp/` |
| `build-init.js` | 用导出的种子重新拼装初始状态包（表结构原样保留、不改写） |
| `reset-crawl-to-zero.sql` / `.sh` | 采集域归零（**破坏性脚本**，需人工确认后执行；含 Redis 布隆与 ES 清理） |
| `restore-external-from-es.js` / `fallback-content-from-es.js` | 从 ES 恢复公告正文的应急脚本 |
| `README-数据恢复.md` | 数据恢复说明 |

校验用法：`node sql/tools/validate-sql-structure.js`（缺省校验初始状态包）。

## 4. 其他域的脚本（不在初始包内）

`sql/2026-09-*.sql`（电子证照、人员资质、规则配置中心、安许风险联动等）由相应负责同事维护。
本初始状态包**不包含其表与菜单**，以免互相覆盖；各文件按其自身说明执行。

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
