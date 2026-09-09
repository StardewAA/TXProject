# bidding 模块 SQL 完整执行清单（小组成员环境初始化用）

> 用途：让团队成员/演示环境与本开发库一致（16 张表 + 演示数据 + 菜单权限 + 爬虫源配置）。
> 业务库：`ruoyi-vue-pro`（bidding 全部业务表）；菜单脚本需在 **system 库**执行（system_menu 属 system 服务库）。
> 全部 DDL/配置脚本幂等（IF NOT EXISTS / ON DUPLICATE KEY）为主，可重复执行；**演示数据文件除外**（勿重复导入）。

## 目录说明（2026-09-10 起分类维护）

- `sql/history/`：**历史批次**（2026-09-03 ~ 2026-09-09：建表 DDL/菜单/早期修复，环境从零初始化按 §0 顺序执行即可）；
- `sql/latest/`：**最新批次**（2026-09-10 起：采集源配置/授权调整/索引与数据补偿等增量脚本，按日期与提交说明回放）；
- 本文件为总索引与执行顺序说明；新 SQL 一律按日期落 `latest/`，跑稳并归档后（或跨批次后）移入 `history/`。

## 0. 文件清单与执行顺序（history 部分）

| 顺序 | 文件 | 内容 | 执行库 |
| --- | --- | --- | --- |
| 1 | sql/history/2026-09-03-notice.sql | 公告/公示主档+正文（T轨） | ruoyi-vue-pro |
| 2 | sql/history/2026-09-03-filing.sql | 备案（T轨） | ruoyi-vue-pro |
| 3 | sql/history/2026-09-03-external-notice.sql | 外部公告主档/附件/meta + 采集源/任务/批次 + 关注订阅/关注项/事件/订阅事件（G/T 轨全套） | ruoyi-vue-pro |
| 4 | sql/history/2026-09-05-external-notice-review.sql | 外部公告审核字段 | ruoyi-vue-pro |
| 5 | sql/history/2026-09-06-ai-extract.sql | AI 结构化抽取字段 | ruoyi-vue-pro |
| 6 | sql/history/2026-09-07-body-blocks.sql | 正文结构化字段 | ruoyi-vue-pro |
| 7 | sql/history/2026-09-09-subject.sql | 主体档案/授权/经办用户（T/G 轨） | ruoyi-vue-pro |
| 8 | sql/history/2026-09-03-notice-menu.sql | 公告域菜单（60001000 目录 + 权限按钮） | **system** |
| 9 | sql/history/2026-09-08-notice-external-menu.sql | 外部公告/关注 + 采集管理菜单（60001200/1、60002000 目录 + 60002100~106） | **system** |
| 10 | sql/history/2026-09-09-subject-menu.sql | 主体域菜单（60003000 目录 + 按钮） | **system** |
| 11 | sql/latest/2026-09-10-crawl-source-config.sql | 采集源配置（QYZB 六栏目/GX） | ruoyi-vue-pro |
| 12 | **sql/latest/2026-09-10-demo-data.sql** | 演示数据：外部公告 ACTIVE 5 条（大厅可见）+ 采集源 3 条 | ruoyi-vue-pro（**勿重复执行**） |
| 13 | sql/latest/2026-09-10-backfill-external-notice-publish-time.sql | 存量 publish_time 回填（可选） | ruoyi-vue-pro |

## 0.1 近期增量脚本（sql/latest，按需/按提交说明回放）

- 菜单与租户授权：2026-09-09-menu-permission-fix / 2026-09-09-notice-menu-under-bidding / 2026-09-09-process-track-perm-align / 2026-09-09-tenant-bidding-grant-sample（history）、2026-09-10-external-admin-menu-pages、2026-09-10-revoke-platform-menus-tenant
- 采集与来源配置：2026-09-10-crawl-source-window-split / crawl-task-payload-queue / fetch-job-produced / fetch-rename-source-type / qyzb-api-source-sample / qyzb-six-columns / qyzb-template-inherit / gx-three-columns / source-config-correct
- 数据修复与补偿：2026-09-10-ai-config-restore / source-name-backfill / external-notice-pdf-download-url / batch-approve-external / tenant1-tenderer-grant
- 导出工具：export-bidding-data / export-bidding-tables

