-- 个人用户可以咨询企业租户下的专家。
-- tenant_id 继续表示专家所属租户，customer_tenant_id 记录咨询人所属租户，
-- 这样专家管理、结算和个人订单都能按各自租户隔离。
SET @has_customer_tenant_id = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'consult_order'
    AND COLUMN_NAME = 'customer_tenant_id'
);
SET @add_customer_tenant_id = IF(
  @has_customer_tenant_id = 0,
  'ALTER TABLE consult_order ADD COLUMN customer_tenant_id bigint NULL COMMENT ''咨询人所属租户'' AFTER tenant_id',
  'SELECT 1'
);
PREPARE stmt FROM @add_customer_tenant_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE consult_order
SET customer_tenant_id = tenant_id
WHERE customer_tenant_id IS NULL;

ALTER TABLE consult_order
  MODIFY COLUMN tenant_id bigint NOT NULL COMMENT '专家所属租户',
  MODIFY COLUMN customer_tenant_id bigint NOT NULL COMMENT '咨询人所属租户';

SET @has_customer_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'consult_order'
    AND INDEX_NAME = 'idx_consult_customer'
);
SET @add_customer_index = IF(
  @has_customer_index = 0,
  'ALTER TABLE consult_order ADD KEY idx_consult_customer (customer_tenant_id,user_id,status)',
  'SELECT 1'
);
PREPARE stmt FROM @add_customer_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
