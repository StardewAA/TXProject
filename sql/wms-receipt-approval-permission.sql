-- MySQL: add receipt submission permission; inherit roles already allowed to complete receipts.
-- Run against the WMS system database only. No Flowable runtime tables are modified.
START TRANSACTION;
INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, status,
   visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '入库单提交审批', 'wms:receipt-order:submit-approval', 3, 8, source.parent_id,
       '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu source
WHERE source.permission = 'wms:receipt-order:complete' AND source.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu existing
                  WHERE existing.permission = 'wms:receipt-order:submit-approval' AND existing.deleted = b'0')
LIMIT 1;
INSERT INTO system_role_menu
  (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT grants.role_id, target.id, '1', NOW(), '1', NOW(), b'0', grants.tenant_id
FROM system_role_menu grants
JOIN system_menu source ON source.id = grants.menu_id
JOIN system_menu target ON target.permission = 'wms:receipt-order:submit-approval' AND target.deleted = b'0'
WHERE source.permission = 'wms:receipt-order:complete' AND source.deleted = b'0' AND grants.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing WHERE existing.role_id = grants.role_id
                  AND existing.menu_id = target.id AND existing.tenant_id = grants.tenant_id AND existing.deleted = b'0');
COMMIT;
