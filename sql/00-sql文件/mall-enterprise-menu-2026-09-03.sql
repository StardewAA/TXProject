-- 企业化商城：企业入驻审核菜单与权限
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '企业入驻', '', 2, 1, COALESCE((SELECT id FROM system_menu WHERE (path = 'mall' OR name = '商城系统') AND type = 1 AND deleted = b'0' ORDER BY id LIMIT 1), 2000), 'enterprise', 'ep:office-building', 'mall/product/enterprise/index', 'ProductEnterprise', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'mall/product/enterprise/index' AND deleted = b'0');
