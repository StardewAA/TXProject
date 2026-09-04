-- ERP 审批管理：订单接入现有 BPM/Flowable，多级审批节点由流程设计器动态配置。

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_purchase_order'
       AND COLUMN_NAME = 'process_instance_id') = 0,
    'ALTER TABLE erp_purchase_order ADD COLUMN process_instance_id VARCHAR(64) NULL COMMENT ''BPM 流程实例编号'' AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'erp_sale_order'
       AND COLUMN_NAME = 'process_instance_id') = 0,
    'ALTER TABLE erp_sale_order ADD COLUMN process_instance_id VARCHAR(64) NULL COMMENT ''BPM 流程实例编号'' AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE system_dict_data
SET label = '审批中', color_type = 'processing', update_time = NOW()
WHERE dict_type = 'erp_audit_status' AND value = '10' AND deleted = b'0';
UPDATE system_dict_data
SET label = '审批通过', color_type = 'success', update_time = NOW()
WHERE dict_type = 'erp_audit_status' AND value = '20' AND deleted = b'0';

INSERT INTO system_dict_data
    (sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 0, '草稿', '0', 'erp_audit_status', 0, 'default', '', '', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'erp_audit_status' AND value = '0' AND deleted = b'0');
INSERT INTO system_dict_data
    (sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 30, '审批驳回', '30', 'erp_audit_status', 0, 'error', '', '', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'erp_audit_status' AND value = '30' AND deleted = b'0');
INSERT INTO system_dict_data
    (sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 40, '已撤销', '40', 'erp_audit_status', 0, 'warning', '', '', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'erp_audit_status' AND value = '40' AND deleted = b'0');

-- 审批管理放在 ERP 首页与采购管理之间。
INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '审批管理', '', 2, 5, 2563, 'approval', 'ant-design:audit-outlined',
       'erp/approval/index', 'ErpApprovalManagement', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id = 2563 AND path = 'approval' AND deleted = b'0');

SET @approval_menu_id = (
    SELECT id FROM system_menu WHERE parent_id = 2563 AND path = 'approval' AND deleted = b'0' LIMIT 1
);

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '审批管理查询', 'erp:approval:query', 3, 1, @approval_menu_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'erp:approval:query' AND deleted = b'0');

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '提交订单审批', 'erp:approval:submit', 3, 2, @approval_menu_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'erp:approval:submit' AND deleted = b'0');

-- 已拥有 ERP 根菜单的角色自动获得审批管理菜单和按钮权限。
-- 同时授予 BPM 流程实例、任务查询权限，供 ERP 页面展示完整审批轨迹。
INSERT INTO system_role_menu
    (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT source.role_id, target.id, '1', NOW(), '1', NOW(), b'0', source.tenant_id
FROM system_role_menu source
JOIN system_menu target ON target.id IN (
    @approval_menu_id,
    (SELECT id FROM system_menu WHERE permission = 'erp:approval:query' AND deleted = b'0' LIMIT 1),
    (SELECT id FROM system_menu WHERE permission = 'erp:approval:submit' AND deleted = b'0' LIMIT 1),
    (SELECT id FROM system_menu WHERE permission = 'bpm:process-instance:query' AND deleted = b'0' LIMIT 1),
    (SELECT id FROM system_menu WHERE permission = 'bpm:task:query' AND deleted = b'0' LIMIT 1)
)
WHERE source.menu_id = 2563 AND source.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id = source.role_id AND existing.menu_id = target.id
        AND existing.tenant_id = source.tenant_id AND existing.deleted = b'0'
  );
