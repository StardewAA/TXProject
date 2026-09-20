-- ============================================================
-- HRM P1：新增「用工主体信息」菜单，并授权给已有「员工管理」权限的角色
-- 执行库：ruoyi-vue-pro
-- 执行后需清理权限缓存（见文件末尾说明）
-- ============================================================

-- 【1】菜单：挂在「员工管理」（1477）下
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '用工主体信息', '', 2, 3, 1477, 'company', 'ep:office-building', 'hrm/company/index', 'HrmCompanyProfile',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `name` = '用工主体信息' AND `parent_id` = 1477 AND `deleted` = b'0'
);

SET @companyMenuId := (
  SELECT `id` FROM `system_menu`
  WHERE `name` = '用工主体信息' AND `parent_id` = 1477 AND `deleted` = b'0' LIMIT 1
);

-- 【2】授权：所有已拥有「员工管理」菜单的角色
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.role_id, @companyMenuId, 'admin', NOW(), 'admin', NOW(), b'0', r.tenant_id
FROM (
  SELECT DISTINCT rm.`role_id` AS role_id, IFNULL(rl.`tenant_id`, 0) AS tenant_id
  FROM `system_role_menu` rm
  JOIN `system_role` rl ON rl.`id` = rm.`role_id`
  WHERE rm.`menu_id` = 1477 AND rm.`deleted` = b'0'
) r
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` x
  WHERE x.`role_id` = r.role_id AND x.`menu_id` = @companyMenuId AND x.`deleted` = b'0'
);

-- 【3】核对
SELECT m.`id`, m.`name`, m.`path`, m.`component`, COUNT(rm.`role_id`) AS role_count
FROM `system_menu` m
LEFT JOIN `system_role_menu` rm ON rm.`menu_id` = m.`id` AND rm.`deleted` = b'0'
WHERE m.`name` = '用工主体信息' AND m.`deleted` = b'0'
GROUP BY m.`id`, m.`name`, m.`path`, m.`component`;

-- 【4】执行后必须清理权限缓存（否则菜单不生效）：
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern 'menu_role_ids:*' | xargs -r redis-cli del"
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern 'permission_menu_ids:*' | xargs -r redis-cli del"
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern 'user_role_ids:*' | xargs -r redis-cli del"
