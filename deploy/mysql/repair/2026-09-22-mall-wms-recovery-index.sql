-- WMS paid-order recovery scans by tenant and terminal order state before checking shipment existence.
-- Keep the leading columns aligned with TradeWmsRecoveryMapper so the compensating job does not scan a tenant's history.

SET @trade_wms_recovery_index_signature = (
  SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index), '')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'trade_order'
    AND index_name = 'idx_trade_order_wms_recovery');
SET @trade_wms_recovery_index_non_unique = (
  SELECT MIN(non_unique)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'trade_order'
    AND index_name = 'idx_trade_order_wms_recovery');
SET @trade_wms_recovery_index_ddl = CASE
  WHEN @trade_wms_recovery_index_signature = '' THEN
    'ALTER TABLE trade_order ADD INDEX idx_trade_order_wms_recovery (tenant_id, pay_status, status, deleted, id)'
  WHEN @trade_wms_recovery_index_signature <> 'tenant_id,pay_status,status,deleted,id'
       OR @trade_wms_recovery_index_non_unique <> 1 THEN
    'ALTER TABLE trade_order DROP INDEX idx_trade_order_wms_recovery, ADD INDEX idx_trade_order_wms_recovery (tenant_id, pay_status, status, deleted, id)'
  ELSE 'SELECT 1'
END;
PREPARE trade_wms_recovery_index_stmt FROM @trade_wms_recovery_index_ddl;
EXECUTE trade_wms_recovery_index_stmt;
DEALLOCATE PREPARE trade_wms_recovery_index_stmt;
