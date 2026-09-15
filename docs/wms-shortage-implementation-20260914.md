# 缺料消息实施计划与接口约定

依据：2026-09-14 用户已确认 WMS 发现缺料后向 ERP 发结构化消息，接收不创建采购申请、不启动采购审批。沿用既有两种方向：MES 领料缺原料到 ERP；ERP 销售缺成品到 MES。

## 约束

- 保留两个开发分支全部已有修改，不提交、推送、清理、修改移库或历史库存。
- WMS 在 txgycloud-wms；ERP/MES 在 txgycloud。沿用现有开发分支和现有成果，不迁移工作区。
- 库存与通知独立；缺口阻止提交审批时必须提交缺口记录；库存执行仍保留事务内防负库存。
- 未启用预留：reservedQuantity=0、reservationSupported=false、availableQuantity=currentQuantity。
- 仅专用内存数据库、HTTP 测试服务测试；真实三系统联调单独标记。

## 契约

POST /admin-api/{erp|mes}/wms-shortage/receive。复用 Bearer 访问令牌和 tenant-id。接收端 require permission `{erp|mes}:wms-shortage:receive`，并以配置的本租户 WMS 服务账号 userId 核对实际登录身份，不仅信任报文字段。

JSON 每个来源行一个事件：eventId(UUID，版本变化重用)、eventVersion(正整数)、tenantId、senderSystem="WMS"、sourceSystem("MES"或"ERP")、targetSystem、eventType("PRODUCTION_RAW_MATERIAL_SHORTAGE"或"SALES_FINISHED_GOODS_SHORTAGE")、businessType("PRODUCTION_SHIPMENT"或"SALE_SHIPMENT")、sourceOrderNo、sourceLineNo(整数)、wmsOrderNo、warehouseId、warehouseCode、warehouseName、skuId、skuCode、skuName、requestedQuantity、currentQuantity、reservedQuantity、availableQuantity、shortageQuantity、reservationSupported=false、unit、occurredAt(ISO本地时间)、shortageStatus("OPEN"|"RESOLVED"|"CANCELLED")。

数量按仓库/SKU分组，按来源行顺序分配现存量，行缺口之和等于整组缺口。currentQuantity/availableQuantity 是仓库SKU总量；shortageQuantity 是此来源行分配后的缺口。

成功及同版本同内容重复返回 {"code":0,"data":true}。同版本不同内容拒绝；旧版本不得覆盖新状态，可成功确认但不产生新消息。事件ID跨业务复用拒绝。接收成功只表示消息持久化。

## 任务

- [x] 1. WMS：新增缺口/outbox表与服务，创建来源出库后检查、提交审批前检查、审批执行失败后检查；补货复查和取消更新版本；定向数据库测试。
- [x] 2. ERP/MES：新增独立消息接收/查询接口及消息箱，租户与身份校验、幂等；迁移脚本和定向接收测试。
- [x] 3. WMS：提交后定时投递，按租户和目标配置 URL/令牌，超时与有限退避、人工重试；假接收端协议测试。
- [x] 4. WMS：出库详情增加缺口表、重查及重试；更新交接文档，执行定向检查和独立审查。

发送状态 PENDING/SENDING/SUCCEEDED/FAILED/WAITING_INTEGRATION/SIMULATED，缺口状态 OPEN/RESOLVED/CANCELLED 独立保存。版本更新重置投递次数；当前版本最多自动发送5次，网络/5xx退避60/120/240/480秒，4xx或业务拒绝停止自动重试；人工重试受权限控制。未配置地址或令牌不发送；模拟单不发送。

## 进度

已核对两个目录与分支；CodeGraph 启动失败，已回退文件检索。ERP 现有采购协作类接收不适用于纯通知。已获用户批准业务范围，实施选择自主决定。

WMS47项定向测试、最终前端构建及独立MySQL8.4最新读取验证通过；独立审查发现的4项事务/生命周期/调度问题已修复并复核。临时MySQL容器及其测试卷已清理。ERP/MES各9项定向测试通过，接收端精确小数、事务重试和HTTP错误分类3项审查问题已修复并复核。合计65项，源码开发完成；尚未部署或真实联调。
