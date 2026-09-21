-- MES 生产工单增加 WMS 领料/入库集成结果字段
ALTER TABLE mes_pro_work_order
    ADD COLUMN wms_shipment_order_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'WMS 领料出库单号',
    ADD COLUMN wms_shipment_status TINYINT NOT NULL DEFAULT 0 COMMENT 'WMS 领料状态：0未申请 1申请中 2已出库 3失败 4取消',
    ADD COLUMN wms_receipt_order_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'WMS 完工入库单号',
    ADD COLUMN wms_receipt_status TINYINT NOT NULL DEFAULT 0 COMMENT 'WMS 入库状态：0未申请 1申请中 2已入库 3失败 4取消';
