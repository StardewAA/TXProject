# 商城支付提交与关闭并发保护（2026-09-17）

## 范围

本轮补齐商城手动取消、到期取消与旧支付页再次提交之间的并发保护。改动仅涉及 pay 内部生命周期及商城调用；不启用或修改任何支付渠道，不声称已经强制关闭第三方收款页面。

- 新内部 RPC：`PUT /rpc-api/pay/order/close?id=...&appKey=...&merchantOrderId=...`，实际前缀以 `PayOrderApi.PREFIX` 为准。
- `closeOrder` 核对支付应用、商户订单归属，不要求已经到期；`expireOrder` 仍要求持久化支付期限已到。
- 已关闭返回成功；已支付、退款中、渠道未知、仍待付款或并发核对不一致返回失败。商城只有支付安全关闭之后，才能继续取消业务订单及释放业务资源。

## 核心不变量

1. 新 `PayOrderLifecycleService.prepare` 使用独立 `REQUIRES_NEW / READ_COMMITTED` 短事务和支付父单 `FOR UPDATE` 当前读，校验状态、期限、应用、商户单号、金额及支付内容没有变更，再插入支付尝试。
2. 新尝试与最终关闭争用同一父单行锁。最终关闭在锁内重查所有当前尝试，不复用外部渠道查询之前的旧列表，也不继承调用方的旧事务快照。
3. 发出第三方请求之前，尝试已单独提交。第三方调用不在锁事务中，异常不能使尝试记录回滚消失。
4. `prepare` 在锁内拒绝尚未确认发送状态的旧尝试，避免两个并发提交都通过前置校验后重复外发。已正常返回的历史渠道尝试仍沿用现有渠道切换规则。
5. `finishDispatch` 只对已有尝试推进发送状态，不反向持父单锁再修改已有尝试，避免与支付成功回调的锁顺序相反。
6. 明确 `SUCCESS` 的同步响应先完成幂等入账，再确认父单 `SUCCESS/REFUND` 且关联本次 attempt，最后推进 `DISPATCHED`。入账前保持 `PREPARED`，阻止已预检的第二次提交在钱包已扣款、父单仍待付款的短窗再次扣款。通知失败不推进标记；已确认入账后标记保存失败保留原标记及日志，不丢失真实成功结果。

## 发送状态与支付状态分离

`pay_order_extension.submit_state` 为新增可空字段：

| 值 | 含义 | 仅凭渠道“关闭／不存在”能否取消 |
| --- | --- | --- |
| `0 PREPARED` | 尝试已落盘，渠道调用尚未确认结束 | 否 |
| `1 DISPATCHED` | 渠道调用已正常返回非空响应 | 还必须确认支付状态 CLOSED |
| `2 UNKNOWN` | 调用异常或空响应，结果未知 | 否 |
| `NULL` | 历史记录，发送过程未核实 | 否 |

必须所有当前尝试都为 `DISPATCHED + CLOSED` 且没有遗留错误信息，或者根本没有尝试，才能关闭支付父单。即使某次渠道暂时查不到订单，也不能把尚未发出的请求或迟到请求当成已经关闭。明确支付成功的渠道结果仍可按现有回调处理，不因发送状态不明而伪造未支付。

关闭前对本地已经 `CLOSED` 的尝试也重新查询渠道。仅接受 `CLOSED` 且错误码、错误消息均为空的明确结果；`ACQ.TRADE_NOT_EXIST`、`ORDERNOTEXIST`、一般查询错误等都不是关闭证据。取得新的明确关闭结果后，通过条件更新记录证据并清除旧查询错误，再进行父单关闭检查。没有修改公共支付渠道适配器。

标记保存失败保持原 `PREPARED`，保守拒绝关闭。不能自动把历史 NULL、崩溃遗留 PREPARED 或 UNKNOWN 批量改成 DISPATCHED；需要有证据的人工核实流程，本轮不编造该核实结果。

### 历史记录及未知结果对用户的影响

- 从未发起付款、没有 attempt 的待付款订单仍可正常发起或取消；已经完成付款的订单继续显示真实支付结果，不因历史发送标记为空而回退为待付。
- 仍待付款但包含历史 NULL、PREPARED、UNKNOWN 的订单，会暂停再次发起付款及自动／手动取消，优先避免重复扣款或付款后错误释放库存。这不是确认用户没有付款。
- 查询只返回“交易不存在”或错误（即使适配器映射为 CLOSED）也会保留待核实，不保证旧付款页面已失效。
- 钱包等没有独立异步回调的渠道若在扣款后进程崩溃，或成功通知持续失败，仍需要依据钱包流水和业务订单对账恢复；不得让用户反复重试来代替核实。
- 人工处理需要核对支付父单、商户单号、attempt、渠道／钱包流水及实际业务状态，保留处理记录。不能按日期、时长或“暂时查不到”直接批量改标记、删除尝试或将订单当未支付取消。
- 本轮不重新打开已有关闭订单、不自动修复历史金额、不给旧记录猜测归属。生产启用前应单独列出受影响旧订单清单和核实责任人。

## 数据库与发布顺序

- 正向脚本：`deploy/mysql/repair/2026-09-17-pay-order-submit-state.sql`。
- 回退说明：`deploy/mysql/repair/2026-09-17-pay-order-submit-state-rollback.sql`。
- 先备份并在隔离库验证，再为正式表补可空列，最后更新 pay 镜像。脚本可重复执行，并拒绝已有同名但不兼容的字段。
- 不执行历史记录回填，不更改金额、支付状态、账号或渠道配置。
- 回退保留新增列及尝试证据。回退旧应用前必须隔离支付提交／取消入口：旧镜像不理解该发送状态，直接回退会重新引入竞态。

