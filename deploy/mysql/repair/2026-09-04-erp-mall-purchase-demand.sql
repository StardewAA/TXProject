-- Mall shortage procurement demand integration for ERP.
-- Run this once against the local/shared txgy database before deploying the ERP service.

CREATE TABLE IF NOT EXISTS `erp_mall_purchase_demand` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `request_id` varchar(128) NOT NULL COMMENT 'Mall idempotency request id',
  `request_hash` varchar(64) NOT NULL COMMENT 'SHA-256 of the accepted request payload',
  `mall_demand_no` varchar(128) NOT NULL COMMENT 'Mall shortage demand number',
  `order_no` varchar(128) DEFAULT NULL COMMENT 'Mall order number',
  `shop_order_no` varchar(128) DEFAULT NULL COMMENT 'Shop order number',
  `enterprise_id` bigint NOT NULL COMMENT 'Enterprise id',
  `shop_id` bigint DEFAULT NULL COMMENT 'Shop id',
  `warehouse_id` bigint NOT NULL COMMENT 'ERP warehouse id',
  `erp_request_no` varchar(64) NOT NULL COMMENT 'ERP shortage demand number',
  `erp_purchase_order_no` varchar(64) DEFAULT NULL COMMENT 'Linked ERP supplier purchase order number',
  `status` varchar(32) NOT NULL COMMENT 'ACCEPTED, REJECTED, DELAYED, PURCHASE_COMPLETED, PURCHASE_FAILED, CANCEL_REQUESTED, CANCELLED, CANCEL_REJECTED',
  `reason_code` varchar(64) DEFAULT NULL COMMENT 'Rejection or exception code',
  `reason_message` varchar(512) DEFAULT NULL COMMENT 'Rejection or exception message',
  `expected_arrival_date` date DEFAULT NULL COMMENT 'Estimated arrival date',
  `required_delivery_date` date DEFAULT NULL COMMENT 'Requested delivery date',
  `contact_requirement` varchar(1024) DEFAULT NULL COMMENT 'Contact requirement',
  `technical_requirement` varchar(2048) DEFAULT NULL COMMENT 'Technical requirement',
  `attachment_urls` varchar(2048) DEFAULT NULL COMMENT 'Attachment URLs',
  `cancel_reason` varchar(512) DEFAULT NULL COMMENT 'Cancellation reason from mall',
  `creator` varchar(64) DEFAULT '' COMMENT 'Creator',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updater` varchar(64) DEFAULT '' COMMENT 'Updater',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Deleted',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'Tenant id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_request_id` (`tenant_id`, `request_id`),
  UNIQUE KEY `uk_tenant_mall_demand_no` (`tenant_id`, `mall_demand_no`),
  UNIQUE KEY `uk_tenant_erp_request_no` (`tenant_id`, `erp_request_no`),
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_tenant_order_no` (`tenant_id`, `order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP mall shortage procurement demand';

CREATE TABLE IF NOT EXISTS `erp_mall_purchase_demand_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `demand_id` bigint NOT NULL COMMENT 'Mall shortage procurement demand id',
  `demand_item_id` varchar(128) NOT NULL COMMENT 'Mall demand item id',
  `sku_id` varchar(128) NOT NULL COMMENT 'Mall SKU id',
  `erp_material_code` varchar(128) NOT NULL COMMENT 'ERP material code from mall mapping',
  `product_name` varchar(255) NOT NULL COMMENT 'Product name snapshot',
  `specification` varchar(512) DEFAULT NULL COMMENT 'Specification snapshot',
  `unit_name` varchar(64) NOT NULL COMMENT 'Unit snapshot',
  `order_quantity` decimal(24,6) NOT NULL COMMENT 'Original order quantity',
  `reserved_quantity` decimal(24,6) NOT NULL COMMENT 'Reserved quantity',
  `shortage_quantity` decimal(24,6) NOT NULL COMMENT 'Shortage quantity',
  `creator` varchar(64) DEFAULT '' COMMENT 'Creator',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updater` varchar(64) DEFAULT '' COMMENT 'Updater',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Deleted',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'Tenant id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_demand_item` (`tenant_id`, `demand_id`, `demand_item_id`),
  KEY `idx_tenant_demand_id` (`tenant_id`, `demand_id`),
  KEY `idx_tenant_sku_id` (`tenant_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP mall shortage procurement demand item';

-- API permissions are global button records. They are intentionally not assigned
-- to any tenant role here; grant them only to the mall integration account.
-- Keep them under ERP -> 采购管理 so they are visible in the role permission tree.
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '商城缺货采购接收', 'erp:mall-purchase-demand:receive', 3, 1,
       (SELECT `id` FROM `system_menu` WHERE `path` = 'purchase' AND `parent_id` =
        (SELECT `id` FROM `system_menu` WHERE `path` = '/erp' AND `parent_id` = 0 AND `deleted` = b'0' LIMIT 1)
        AND `deleted` = b'0' LIMIT 1), '', '#', NULL, NULL,
       0, b'0', b'0', b'0', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'erp:mall-purchase-demand:receive' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '商城缺货采购查询', 'erp:mall-purchase-demand:query', 3, 2,
       (SELECT `id` FROM `system_menu` WHERE `path` = 'purchase' AND `parent_id` =
        (SELECT `id` FROM `system_menu` WHERE `path` = '/erp' AND `parent_id` = 0 AND `deleted` = b'0' LIMIT 1)
        AND `deleted` = b'0' LIMIT 1), '', '#', NULL, NULL,
       0, b'0', b'0', b'0', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'erp:mall-purchase-demand:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '商城缺货采购取消', 'erp:mall-purchase-demand:cancel', 3, 3,
       (SELECT `id` FROM `system_menu` WHERE `path` = 'purchase' AND `parent_id` =
        (SELECT `id` FROM `system_menu` WHERE `path` = '/erp' AND `parent_id` = 0 AND `deleted` = b'0' LIMIT 1)
        AND `deleted` = b'0' LIMIT 1), '', '#', NULL, NULL,
       0, b'0', b'0', b'0', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'erp:mall-purchase-demand:cancel' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '商城缺货采购结果更新', 'erp:mall-purchase-demand:update', 3, 4,
       (SELECT `id` FROM `system_menu` WHERE `path` = 'purchase' AND `parent_id` =
        (SELECT `id` FROM `system_menu` WHERE `path` = '/erp' AND `parent_id` = 0 AND `deleted` = b'0' LIMIT 1)
        AND `deleted` = b'0' LIMIT 1), '', '#', NULL, NULL,
       0, b'0', b'0', b'0', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'erp:mall-purchase-demand:update' AND `deleted` = b'0');

