-- 商城出库 P0 安全边界：WMS 单据持久化明确店铺归属；历史缺失店铺的单据保持 NULL，交人工处理。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_shipment_order' AND COLUMN_NAME='shop_id')=0,
  'ALTER TABLE wms_shipment_order ADD COLUMN shop_id BIGINT NULL COMMENT ''商城来源店铺；非商城或历史单据为空'' AFTER business_chain_no',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_shipment_order'
    AND INDEX_NAME='idx_wms_shipment_order_shop')=0,
  'ALTER TABLE wms_shipment_order ADD INDEX idx_wms_shipment_order_shop (tenant_id,shop_id,source_module,biz_order_no)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- trade_wms_shipment 允许 NULL 只用于标识无法解析店铺的人工异常；正式请求仍由服务端强制 shop_id > 0。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_wms_shipment' AND COLUMN_NAME='shop_id'
    AND IS_NULLABLE='NO')=1,
  'ALTER TABLE trade_wms_shipment MODIFY COLUMN shop_id BIGINT NULL DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
