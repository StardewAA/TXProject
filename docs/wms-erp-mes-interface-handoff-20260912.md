# WMS / ERP / MES 接口交接

本文以 `txgycloud-wms` 当前代码为准。WMS 负责仓库验收/出库确认、仓库 BPM 审批、库存与流水；ERP/MES 完成自身审批后调用申请接口。WMS 不代建 ERP/MES 页面或内部审批。

真实 ERP/MES 网络联调尚未完成。原 ERP 采购入库已通过本机模拟来源、真实 WMS/BPM 验收；本轮新增业务的部署与浏览器证据单独记入执行记录，不能用单元测试代替业务验收。

## 1. 四类业务

| 来源 | orderType | WMS 单据类型 | 物料/仓库类型 | 鉴权申请地址（POST） | 内部 RPC 地址（POST） |
|---|---|---|---|---|---|
| ERP | PURCHASE_RECEIPT | 101 采购入库 | RAW_MATERIAL | /admin-api/wms/external-application/erp/receipt | /rpc-api/wms/integration/receipt/purchase |
| ERP | SALE_SHIPMENT | 201 销售出库 | FINISHED_GOODS | /admin-api/wms/external-application/erp/shipment | /rpc-api/wms/integration/shipment/sale |
| MES | PRODUCTION_RECEIPT | 100 完工入库 | FINISHED_GOODS | /admin-api/wms/external-application/mes/receipt | /rpc-api/wms/integration/receipt/production |
| MES | PRODUCTION_SHIPMENT | 202 生产领料出库 | RAW_MATERIAL | /admin-api/wms/external-application/mes/shipment | /rpc-api/wms/integration/shipment/production |

本轮最小范围：完工入库为成品、领料为原料；每张申请一个仓库；出库按申请数量整单确认和执行，不支持部分发货、预留核销或拆单。退货、退料等未扩展，移库未开发或修改。

申请只创建现有 `wms_receipt_order` / `wms_shipment_order` 及明细，返回成功不代表库存执行成功。流程为：来源审批完成 → WMS 草稿 → WMS 验收/出库确认 → 仓库人工 BPM → 库存执行 → 查询结果/约定回调。

## 2. 地址、鉴权与租户

本机网关入口 `http://localhost:5173`；正式环境域名由部署方提供。

鉴权接口请求头：

```http
Authorization: Bearer <现有系统签发且未过期的访问令牌>
tenant-id: 2005
Content-Type: application/json
```

租户来自已认证请求上下文，申请体 `tenantId` 必须与上下文相等。接入组应使用对应租户的服务账号/令牌，具体签发、续期、账号与权限分配由系统管理员负责；本轮没有新增账号或扩大权限。不要把 `tenant-id` 或来源字段当作身份凭证。

| 接口用途 | 所需权限 |
|---|---|
| 两类入库申请 | wms:receipt-order:create |
| 两类出库申请 | wms:shipment-order:create |
| 入库结果查询 | wms:receipt-order:query |
| 出库结果查询 | wms:shipment-order:query |
| 独立只读主数据、库存、流水查询 | wms:inventory:query |
| 仓库入库/出库提交审批 | wms:receipt-order:submit-approval / wms:shipment-order:submit-approval |

现有 `/rpc-api/wms/**` 按项目原规则 `permitAll`，只供受信内部服务网络使用，携带租户上下文；它不是对公网的鉴权方案。接入方优先使用上述 `/admin-api` 入口，不应直接暴露内部 RPC。没有更改共享网关或全系统安全规则。

## 3. 申请报文