## 商城支付入口身份边界

- APP 获取支付单和提交支付必须具有已认证的 `MEMBER` 身份；订单的 `userId` 与该会员相同，且订单 `userType` 也必须为 `MEMBER`。同一个数字 ID 的管理员不是同一个会员。
- 校验先于钱包创建、支付发起和主动查询渠道。按支付单号读取也执行同样归属检查；主动同步后返回的数据再次核对归属。
- 历史缺少 `userId` 或 `userType` 的订单不再默认公开或允许支付，不自动推断或补写归属。
- 后台提交支付入口拒绝 `MEMBER` 订单，避免绕过 APP 身份边界；未扩展非会员后台业务的权限规则。`PayDemoOrder` 显式为 `ADMIN`，原示例路径保持兼容；钱包充值继续沿用创建时传入的用户身份。
- 两端 submit 参数开启 `@Valid`，支付单编号必须大于零。自己的钱包支付使用认证主体取得钱包，并覆盖客户端传入的钱包编号。

## 支付改价边界

- 改价与 `prepare` 共用父单行锁并在独立 RC 短事务中重新检查，只有父单处于 `WAITING` 才允许处理。
- 金额必须是正整数分；`null`、零、负数及无效订单编号拒绝。当前 API 的合法数值上界仍为 Java `Integer.MAX_VALUE`，本轮不改变既有业务金额上限配置。
- 同额的 `WAITING` 请求为无写入幂等操作；成功、退款、已关闭的订单即使同额也拒绝。
- 真正改变金额必须没有任何付款尝试。无论 attempt 的支付状态／发送状态为何，包含历史 NULL 或已关闭尝试，都不能推测安全后继续改价。
- 若改价先获得锁，已完成旧金额预检的付款准备会被金额快照检查拒绝，刷新后可按新金额正常付款；若付款准备先获得锁，则改价被拒绝，原金额保持不变。
- 商城 `TradeOrderUpdateServiceImpl.updateOrderPrice` 的本地事务覆盖订单与明细修改，支付 RPC 的业务失败或异常会让这些本地修改一起回滚。已用真实 SQL 验证，但这不是分布式事务保证：RPC 超时不能证明远端从未提交，跨服务结果不确定仍应依据两边记录核实，不能盲目重复写历史金额。

## 已完成本地验证

本轮支付专项 **122 项**、商城改价回滚 **3 项**，合计 **125 项通过**：

- `PayOrderLifecycleConcurrencyTest`：19 项真实 SQL、独立 JDBC 连接与 Spring 事务代理测试，包含关闭先行、提交先行、两个提交竞争、调用方旧快照、未外发／未知／历史状态、已关闭且完成发送、金额归属变更、外层回滚仍保留尝试、标记更新失败、钱包成功后暂停入账时第二提交仍被阻止、改价与付款准备两个先后时序，以及 16 种 attempt／发送状态组合与金额边界。
- `PayOrderDeadlineTest`：21 项到期／手动关闭及渠道等待、未知、无归属权限、查询错误不能充当关闭、本地错误关闭重新核实等分支。
- `PayOrderServiceTest`：48 项原支付服务回归与新增外调异常／空响应持久化测试；断言外调时无活动数据库事务。
- `PayOrderOwnershipTest`：17 项控制器身份、归属、历史缺归属、钱包调用顺序、后台绕过拒绝与正常 ADMIN 示例兼容测试。
- `PayOrderClockResponseTest`：2 项持久化期限和服务端时钟回归，使用有归属的会员支付单。
- `PayOrderRetryValidationTest`：7 项已有支付尝试核实回归。
- `PayOrderSubmitRecoveryTest`：8 项已知成功入账／发送标记失败恢复、通知失败保持 PREPARED、非成功不伪造成功、持久化支付状态与 attempt 匹配检查；包含 `InOrder` 顺序断言。
- `TradeOrderPriceRollbackTest`：3 项真实本地 SQL 事务测试，证明支付改价业务失败、RPC 异常时订单和明细均回滚，正常改价一起提交。

命令：

```text
mvn -o -pl txgy-module-pay/txgy-module-pay-server,txgy-module-mall/txgy-module-trade-server -am -Dtest=PayOrderOwnershipTest,PayOrderClockResponseTest,PayOrderRetryValidationTest,PayOrderLifecycleConcurrencyTest,PayOrderDeadlineTest,PayOrderServiceTest,PayOrderSubmitRecoveryTest,TradeOrderPriceRollbackTest -Dsurefire.failIfNoSpecifiedTests=false test
```

并发类支持隔离 MySQL 验证，读取 `MALL_PAY_LIFECYCLE_TEST_JDBC / USER / PASSWORD`；仅允许本机精确测试库 `mall_pay_lifecycle_test_20260917`，仅操作 `lifecycle_test_order` 与 `lifecycle_test_attempt` 两张模拟表。测试连接默认 MySQL RR，门闩明确验证独立 RC 生效。不得将这些测试环境变量指向业务库，日志不记录凭据。

实际 MySQL、正式补列、镜像部署及联合联调结果由本轮总验收记录追加，本文件不提前宣称完成。
