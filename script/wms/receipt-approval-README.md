# 入库审批初始化

仅发布 `wms-server`、`bpm-server` 及 WMS 前端；无需发布 ERP 或修改 Flowable 表。

1. 在 WMS 使用的 system 数据库执行 `sql/wms-receipt-approval-permission.sql`，新增提交审批权限并继承已有完成入库角色。重新登录或按现有方式刷新权限缓存。
2. 使用当前租户管理员的真实 access token 执行（BaseUrl 为网关根地址，不带 `/admin-api`）：

```powershell
./script/wms/Initialize-ReceiptApproval.ps1 -BaseUrl http://localhost:48080 -Token $token -TenantId 1 -ManagerUserId 1 -ApproverUserId 1
```

用户 ID 必须是在该租户启用的真实用户。`ManagerUserId` 必须包含执行发布操作的登录用户。脚本调用既有 `/admin-api/bpm/model/list`、`create`、`deploy` API；模型存在时默认不改动，显式 `-UpdateExisting` 才更新发布。所需权限为 `bpm:model:create/update/deploy`。可通过 `-Category` 使用已有分类编码。

模型含一个人工审批节点，候选人由参数指定，关闭自动去重且同人审批仍需人工操作。模型隐藏于通用发起列表，只从入库单提交；流程业务 key 是入库单 ID。BPM 详情组件路径为 `/wms/order/receipt/bpm-detail`。

验收：创建明细完整的入库单 → 提交 → 审批人 BPM 待办 → 真实点击通过 → 自动执行入库。拒绝不写库存。库存执行失败时回调抛出业务异常，BPM 同步审批事务回滚，修复原因后可在原待办重新审批。回调仅使用内部 RPC 登录上下文及租户头；不开放匿名接口。

不要给该模型增加第二份 HTTP 后置回调：专用 `WmsReceiptOrderStatusListener` 已负责结果同步。初始资源不包含自动审批、空候选人通过、定时器或模拟 token。
