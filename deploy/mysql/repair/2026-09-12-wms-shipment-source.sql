-- WMS only. Check information_schema before each additive change. No stock updates.
SET @wms_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_shipment_order' AND column_name='source_module'), 'SELECT 1', 'ALTER TABLE wms_shipment_order ADD COLUMN source_module varchar(16) NULL');
PREPARE wms_stmt FROM @wms_ddl; EXECUTE wms_stmt; DEALLOCATE PREPARE wms_stmt;
SET @wms_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_shipment_order' AND column_name='business_chain_no'), 'SELECT 1', 'ALTER TABLE wms_shipment_order ADD COLUMN business_chain_no varchar(64) NULL');
PREPARE wms_stmt FROM @wms_ddl; EXECUTE wms_stmt; DEALLOCATE PREPARE wms_stmt;
SET @wms_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_shipment_order' AND column_name='simulation'), 'SELECT 1', 'ALTER TABLE wms_shipment_order ADD COLUMN simulation bit NOT NULL DEFAULT 0');
PREPARE wms_stmt FROM @wms_ddl; EXECUTE wms_stmt; DEALLOCATE PREPARE wms_stmt;
SET @wms_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_shipment_order_detail' AND column_name='source_line_no'), 'SELECT 1', 'ALTER TABLE wms_shipment_order_detail ADD COLUMN source_line_no int NULL');
PREPARE wms_stmt FROM @wms_ddl; EXECUTE wms_stmt; DEALLOCATE PREPARE wms_stmt;
SET @wms_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_shipment_order_detail' AND column_name='source_snapshot_json'), 'SELECT 1', 'ALTER TABLE wms_shipment_order_detail ADD COLUMN source_snapshot_json longtext NULL');
PREPARE wms_stmt FROM @wms_ddl; EXECUTE wms_stmt; DEALLOCATE PREPARE wms_stmt;

SET @wms_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_shipment_order' AND column_name='notification_status'), 'SELECT 1', 'ALTER TABLE wms_shipment_order ADD COLUMN notification_status varchar(32) NULL');
PREPARE wms_stmt FROM @wms_ddl; EXECUTE wms_stmt; DEALLOCATE PREPARE wms_stmt;

SET @wms_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_shipment_order' AND column_name='notification_error'), 'SELECT 1', 'ALTER TABLE wms_shipment_order ADD COLUMN notification_error varchar(1000) NULL');
PREPARE wms_stmt FROM @wms_ddl; EXECUTE wms_stmt; DEALLOCATE PREPARE wms_stmt;

SET @wms_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_shipment_order' AND column_name='notification_attempts'), 'SELECT 1', 'ALTER TABLE wms_shipment_order ADD COLUMN notification_attempts int NOT NULL DEFAULT 0');
PREPARE wms_stmt FROM @wms_ddl; EXECUTE wms_stmt; DEALLOCATE PREPARE wms_stmt;

SET @wms_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_shipment_order' AND column_name='next_notification_time'), 'SELECT 1', 'ALTER TABLE wms_shipment_order ADD COLUMN next_notification_time datetime NULL');
PREPARE wms_stmt FROM @wms_ddl; EXECUTE wms_stmt; DEALLOCATE PREPARE wms_stmt;
