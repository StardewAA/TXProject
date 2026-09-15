# MES 小组接入 WMS 接口文档

2026-09-14补充：已新增纯缺口消息源码，ERP销售成品不足由WMS通知MES消息箱，不自动建生产计划。见 [MES消息接收契约](../../txgycloud/docs/mes-shortage-notification-interface-20260914.md)、[WMS缺口及投递](wms-shortage-outbox-20260914.md)、[本机定向测试](wms-shortage-test-record-20260914.md)。新功能尚未部署或真实联调；下文原有执行结果查询保持独立。

版本：2026-09-12；交付范围：WMS 已实现接口，待真实 MES 联调。本文可单独交给 MES 小组使用。示例均为契约示意，不是生产编码或已执行单据。

## 1. 业务范围与审批分工

MES 完成生产业务内部审批 → 调用 WMS 申请接口 → WMS 验收／出库确认 → WMS 仓库负责人 BPM 审批 → 执行库存 → MES 查询结果（回调待约定）。

MES 的内部审批由 MES 小组负责，不在 WMS 再建对应审批节点。接口受理成功只代表草稿已创建，不代表库存成功。

| 业务 | orderType | 物料及仓库类型 | POST 申请地址 |
|---|---|---|---|
| 生产完工入库 | PRODUCTION_RECEIPT | FINISHED_GOODS | /admin-api/wms/external-application/mes/receipt |
| 生产领料出库 | PRODUCTION_SHIPMENT | RAW_MATERIAL | /admin-api/wms/external-application/mes/shipment |

每单一个仓库。入库仅合格数加库存；出库按申请数量整单确认，审批通过后扣库存，库存不足禁止负库存。暂不支持部分出库、拆单、预留核销、退货或退料；不涉及移库。

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
| 本文入库申请 | wms:receipt-order:create |
| 本文出库申请 | wms:shipment-order:create |
| 入库结果查询 | wms:receipt-order:query |
| 出库结果查询 | wms:shipment-order:query |
| 独立只读主数据、库存、流水查询 | wms:inventory:query |
| 仓库入库/出库提交审批 | wms:receipt-order:submit-approval / wms:shipment-order:submit-approval |

现有 `/rpc-api/wms/**` 按项目原规则 `permitAll`，只供受信内部服务网络使用，携带租户上下文；它不是对公网的鉴权方案。接入方优先使用上述 `/admin-api` 入口，不应直接暴露内部 RPC。没有更改共享网关或全系统安全规则。仓库提交和审批权限由 WMS 人员持有，接入账号不因调用申请接口而需要仓库审批权限。

## 3. 申请字段与完整示例

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

`price/totalPrice` 规则见第4节。示例中的物料、SKU、仓库及往来单位编码必须替换为同租户 WMS 实际编码，不使用 MES 内部数据库 ID。不得用来源给出的合格数替代 WMS 实际验收。保留字段 `reservedQuantity/releasedQuantity/shortageQuantity` 不代表已实现预留能力。

### 3.1 生产完工入库

`POST /admin-api/wms/external-application/mes/receipt`

```json
{
  "requestId": "MES-FG-20260912-001-v1",
  "tenantId": 2005,
  "sourceModule": "MES",
  "occurredAt": "2026-09-12T10:00:00",
  "businessChainNo": "MES-CHAIN-20260912-001",
  "sourceOrderNo": "MES-FG-20260912-001",
  "orderType": "PRODUCTION_RECEIPT",
  "eventVersion": 1,
  "items": [
    {
      "lineNo": 1,
      "sourceLineNo": 11,
      "materialCode": "FG-001",
      "skuCode": "FG-001-STD",
      "materialType": "FINISHED_GOODS",
      "materialNameSnapshot": "示例成品",
      "specificationSnapshot": "标准规格",
      "unitCode": "kg",
      "unitNameSnapshot": "kg",
      "warehouseCode": "WMS-FG-WH",
      "requiredQuantity": 10,
      "price": 12.5,
      "totalPrice": 125,
      "qualityRequiredSnapshot": false
    }
  ]
}
```

### 3.2 生产领料出库

`POST /admin-api/wms/external-application/mes/shipment`

```json
{
  "requestId": "MES-MR-20260912-001-v1",
  "tenantId": 2005,
  "sourceModule": "MES",
  "occurredAt": "2026-09-12T10:00:00",
  "businessChainNo": "MES-CHAIN-20260912-002",
  "sourceOrderNo": "MES-MR-20260912-001",
  "orderType": "PRODUCTION_SHIPMENT",
  "eventVersion": 1,
  "items": [
    {
      "lineNo": 1,
      "sourceLineNo": 11,
      "materialCode": "RAW-001",
      "skuCode": "RAW-001-STD",
      "materialType": "RAW_MATERIAL",
      "materialNameSnapshot": "示例原料",
      "specificationSnapshot": "标准规格",
      "unitCode": "kg",
      "unitNameSnapshot": "kg",
      "warehouseCode": "WMS-RAW-WH",
      "requiredQuantity": 10,
      "price": 12.5,
      "totalPrice": 125,
      "qualityRequiredSnapshot": false
    }
  ]
}
```