## 1. 快速命令（业务库 ruoyi-vue-pro，cwd=仓库根）
```bash
mysql -uroot -p123456 ruoyi-vue-pro < sql/history/2026-09-03-notice.sql
mysql -uroot -p123456 ruoyi-vue-pro < sql/history/2026-09-03-filing.sql
mysql -uroot -p123456 ruoyi-vue-pro < sql/history/2026-09-03-external-notice.sql
mysql -uroot -p123456 ruoyi-vue-pro < sql/history/2026-09-05-external-notice-review.sql
mysql -uroot -p123456 ruoyi-vue-pro < sql/history/2026-09-06-ai-extract.sql
mysql -uroot -p123456 ruoyi-vue-pro < sql/history/2026-09-07-body-blocks.sql
mysql -uroot -p123456 ruoyi-vue-pro < sql/history/2026-09-09-subject.sql
mysql -uroot -p123456 ruoyi-vue-pro < sql/latest/2026-09-10-crawl-source-config.sql
mysql -uroot -p123456 ruoyi-vue-pro < sql/latest/2026-09-10-demo-data.sql
```

## 2. 菜单权限（system 库）与角色挂载
- 执行 sql/history/2026-09-03-notice-menu.sql、sql/history/2026-09-08-notice-external-menu.sql、sql/history/2026-09-09-subject-menu.sql（system 库）；
- 菜单行只注册权限点，需挂角色（system_role_menu）后对应用户重新登录：
```sql
SELECT id, name FROM system_role;  -- 找到目标角色 role_id
-- 示例：给 role_id=2 挂 09-08/09-09 菜单（按需改 role_id）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 2, id, '1', NOW(), '1', NOW(), b'0', 0 FROM system_menu
 WHERE id IN (60001200,60001201,60002100,60002101,60002102,60002103,60002104,60002105,60002106,60003000,60003100,60003101,60003200,60003201);
```

## 3. 成员常见问题速查

| 现象 | 原因 / 处理 |
| --- | --- |
| 接口报表不存在（如 bidding_subject_profile） | 漏跑对应 DDL（第 7 或第 3 步），按顺序补执行 |
| 大厅/列表看不到公告 | 未导入演示数据（第 12 步），或数据 status 非 ACTIVE（NEW 待审核不上大厅，需 approve；批量放行见 sql/latest/2026-09-10-batch-approve-external.sql） |
| 关注/审核/采集接口 403 | 菜单脚本未在 system 库执行，或角色未挂 system_role_menu（§2）；租户菜单口径见 sql/latest/2026-09-10-revoke-platform-menus-tenant.sql |
| crawl-parser/list 空或 404 | 后端需为含 parser 插件的最新构建；权限码 crawl-source:query（60002100） |
| 时间字段是毫秒数字 | 平台统一序列化（与 system 一致）；前端用 dayjs(Number(v)) 格式化 |
| 采集源跑不动 | enabled=1 且 fetch_interval_seconds 至少 120；人工触发 POST /admin-api/bidding/crawl-source/run?sourceCode=QYZB |
| 前端 48080 网关 404（No static resource） | 网关路由需含 bidding（bidding-admin-api）；见 docs/开发联调与运维/爬虫开发-会话状态与断点.md §5 |
| 关键词检索为空 | bidding.es.enabled=true 后执行 POST /bidding/notice-external/reindex 与 /bidding/notice/reindex(-all) 补偿存量 |

## 4. 版本配套
- 后端：txgy-module-bidding-server 最新构建（9 解析器 + 下载器 v1 + ES 启动探活）；
- 菜单 id 段 6000xxxx 冲突时自行改 id（脚本头有说明）；
- 演示数据中 EBIDDING 源的 origUrl 指向 ebidding.com.cn（旧演示域名，仅展示用）；QYZB 演示公告来自真实站点采集。

---

## 附：编写说明（本文档尾部固定段）

### 编写原因
小组成员环境缺表/缺数据/权限报错，需要一套完整、有序、可照做的 SQL 执行包与排错清单；2026-09-10 按"历史/最新"双目录归类便于追批次。

### 编写依据
模块 sql/ 下既有脚本（DDL/菜单/配置）+ 2026-09-10 开发库演示数据导出（mysqldump ACTIVE 外部公告与采集源）+ 当日增量脚本提交记录。

### 所满足的业务需求内容及其实现方式
"全员可联调"：统一建表（16 张）、演示数据（大厅 5 条 ACTIVE + 3 采集源）、菜单权限与角色挂载指引、频控与网关口径，使各成员环境与开发库对齐。
