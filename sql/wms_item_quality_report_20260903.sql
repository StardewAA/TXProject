-- WMS 商品质检报告（MySQL 8.x）
-- 执行前请先备份；本脚本为一次性增量脚本。

SET @quality_column_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_item'
      AND COLUMN_NAME = 'current_quality_report_id'
);
SET @quality_column_sql = IF(
    @quality_column_exists = 0,
    'ALTER TABLE `wms_item` ADD COLUMN `current_quality_report_id` bigint NULL COMMENT ''当前有效质检报告编号'' AFTER `remark`',
    'SELECT 1'
);
PREPARE quality_column_stmt FROM @quality_column_sql;
EXECUTE quality_column_stmt;
DEALLOCATE PREPARE quality_column_stmt;

SET @quality_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_item'
      AND INDEX_NAME = 'idx_current_quality_report_id'
);
SET @quality_index_sql = IF(
    @quality_index_exists = 0,
    'ALTER TABLE `wms_item` ADD INDEX `idx_current_quality_report_id` (`current_quality_report_id`)',
    'SELECT 1'
);
PREPARE quality_index_stmt FROM @quality_index_sql;
EXECUTE quality_index_stmt;
DEALLOCATE PREPARE quality_index_stmt;

CREATE TABLE IF NOT EXISTS `wms_item_quality_report` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
    `item_id` bigint NOT NULL COMMENT '商品编号',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '质检状态：0待质检 1合格 2异常',
    `image_urls` json NULL COMMENT '质检报告图片 URL 数组',
    `remark` varchar(500) NULL COMMENT '异常说明/质检备注',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_item_id` (`item_id`),
    KEY `idx_status` (`status`),
    KEY `idx_tenant_item_id` (`tenant_id`, `item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 商品质检报告';

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '质检报告查询', 'wms:item-quality-report:query', 3, 8, 1367, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'wms:item-quality-report:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '质检报告新增', 'wms:item-quality-report:create', 3, 9, 1367, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'wms:item-quality-report:create' AND `deleted` = b'0');

-- 不同初始化版本的商品管理菜单 ID 不一致，按商品查询权限动态修正父菜单。
SET @item_menu_id = (
    SELECT `parent_id`
    FROM `system_menu`
    WHERE `permission` = 'wms:item:query'
      AND `deleted` = b'0'
    ORDER BY `id` DESC
    LIMIT 1
);
UPDATE `system_menu`
SET `parent_id` = @item_menu_id,
    `update_time` = NOW()
WHERE `permission` IN (
    'wms:item-quality-report:query',
    'wms:item-quality-report:create'
)
  AND `deleted` = b'0'
  AND @item_menu_id IS NOT NULL;

-- 将新权限加入所有已包含商品管理菜单的租户套餐，否则租户侧角色树不可见。
SET @quality_query_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `permission` = 'wms:item-quality-report:query' AND `deleted` = b'0'
    ORDER BY `id` DESC LIMIT 1
);
SET @quality_create_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `permission` = 'wms:item-quality-report:create' AND `deleted` = b'0'
    ORDER BY `id` DESC LIMIT 1
);
UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', @quality_query_menu_id),
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND JSON_CONTAINS(`menu_ids`, CAST(@item_menu_id AS JSON))
  AND NOT JSON_CONTAINS(`menu_ids`, CAST(@quality_query_menu_id AS JSON));
UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', @quality_create_menu_id),
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND JSON_CONTAINS(`menu_ids`, CAST(@item_menu_id AS JSON))
  AND NOT JSON_CONTAINS(`menu_ids`, CAST(@quality_create_menu_id AS JSON));

-- 已拥有商品创建或更新权限的角色，自动获得质检报告查询、新增权限。
INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT source_role.role_id, quality_menu.id, '1', NOW(), '1', NOW(), b'0', source_role.tenant_id
FROM `system_role_menu` source_role
JOIN `system_menu` source_menu ON source_menu.id = source_role.menu_id
JOIN `system_menu` quality_menu ON quality_menu.permission IN (
    'wms:item-quality-report:query',
    'wms:item-quality-report:create'
)
WHERE source_role.deleted = b'0'
  AND source_menu.deleted = b'0'
  AND quality_menu.deleted = b'0'
  AND source_menu.permission IN ('wms:item:create', 'wms:item:update')
  AND NOT EXISTS (
      SELECT 1
      FROM `system_role_menu` existing_role_menu
      WHERE existing_role_menu.role_id = source_role.role_id
        AND existing_role_menu.menu_id = quality_menu.id
        AND existing_role_menu.deleted = b'0'
        AND existing_role_menu.tenant_id = source_role.tenant_id
  );
