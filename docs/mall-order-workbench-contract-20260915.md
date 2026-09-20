# 商城订单工作台契约（阶段 1C）

状态：阶段 1C 已部署；2026-09-16 真实账号联调及覆盖缺口见 [实施记录](mall-order-workbench-20260915.md)。用户已确认本批提交本地 master，具体编号以 Git 及外部交接记录为准，不推送远程。本文仅定义交易待办；客服已重新列入后续总计划，不因本契约更新而视作已实现。

## 路径和身份

- PC/H5：`/app-api/trade/order-workbench/{summary,page,read}`，`role=BUYER|SUPPLIER`。
- web-antd：`/admin-api/trade/order-workbench/{summary,page,read}`，`role=ADMIN`。
- SUPPLIER 必填 `shopId`，服务端每次校验企业、会员、店铺授权；BUYER 忽略客户端会员编号，固定当前会员；ADMIN 使用真实后台账号及现有订单/售后权限。
- 返回 `scopeKey=tenantId:userType:userId:role:shopId`（无店铺为 0），前端切换身份/租户/店铺必须废弃旧请求。

## GET summary

查询参数：`role,shopId?,afterCursor?`（字符串整数，首次不传），`orderIds?`、`afterSaleIds?`（逗号列表，各不超过 100 个，用于当前行新标记）。状态计数共同筛选支持 `no,commentStatus`；ADMIN 另外支持原 `TradeOrderPageReqVO` 的全部共同字段。`status` 和分页不影响各状态计数。全局待办不受这些搜索条件影响，必须标注“本人全部待办/当前店铺全部待办/平台授权范围全部待办”。

筛选补充：`commentStatus` 仅作用于 BUYER 状态计数，SUPPLIER 不使用它。BUYER 工作台汇总支持 `no`，既有买家订单分页接口不支持该条件，不能据此宣称已新增买家订单号搜索。ADMIN 使用原查询模型；当前框架也可能绑定同名请求头（如 `terminal`），测试需与实际后台请求保持一致，不额外注入隐藏筛选。

```typescript
type TodoAction = 'PAYMENT' | 'RECEIVE' | 'COMMENT' | 'BUYER_RETURN'
  | 'DELIVERY' | 'AFTER_AUDIT' | 'AFTER_RECEIVE' | 'PLATFORM_REFUND' | 'ERP_EXCEPTION';
interface TodoEntry {
  eventId: string; // 与 cursor 同一租户事务提交序号空间，字符串避免 JS 精度损失
  entityType: 'ORDER' | 'AFTER_SALE' | 'ERP';
  entityId: number; // ERP 条目使用订单编号；requestId 单独返回
  orderId: number; afterSaleId?: number; requestId?: string;
  no: string; shopId?: number; action: TodoAction; reason: string;
  unread: boolean; createdTime: string;
}
interface WorkbenchSummary {
  scopeKey: string; serverTime: number; cursor: string; resetRequired: boolean;
  pollIntervalSeconds: number; // 20
  pendingCount: number; unreadCount: number; newCount: number;
  todoCounts: Record<TodoAction, number>;
  orderStatusCounts: { allCount: number; unpaidCount: number; undeliveredCount: number;
    deliveredCount: number; completedCount: number; canceledCount: number; uncommentedCount: number } | null;
  entries: TodoEntry[]; // 当前未处理事项最近 20 条，可能含已读
  hasMore: boolean;
  rowEntries: TodoEntry[]; // 精确匹配当前传入的 orderIds/afterSaleIds，仅该账号未读的仍有效事项
}
```

`newCount` 只计算 `(afterCursor,cursor]` 仍有效的新事项；首次加载或游标超前返回 `resetRequired=true,newCount=0`，仍展示真实未读数，不逐条弹历史。已处理/失权条目即刻退出待办，不要求先已读。`entries` 是有限预览，不可用长度推算总量。请求失败显示“暂无法更新”和上次时间，不能改成零。

后台售后专员有 `trade:after-sale:query + refund` 即可取得平台退款待办，不为此授予订单查询权限；无 `trade:order:query` 时 `orderStatusCounts=null`，前端显示缺数而不是零。ERP异常必须同时有订单查询和更新权限；普通查询权限不能生成退款处理资格。

## GET page

参数 `role,shopId?,pageNo=1,pageSize=20,action?,unreadOnly?`；pageSize 最大 100。返回 `{list:TodoEntry[],total:number}`，这是**待办事件页**，不是普通订单列表。点击条目以 `orderId/afterSaleId` 精确打开真实详情，不强制重置正在编辑的业务表单。平台退款进入售后详情，ERP 异常进入关联订单 ERP 状态。

## POST read

请求 `{role,shopId?,eventIds:string[]}`，1—100 个已展示的具体事件编号。返回 `true`。每条再次校验当前账号、租户、店铺、资源归属及当前权限；禁止伪造读任意租户/店铺事件。已读按 `tenantId+userType+userId+role+shopId+eventId` 持久化幂等，同账号 PC/H5 同步，其他经营人员不被清除。无 read-all/snapshotCursor 盲清接口。已读不触发任何业务状态变更。

## 行动作及 ERP 边界

原 supplier order/page 行增加 `deliverable,deliveryBlockReason,erpStatus`；原 supplier after-sale/page 行增加 `processable,processBlockReason,erpStatus`。DETAIL 与真实发货操作使用相同判定：OUTBOUND 请求已提交等待 ERP 时不提示再次发货；退货 RETURN 已提交时不提示重复收货。后台订单/售后展示操作仍检查现有权限；待办不能授予原本没有的权限。商品审核不混入订单待办。

