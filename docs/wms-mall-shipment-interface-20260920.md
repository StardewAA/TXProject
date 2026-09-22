# 商城申请 WMS 出库及结果回调接口文档

版本：2026-09-20
适用范围：商城销售订单向 WMS 申请成品出库。

## 1. 业务流程

1. 商城完成自身的发货前置校验后，调用 WMS 出库申请接口。
2. WMS 幂等受理申请并创建出库草稿，不立即扣减库存。
3. WMS 人员确认出库内容，提交仓库 BPM 审批。
4. 审批通过后 WMS 扣减库存并生成库存流水。
5. 库存事务提交后，WMS 主动回调商城。商城明确返回 `code=0` 且 `data=true` 才视为接收成功。

申请受理成功只代表 WMS 草稿已创建，不代表已出库。本期不支持部分出库、拆单或通过重发修改已受理单据。

## 2. 商城调用 WMS：提交出库申请

### 2.1 地址与权限

```http
POST /admin-api/wms/external-application/mall/shipment
Authorization: Bearer <商城服务账号访问令牌>
tenant-id: 2005
Content-Type: application/json
```

所需权限：`wms:shipment-order:create`。

必须由商城服务端调用，不得将 WMS 服务账号令牌下发到浏览器。`tenantId` 必须与已认证请求上下文的 `tenant-id` 一致。

### 2.2 请求示例

```json
{
  "requestId": "mall-outbound-20260920-0001",
  "tenantId": 2005,
  "sourceModule": "MALL",
  "occurredAt": "2026-09-20T10:00:00",
  "businessChainNo": "MALL-CHAIN-20260920-0001",
  "sourceOrderNo": "MALL-ORDER-20260920-0001",
  "partnerCode": "CUSTOMER-001",
  "orderType": "SALE_SHIPMENT",
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
      "unitCode": "PC",
      "unitNameSnapshot": "件",
      "warehouseCode": "WMS-FG-WH",
      "requiredQuantity": 3,
      "price": 12.50,
      "totalPrice": 37.50,
      "qualityRequiredSnapshot": false
    }
  ]
}
```

### 2.3 请求字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| requestId | 是 | 商城生成的传输幂等号，最多 64 字符；禁止以 `B:` 开头 |
| tenantId | 是 | 租户编号，必须与请求头 `tenant-id` 一致 |
| sourceModule | 是 | 固定为 `MALL` |
| occurredAt | 是 | 业务发生时间，格式 `yyyy-MM-dd'T'HH:mm:ss` |
| businessChainNo | 是 | 跨系统业务链跟踪号，最多 64 字符 |
| sourceOrderNo | 是 | 商城订单号，最多 55 字符；WMS 单号为 `WMS-MALL-<sourceOrderNo>` |
| partnerCode | 否 | WMS 往来单位编码；传入时必须已存在于当前租户 |
| orderType | 是 | 固定为 `SALE_SHIPMENT` |
| eventVersion | 是 | 正整数，首次为 `1`；提高版本不代表可覆盖旧单 |
| items | 是 | 非空出库明细数组 |

明细字段：

| 字段 | 必填 | 说明 |
|---|---:|---|
| lineNo | 是 | 请求内不重复的正整数行号 |
| sourceLineNo | 否 | 商城原始明细行号；缺省时使用 `lineNo`，同样不得重复 |
| materialCode | 是 | WMS 物料编码，最多 20 字符 |
| skuCode | 是 | WMS SKU 编码，最多 64 字符，必须属于 `materialCode` |
| materialType | 是 | 固定为 `FINISHED_GOODS` |
| materialNameSnapshot | 是 | 下单时物料名称快照，最多 255 字符 |
| specificationSnapshot | 否 | 规格快照 |
| unitCode / unitNameSnapshot | 是 | 数量单位编码与名称，各最多 64 字符 |
| warehouseCode | 是 | WMS 成品仓编码，最多 20 字符；一张申请只能有一个仓库 |
| requiredQuantity | 是 | 大于 0，最多两位小数；不做单位换算 |
| price | 否 | 来源单价，人民币元，非负且最多两位小数 |
| totalPrice | 否 | 行金额；传入时必须同时传 `price`，并等于数量乘单价四舍五入两位 |

编码必须使用当前租户 WMS 的真实编码，不传商城数据库 ID，不按名称或数字相同猜测关联。

### 2.4 受理成功响应

```json
{
  "code": 0,
  "data": {
    "accepted": true,
    "requestId": "mall-outbound-20260920-0001",
    "targetOrderNo": "WMS-MALL-MALL-ORDER-20260920-0001",
    "businessStatus": "RECEIVED",
    "processedVersion": 1
  },
  "msg": ""
}
```

## 3. 幂等和超时处理

- 传输幂等键：`tenantId + requestId`。相同请求重放返回原结果；同一 `requestId` 的内容发生变化则报冲突。
- 业务幂等键：`tenantId + MALL + SALE_SHIPMENT + sourceOrderNo`。即使换了 `requestId`，也不会重复建立出库单。
- 请求超时时，优先使用结果查询对账，或使用完全相同的 `requestId` 和请求体重试。
- 不要通过更换单号、改数量或提高 `eventVersion` 覆盖已受理申请。

## 4. 商城调用 WMS：查询出库结果

```http
GET /admin-api/wms/external-application/mall/execution?sourceOrderNo=MALL-ORDER-20260920-0001
Authorization: Bearer <商城服务账号访问令牌>
tenant-id: 2005
```

所需权限：`wms:shipment-order:query`。

响应示例：

