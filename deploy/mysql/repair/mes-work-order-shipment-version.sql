-- ============================================================
-- mes_pro_work_order 增加「WMS 领料申请版本」字段
--
-- 用途：同一工单多次领料（含缺料恢复后的重新申请）时，向 WMS 报文的
--       requestId / eventVersion 以及 WMS 出库单号按版本区分，
--       避免被 WMS 幂等校验（同 requestId 内容不一致）或出库单号唯一键拒绝。
-- 幂等，可重复执行。
-- ============================================================

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_pro_work_order'
       AND COLUMN_NAME = 'wms_shipment_version') = 0,
    'ALTER TABLE mes_pro_work_order ADD COLUMN wms_shipment_version int NOT NULL DEFAULT 0 COMMENT ''WMS 领料申请版本，每次申请递增'' AFTER wms_shipment_status',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 校验
SELECT column_name, column_type, column_default, column_comment
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_pro_work_order'
  AND COLUMN_NAME = 'wms_shipment_version';
