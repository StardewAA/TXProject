-- 第 22.5 节：跨租户询价定向邀请范围。
-- 只新增表结构/索引，不删除或改写既有邀请数据；可重复执行。

CREATE TABLE IF NOT EXISTS product_purchase_inquiry_invited_scope (
  id bigint NOT NULL AUTO_INCREMENT,
  inquiry_id bigint NOT NULL,
  target_type varchar(16) NOT NULL COMMENT 'TENANT/ENTERPRISE/SHOP',
  target_tenant_id bigint NOT NULL,
  target_enterprise_id bigint NULL,
  target_shop_id bigint NULL,
  target_enterprise_key bigint GENERATED ALWAYS AS (IFNULL(target_enterprise_id,0)) STORED,
  target_shop_key bigint GENERATED ALWAYS AS (IFNULL(target_shop_id,0)) STORED,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inquiry_scope_normalized
    (inquiry_id,target_type,target_tenant_id,target_enterprise_key,target_shop_key),
  KEY idx_scope_target (target_tenant_id,target_enterprise_id,target_shop_id,inquiry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨租户询价定向邀请范围';

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_purchase_inquiry_invited_scope'
    AND COLUMN_NAME='target_enterprise_key')=0,
  'ALTER TABLE product_purchase_inquiry_invited_scope ADD COLUMN target_enterprise_key BIGINT GENERATED ALWAYS AS (IFNULL(target_enterprise_id,0)) STORED AFTER target_shop_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_purchase_inquiry_invited_scope'
    AND COLUMN_NAME='target_shop_key')=0,
  'ALTER TABLE product_purchase_inquiry_invited_scope ADD COLUMN target_shop_key BIGINT GENERATED ALWAYS AS (IFNULL(target_shop_id,0)) STORED AFTER target_enterprise_key',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 若历史数据中存在相同 scope 的重复行，本语句会失败并中止部署，避免静默删改历史数据。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_purchase_inquiry_invited_scope'
    AND INDEX_NAME='uk_inquiry_scope_normalized')=0,
  'ALTER TABLE product_purchase_inquiry_invited_scope ADD UNIQUE KEY uk_inquiry_scope_normalized (inquiry_id,target_type,target_tenant_id,target_enterprise_key,target_shop_key)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS invalid_active_scope_count
FROM product_purchase_inquiry_invited_scope s
LEFT JOIN product_purchase_inquiry i ON i.id=s.inquiry_id AND i.deleted=b'0'
WHERE s.deleted=b'0' AND (
  i.id IS NULL OR s.target_tenant_id=3000 OR s.target_tenant_id=i.tenant_id
  OR s.target_type NOT IN ('TENANT','ENTERPRISE','SHOP')
  OR (s.target_type='TENANT' AND (s.target_enterprise_id IS NOT NULL OR s.target_shop_id IS NOT NULL))
  OR (s.target_type='ENTERPRISE' AND (s.target_enterprise_id IS NULL OR s.target_shop_id IS NOT NULL))
  OR (s.target_type='SHOP' AND (s.target_enterprise_id IS NULL OR s.target_shop_id IS NULL))
);