```json
{
  "requestId": "MES-FG-20260912-001-v1",
  "tenantId": 2005,
  "sourceModule": "MES",
  "occurredAt": "2026-09-12T10:00:00",
  "businessChainNo": "MES-MO-20260912-001",
  "sourceOrderNo": "MES-FG-20260912-001",
  "orderType": "PRODUCTION_RECEIPT",
  "eventVersion": 1,
  "items": [{
    "lineNo": 1,
    "sourceLineNo": 11,
    "materialCode": "FG-001",
    "materialType": "FINISHED_GOODS",
    "materialNameSnapshot": "示例成品",
    "skuCode": "FG-001-STD",
    "specificationSnapshot": "标准规格",
    "unitCode": "PC",
    "unitNameSnapshot": "件",
    "warehouseCode": "WMS-FG-WH",
    "requiredQuantity": 10,
    "qualityRequiredSnapshot": false
  }]
}
```

示例编码需要替换成同租户 WMS 实际编码，不可使用 ERP/MES 内部数据库 ID。ERP 采购示例改为 `sourceModule=ERP, orderType=PURCHASE_RECEIPT, materialType=RAW_MATERIAL`；ERP 销售为 `ERP/SALE_SHIPMENT/FINISHED_GOODS`；MES 领料为 `MES/PRODUCTION_SHIPMENT/RAW_MATERIAL`，并选择对应仓库。

| 字段 | 规则 |
|---|---|
| requestId | 非空，最多 64 字符，不能以内部保留前缀 `B:` 开头 |
| tenantId / sourceModule | 必填；租户必须匹配上下文，来源必须匹配该业务入口 |
| sourceOrderNo | 非空，最多 56 字符；WMS 单号为 `WMS-<来源>-<来源单号>` |
| businessChainNo | 非空，最多 64 字符，用于跨系统追踪 |
| occurredAt / eventVersion | 必填业务时间、正整数事件版本；版本不是更改单据的授权 |
| partnerCode | 可选；如提供必须映射同租户 WMS 往来单位；采购校验供应商，销售校验客户 |
| items | 非空；每行的 lineNo 为不重复正整数；sourceLineNo 缺省沿用 lineNo，也必须唯一 |
| materialCode / skuCode | 物料最多 20 字符、SKU 最多 64 字符；SKU 必须属于该物料与租户 |
| warehouseCode | 非空，最多 20 字符；同单只能一个仓库，类型匹配 |
| materialNameSnapshot | 必填，最多 255 字符；单位编码/名称必填，各最多 64 字符 |
| requiredQuantity | 大于 0，最多两位小数；总量小于 10^18；不换算单位 |
| qualityRequiredSnapshot | 入库保留质检要求；若未给出则使用 WMS 当前物料要求快照；需要质检的行由 WMS 验收上传本单质检凭证 |
| specificationSnapshot / batchNo / attachments | 可选，原样保留来源明细快照；附件为 fileId、url、fileName、mimeType、sha256 等元数据，不会自动下载或代表验收通过 |

DTO 内保留的 `reservedQuantity/releasedQuantity/shortageQuantity` 不是本轮支持的库存能力；申请方不要据此推断已预留。来源传入的实收/合格数不会直接执行库存，仓库仍需在 WMS 验收。

受理响应示意：

```json
{"code":0,"data":{"accepted":true,"requestId":"MES-FG-20260912-001-v1","targetOrderNo":"WMS-MES-MES-FG-20260912-001","businessStatus":"RECEIVED","processedVersion":1},"msg":""}
```

## 4. 幂等、追踪与编辑边界

传输幂等为同租户 `requestId`：同请求重放返回原结果，内容或操作不同报冲突。来源业务幂等为 `(tenantId, sourceModule, orderType, sourceOrderNo)`，由同一集成台账的内部哈希键串行化；更换 requestId 仍不会重复建单。业务摘要忽略重试发生时间，规范化行顺序和数量小数尾零，其他业务内容（含版本和业务链）变化报冲突。

不要通过提高 eventVersion 或改变数量来覆盖已受理申请；本轮不支持来源变更、部分发货或新版本替换原单。来源单号和明细行关联、创建时来源快照保留；来源单不得修改类型、仓库、来源关联、SKU、申请数量或金额，不得增删来源明细。仓库可填写入库验收数据。

