# 店铺客服范围与坐席权限基础（2026-09-17）

历史阶段说明：本文仅为 S1 后端基础批次快照，保留当时的测试与部署证据。此后的简化注册、客服消息/FAQ、前端接待台和双端入口已有后续实现，最新状态见 [简化店铺客服闭环](./mall-customer-chat-20260917.md) 与本日注册验收记录。新客服仍默认关闭，不因代码完成自动授予坐席或开放。

## 本批范围

按最新版《注册客服与外部服务范围调整_2026-09-17.md》执行：首版是固定 FAQ + 人工客服，真实 AI 后置。本批完成 **S1 后端范围/权限/领取状态基础**，不宣称客服完整上线，也没有改动注册规则。新登录名/图片验证码注册仍需后续实现 U 项。

只改商城 promotion 模块、新增商城 SQL 和文档。原 PC/H5 未提交改动保留；不访问远程，不改 ERP/WMS、system、共享框架，不授予真实坐席权限，不使用历史 316/admin 验证码授权。

## 实现

- 新会话以 `tenant_id + scope + shop_id + member_id` 唯一；scope 只能是 `PLATFORM`（shop_id=0）或 `SHOP`（shop_id>0），租户取已验证登录上下文，显式 SQL 条件再次隔离。
- 新会话 UUID 与原平台会话数字 ID 分开。新增 `promotion_kefu_session`、`promotion_kefu_agent`、`promotion_kefu_scope_audit`，不复用旧管理员广播，不把旧会话猜测分到店铺。
- MEMBER=1、ADMIN=2 独立；相同数字 ID 不共享授权。供应商/企业经营资格不自动赋予坐席权限。拒绝跨租户访问管理员模式，RPC 沿现有租户传递及查询拦截检查店铺/账号。
- 会员只能打开/读取自己的会话；坐席按独立范围授权查看队列。店铺停用/不存在、账号停用、无授权时拒绝接待；会员可继续读取本人的历史状态。
- `WAITING → ACTIVE → CLOSED`；会员再次打开 CLOSED 会话转 WAITING 并递增版本。WAITING **只表示待接待，不表示有人在线或已排队承诺**。本批未实现在线心跳、文本发送等，不能当可聊天页面使用。
- 授权行锁在前、会话行锁在后，领取/结束以当前版本校验，同一次请求可安全重试。撤权时原子退回该坐席名下 ACTIVE 会话并记录审计。旧领取/结束/授权请求不能越过新的会话或授权版本。
- 开启/重开/领取/结束/授权/撤权/退回事件与状态变化同一事务；审计失败回滚状态。
- 查询按状态分页，page 1～10000、size 1～100。当前队列返回会话基础字段，不含消息正文、商品卡片、地址或电话。

## 接口契约（默认关闭）

`mall.customer-service.scoped-enabled` 默认为 `false`；尚无前端入口。不开启公共租户或注册/浏览名单。完整接待链路、消息契约及受控坐席名单验收后才能启用；本批不应单独打开作为生产客服。

| 路径（前缀见下） | 操作 | 身份/权限 |
| --- | --- | --- |
| POST /promotion/kefu-scope/open | `{scope, shopId}`，幂等打开/重开 | app，当前会员 |
| GET /promotion/kefu-scope/get?id=UUID | 读取本人会话 | app，当前会员 |
| GET /promotion/kefu-scope/workbench | scope/shopId/state/page/size | app 授权会员坐席；admin 还需 receive 权限 |
| POST /promotion/kefu-scope/claim、/close | `{sessionId, version}` | 显式授权坐席；admin 还需 receive 权限 |
| POST /promotion/kefu-scope/agent | `{scope,shopId,actorType,actorId,enabled,version,reason}` | admin 专用 manage 权限 |
| GET /promotion/kefu-scope/agents | scope/shopId/page/size | admin 专用 manage 权限 |

app 前缀 `/app-api`，admin 前缀 `/admin-api`。`promotion:kefu-agent:manage` 与 `promotion:kefu-agent:receive` 独立，不沿用旧 `promotion:kefu-message:send` 或商品维护权限。未新增菜单/默认角色授权；后续后台授权页面接入这两个权限。grant.version 为当前授权版本，不存在时为0；从 agents 接口读取，不允许“最后写入覆盖撤权”。

