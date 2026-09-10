-- ERP 供应链协同单 v0.3：复用协作单 B 模型，扩展协同类型/来源/确认信息，新增确认日志与菜单权限。
-- 幂等可重复执行。

-- ============ 1. erp_wms_collaboration_b 扩展 ============

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b'
       AND COLUMN_NAME = 'collaboration_type') = 0,
    'ALTER TABLE erp_wms_collaboration_b ADD COLUMN collaboration_type varchar(32) NOT NULL DEFAULT ''SHORTAGE_COLLABORATION'' COMMENT ''协同单类型：PURCHASE_COLLABORATION/SALE_COLLABORATION/AWARD_COLLABORATION/SHORTAGE_COLLABORATION'' AFTER business_chain_no',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b'
       AND COLUMN_NAME = 'source_module') = 0,
    'ALTER TABLE erp_wms_collaboration_b ADD COLUMN source_module varchar(32) NULL COMMENT ''来源系统模块'' AFTER collaboration_type',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b'
       AND COLUMN_NAME = 'source_order_no') = 0,
    'ALTER TABLE erp_wms_collaboration_b ADD COLUMN source_order_no varchar(64) NULL COMMENT ''通用来源单号（外部采购单/销售需求单等）'' AFTER source_module',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b'
       AND COLUMN_NAME = 'source_enterprise_code') = 0,
    'ALTER TABLE erp_wms_collaboration_b ADD COLUMN source_enterprise_code varchar(64) NULL COMMENT ''发起方企业编码'' AFTER source_order_no',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b'
       AND COLUMN_NAME = 'target_enterprise_code') = 0,
    'ALTER TABLE erp_wms_collaboration_b ADD COLUMN target_enterprise_code varchar(64) NULL COMMENT ''接收方企业编码'' AFTER source_enterprise_code',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b'
       AND COLUMN_NAME = 'currency') = 0,
    'ALTER TABLE erp_wms_collaboration_b ADD COLUMN currency varchar(16) NULL COMMENT ''币种'' AFTER target_enterprise_code',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b'
       AND COLUMN_NAME = 'confirm_user_id') = 0,
    'ALTER TABLE erp_wms_collaboration_b ADD COLUMN confirm_user_id bigint NULL COMMENT ''业务确认人编号'' AFTER reason',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b'
       AND COLUMN_NAME = 'confirm_user_name') = 0,
    'ALTER TABLE erp_wms_collaboration_b ADD COLUMN confirm_user_name varchar(64) NULL COMMENT ''业务确认人'' AFTER confirm_user_id',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b'
       AND COLUMN_NAME = 'confirm_time') = 0,
    'ALTER TABLE erp_wms_collaboration_b ADD COLUMN confirm_time datetime NULL COMMENT ''业务确认时间'' AFTER confirm_user_name',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ 2. erp_wms_collaboration_b_item 扩展 ============

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b_item'
       AND COLUMN_NAME = 'source_line_no') = 0,
    'ALTER TABLE erp_wms_collaboration_b_item ADD COLUMN source_line_no int NULL COMMENT ''来源明细行号'' AFTER line_no',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b_item'
       AND COLUMN_NAME = 'reference_price') = 0,
    'ALTER TABLE erp_wms_collaboration_b_item ADD COLUMN reference_price decimal(24, 6) NULL COMMENT ''参考价格'' AFTER shortage_quantity',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b_item'
       AND COLUMN_NAME = 'tax_rate') = 0,
    'ALTER TABLE erp_wms_collaboration_b_item ADD COLUMN tax_rate decimal(7, 4) NULL COMMENT ''参考税率'' AFTER reference_price',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_wms_collaboration_b_item'
       AND COLUMN_NAME = 'delivery_address') = 0,
    'ALTER TABLE erp_wms_collaboration_b_item ADD COLUMN delivery_address varchar(255) NULL COMMENT ''收货/交货地址'' AFTER tax_rate',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ 3. 业务确认日志表 ============

CREATE TABLE IF NOT EXISTS `erp_collaboration_confirm_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `collaboration_id` bigint NOT NULL COMMENT '协同单编号（erp_wms_collaboration_b.id）',
    `collaboration_no` varchar(64) NOT NULL COMMENT '协同单号',
    `action` varchar(32) NOT NULL COMMENT '确认动作：ACCEPTED/REJECTED/NEED_SUPPLEMENT',
    `reason` varchar(1024) NULL COMMENT '原因或补充说明',
    `confirm_user_id` bigint NULL COMMENT '确认人编号',
    `confirm_user_name` varchar(64) NULL COMMENT '确认人',
    `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_collab_log_id` (`tenant_id`, `collaboration_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 供应链协同单业务确认日志';

-- ============ 4. 菜单与权限（挂在 ERP 系统根节点 2563 下） ============

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '供应链协同', '', 2, 6, 2563, 'collaboration', 'ant-design:team-outlined',
       'erp/collaboration/index', 'ErpCollaborationOrder', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id = 2563 AND path = 'collaboration' AND deleted = b'0');

SET @collab_menu_id = (
    SELECT id FROM system_menu WHERE parent_id = 2563 AND path = 'collaboration' AND deleted = b'0' LIMIT 1
);

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '协同单查询', 'erp:collaboration-order:query', 3, 1, @collab_menu_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'erp:collaboration-order:query' AND deleted = b'0');

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '协同单接受确认', 'erp:collaboration-order:purchase-confirm', 3, 2, @collab_menu_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'erp:collaboration-order:purchase-confirm' AND deleted = b'0');

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '协同单拒绝', 'erp:collaboration-order:reject', 3, 3, @collab_menu_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'erp:collaboration-order:reject' AND deleted = b'0');

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '协同单要求补充', 'erp:collaboration-order:supplement', 3, 4, @collab_menu_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'erp:collaboration-order:supplement' AND deleted = b'0');
