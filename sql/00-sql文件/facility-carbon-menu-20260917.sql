-- =============================================================================
-- 治污设施 + 换炭作业票：菜单、按钮、角色绑定、租户套餐
-- 日期：2026-09-17
-- 说明：复用已清空的 34001-34299 段中的 34303/34304（菜单）与 34321-34328（按钮）。
-- ⚠️ 两点务必注意：
--    1) system_menu 无 tenant_id（菜单是全局的），租户间靠角色绑定区分可见性；
--    2) 租户套餐 system_tenant_package.menu_ids 会过滤 assign-role-menu 接口分配的菜单，
--       新菜单 id 必须同时追加进套餐 119，否则日后走接口分配时会被砍掉。
-- 改完需重新登录（前端菜单树是登录时拉的）。
-- =============================================================================

DELETE FROM `system_menu` WHERE id BETWEEN 34303 AND 34304 OR id BETWEEN 34321 AND 34328;
DELETE FROM `system_role_menu` WHERE menu_id BETWEEN 34303 AND 34304 OR menu_id BETWEEN 34321 AND 34328;

INSERT INTO `system_menu`
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    -- 菜单（type=2 挂在 34000「安全环保检测」下）
    (34303, '治污设施', '', 2, 200, 34000, 'facility', '', 'mes/safetyEnv/facility/index', 'MesSetFacility',
     0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'),
    (34304, '换炭作业票', '', 2, 201, 34000, 'carbonReplace', '', 'mes/safetyEnv/carbonReplace/index', 'MesSetCarbonReplace',
     0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'),
    -- 治污设施按钮
    (34321, '治污设施查询', 'mes:set-facility:query', 3, 1, 34303, '', '', '', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'),
    (34322, '治污设施创建', 'mes:set-facility:create', 3, 2, 34303, '', '', '', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'),
    (34323, '治污设施更新', 'mes:set-facility:update', 3, 3, 34303, '', '', '', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'),
    (34324, '治污设施删除', 'mes:set-facility:delete', 3, 4, 34303, '', '', '', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'),
    -- 换炭作业票按钮
    (34325, '换炭票查询', 'mes:set-carbon-replace:query', 3, 1, 34304, '', '', '', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'),
    (34326, '换炭票创建', 'mes:set-carbon-replace:create', 3, 2, 34304, '', '', '', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'),
    (34327, '换炭票更新', 'mes:set-carbon-replace:update', 3, 3, 34304, '', '', '', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'),
    (34328, '换炭票删除', 'mes:set-carbon-replace:delete', 3, 4, 34304, '', '', '', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0');

-- 角色绑定：企业超级管理员(60185) + MES 环保专员(租户1 的 61001、租户2010 的 61011)
INSERT INTO `system_role_menu` (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.role_id, m.menu_id, '1', NOW(), '1', NOW(), b'0', r.tenant_id
FROM (SELECT 60185 AS role_id, 2010 AS tenant_id
      UNION ALL SELECT 61001, 1
      UNION ALL SELECT 61011, 2010) r
         CROSS JOIN (SELECT 34303 AS menu_id
                     UNION ALL SELECT 34304
                     UNION ALL SELECT 34321 UNION ALL SELECT 34322
                     UNION ALL SELECT 34323 UNION ALL SELECT 34324
                     UNION ALL SELECT 34325 UNION ALL SELECT 34326
                     UNION ALL SELECT 34327 UNION ALL SELECT 34328) m;

-- 租户套餐 119：追加新菜单 id
-- menu_ids 是 text 存 JSON 数组；本机 MySQL 的 JSON_ARRAY_APPEND 不支持多值写法，
-- 故用字符串追加。以原来的尾元素 "34315]" 作为锚点，追加后锚点不再出现，天然幂等。
UPDATE `system_tenant_package`
SET menu_ids = REPLACE(menu_ids, '34315]',
        '34315, 34303, 34304, 34321, 34322, 34323, 34324, 34325, 34326, 34327, 34328]')
WHERE id = 119
  AND menu_ids LIKE '%34315]%';