### 3.3 受理响应示例

```json
{
  "code": 0,
  "data": {
    "accepted": true,
    "requestId": "MES-FG-20260912-001-v1",
    "targetOrderNo": "WMS-MES-MES-FG-20260912-001",
    "businessStatus": "RECEIVED",
    "processedVersion": 1
  },
  "msg": ""
}
```

## 4. 来源单价与金额

申请明细新增可选 `price`（单价）和 `totalPrice`（行金额），单位为人民币元。
- 两者均未传：表示未提供，不代表零元；明确的零值会保留。
- 传单价、未传金额：WMS 按 `requiredQuantity × price` 四舍五入两位计算。
- 传金额必须同时传单价，金额必须与上述计算一致。
- 非负、最多两位小数，整数部分最多14位；超限或不一致返回申请校验错误。
- 来源单价及金额保留在来源快照和现有 WMS 单据明细，仓库只读核对，不覆盖商品档案成本价或销售价。
- 金额表示申请数量金额，入库实际执行仍仅按合格数量；不是财务结算接口。不扩展税额、折扣、币种、库存成本核算或单位换算。
- 旧单据不自动补价，不支持通过重发更改已受理单价。更正已受理业务需双方人工协调，不能提高 eventVersion 覆盖原单。

MES 完工及领料的成本单价来源、确认时点由 MES／相关业务负责人约定后传入。WMS 不自行采用商品销售价作为生产成本；这项计价约定尚待确认。

## 5. 幂等、追踪与编辑边界

传输幂等为同租户 `requestId`：同请求重放返回原结果，内容或操作不同报冲突。来源业务幂等为 `(tenantId, sourceModule, orderType, sourceOrderNo)`，由同一集成台账的内部哈希键串行化；更换 requestId 仍不会重复建单。业务摘要忽略重试发生时间，规范化行顺序和数量小数尾零，其他业务内容（含版本和业务链）变化报冲突。

不要通过提高 eventVersion 或改变数量来覆盖已受理申请；本轮不支持来源变更、部分发货或新版本替换原单。来源单号和明细行关联、创建时来源快照保留；来源单不得修改类型、仓库、来源关联、SKU、申请数量或金额，不得增删来源明细。仓库可填写入库验收数据。

审批回调以当前 BPM 实例与运行状态校验，单据行锁及条件更新保护重复执行。出库库存、流水、执行成功状态共同提交；执行失败时回滚执行保存点，保留审批终态和失败原因。重试执行复用原单，不重新建单。服务间 BPM/WMS 不具有分布式原子提交；网络异常仍需重试/对账。

## 6. 执行结果

鉴权查询：

```http
GET /admin-api/wms/external-application/execution?sourceOrderNo=MES-FG-20260912-001&orderType=PRODUCTION_RECEIPT
```

内部兼容查询：`GET /rpc-api/wms/integration/order/execution/by-source?tenantId=2005&sourceOrderNo=...&orderType=...`。
原受理结果（不是实时执行结果）：`GET /rpc-api/wms/integration/request/{requestId}`。

实时结果示意：

```json
{"code":0,"data":{"aggregateType":"RECEIPT","aggregateNo":"WMS-MES-MES-FG-20260912-001","processingStatus":"SUCCESS","resultCode":0,"resultData":{"sourceModule":"MES","sourceOrderNo":"MES-FG-20260912-001","businessChainNo":"MES-CHAIN-20260912-001","orderType":"PRODUCTION_RECEIPT","approvalStatus":2,"executionStatus":1,"completedQuantity":8,"notificationStatus":"POLLING_ONLY","items":[{"lineNo":1,"sourceLineNo":11,"materialCode":"FG-001","skuCode":"FG-001-STD","requiredQuantity":10,"actualQuantity":10,"qualifiedQuantity":8,"unqualifiedQuantity":2}]}},"msg":""}
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

出库结果使用同一查询地址，参数为 `sourceOrderNo=MES-MR-20260912-001&orderType=PRODUCTION_SHIPMENT`，`aggregateType=SHIPMENT`。金额来源以申请及单据明细为准，不假定执行结果或回调包含财务结算金额。

## 7. 回调与重试

当前已实现的是 **POLLING_ONLY：MES 主动查询结果**，未配置 MES 回调地址，不会向猜测的地址推送。成功、拒绝、取消、出库失败均可通过第6节查询。

建议未来回调沿用以下结构；**这是待双方确认的建议，不是已启用接口**：

```json
{
  "requestId": "WMS-MES-<稳定事件ID>",
  "tenantId": 2005,
  "eventVersion": 1,
  "occurredAt": "2026-09-12T11:00:00",
  "businessChainNo": "MES-CHAIN-20260912-001",
  "sourceOrderNo": "MES-FG-20260912-001",
  "wmsOrderNo": "WMS-MES-MES-FG-20260912-001",
  "orderType": "PRODUCTION_RECEIPT",
  "executionStatus": "COMPLETED",
  "items": [
    {
      "lineNo": 1,
      "sourceLineNo": 11,
      "materialCode": "FG-001",
      "skuCode": "FG-001-STD",
      "actualQuantity": 10,
      "qualifiedQuantity": 8,
      "unqualifiedQuantity": 2
    }
  ]
}
```

建议接收方按 requestId 幂等，成功及已处理重复事件返回 `{"code":0,"data":true}`；地址、鉴权／签名、超时、重试窗口、事件范围和明细映射均需双方确认后实施。建议在联调期间约定轮询频率，避免高频无限查询。


### 7.1 失败处理与错误码

无响应/超时先查询来源单结果，或使用完全相同的 requestId 和请求重试；不要自动换来源单号。`code!=0` 视为失败，不只看 HTTP 200。

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

## 8. 只读查询

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
GET /admin-api/wms/external-read/history?pageNo=1&pageSize=20&sourceOrderNo=MES-MR-20260912-001&orderType=2
```

