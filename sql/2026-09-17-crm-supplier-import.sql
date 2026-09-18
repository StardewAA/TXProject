-- ============================================================================
-- CRM 供应商 Word 导入：客户新增「联系人」，报价单新增「供应商联系人/电话/地址」
-- 日期：2026-09-17
--
-- 背景：
--   1. 供应商复用 crm_customer（客户类别 customer_type=2 上游供应商）；
--      Word 导入需要保存「联系人」，而 crm_customer 原本没有该字段，故新增 contact_name。
--   2. 报价单选中供应商后需要回填「联系人 / 联系电话 / 地址」，故 crm_quotation 新增三列。
--
-- 说明：本脚本幂等，可重复执行。
--
-- 执行方式（务必带 utf8mb4，否则中文会乱码）：
--   docker cp sql/2026-09-17-crm-supplier-import.sql <mysql容器>:/tmp/
--   docker exec <mysql容器> sh -c "mysql --default-character-set=utf8mb4 -uroot -p'xxx' ruoyi-vue-pro < /tmp/2026-09-17-crm-supplier-import.sql"
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. crm_customer 新增 contact_name（联系人）
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'contact_name');
SET @ddl1 := IF(@c1 = 0,
    'ALTER TABLE `crm_customer` ADD COLUMN `contact_name` varchar(64) NULL COMMENT ''联系人'' AFTER `name`',
    'DO 0');
PREPARE s1 FROM @ddl1; EXECUTE s1; DEALLOCATE PREPARE s1;

-- ---------------------------------------------------------------------------
-- 2. crm_quotation 新增 supplier_contact / supplier_phone / supplier_address
-- ---------------------------------------------------------------------------
SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_quotation' AND COLUMN_NAME = 'supplier_contact');
SET @ddl2 := IF(@c2 = 0,
    'ALTER TABLE `crm_quotation` ADD COLUMN `supplier_contact` varchar(64) NULL COMMENT ''供应商联系人'' AFTER `customer_id`',
    'DO 0');
PREPARE s2 FROM @ddl2; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_quotation' AND COLUMN_NAME = 'supplier_phone');
SET @ddl3 := IF(@c3 = 0,
    'ALTER TABLE `crm_quotation` ADD COLUMN `supplier_phone` varchar(64) NULL COMMENT ''供应商联系电话'' AFTER `supplier_contact`',
    'DO 0');
PREPARE s3 FROM @ddl3; EXECUTE s3; DEALLOCATE PREPARE s3;

SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_quotation' AND COLUMN_NAME = 'supplier_address');
SET @ddl4 := IF(@c4 = 0,
    'ALTER TABLE `crm_quotation` ADD COLUMN `supplier_address` varchar(255) NULL COMMENT ''供应商地址'' AFTER `supplier_phone`',
    'DO 0');
PREPARE s4 FROM @ddl4; EXECUTE s4; DEALLOCATE PREPARE s4;
