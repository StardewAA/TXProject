-- 支付请求发送状态。旧数据保持 NULL，表示历史请求是否实际发送无法确认。
SET @has_submit_state = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'pay_order_extension'
    AND COLUMN_NAME = 'submit_state'
);

SET @add_submit_state = IF(
  @has_submit_state = 0,
  'ALTER TABLE pay_order_extension ADD COLUMN submit_state tinyint NULL COMMENT ''请求发送状态：0准备中 1已发送 2结果未知'' AFTER status',
  'SELECT 1'
);

PREPARE stmt FROM @add_submit_state;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
