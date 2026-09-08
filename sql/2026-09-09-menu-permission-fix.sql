-- ============================================================================
-- 菜单权限修复 2026-09-09：错位纠正 + 去重 + 补 filing 备案权限入库
-- ============================================================================
-- 1) 错位按钮归位：6745(爬取任务查询)→6739(采集任务页)；6747(批次查询)→6740(采集批次页)
UPDATE system_menu SET parent_id = 6739 WHERE id = 6745;
UPDATE system_menu SET parent_id = 6740 WHERE id = 6747;
-- 2) 去重：6746 与 6744 同权限(采集任务重试) → 删 6746
DELETE FROM system_menu WHERE id = 6746 AND permission = 'bidding:crawl-task:update';
-- 3) 补 filing 备案权限：挂 6748 投标流程跟踪页下（复用前端页面，不新增）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(6764, '备案提交', 'bidding:filing:create', 3, 5, 6748, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(6765, '备案结果回推', 'bidding:filing:update', 3, 6, 6748, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
-- 4) 角色绑定：新菜单绑到 6748 已绑的角色（super_admin=1/common=2，tenant_id=0 平台级）
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT rm.role_id, m.id, 0, '1', NOW(), '1', NOW(), b'0'
FROM system_menu m CROSS JOIN (SELECT DISTINCT role_id FROM system_role_menu WHERE menu_id = 6748 AND deleted = 0) rm
WHERE m.id IN (6764, 6765);