审批回调以当前 BPM 实例与运行状态校验，单据行锁及条件更新保护重复执行。出库库存、流水、执行成功状态共同提交；执行失败时回滚执行保存点，保留审批终态和失败原因。重试执行复用原单，不重新建单。服务间 BPM/WMS 不具有分布式原子提交；网络异常仍需重试/对账。

## 5. 执行结果

鉴权查询：

```http
GET /admin-api/wms/external-application/execution?sourceOrderNo=MES-FG-20260912-001&orderType=PRODUCTION_RECEIPT
```

内部兼容查询：`GET /rpc-api/wms/integration/order/execution/by-source?tenantId=2005&sourceOrderNo=...&orderType=...`。
原受理结果（不是实时执行结果）：`GET /rpc-api/wms/integration/request/{requestId}`。

实时结果示意：

```json
{"code":0,"data":{"aggregateType":"RECEIPT","aggregateNo":"WMS-MES-MES-FG-20260912-001","processingStatus":"SUCCESS","resultCode":0,"resultData":{"sourceModule":"MES","sourceOrderNo":"MES-FG-20260912-001","businessChainNo":"MES-MO-20260912-001","orderType":"PRODUCTION_RECEIPT","approvalStatus":2,"executionStatus":1,"completedQuantity":8,"notificationStatus":"POLLING_ONLY","items":[{"lineNo":1,"sourceLineNo":11,"materialCode":"FG-001","skuCode":"FG-001-STD","requiredQuantity":10,"actualQuantity":10,"qualifiedQuantity":8,"unqualifiedQuantity":2}]}},"msg":""}
```

`resultData.items` 包含来源关联及快照字段。入库 `completedQuantity` 成功后为合格数之和，成功前为 0；出库成功后为确认出库数，成功前为 0。入库实收应等于合格+不合格，仅合格数加库存；出库审批通过后扣库存，不允许负库存。

| processingStatus | 含义 |
|---|---|
| DRAFT | 待 WMS 验收/确认，库存未执行 |
| WAITING_APPROVAL | 仓库 BPM 审批中，库存未执行 |
| WAITING_EXECUTION | 已批准但尚未成功执行 |
| EXECUTION_FAILED | 出库执行失败，查看 executionError；不得当作已出库 |
| SUCCESS | 库存、流水和执行成功状态已提交 |
| REJECTED / CANCELLED | 审批拒绝 / 流程或单据取消 |

内部数值：approvalStatus `-1` 未提交、`1` 运行、`2` 通过、`3` 拒绝、`4` 取消；executionStatus `0` 待执行、`1` 成功、`2` 出库失败。历史单据默认字段不等于有审批历史，本轮没有迁移历史库存。

## 6. 回调、重试与数据责任

ERP 继续使用已有 Feign 契约 `ErpWmsCallbackApi.callbackExecutionResult`，地址 `POST /rpc-api/erp/wms-callback/execution-result`，WMS 传递租户上下文。采购入库为 `PURCHASE_RECEIPT`，销售出库为 `SALE_SHIPMENT`，成功执行后 `executionStatus=COMPLETED`。

```json
{"requestId":"WMS-SHIPMENT-2005-<WMS单ID>-1","tenantId":2005,"eventVersion":1,"occurredAt":"2026-09-12T11:00:00","businessChainNo":"ERP-SO-001","sourceOrderNo":"SO-001","wmsOrderNo":"WMS-ERP-SO-001","orderType":"SALE_SHIPMENT","executionStatus":"COMPLETED","items":[{"lineNo":1,"sourceLineNo":11,"materialCode":"FG-001","skuCode":"FG-001-STD","actualQuantity":3,"qualifiedQuantity":3,"unqualifiedQuantity":0}]}
```

ERP 成功确认须返回 `{"code":0,"data":true}`。通知失败不回滚已经执行的库存；后台每 60 秒扫描可重试通知，失败后至少等待 60 秒，再用同一个回调 requestId 重试。通知行锁防止调度与人工重试并发发送。人工重试为 `POST /admin-api/wms/{receipt-order|shipment-order}/retry-notification?id=...`，不能用重发库存执行代替重试通知。