服务异常码：1013016000 未开放、1013016001 参数错误、1013016002 无权访问（不区分不存在/别租户）、1013016003 状态冲突。所有写请求的操作者由令牌读取，客户端不能提交操作者身份。

## 数据库与回退

- 正向脚本：`deploy/mysql/repair/2026-09-17-promotion-kefu-scope.sql`。仅新增三表，`CREATE TABLE IF NOT EXISTS` 可重入；若已存在异构表，须人工核对结构，不用 IF NOT EXISTS 掩盖结构差异。
- 回退脚本：同目录 `2026-09-17-promotion-kefu-scope-rollback.sql`。必须先关闭开关、确认无在途写入；只允许三表全空时移除。已有数据时保留表，回退镜像，不删会话/授权/审计。拒绝回退后可能留下同名辅助过程，复用前先核对并清理该过程。
- 实施前已备份原两张客服表和 Compose；业务库只增加三张空表。原会话7条、消息42条，备份导出 SHA-256 前后一致，无改名/归属迁移。
- 隔离 MySQL 库重复执行、空表回退、非空拒绝回退、重新导入均已验证。专用测试数据库和临时数据库用户已删除，不保留假会员或业务消息。

## 验证与部署证据

证据目录：`E:/txgy/local-backups/mall-kefu-scope-20260917/`，不提交包含聊天备份或部署环境配置的本机证据。

- promotion 测试75项：范围/状态28项、真实方法权限代理5项、RPC客户端注册1项、原积分返还17项、商品可见性24项；最终 backend-final.log 全部通过。
- 28项范围/状态测试另在 Docker MySQL 8.4 的独立数据库复测通过（RPC使用受控替身，不是实际账号联调）。覆盖并发打开、抢接、撤权竞争、越权、审计失败事务回滚。
- PC 371、H5 293项既有测试本轮重跑通过。前端本批未修改，不重复更新镜像；不能把历史浏览器回归次数计为本批新测试。
- Maven JDK25 离线定向依赖构建通过，仅打包 promotion 的27模块依赖集合，不构建81模块全集。
- 初次 -1 镜像部署发现 promotion 未注册新用到的 ProductShopApi，立即回退旧镜像（旧服务已恢复健康），补齐商城 promotion 自身的 RpcConfiguration 并增加注册回归，不改共享RPC框架。
- 修复后已部署 `mall-promotion:kefu-scope-20260917-2`，容器 healthy、重启次数0，实际启动10.533秒。`smoke-result.json` 22项通过：运行镜像/包哈希、配置仅变镜像、健康、新旧接口匿名拒绝、公开装修/优惠券、租户1入口与开关、三前端HTTP以及零真实客服写入。保留首次失败记录，不把单测通过当作真实启动通过。
- PC http://localhost:15175/、H5 http://localhost:3000/、后台 http://localhost:5173/ 均可访问；此处是HTTP/接口检查，不冒称本轮浏览器完整业务验收。未重建这三个前端容器。

`find-docs` 技能引导核对事务代理及行锁语义；Context7 前次额度耗尽，本批使用 [Spring 官方事务说明](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html) 和 [MySQL 8.4 行锁说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)。测试使用真实事务代理，避免自调用绕过事务。

## 下一步（仍未完成）

1. PC销售中心/后台授权与接待台，坐席列表可见范围、接待台分页/撤权提示、真实在线状态；不默认为13家店或现有供应商授予权限。
2. 新范围消息表/API、请求幂等、多端游标及已读；商品/本人订单/售后卡片服务端鉴权；旧平台历史阅读衔接和安全定向推送，禁止直接复用旧管理员广播。
3. PC/H5全链路入口、私有附件、留言、转人工及版本保护；首版固定 FAQ 发布/匹配范围。真实 AI 不作为首版门槛。
4. 图片验证码注册 U 项独立安排，不把旧短信挑战测试计为完成；物流/地图保持未授权降级。

本批尚不能勾选 S1、C/Q 整项完成；新客服未向顾客开放。没有实际账号授权联调，没有本地提交或远程推送。
