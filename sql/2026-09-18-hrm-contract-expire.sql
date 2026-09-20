-- ============================================================
-- HRM 迭代 1.5：劳动合同到期预警
-- 1) 新增站内信模板 hrm_contract_expire_warning
-- 2) 合同管理下挂「到期预警」菜单与权限（授权租户 2020 超管与 HR 角色）
-- 幂等：可重复执行
-- ============================================================

INSERT INTO `system_notify_template` (id, name, code, nickname, content, type, params, status, remark,
                                      creator, updater)
VALUES (6845, '劳动合同到期预警通知', 'hrm_contract_expire_warning', '合同到期预警',
        '您的劳动合同（编号 {contractNo}）将于 {endTime} 到期，剩余 {remainDays} 天，请及时联系 HR 办理续签。',
        1, '["contractNo","endTime","remainDays"]', 0, 'HRM 合同到期预警 Job 使用', 'expire-dev', 'expire-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), content = VALUES(content), params = VALUES(params),
                        status = VALUES(status), deleted = b'0';

-- ============ 菜单与权限（挂在「合同管理」下） ============
INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6840, '到期预警', 'hrm:contract:expire:query', 2, 3, 6750, 'expire', 'lucide:alarm-clock',
        'hrm/contract/expire/index', 'HrmContractExpire', 0, b'1', b'1', b'1', 'expire-dev', 'expire-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id),
                        path = VALUES(path), component = VALUES(component), component_name = VALUES(component_name),
                        sort = VALUES(sort), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6841, '到期预警查询', 'hrm:contract:expire:query', 3, 1, 6840, '', '', '', 0, b'1', b'1', b'1', 'expire-dev', 'expire-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_role_menu` (role_id, menu_id, tenant_id, creator, updater)
SELECT r.id, m.id, r.tenant_id, 'expire-dev', 'expire-dev'
FROM `system_role` r
JOIN `system_menu` m ON m.id IN (6840, 6841)
WHERE r.tenant_id = 2020 AND r.deleted = 0
  AND (r.id = 60366 OR r.id = 60468)
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM `system_role_menu`
                                 WHERE role_id = r.id AND menu_id = m.id AND tenant_id = r.tenant_id) t);