通知状态：`WAITING_EXECUTION`、`PENDING`、`FAILED`、`DELIVERED`、`SIMULATED`、`POLLING_ONLY`、`NOT_REQUIRED`。`DELIVERED` 仅表示 ERP 接口确认，仍需 ERP 小组核对自身业务状态；真实网络联调没有完成。拒绝、取消、出库失败状态当前通过实时查询读取，现有自动回调只发送成功执行结果。

MES 当前为 `POLLING_ONLY`，未配置或猜测 MES 回调 URL，也未向 MES 发请求。建议 MES 沿用上述回调结构，orderType 使用两类 MES 类型，并确认：接收地址、服务鉴权/签名、超时与重试窗口、重复 requestId 确认语义、业务链和来源行号映射。该建议不是对方已实现的功能。

调用失败处理：无响应/超时先查询来源单结果，或使用完全相同的 requestId 和请求重试；不要自动换来源单号。`code!=0` 视为失败，不只看 HTTP 200。

| 业务码 | 含义/处理 |
|---|---|
| 401 / 403 | 未登录、令牌失效或权限不足；恢复鉴权，不反复建单 |
| 400 | 参数校验失败（具体 HTTP/业务码以网关返回为准） |
| 1060900000 | requestId 与已有请求内容或操作冲突 |
| 1060900009 | 租户不匹配 |
| 1060900018 | 请求记录不存在 |
| 1060900022 | 功能未启用/不在当前支持范围 |
| 1060900023 | 申请校验失败；现有错误文案可能仍写“采购入库”，检查具体原因 |
| 1060900024 | 已受理来源业务内容不一致 |
| 1060200000 / 1060201000 | 入库单/出库单不存在 |
| 1060300000 | 库存不足；出库结果中的 executionError 给出原因 |

现有部分仓库校验抛出 IllegalArgumentException，可能经通用异常处理返回 500；调用组应保留 `code/msg`，不要将所有 500 都作为可无限重试的网络错误。

## 7. 只读查询（ERP/MES 共用）

以下均 GET，必须携带鉴权和租户请求头，只有查询权限，不委托主数据、单据或库存写服务。

| 地址 | 筛选 | 响应 |
|---|---|---|
| /admin-api/wms/external-read/warehouses | code 精确、name 包含 | 仓库编码/名称/类型/状态/地址等 |
| /admin-api/wms/external-read/skus | materialCode、skuCode 精确；materialName 包含 | 已启用物料的 SKU，包含尚无库存的规格 |
| /admin-api/wms/external-read/inventory | materialCode、skuCode、warehouseCode 精确；materialName 包含 | 分仓、分 SKU 的现存量 |
| /admin-api/wms/external-read/history | 上述库存筛选、orderNo、orderType、sourceOrderNo；startTime/endTime | 库存变化、前后余额及来源单关联 |

分页均 `pageNo>=1`、`pageSize=1..200`，默认 1/20。时间筛选格式 `yyyy-MM-dd HH:mm:ss`，边界包含；流水按 createTime 倒序、id 倒序稳定分页。流水 `orderType=1` 为入库、`2` 为出库，是流水单据大类，不是申请的字符串业务类型或单据 100/101/201/202 数值。历史流水即使主数据已删除仍保留，但关联资料为空，不能泄露其他租户资料。

```http
GET /admin-api/wms/external-read/inventory?pageNo=1&pageSize=20&skuCode=FG-001-STD&warehouseCode=WMS-FG-WH
GET /admin-api/wms/external-read/history?pageNo=1&pageSize=20&sourceOrderNo=SO-001&orderType=2
```

```json
{"code":0,"data":{"list":[{"id":123,"materialCode":"FG-001","materialName":"示例成品","skuCode":"FG-001-STD","skuName":"标准规格","unitName":"件","warehouseCode":"WMS-FG-WH","warehouseName":"成品仓","quantity":5}],"total":1},"msg":""}
```

