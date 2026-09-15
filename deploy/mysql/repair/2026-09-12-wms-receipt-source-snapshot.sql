-- 先应用 2026-09-12-wms-receipt-source.sql，再部署本次 WMS。
-- 只新增来源明细快照字段，不回填历史单据，不迁移库存；可重复执行。
SET @ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'wms_receipt_order_detail' AND column_name = 'source_snapshot_json'),
    'SELECT 1', 'ALTER TABLE wms_receipt_order_detail ADD COLUMN source_snapshot_json longtext NULL COMMENT ''创建时来源明细快照 JSON''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