## 持久化、游标与恢复

业务变更事务注册最后阶段 `beforeCommit` 回调，同事务落持久变更日志 `trade_order_workbench_change`；异常导致事务回滚，不依赖会吞异常的旧日志切面。日志包含订单编号及必要的阶段失效标记。只在业务写锁之后获取租户事件时钟行锁，时钟持有至提交，避免自增 ID 先分配后晚提交造成漏读；此回调不再读取业务、不作 RPC、不再获取业务行锁。查询事务以 READ_COMMITTED 消费所属 scope 的已提交变更日志并投影持久事件，查询锁时钟后只作本地非锁定事实读取及自身待办表写入。事件重算幂等，同一阶段重复回调不重复建事件；售后冻结后重新可发货，即使两次变更发生在轮询间，阶段失效版本也会关旧建新。已读表与真实待办分离。

旧数据和漏挂点有界恢复只写新待办表，不改订单/售后/库存/支付；首次恢复不制造在线历史消息轰炸。查询仍复核真实可处理性与权限。恢复上限失败必须返回明确错误，不返回截断的假总数。完整状态计数在数据库聚合，移除 status、保留共同筛选。事务实现参考 Spring `TransactionSynchronization.beforeCommit` 和 MySQL InnoDB locking reads 官方说明；最终锁序与并发测试结果另记。

买家支付待办在获取事务/时钟锁前通过既有 PayOrderApi 读取实际支付状态，再与锁内订单编号、支付单号、金额、支付期限、ERP门槛核对；旧支付单已关闭/已支付不会被误报。原RPC保留租户上下文，不引入交易模块直查pay表或跨库假设。上限200笔候选支付单；RPC失败显示暂不可更新，不伪装零。新单若在快照后创建，下一轮核实后才列可付款，不猜测成功。范围恢复最多10000笔，越限明确503，不返回假全量计数。

后台额外接口：`GET /admin-api/trade/after-sale/get-count`，原售后查询参数加 `shopId`，忽略 status、分页；返回 `Record<string,number>` 包含 `all,10,20,30,40,50,61,62,63`。后台订单/详情加 `deliverable,deliveryBlockReason,pickupable,pickupBlockReason,erpStatus`；售后/详情加 `processable,processBlockReason,erpStatus`。`deliverable` 专指快递发货，不能用于判断自提核销。业务按钮仍受原操作权限及真实状态保护。

## 2026-09-15 19:03 后端实现与补测结果

本批后端代码和打包完成，尚不凭本节声称 Docker 部署或真实账号联调完成；后续部署/联调结果由主实施记录补充，不沿用上一批临时短信授权。

- 交易模块测试 **153 项通过、0 失败、0 错误、0 跳过**，包括原有 124 项交易/ERP/结算/供应商等回归及新增工作台 29 项。
- 工作台专项覆盖：完整计数及分页、首次不弹历史、已读不减待办、同账号双客户端读取、其他经营人员已读独立、已被处理移除、未付/ERP等待不催发货、商家/平台退款职责、退货等待、重复轮询/回调幂等、同一发货阶段在轮询间退出重入、回滚不生成提醒、事件写失败回滚业务、不同租户/会员/身份/店铺拒绝、过滤统计与全局待办分离、参数限制、旧支付关闭/已支付不误报、旧合法支付期限不改写、RPC失败不返回假零、付款资源/金额核对、跨角色投影隔离、支付快照晚于游标仍会重算、支付核实期间跨过交易期限、已终止预占异常退出与失败释放继续保留、相同订单同事务日志合并、并发晚提交序号不被越过、售后专员不获得订单统计权限。
- 另以 **80 种订单主状态/付款/配送/退款/售后组合**核对待办 SQL 投影与实际商家发货共用门槛的一致性（作为一项参数矩阵测试计入上述 29 项）。
- MySQL 8.4 原生隔离验证由主代理完成：新 DDL 重放、事务回滚、双连接时钟提交顺序、已读复合键 4 组通过，隔离临时库已清理；未以 H2 结果替代 MySQL 结果。
- 锁序：实际业务行锁先于租户时钟；业务末尾只写 journal；查询授权/用户条件解析/支付 RPC 在时钟事务前；查询锁时钟后不作 RPC、不加业务行锁。待办写故障与业务同事务回滚，不吞掉异常后丢提醒。
- 原订单、售后、库存、支付未通过本批初始化脚本改写。取消订单仍可能保留 ERP RELEASE 失败待核实，因为“已取消”不能被解释成“库存已释放”。

验证命令：宿主机 Maven 离线 `-pl txgy-module-mall/txgy-module-trade-server -am -DskipTests=false -Dtest=*Test -Dsurefire.failIfNoSpecifiedTests=false package`，低内存单进程/单测试 JVM，使用 SerialGC，不在 Docker 重编 81 模块。

最终构建产物：`txgy-module-mall/txgy-module-trade-server/target/txgy-module-trade-server.jar`；2026-09-15 19:03:04，164016621 字节；SHA256 `6E041EB3F581C67A5F9301F32242872AB3C17411DAE678DB9678FED36A59A542`。私有构建日志：`E:/txgy/local-backups/mall-product-review-20260915/order-workbench-package.log`。镜像是否已使用此产物另行验收。

实现依据：[Spring TransactionSynchronization](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html)、[MySQL InnoDB locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)。Context7 当前额度不足，本轮改为核对官方文档，没有仅凭旧框架知识假定回调已经提交。
