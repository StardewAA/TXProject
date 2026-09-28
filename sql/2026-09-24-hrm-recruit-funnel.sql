-- 与 deploy/mysql/repair/2026-09-24-hrm-recruit-funnel-menu.sql 内容一致
-- HRM：招聘漏斗看板菜单与权限（测试报告第 3.3 项）

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6915, '招聘漏斗', 'hrm:recruit:funnel:query', 2, 30, 1486, 'funnel',
        'hrm/recruit/funnel/index', 'HrmRecruitFunnel', 0, b'1', b'1', b'1', 'hrm-dev', 'hrm-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id),
                        path = VALUES(path), component = VALUES(component),
                        component_name = VALUES(component_name), deleted = b'0';

INSERT INTO `system_role_menu` (role_id, menu_id, tenant_id, creator, updater)
SELECT r.id, m.id, r.tenant_id, 'hrm-dev', 'hrm-dev'
FROM `system_role` r
JOIN `system_menu` m ON m.id = 6915
WHERE r.tenant_id = 2020 AND r.deleted = 0
  AND (r.id = 60366 OR r.id = 60468)
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM `system_role_menu`
                                 WHERE role_id = r.id AND menu_id = m.id AND tenant_id = r.tenant_id) t);