```json
{"code":0,"data":{"list":[{"id":123,"materialCode":"FG-001","materialName":"示例成品","skuCode":"FG-001-STD","skuName":"标准规格","unitName":"件","warehouseCode":"WMS-FG-WH","warehouseName":"成品仓","quantity":5}],"total":1},"msg":""}
```

```json
{"code":0,"data":{"list":[{"id":456,"skuCode":"RAW-001-STD","warehouseCode":"WMS-RAW-WH","beforeQuantity":8,"quantity":-3,"afterQuantity":5,"orderId":1234,"orderNo":"WMS-MES-MES-MR-20260912-001","orderType":2,"sourceOrderNo":"MES-MR-20260912-001","businessChainNo":"MES-CHAIN-20260912-002","createTime":"2026-09-12T11:00:00"}],"total":1},"msg":""}
```

兼容内部接口：`/rpc-api/wms/stock-read/page`、`/warehouses`、`/skus` 和 `/rpc-api/wms/stock-history-read/page`。旧仓库/SKU 内部选择器为列表接口；鉴权入口提供筛选分页。仓库/SKU 当前在本租户列表上过滤分页，规模增大时可改为数据库分页；库存与流水已使用数据库分页。

实际库存实体仅有 `quantity`。它是现存/账面数量；本轮没有预留量字段、可用量计算或单位换算，不能虚构 `available = onHand - reserved` 已实现。出库页面原兼容字段 availableQuantity 若存在也仅来自账面余额。查询为实时读取，多次分页不保证跨页同一时点快照。

### 8.1 仓库和 SKU 示例

```http
GET /admin-api/wms/external-read/warehouses?pageNo=1&pageSize=20&code=WMS-FG-WH
GET /admin-api/wms/external-read/skus?pageNo=1&pageSize=20&materialCode=FG-001
```

仓库响应示例（字段可为空）：

```json
{"code":0,"data":{"list":[{"code":"WMS-FG-WH","name":"成品仓","warehouseType":"FINISHED_GOODS","status":0,"address":null,"principal":null,"remark":null,"sort":1}],"total":1},"msg":""}
```

SKU 响应示例：

```json
{"code":0,"data":{"list":[{"materialCode":"FG-001","materialName":"示例成品","skuCode":"FG-001-STD","skuName":"标准规格","unitName":"kg"}],"total":1},"msg":""}
```

此 SKU 只读接口当前不返回单价或物料类型字段；不要假设这些字段已在响应中。业务物料类型需与 WMS 主数据映射约定，来源单据价格由申请方传入。空查询返回 `{"code":0,"data":{"list":[],"total":0},"msg":""}`。

## 9. 接入步骤与交付边界

1. 管理员提供部署域名、租户和接入账号令牌及所需权限；本机 localhost 仅本机可达，不是其他机器的联调地址。
2. 用只读接口核对物料、SKU、仓库编码及类型，确认双方数量单位一致。
3. MES 完成自身审批后，使用新的专用来源单号发送申请；记录 requestId、sourceOrderNo、targetOrderNo。
4. WMS 仓库人员验收／确认并通过仓库 BPM；MES 查询直到明确终态，核对数量和来源行号。
5. 验证 MES 结果轮询；回调接收与成本计价约定列为待办。

| 项目 | 当前状态 |
|---|---|
| 本文两类业务及只读接口 | WMS 已实现；此前有本机模拟来源、真实 WMS/BPM 验收记录 |
| 来源价格 | 已实现并部署；价格定向后端测试19项通过，前端构建通过，未将其当作真实 MES 价格联调 |
| 仓库、SKU、库存、流水查询 | 已有本机筛选分页及401/403验证记录 |
| 真实 MES 网络与业务联调 | 尚未完成，由双方按本文对接 |
| 本次文档拆分 | 仅整理文档，不新增业务测试、数据或权限 |

WMS 负责主数据映射校验、仓库操作、库存执行、流水及执行结果。MES 负责自身审批、来源业务数据、价格责任、单号与行号映射以及消费执行结果。模拟申请会标记 simulation=true，不发送真实系统通知；真实接入应调用本文申请接口，不调用模拟入口。

追溯资料：[总接口文档](wms-erp-mes-interface-handoff-20260912.md)、[价格补充](wms-source-prices-20260912.md)、[本机验收记录](wms-four-flows-progress-20260912.md)。
