-- Run against the WMS database. Safe to run again; existing status values are preserved.
-- Matches erp_wms_integration_v0_2.sql: 0 = enabled, 1 = disabled.
SET @wms_merchant_status_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'wms_merchant' AND COLUMN_NAME = 'status'),
    'SELECT ''wms_merchant.status already exists'' AS migration_result',
    'ALTER TABLE `wms_merchant` ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT ''Enable status: 0 enabled, 1 disabled'' AFTER `source_version`'
);
PREPARE wms_merchant_status_stmt FROM @wms_merchant_status_ddl;
EXECUTE wms_merchant_status_stmt;
DEALLOCATE PREPARE wms_merchant_status_stmt;