```json
{"code":0,"data":{"list":[{"id":456,"skuCode":"FG-001-STD","warehouseCode":"WMS-FG-WH","beforeQuantity":8,"quantity":-3,"afterQuantity":5,"orderId":1234,"orderNo":"WMS-ERP-SO-001","orderType":2,"sourceOrderNo":"SO-001","businessChainNo":"ERP-SO-001","createTime":"2026-09-12T11:00:00"}],"total":1},"msg":""}
```

兼容内部接口：`/rpc-api/wms/stock-read/page`、`/warehouses`、`/skus` 和 `/rpc-api/wms/stock-history-read/page`。旧仓库/SKU 内部选择器为列表接口；鉴权入口提供筛选分页。仓库/SKU 当前在本租户列表上过滤分页，规模增大时可改为数据库分页；库存与流水已使用数据库分页。

实际库存实体仅有 `quantity`。它是现存/账面数量；本轮没有预留量字段、可用量计算或单位换算，不能虚构 `available = onHand - reserved` 已实现。出库页面原兼容字段 availableQuantity 若存在也仅来自账面余额。查询为实时读取，多次分页不保证跨页同一时点快照。

## 8. 模拟与部署

入库管理 `http://localhost:5173/#/wms/receipt` 的“模拟 ERP / MES 入库”只提供 ERP 采购、MES 完工两类。出库管理 `http://localhost:5173/#/wms/shipment` 的“模拟 ERP / MES 出库”只提供 ERP 销售、MES 领料两类，草稿在出库管理确认并提交审批。模拟的是来源系统已审批通过的申请。模拟开关 `txgy.wms.simulation-enabled` 默认 false，本机验收环境开启；来源带 `SIM-` 前缀且保存 simulation=true，不发送真实 ERP/MES 通知。

出库模拟入口已按业务类型筛选仓库：SALE_SHIPMENT 对应 FINISHED_GOODS，PRODUCTION_SHIPMENT 对应 RAW_MATERIAL；复用库存选择组件按已选 warehouseId 分页查询正账面库存，显示仓库及账面数量。切换业务或仓库时确认后清空旧选料，取消保留；选入和提交时额外检查明细仓库一致。入库继续选择 SKU 主数据，不以已有库存限制首次入库。本调整不修改申请接口或后端仓库/物料校验，不改变库存执行时的不足保护。

入库选料补充：采购仅查询RAW_MATERIAL、完工仅查询FINISHED_GOODS，列表和已选明细显示“原材料/成品”，不按商品名或目标仓库猜测类型。兼容SKU只读接口 `/admin-api/wms/item-sku/page` 新增可选 `materialType` 筛选，数据库在分页前过滤；响应增加主数据实际 `materialType`，未设置仍为空，不自动补值。未传筛选保持原查询范围，原鉴权、租户逻辑不变。公共选择器通过可选参数启用本功能，其他调用默认行为不变。

本轮增量 SQL 为 `deploy/mysql/repair/2026-09-12-wms-shipment-source.sql`，9 个字段已检查并执行，不写库存，不需重跑。`Initialize-ShipmentApproval.ps1` 为历史初始化脚本；当前两类 WMS 模型由 `Configure-WarehouseRoleApproval.ps1 -UsePermissionCandidates` 使用 `warehouse-role-approval.bpmn` 配置为按角色所获菜单权限分配的仓库人工审批，脚本同时核对已发布定义，避免仅更新草稿但发布失败时误判完成。当前入库版本4、出库版本11。

当前按角色明确获得的权限解析候选人：入库为 `wms:receipt-order:approve-warehouse`，出库复用 `wms:shipment-order:approve-supervisor`。角色名称不限，用户获得该角色且账号启用后，可以参与新生成的对应审批任务；仅有提交审批权限不成为审批人，超级管理员身份也不隐式成为候选人。查询明确隔离租户，校验用户、角色、菜单及租户套餐启用与套餐授权。任意一位候选人通过即可结束该节点，其他待办结束；无启用候选人时拒绝。候选成员在任务生成时解析，后续授权变化不会自动重分配或撤销历史任务。ERP 采购/销售审批、MES 内部审批由来源系统负责，不在 WMS 再加对应节点。

