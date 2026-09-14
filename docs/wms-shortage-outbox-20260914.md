# WMS 库存缺口消息与投递说明

2026-09-14：源码已新增缺口记录、版本 outbox、投递和出库详情展示。尚未部署至运行容器、未执行业务数据库迁移、未完成真实 ERP/MES 联调。消息接收不创建采购申请、生产计划或审批。

## 方向及库存责任

| 来源出库 | 缺料事件 | 接收方 |
| --- | --- | --- |
| MES / PRODUCTION_SHIPMENT | PRODUCTION_RAW_MATERIAL_SHORTAGE | ERP 消息箱 |
| ERP / SALE_SHIPMENT | SALES_FINISHED_GOODS_SHORTAGE | MES 消息箱 |

来源草稿创建后、仓库提交审批前以及批准后库存执行失败时检查。库存不足保持原草稿，不提交新 BPM；库存执行仍使用原事务和保存点，不写出库流水、不扣库存。因检查阻止审批时，只对专用库存不足异常允许缺口事务提交，并返回业务错误 1060300000。其他异常不豁免回滚。

同一仓库/SKU按来源行号排序，依次分配现存量；各行缺口之和为该SKU总需求减现存量的正数部分。例如两行需求3和4，库存5，行缺口为0和2，不是0和0。当前没有预留功能：reservedQuantity=0、reservationSupported=false、availableQuantity=currentQuantity；当前可用量为SKU总账面量，行缺口为分配后缺口。

## 表、版本与事务

新增表 wms_shortage_event，脚本 deploy/mysql/repair/2026-09-14-wms-shortage.sql。每个缺口来源行使用稳定 UUID eventId；现存量、缺口或缺口状态变化时增加 eventVersion，原报文和投递记录保留。相同检查结果不新增版本。来源单本身已有业务幂等保护；来源行对应的订单ID、明细ID在该不可变来源单内唯一。

草稿/缺口在同一事务提交。发送器只读取已提交最新版本，以出库单行锁串行化复查、取消和投递；HTTP调用有超时。崩溃事务回滚后以同一eventId/version重试，接收端必须幂等。旧版尚未发送的事件被最新版取代，历史保留，不再按旧缺口投递。

缺口状态与通知状态独立：

| 缺口状态 | 含义 |
| --- | --- |
| OPEN | 当前仍缺料 |
| RESOLVED | 补货复查时已无缺口，不表示已出库 |
| CANCELLED | 来源出库已作废或删除 |

| 通知状态 | 含义 |
| --- | --- |
| WAITING_INTEGRATION | 本租户目标地址或令牌未配置 |
| PENDING / SENDING | 待发送 / 投递事务正在执行 |
| SUCCEEDED | 对方 HTTP 2xx 且 code=0,data=true |
| FAILED | 未收到成功确认，查看错误和下次重试时间 |
| SIMULATED | 模拟来源，不投递 |

补货后的 RESOLVED、作废后的 CANCELLED 同样生成新版消息，使接收箱不继续展示旧缺口。通知成功与补货、审批、库存执行互不等价。若批准后执行失败，补货复查通过会恢复待提交审批，仍由仓库重新提交。

## 发送配置

先对目标环境应用新增表脚本，再发布源码。仅准备脚本，不自动修改运行数据库。

WMS 配置中心按租户分别配置以下键：

| 键 | 内容 |
| --- | --- |
| wms.shortage.sender-enabled | true 时启用每60秒扫描；默认不启用自动投递 |
| wms.shortage.targets.erp.<tenantId>.url | ERP完整入口地址，路径 /admin-api/erp/wms-shortage/receive |
| wms.shortage.targets.erp.<tenantId>.token | 本租户 WMS 接入账号的访问令牌，不带 Bearer 前缀 |
| wms.shortage.targets.mes.<tenantId>.url | MES完整入口地址，路径 /admin-api/mes/wms-shortage/receive |
| wms.shortage.targets.mes.<tenantId>.token | MES接收侧认可的本租户 WMS 账号访问令牌 |

请求头 Authorization: Bearer <访问令牌>、tenant-id: <tenantId>、Content-Type: application/json。接收方独立配置 WMS 账号身份绑定及 receive 权限；不能仅凭 senderSystem=WMS 信任请求。正式地址由部署方配置，跨主机不使用 localhost。

连接超时5秒，请求超时10秒，不跟随重定向。每版最多5次自动发送，网络/5xx失败依次等待60、120、240、480秒；4xx、业务拒绝、非法配置/报文停止自动重试。人工重试可在修复配置后再次投递，不生成新事件、不操作库存。未配置时每5分钟复查配置，单轮扫描上限100条。错误记录只保存通用原因和HTTP码，不写令牌或任意对方错误正文。

## 查询和操作接口

| 接口 | 权限 | 结果 |
| --- | --- | --- |
| GET /admin-api/wms/shipment-shortage/list?orderId=... | wms:shipment-order:query | 本租户该单最新缺口版本列表，不检查库存、不发消息 |
| POST /admin-api/wms/shipment-shortage/recheck?orderId=... | wms:shipment-order:submit-approval | 复查并返回最新列表，不提交审批 |
| POST /admin-api/wms/shipment-shortage/retry?eventId=... | wms:shipment-order:submit-approval | data=true仅代表对方确认 |

通用响应使用 code/data/msg。401/403为鉴权或权限不足；提交审批库存不足为1060300000。非法/跨租户事件不会被读取或投递。新查询与复查无效单号目前沿用 IllegalArgumentException 通用处理，客户端不能无限重试业务输入错误。

前端入口：WMS → 出库管理 → 单号详情 → 库存缺口通知，也显示在出库 BPM 业务详情组件中。按钮权限与接口一致。库存执行结果回调通知和缺口通知为两个独立区域。

## 报文示例

以下为契约示例，不是实际业务单据：

```json
{
  "eventId": "318ab54b-125a-45d5-b7c0-c74d5b0d9a41",
  "eventVersion": 1,
  "tenantId": 2005,
  "senderSystem": "WMS",
  "sourceSystem": "MES",
  "targetSystem": "ERP",
  "eventType": "PRODUCTION_RAW_MATERIAL_SHORTAGE",
  "businessType": "PRODUCTION_SHIPMENT",
  "sourceOrderNo": "MES-MR-EXAMPLE-001",
  "sourceLineNo": 1,
  "wmsOrderNo": "WMS-MES-MES-MR-EXAMPLE-001",
  "warehouseId": 106,
  "warehouseCode": "RAW-WH",
  "warehouseName": "原料仓",
  "skuId": 123,
  "skuCode": "RAW-STD",
  "skuName": "示例原料",
  "requestedQuantity": 10,
  "currentQuantity": 3,
  "reservedQuantity": 0,
  "reservationSupported": false,
  "availableQuantity": 3,
  "shortageQuantity": 7,
  "unit": "kg",
  "occurredAt": "2026-09-14T09:00:00",
  "shortageStatus": "OPEN"
}
```

成功及重复确认：`{"code":0,"data":true,"msg":""}`。对方已确认的某一版本不重复发送；新版本需要新的接收确认。

## 联调待办

部署 WMS、ERP/MES 新代码及各自表；配置租户账号、接收权限与目标地址；用专用测试物料/单据执行真实网络联调；核验接收箱可见性及未读提醒；验证补货后重新检查、仓库审批和实际流水。现有测试只证明本机隔离数据库和HTTP测试服务器行为，不能代替这些项目。