```json
{
  "code": 0,
  "data": {
    "aggregateType": "SHIPMENT",
    "aggregateNo": "WMS-MALL-MALL-ORDER-20260920-0001",
    "processingStatus": "SUCCESS",
    "resultCode": 0,
    "resultData": {
      "sourceModule": "MALL",
      "sourceOrderNo": "MALL-ORDER-20260920-0001",
      "businessChainNo": "MALL-CHAIN-20260920-0001",
      "orderType": "SALE_SHIPMENT",
      "approvalStatus": 2,
      "executionStatus": 1,
      "completedQuantity": 3,
      "notificationStatus": "DELIVERED",
      "notificationError": "",
      "items": [
        {
          "lineNo": 1,
          "sourceLineNo": 11,
          "materialCode": "FG-001",
          "skuCode": "FG-001-STD",
          "requiredQuantity": 3,
          "actualQuantity": 3
        }
      ]
    }
  },
  "msg": ""
}
```

`processingStatus` 取值：

| 状态 | 含义 |
|---|---|
| DRAFT | WMS 草稿，库存未变更 |
| WAITING_APPROVAL | 仓库 BPM 审批中 |
| WAITING_EXECUTION | 已审批，尚未完成库存执行 |
| EXECUTION_FAILED | 执行失败，查看 `executionError` |
| SUCCESS | 库存、流水和执行状态已提交 |
| REJECTED | 仓库审批拒绝 |
| CANCELLED | 流程或单据取消 |

## 5. WMS 回调商城：出库成功通知

> 商城开发组需在 `trade-server` 实现此接收接口。WMS 侧 Feign 契约已提供，但不代表商城接收逻辑已完成。

```http
POST /rpc-api/trade/wms-callback/execution-result
Content-Type: application/json
```

该地址仅用于同一受信服务网络内的 WMS 调用，不得通过公网、网关或浏览器暴露。WMS 调用时携带当前租户上下文；商城接收方必须再校验请求体 `tenantId` 与上下文一致。

回调请求：

```json
{
  "requestId": "mall-outbound-20260920-0001",
  "tenantId": 2005,
  "eventVersion": 1,
  "occurredAt": "2026-09-20T11:00:00",
  "businessChainNo": "MALL-CHAIN-20260920-0001",
  "sourceOrderNo": "MALL-ORDER-20260920-0001",
  "wmsOrderNo": "WMS-MALL-MALL-ORDER-20260920-0001",
  "orderType": "SALE_SHIPMENT",
  "executionStatus": "COMPLETED",
  "items": [
    {
      "lineNo": 1,
      "sourceLineNo": 11,
      "materialCode": "FG-001",
      "skuCode": "FG-001-STD",
      "actualQuantity": 3
    }
  ]
}
```

关键约定：

- `requestId` 是商城提交出库申请时的原始 `requestId`，WMS 重试回调时保持不变。
- `executionStatus` 本期固定为 `COMPLETED`，表示 WMS 库存扣减和流水已成功提交。
- `actualQuantity` 是实际扣减数量；本期不支持部分出库，因此应与申请数量相等。
- 商城必须以 `tenantId + requestId` 幂等处理，重复的相同成功结果不得重复推进订单、重复发货或重复发消息。
- 若订单号、WMS 单号或明细与已接收结果冲突，商城应拒绝覆盖并报警。

商城接收成功必须返回：

```json
{
  "code": 0,
  "data": true,
  "msg": ""
}
```

任何网络异常、`code != 0`、`data != true` 或空响应都视为回调失败。WMS 不回滚已完成的库存事务，而是至少等待 60 秒后使用同一 `requestId` 重试。

本期仅主动回调出库成功。审批拒绝、取消、库存不足或执行失败通过第 4 节查询接口获取。

## 6. 常见错误

| 业务码 | 含义与处理 |
|---|---|
| 401 / 403 | 令牌失效或权限不足；恢复鉴权后沿用原请求重试 |
| 400 | Bean Validation 参数错误，查看 `msg` |
| 1060900000 | `requestId` 与已有请求的操作或内容冲突 |
| 1060900009 | 请求体租户与认证上下文不一致 |
| 1060900023 | 出库申请校验失败，根据 `msg` 检查来源、单号、仓库、SKU、数量或金额 |
| 1060900024 | 同一来源业务已受理，但本次业务内容不一致 |
| 1060201000 | 出库单不存在 |
| 1060300000 | 库存不足，不允许负库存 |

调用方必须同时判断 HTTP 结果和响应体 `code`，不得只因 HTTP 200 就认定成功。

## 7. 商城开发组对接清单

1. 申请专用 WMS 服务账号，仅授予 `wms:shipment-order:create` 和 `wms:shipment-order:query`。
2. 建立商城 SKU 与 WMS `materialCode + skuCode + warehouseCode` 的明确关联。
3. 实现 `/rpc-api/trade/wms-callback/execution-result`，校验租户并按 `tenantId + requestId` 幂等。
4. 商城保存 `requestId、sourceOrderNo、targetOrderNo`，超时时使用原请求号重试或查询对账。
5. 联调覆盖：正常出库、重复申请、申请超时、回调重放、回调暂时失败后重试、库存不足、审批拒绝和跨租户拒绝。

## 8. 当前交付边界

- WMS 侧已提供商城出库申请、结果查询、商城回调路由和 60 秒重试扫描。
- `trade-api` 已提供回调 Java/Feign 契约及 DTO。
- 商城 `trade-server` 仍需由商城开发组实现回调接收、幂等落库和订单状态推进。
- 尚未进行真实商城与 WMS 网络联调；正式上线前必须按第 7 节联调验收。
