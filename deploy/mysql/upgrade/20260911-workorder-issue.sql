-- =====================================================================
-- 工单下发功能：mes_pro_work_order 新增下发人/下发时间字段
-- 依据：《MES工单状态机公示文档 v2.2》决策①
-- 幂等：可重复执行（information_schema 判断 + PREPARE）
-- =====================================================================

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mes_pro_work_order'
      AND COLUMN_NAME = 'issue_by') = 0,
  'ALTER TABLE mes_pro_work_order ADD COLUMN issue_by BIGINT NULL COMMENT ''下发人'' AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mes_pro_work_order'
      AND COLUMN_NAME = 'issue_time') = 0,
  'ALTER TABLE mes_pro_work_order ADD COLUMN issue_time DATETIME NULL COMMENT ''下发时间'' AFTER issue_by',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
