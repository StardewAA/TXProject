-- WMS 审批管理：复用 BPM 数据与页面，只展示入库、出库审批流程。
SET NAMES utf8mb4;
SET @wms_parent = (SELECT id FROM system_menu WHERE path = '/wms' AND deleted = 0 LIMIT 1);

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, updater)
SELECT '审批管理', '', 1, 60, @wms_parent, 'approval', 'lucide:clipboard-check', '', NULL,
       0, b'1', b'1', b'1', 'wms-approval', 'wms-approval'
WHERE @wms_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id = @wms_parent AND path = 'approval' AND deleted = 0);

SET @approval_parent = (SELECT id FROM system_menu
                        WHERE parent_id = @wms_parent AND path = 'approval' AND deleted = 0 LIMIT 1);
UPDATE system_menu SET name = '审批管理', updater = 'wms-approval'
WHERE id = @approval_parent;

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, updater)
SELECT '我的审批', 'bpm:process-instance:query', 2, 1, @approval_parent, 'my',
       'lucide:file-user', 'wms/approval/my/index', 'WmsApprovalMy',
       0, b'1', b'1', b'1', 'wms-approval', 'wms-approval'
WHERE @approval_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'wms/approval/my/index' AND deleted = 0);

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, updater)
SELECT '待办审批', 'bpm:task:query', 2, 2, @approval_parent, 'todo',
       'lucide:list-todo', 'wms/approval/todo/index', 'WmsApprovalTodo',
       0, b'1', b'1', b'1', 'wms-approval', 'wms-approval'
WHERE @approval_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'wms/approval/todo/index' AND deleted = 0);

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, updater)
SELECT '已办审批', 'bpm:task:query', 2, 3, @approval_parent, 'done',
       'lucide:badge-check', 'wms/approval/done/index', 'WmsApprovalDone',
       0, b'1', b'1', b'1', 'wms-approval', 'wms-approval'
WHERE @approval_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'wms/approval/done/index' AND deleted = 0);

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, updater)
SELECT '抄送我的', 'bpm:process-instance:query', 2, 4, @approval_parent, 'copy',
       'lucide:copy-check', 'wms/approval/copy/index', 'WmsApprovalCopy',
       0, b'1', b'1', b'1', 'wms-approval', 'wms-approval'
WHERE @approval_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'wms/approval/copy/index' AND deleted = 0);

UPDATE system_menu SET name = '我的审批', updater = 'wms-approval'
WHERE component = 'wms/approval/my/index' AND deleted = 0;
UPDATE system_menu SET name = '待办审批', updater = 'wms-approval'
WHERE component = 'wms/approval/todo/index' AND deleted = 0;
UPDATE system_menu SET name = '已办审批', updater = 'wms-approval'
WHERE component = 'wms/approval/done/index' AND deleted = 0;
UPDATE system_menu SET name = '抄送我的', updater = 'wms-approval'
WHERE component = 'wms/approval/copy/index' AND deleted = 0;

-- 已有 WMS 使用角色自动获得新入口，保留原授权不变。
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, updater)
SELECT DISTINCT existing.role_id, added.id, existing.tenant_id, 'wms-approval', 'wms-approval'
FROM system_role_menu existing
JOIN system_menu added ON added.id = @approval_parent OR added.parent_id = @approval_parent
WHERE existing.menu_id = @wms_parent AND existing.deleted = 0 AND added.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu current_grant
      WHERE current_grant.role_id = existing.role_id
        AND current_grant.menu_id = added.id
        AND current_grant.tenant_id = existing.tenant_id
        AND current_grant.deleted = 0
  );

-- 当前单租户验收套餐同步增加菜单，后续租户可由套餐管理正常授权。
SET @approval_my = (SELECT id FROM system_menu WHERE component = 'wms/approval/my/index' AND deleted = 0 LIMIT 1);
SET @approval_todo = (SELECT id FROM system_menu WHERE component = 'wms/approval/todo/index' AND deleted = 0 LIMIT 1);
SET @approval_done = (SELECT id FROM system_menu WHERE component = 'wms/approval/done/index' AND deleted = 0 LIMIT 1);
SET @approval_copy = (SELECT id FROM system_menu WHERE component = 'wms/approval/copy/index' AND deleted = 0 LIMIT 1);

UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', CAST(@approval_parent AS UNSIGNED))
WHERE id = 120 AND deleted = 0 AND @approval_parent IS NOT NULL
  AND NOT JSON_CONTAINS(menu_ids, CAST(@approval_parent AS JSON));
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', CAST(@approval_my AS UNSIGNED))
WHERE id = 120 AND deleted = 0 AND @approval_my IS NOT NULL
  AND NOT JSON_CONTAINS(menu_ids, CAST(@approval_my AS JSON));
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', CAST(@approval_todo AS UNSIGNED))
WHERE id = 120 AND deleted = 0 AND @approval_todo IS NOT NULL
  AND NOT JSON_CONTAINS(menu_ids, CAST(@approval_todo AS JSON));
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', CAST(@approval_done AS UNSIGNED))
WHERE id = 120 AND deleted = 0 AND @approval_done IS NOT NULL
  AND NOT JSON_CONTAINS(menu_ids, CAST(@approval_done AS JSON));
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', CAST(@approval_copy AS UNSIGNED))
WHERE id = 120 AND deleted = 0 AND @approval_copy IS NOT NULL
  AND NOT JSON_CONTAINS(menu_ids, CAST(@approval_copy AS JSON));
