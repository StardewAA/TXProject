# IM 即时通讯模块 — 需求分析与开发计划

> 分析日期：2026-09-08
> 分析范围：管理后台（`txgy-ui-admin-vben/apps/web-ele`）IM 模块 + 后端（`txgy-module-im`）
> 重要结论先行：**IM 模块后端接口与前端页面框架已基本完整（管理端 13+ Controller、17 张表、菜单 SQL 齐全），本次工作重心是"补齐管理页面缺口 + 逐模块增强 + 验证"，不是从零开发。**

---

## 一、现状盘点

### 1.1 技术栈

| 项 | 说明 |
|---|---|
| 前端 | Vue 3 + Vben Admin 5 + Element Plus + TypeScript + Vite（pnpm monorepo，应用为 `apps/web-ele`） |
| 后端 | Spring Boot（`txgy-module-im` → `txgy-module-im-server`），IM 工作台走 WebSocket + LiveKit 音视频 |
| 菜单 | `system_menu` 动态下发（id 1418~1475），与截图菜单树完全一致 |
| 数据库 | `im_*` 17 张表，初始化脚本 `deploy/mysql/init/0032/0033`，全量备份 `0069` |

### 1.2 截图菜单对照（差距表）

| 菜单项 | 后端接口 | 前端页面 | 状态 |
|---|---|---|---|
| 数据统计 | overview/趋势/分布/TOP 共 6 个接口 | KPI + 双趋势图 + 分布图 | ✅ 完整 |
| 好友列表 | 仅 `page` | 仅列表 + 跳对话 | ⚠️ 半成品 → **M1 补齐** |
| 好友申请 | 仅 `page` | 纯只读列表 | ⚠️ 半成品 → M2 |
| 好友消息 | `page` + `get` | 列表 + 详情弹窗 | ✅ 完整 |
| 群聊列表 | page/get/ban/unban/dissolve + 成员列表 | 列表 + 详情 + 封禁/解封/解散 | ✅ 完整 |
| 群聊申请 | 仅 `page` | 纯只读列表 | ⚠️ 半成品 → M3 |
| 群聊消息 | `page` + `get` | 列表 + 详情 | ✅ 完整 |
| 通话记录 | page/get/participant-list | 列表 + 详情 + 参与者 | ✅ 完整 |
| 敏感词/表情/频道管理（菜单在截图外） | CRUD 齐全 | CRUD 齐全 | ✅ 完整 |

其他横切结论：
1. `txgy-module-im-api`（RPC 契约层）为空，IM 无法被其他微服务调用——暂不影响本计划；
2. IM 管理端代码在 web-ele / web-antd / web-antdv-next 三处重复，本计划先落在 **web-ele**（与部署前端一致），后续按需同步；
3. IM 工作台（用户端聊天，`views/im/home`）功能已非常完整，本计划不触碰。

## 二、与库中其他模块的冲突检查（结论：✅ 无冲突）

- 当前分支 `feat/pay-module-enhance`，未提交变更集中在 `deploy/`、`script/`、MES 相关文件，与 IM 改动目录（`txgy-module-im/`、`apps/web-ele/src/{views,api}/im/`、`docs/`）零交集；
- 后端 17 张 `im_*` 表与菜单 SQL 已存在，无需改表结构、无需动其他服务；
- 建议后续提交使用独立分支 `feat/im-module-enhance`，避免与支付/MES 分支混淆。

## 三、开发计划（一次一个小模块，确认后再进行下一个）

| 里程碑 | 内容 | 类型 | 状态 |
|---|---|---|---|
| **M1** | **好友列表增强**：① 关系详情抽屉；② 管理端「解除好友关系」（双向软删 + 双方多端 FRIEND_DELETE 通知 + 缓存失效）；后端新增 `GET /im/manager/friend/get`、`DELETE /im/manager/friend/delete`（权限 `im:manager:friend:delete`）；菜单按钮 SQL `docs/sql/im-friend-manager-delete-button.sql` | 前后端补缺口 | ✅ 完成并验收 |
| M2 | 好友申请页增强：详情抽屉 + 删除申请记录（治理动作，逻辑删除，不影响好友关系）；权限 SQL `docs/sql/im-friend-request-manager-delete-button.sql`（含租户管理员自动授权） | 补缺口 | ✅ 完成并验收 |
| M3 | 群聊申请页增强：详情抽屉 + 删除申请记录；权限 SQL `docs/sql/im-group-request-manager-delete-button.sql` | 补缺口 | ✅ 完成，待验收 |
| M4 | 端到端验证（管理页全菜单走查）+ 独立分支合并 git + 遗留优化项 | 验证收尾 | 待启动 |

### M2/M3 补充说明

- **双端同步规则**（重要教训）：部署的前端是 `apps/web-antd`（Dockerfile `build:antd`），所有前端改动必须**先改 web-ele 出方案，再同步 web-antd 才能部署生效**；两个应用的组件库不同（Element Plus vs Ant Design Vue），需按各自范式重写。
- **SQL 执行方式**：PowerShell 管道执行含中文的 SQL 会静默失败，必须用 `docker cp` 拷入 mysql 容器内执行。
- **数据权限修复**（随 M2 部署）：`AdminUserServiceImpl#getUserListByNickname` 加 `@DataPermission(enable = false)`，加好友搜索按租户全量用户进行（否则"自定义部门"范围为空的角色搜不到任何用户）。
- **UserSimpleRespVO 增加 username 字段**（随 M2 部署）：加好友搜索结果显示 `@账号名` 区分同名用户。

### M1 设计要点（产品 + 技术决策记录）

1. **语义选择**：用户端删好友是"单边删除"（我删 TA，TA 列表仍保留）；管理端解除是**治理动作**，采用双向删除（A→B、B→A 两条记录同时置 DISABLE），并对双方多端推送 FRIEND_DELETE 通知（不入库，前端渲染瞬时气泡），保证两端 IM 工作台状态一致。
2. **幂等**：双向均已 DISABLE 时静默返回，不重复通知。
3. **缓存一致性**：好友状态 Redis 缓存（FRIEND_STATE）双向手动失效（`@Caching` 注解 SpEL 无法从记录 id 推导双方键）。
4. **权限**：查询复用 `im:manager:friend:query`；删除走独立按钮权限 `im:manager:friend:delete`，需执行菜单 SQL 并在角色中勾选。
5. **风险**：解除操作不可恢复（重新加好友需走申请流程），前端 confirm 文案已明确提示。

## 四、Git 与验证

- 每个里程碑完成后：后端 `txgy-module-im-server` 编译通过 + 前端 `web-ele` 构建无类型错误；
- M4 统一走查 7 个菜单页面，确认后以 `feat/im-module-enhance` 分支提交合并；
- 未经用户明确确认不执行 `git push`。
