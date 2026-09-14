-- 采购入库来源追踪。可重复执行；只加可空字段，不迁移历史库存。
-- 必须先应用 2026-09-11-wms-receipt-check-approval.sql，再部署本次 WMS。
SET @ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'wms_receipt_order' AND column_name = 'source_module'),
    'SELECT 1', 'ALTER TABLE wms_receipt_order ADD COLUMN source_module varchar(16) NULL COMMENT ''集成来源模块''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'wms_receipt_order' AND column_name = 'business_chain_no'),
    'SELECT 1', 'ALTER TABLE wms_receipt_order ADD COLUMN business_chain_no varchar(64) NULL COMMENT ''业务链编号''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'wms_receipt_order_detail' AND column_name = 'source_line_no'),
    'SELECT 1', 'ALTER TABLE wms_receipt_order_detail ADD COLUMN source_line_no int NULL COMMENT ''来源单据明细行号''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
