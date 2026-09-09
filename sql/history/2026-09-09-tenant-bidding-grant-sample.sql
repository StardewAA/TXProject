-- ============================================================================
-- 租户开通招投标(bidding)权限：给租户角色批量授 bidding 菜单（幂等可重复执行）
-- 用法：把 <role_ids> 换成目标租户的角色 id（如延河热力 60346 甲方超管/60352 招标管理员）
-- 已执行：2026-09-09 延河热力(2019) 60346+60352 各 23 个 bidding 菜单
-- ============================================================================
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT r.id, m.menu_id, 0, '1', NOW(), '1', NOW(), b'0'
FROM system_role r
CROSS JOIN (SELECT DISTINCT rm.menu_id FROM system_role_menu rm
            JOIN system_menu mm ON mm.id = rm.menu_id
            WHERE rm.role_id = 1 AND rm.deleted = 0
              AND (mm.permission LIKE 'bidding:%'
                   OR mm.id IN (SELECT id FROM system_menu WHERE id IN (SELECT parent_id FROM system_menu WHERE permission LIKE 'bidding:%')))) m
WHERE r.id IN (60346, 60352)
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.deleted = 0);
