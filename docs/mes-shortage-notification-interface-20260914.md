# MES 库存不足消息接收接口

2026-09-14，源码新增，尚未部署或完成真实MES联调。此接口只接收消息，不自动创建采购申请、生产计划或审批。

## 地址与身份

POST /admin-api/mes/wms-shortage/receive。正式域名由部署方提供，WMS配置完整的可信网关URL，不直接暴露模块端口。沿用网关Bearer认证；网关删除外部伪造的login-user再注入经认证身份。正式环境须关闭模拟认证。当前验证没有使用真实账号签发令牌联调。

请求头：Authorization: Bearer <本租户WMS服务账号访问令牌>、tenant-id: <租户ID>、Content-Type: application/json。

接收账号须有 mes:wms-shortage:receive 权限，并配置 mes.wms-shortage.sender-user-id.<tenantId>=<实际WMS服务账号ID>。账号原始租户、请求上下文租户和报文tenantId必须相同；未配置默认拒绝。token和真实账号配置不写入源码。

## 请求

每个来源行一个事件。下列完整示例说明字段，非实际测试单：

```json
{
  "eventId": "318ab54b-125a-45d5-b7c0-c74d5b0d9a41",
  "eventVersion": 1,
  "tenantId": 2005,
  "senderSystem": "WMS",
  "sourceSystem": "ERP",
  "targetSystem": "MES",
  "eventType": "SALES_FINISHED_GOODS_SHORTAGE",
  "businessType": "SALE_SHIPMENT",
  "sourceOrderNo": "ERP-EXAMPLE-001",
  "sourceLineNo": 1,
  "wmsOrderNo": "WMS-ERP-ERP-EXAMPLE-001",
  "warehouseId": 106,
  "warehouseCode": "EXAMPLE-WH",
  "warehouseName": "示例仓库",
  "skuId": 123,
  "skuCode": "EXAMPLE-SKU",
  "skuName": "示例物料",
  "requestedQuantity": 10,
  "currentQuantity": 3,
  "reservedQuantity": 0,
  "availableQuantity": 3,
  "shortageQuantity": 7,
  "reservationSupported": false,
  "unit": "kg",
  "occurredAt": "2026-09-14T09:00:00",
  "shortageStatus": "OPEN"
}
```

字段规则：eventId为标准小写UUID；eventVersion正整数；tenantId/来源行号为非负整数，warehouseId/skuId为正整数。文本非空且不超过255字符；occurredAt为ISO本地日期时间。数量为JSON数字且非负，精度不超过30位、小数不超过10位；requestedQuantity大于0，shortageQuantity不大于requestedQuantity。

接收器从原始 JSON 文本直接解析为 BigDecimal/BigInteger，不经过 Double，保留大额数量的小数位并准确区分相邻金额/数量的同版本冲突。

senderSystem固定WMS，sourceSystem=ERP，targetSystem=MES，eventType=SALES_FINISHED_GOODS_SHORTAGE，businessType=SALE_SHIPMENT。当前预留未启用，reservedQuantity=0、reservationSupported=false、availableQuantity=currentQuantity。多行同SKU先分配库存，行缺口不一定等于行需求减总账面量。

shortageStatus：OPEN要求缺口>0，RESOLVED要求缺口=0，CANCELLED允许保留取消时缺口。RESOLVED仅表示检查时补足，不能作为已出库凭据。

## 成功、幂等与重试

HTTP 2xx且以下响应表示消息已持久化，不表示已采购、已生产、已补货或已读：

```json
{"code":0,"data":true}
```

同tenantId/eventId/eventVersion且规范化内容一致，重复返回成功，不生成新消息。相同版本内容不同返回冲突；eventId不能改绑另一来源业务。旧版本保留接收凭据但不覆盖较新消息、不重新提醒。新版本更新同一消息并重新成为未读。

验证/身份/权限/冲突错误为4xx，应修复报文或配置后人工重试；数据库等临时服务错误为5xx，可按WMS有限退避重试。鉴权失败一般为401/403，非法字段400，版本或身份冲突409；具体错误包络以现有网关及控制器响应为准，成功必须检查code和data，不仅检查HTTP200。

控制器内的错误处理优先于共享全局处理器，数据库异常返回 HTTP 503、其他未预期异常返回 HTTP 500，不会包装成 HTTP 200。数据库唯一键冲突或临时锁异常先在新的事务中重试，最多三次，仍失败再返回 503。

## 消息箱与查询

查看人员由管理员授予 mes:wms-shortage:query 权限。仅持有receive权限不自动获得查看权限。

首次部署先在系统菜单/权限管理注册两个权限项：mes:wms-shortage:receive（仅WMS服务账号角色）、mes:wms-shortage:query（需要处理缺料消息的业务人员角色），再分配对应角色。它们是新增权限码，不会因发布代码自动出现在已有角色中；不应向所有用户默认开放。

- GET /admin-api/mes/wms-shortage/page?pageNo=1&pageSize=20，pageSize 1–100。
- GET /admin-api/mes/wms-shortage/unread-count。
- POST /admin-api/mes/wms-shortage/read?eventId=<uuid>&eventVersion=<看到的版本>。

接口按租户过滤，已读按用户和版本记录。标记旧版已读不会吞掉新版未读提示。页面路径 /wms-shortage-inbox/mes，在web-antd与web-antdv-next中按权限显示“缺料消息”菜单；展示未读数并每60秒刷新。本轮提醒在消息箱内，不声称已接入全局铃铛、短信或离线推送。

## 数据与上线

上线前须先由管理员在“系统管理 → 菜单管理”中登记两个新的按钮权限（挂到该租户可用的 MES 管理菜单下，状态启用）：

- 名称“WMS 缺料消息接收”，权限标识 mes:wms-shortage:receive。
- 名称“WMS 缺料消息查看”，权限标识 mes:wms-shortage:query。

随后在“角色管理 → 分配菜单权限”中，将 receive 分配给本租户 WMS 专用服务账号的角色，将 query 分配给实际处理缺料消息的查看人员角色，并重新登录或刷新权限缓存。仅新增配置属性或填写未登记的权限字符串不足以授权。此步骤由部署管理员执行；本次未修改任何真实菜单、用户或角色。

新增sql/migrations/wms-shortage-inbox-20260914.sql内的 mes_wms_shortage_inbox、mes_wms_shortage_version、mes_wms_shortage_read 三张表。只在隔离MySQL验证语法，未对业务库执行迁移。

WMS负责库存检查、缺口事实、版本与投递；MES负责消息存储、查看权限和后续业务处理。接收器不回写WMS库存，不替代仓库审批。

实现及测试明细见 [接收端交接报告](wms-shortage-receiver-report-20260914.md)。仍待MES部署、真实网关账号配置、消息接收/未读/更新/重复投递联调；WMS补货复查与审批后的库存流水需双方用专用业务单核验。
