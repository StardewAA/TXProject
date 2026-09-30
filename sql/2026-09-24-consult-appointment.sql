-- 专家咨询增加预约时间；已有订单用创建时间回填，保证升级可重复执行。
SET @has_appointment_time = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'consult_order'
    AND COLUMN_NAME = 'appointment_time'
);
SET @add_appointment_time = IF(
  @has_appointment_time = 0,
  'ALTER TABLE consult_order ADD COLUMN appointment_time datetime NULL COMMENT ''预约咨询时间'' AFTER question',
  'SELECT 1'
);
PREPARE stmt FROM @add_appointment_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE consult_order
SET appointment_time = COALESCE(appointment_time, create_time, CURRENT_TIMESTAMP)
WHERE appointment_time IS NULL;

ALTER TABLE consult_order
  MODIFY COLUMN appointment_time datetime NOT NULL COMMENT '预约咨询时间';

SET @has_appointment_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'consult_order'
    AND INDEX_NAME = 'idx_consult_appointment'
);
SET @add_appointment_index = IF(
  @has_appointment_index = 0,
  'ALTER TABLE consult_order ADD KEY idx_consult_appointment (tenant_id,status,appointment_time)',
  'SELECT 1'
);
PREPARE stmt FROM @add_appointment_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