-- Repair names written as question marks by an earlier non-UTF-8 import.
-- Hex literals keep the Chinese names independent of the client code page.
UPDATE `system_menu` SET `name` = CONVERT(0xE59586E59F8EE7BCBAE8B4A7E98787E8B4ADE68EA5E694B6 USING utf8mb4)
WHERE `permission` = 'erp:mall-purchase-demand:receive' AND `deleted` = b'0';
UPDATE `system_menu` SET `name` = CONVERT(0xE59586E59F8EE7BCBAE8B4A7E98787E8B4ADE69FA5E8AFA2 USING utf8mb4)
WHERE `permission` = 'erp:mall-purchase-demand:query' AND `deleted` = b'0';
UPDATE `system_menu` SET `name` = CONVERT(0xE59586E59F8EE7BCBAE8B4A7E98787E8B4ADE58F96E6B688 USING utf8mb4)
WHERE `permission` = 'erp:mall-purchase-demand:cancel' AND `deleted` = b'0';
UPDATE `system_menu` SET `name` = CONVERT(0xE59586E59F8EE7BCBAE8B4A7E98787E8B4ADE7BB93E69E9CE69BB4E696B0 USING utf8mb4)
WHERE `permission` = 'erp:mall-purchase-demand:update' AND `deleted` = b'0';

-- Repair rows created by an earlier version of this script.
-- Read the parent id into a variable first because MySQL rejects updating
-- system_menu while selecting from the same table in the UPDATE statement.
SET @erp_menu_id = (
  SELECT `id` FROM `system_menu`
  WHERE `path` = '/erp' AND `parent_id` = 0 AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);

SET @purchase_menu_id = (
  SELECT `id` FROM `system_menu`
  WHERE `path` = 'purchase' AND `parent_id` = @erp_menu_id AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);

UPDATE `system_menu`
SET `parent_id` = @purchase_menu_id
WHERE `permission` IN (
  'erp:mall-purchase-demand:receive',
  'erp:mall-purchase-demand:query',
  'erp:mall-purchase-demand:cancel',
  'erp:mall-purchase-demand:update'
)
  AND `deleted` = b'0'
  AND @purchase_menu_id IS NOT NULL;

-- Tenant package repair for 西北恒泰新材料有限公司 (tenant 2001).
-- Package 119 is shared by multiple tenants, so clone it before adding the
-- integration permissions. This keeps other tenants on their original menu set.
START TRANSACTION;

-- Tenant 2001 is 西北恒泰新材料有限公司 in the local database.
-- Use the stable id here so PowerShell code-page conversion cannot affect the match.
SET @target_tenant_id = 2001;

SET @target_package_name = CONVERT('erp_mall_purchase_tenant_2001' USING utf8mb4) COLLATE utf8mb4_unicode_ci;

INSERT INTO `system_tenant_package`
  (`name`, `status`, `remark`, `menu_ids`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT @target_package_name, `status`,
       CONCAT(COALESCE(`remark`, ''), '；ERP商城缺货采购接口专用'),
       `menu_ids`, '1', NOW(), '1', NOW(), b'0'
FROM `system_tenant_package`
WHERE `id` = (
  SELECT `package_id` FROM `system_tenant`
  WHERE `id` = @target_tenant_id
)
  AND NOT EXISTS (
    SELECT 1 FROM `system_tenant_package`
    WHERE `name` COLLATE utf8mb4_unicode_ci =
          @target_package_name COLLATE utf8mb4_unicode_ci
      AND `deleted` = b'0'
  );

SET @target_package_id = (
  SELECT `id` FROM `system_tenant_package`
  WHERE `name` COLLATE utf8mb4_unicode_ci =
        @target_package_name COLLATE utf8mb4_unicode_ci
    AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(CAST(`menu_ids` AS JSON), '$', 6735)
WHERE `id` = @target_package_id
  AND JSON_CONTAINS(CAST(`menu_ids` AS JSON), '6735', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(CAST(`menu_ids` AS JSON), '$', 6736)
WHERE `id` = @target_package_id
  AND JSON_CONTAINS(CAST(`menu_ids` AS JSON), '6736', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(CAST(`menu_ids` AS JSON), '$', 6737)
WHERE `id` = @target_package_id
  AND JSON_CONTAINS(CAST(`menu_ids` AS JSON), '6737', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(CAST(`menu_ids` AS JSON), '$', 6738)
WHERE `id` = @target_package_id
  AND JSON_CONTAINS(CAST(`menu_ids` AS JSON), '6738', '$') = 0;

UPDATE `system_tenant`
SET `package_id` = @target_package_id
WHERE `id` = @target_tenant_id
  AND @target_package_id IS NOT NULL
  AND `package_id` <> @target_package_id;

COMMIT;
