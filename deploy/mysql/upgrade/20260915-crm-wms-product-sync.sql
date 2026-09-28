-- =====================================================================
-- CRM 产品管理对接 WMS（进仓产品）同步导入
-- 日期：2026-09-15
-- 背景：WMS 已交付只读接口 GET /admin-api/wms/crm/products/page
--       （返回商品 itemId/编码/名称、SKU skuId/编码/名称、单位），
--       CRM 通过定时任务或手动按钮拉取后导入 CRM 产品管理，只读取 WMS 数据，
--       不新增、修改、冻结、扣减或调整 WMS 库存。
-- 内容：
--   1. crm_product 增加 WMS 外部标识（wms_item_id / wms_sku_id / wms_sku_code）与数据来源 data_source
--   2. 放宽 crm_product.no 长度（WMS 规格编码可能超过 20 位）
--   3. 新增「从 WMS 同步」按钮权限 crm:product:sync，并授予已拥有 crm:product:query 的角色
--   4. 新增产品数据来源字典 crm_product_data_source
--   5. 补充 CRM 产品单位字典：卷、包、张、根、支、捆、桶、组、平方米、立方米
-- 说明：幂等，可重复执行。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. crm_product 增加 WMS 同步字段
-- ---------------------------------------------------------------------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_product' AND COLUMN_NAME = 'wms_item_id') = 0,
  'ALTER TABLE crm_product ADD COLUMN wms_item_id BIGINT NULL COMMENT ''WMS 商品编号（外部标识）'' AFTER owner_user_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_product' AND COLUMN_NAME = 'wms_sku_id') = 0,
  'ALTER TABLE crm_product ADD COLUMN wms_sku_id BIGINT NULL COMMENT ''WMS 规格编号（同步幂等键）'' AFTER wms_item_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_product' AND COLUMN_NAME = 'wms_sku_code') = 0,
  'ALTER TABLE crm_product ADD COLUMN wms_sku_code VARCHAR(64) NULL COMMENT ''WMS 规格编码'' AFTER wms_sku_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_product' AND COLUMN_NAME = 'data_source') = 0,
  'ALTER TABLE crm_product ADD COLUMN data_source TINYINT NOT NULL DEFAULT 1 COMMENT ''数据来源：1 手工创建，2 WMS 同步'' AFTER wms_sku_code',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 放宽产品编码长度，容纳 WMS 规格编码
-- ---------------------------------------------------------------------
SET @ddl = IF((SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_product' AND COLUMN_NAME = 'no') < 64,
  'ALTER TABLE crm_product MODIFY COLUMN no VARCHAR(64) NOT NULL COMMENT ''产品编码''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 同步幂等查询索引
-- ---------------------------------------------------------------------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_product' AND INDEX_NAME = 'idx_wms_sku_id') = 0,
  'ALTER TABLE crm_product ADD INDEX idx_wms_sku_id (tenant_id, wms_sku_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 字典：产品数据来源
-- ---------------------------------------------------------------------
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'CRM 产品数据来源', 'crm_product_data_source', 0, 'CRM 产品-数据来源', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_product_data_source' AND `deleted` = b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '手工创建', '1', 'crm_product_data_source', 0, 'default', '', 'CRM 手工创建的产品', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_product_data_source' AND `label` = '手工创建' AND `deleted` = b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, 'WMS 同步', '2', 'crm_product_data_source', 0, 'primary', '', '由 WMS 进仓产品同步导入', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_product_data_source' AND `label` = 'WMS 同步' AND `deleted` = b'0');

-- ---------------------------------------------------------------------
-- 5. 补充 CRM 产品单位字典（WMS 商品单位常见取值）
-- ---------------------------------------------------------------------
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 20, t.label, t.value, 'crm_product_unit', 0, '', '', 'WMS 商品单位', '1', NOW(), '1', NOW(), b'0'
FROM (
    SELECT '卷' AS label, '20' AS value UNION ALL
    SELECT '包', '21' UNION ALL
    SELECT '张', '22' UNION ALL
    SELECT '根', '23' UNION ALL
    SELECT '支', '24' UNION ALL
    SELECT '捆', '25' UNION ALL
    SELECT '桶', '26' UNION ALL
    SELECT '组', '27' UNION ALL
    SELECT '平方米', '28' UNION ALL
    SELECT '立方米', '29'
) t
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` d
                  WHERE d.`dict_type` = 'crm_product_unit' AND d.`label` = t.label AND d.`deleted` = b'0');

-- ---------------------------------------------------------------------
-- 6. 菜单：CRM 产品管理新增「从 WMS 同步」按钮
-- ---------------------------------------------------------------------
SET @productMenuId = (SELECT `id` FROM `system_menu`
    WHERE `permission` = 'crm:product:query' AND `type` = 2 AND `deleted` = b'0' ORDER BY `id` LIMIT 1);
SET @productMenuId = IFNULL(@productMenuId,
    (SELECT `parent_id` FROM `system_menu` WHERE `permission` = 'crm:product:create' AND `deleted` = b'0' ORDER BY `id` LIMIT 1));
SET @productMenuId = IFNULL(@productMenuId, 0);

INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '从 WMS 同步', 'crm:product:sync', 3, 5, @productMenuId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'crm:product:sync' AND `deleted` = b'0');

-- 授予已拥有「产品查询」权限的角色，避免同步按钮不可见
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT t.role_id, t.menu_id, '1', NOW(), '1', NOW(), b'0', t.tenant_id
FROM (
    SELECT DISTINCT query_rm.role_id AS role_id, sync_menu.id AS menu_id, query_rm.tenant_id AS tenant_id
    FROM `system_role_menu` query_rm
             JOIN `system_menu` query_menu
                  ON query_menu.id = query_rm.menu_id AND query_menu.deleted = b'0'
                      AND query_menu.permission = 'crm:product:query'
             JOIN `system_menu` sync_menu
                  ON sync_menu.permission = 'crm:product:sync' AND sync_menu.deleted = b'0'
    WHERE query_rm.deleted = b'0'
) t
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` exists_rm
                  WHERE exists_rm.role_id = t.role_id AND exists_rm.menu_id = t.menu_id
                    AND exists_rm.tenant_id = t.tenant_id AND exists_rm.deleted = b'0');