宋凯80168保留原角色60084及60096。新增入库仓库审批菜单6741已加入60096，补齐WMS测试套餐120的四项提交/审批权限；既有角色授权均保留。旧出库上层决策权限6739保留，但新模型不使用。两库负责人字段未补填，未固定审批账号。配置与自测见 [审批权限说明](wms-approval-permissions-20260912.md)。

WMS 入库/出库流程的结果短信失败会记录错误，不能回滚审批；库存执行/状态回调异常仍按原事务处理。共享短信服务的长度/手机号问题未修改，短信送达不属于本轮验收成功依据。

本轮过程及实际验收证据见 [执行记录](wms-four-flows-progress-20260912.md)。本文示例为契约示意，不是已完成的测试单。

## 9. 当前交付验证状态（2026-09-12）

| 项目 | 已实现 / 测试 | 本机业务验证 | 待办 |
|---|---|---|---|
| ERP 采购入库 | 保留原成果，回归通过 | 940021 人工 BPM 原料 0→8；角色流程回归 940024 实收 2/合格 1，原料 5→6，各单流水 1 | 真实 ERP 联调 |
| MES 完工入库 | 独立申请契约，复用入库模型，回归通过 | 940022 人工 BPM 成品 0→8；角色流程回归 940025 实收 2/合格 1，成品 8→9，各单流水 1 | 真实 MES 联调、回调契约协商 |
| ERP 销售出库 | 来源幂等、审批执行、事务回滚、ERP 通知服务及测试 | 950026 页面确认、角色人工审批，成品 11→8，流水 1；重复回调不重复 | 真实 ERP 联调 |
| MES 领料出库 | 来源幂等、审批执行、查询结果及测试 | 950027 页面确认、角色人工审批，原料 8→5，流水 1；重复回调不重复 | 真实 MES 联调 |
| 只读接口 | 仓库、SKU、库存、流水查询 | 实际筛选分页成功；未登录 401，错误租户 403 | ERP/MES 小组按鉴权约定接入 |

最新后端 `wms-permission-package.log`：WMS195 + BPM18 = 213项通过，Maven打包成功；含8项真实H2/MyBatis权限候选查询测试。此前库存事务、第二行库存不足整体回滚、审批期间SKU停用、重复回调及6项WMS短信故障隔离测试均保留。前端最近仓库筛选10项测试及构建通过（`wms-shipment-warehouse-green.log`、`wms-shipment-warehouse-build.log`）；本次权限功能未改前端源码，未重复构建前端。全项目类型检查的既有问题未声称解决。

额外本机验收：950028（SIM-ROLE-SHORT-0912）库存 8、出库 100，人工批准后执行失败，流水 0，重复回调不再次执行；950029（SIM-ROLE-REJECT-0912）人工拒绝，已出库 0，流水 0。驳回首次受共享短信异常影响而回滚，WMS 专用隔离修复部署后用原测试单重新人工拒绝成功。此后入库测试将两种库存增加至原料 6、成品 9，库存为验收时点记录，不作为用户下一次自测的固定初始值。

最新前端拆分入口、单号换行及状态显示已用浏览器验证；详情支持完整单号和复制。实际操作步骤见 [四类业务自测](wms-four-flows-self-test-20260912.md)。

本轮 SQL 已在字段核对后执行，9 个新增字段已存在；不需重复执行。详细命令证据、容器备份名称和测试单见执行记录。没有提交或推送，没有开发/修改移库，没有修改 ERP/MES 源码或原开发目录。
# 来源价格补充

入库及出库申请价格字段、金额校验、模拟入口和验证边界见 [来源价格补充契约](wms-source-prices-20260912.md)。此补充不代表 ERP/MES 已联调。
