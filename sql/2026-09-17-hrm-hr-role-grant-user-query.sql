-- ============================================================
-- 修复：HR 角色在「员工档案 → 从后台用户建档」时，读取系统用户列表报 403
-- 原因：该流程调用 /system/user/page，需要 system:user:query 权限
-- 处理：只给 HR 角色补授「用户查询」按钮权限（菜单编号 1001），
--      不授予父菜单「用户管理」(100)，因此侧边栏不会出现系统管理入口
-- 执行库：ruoyi-vue-pro；执行后需清理权限缓存
-- ============================================================

-- 【1】先看现状：哪些 HR 角色缺 system:user:query
SELECT r.id AS role_id, r.name, r.tenant_id,
       SUM(CASE WHEN m.permission = 'system:user:query' THEN 1 ELSE 0 END) AS has_user_query
FROM system_role r
LEFT JOIN system_role_menu rm ON rm.role_id = r.id AND rm.deleted = b'0'
LEFT JOIN system_menu m ON m.id = rm.menu_id AND m.deleted = b'0'
WHERE r.deleted = b'0' AND (r.name = 'HR角色' OR r.code LIKE 'hr_role%')
GROUP BY r.id, r.name, r.tenant_id
ORDER BY r.tenant_id;

-- 【2】补授权限：system:user:query（菜单编号 1001）
INSERT INTO system_role_menu
  (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, 1001, 'admin', NOW(), 'admin', NOW(), b'0', IFNULL(r.tenant_id, 0)
FROM system_role r
WHERE r.deleted = b'0'
  AND (r.name = 'HR角色' OR r.code LIKE 'hr_role%')
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu x
    WHERE x.role_id = r.id AND x.menu_id = 1001 AND x.deleted = b'0'
  );

-- 【3】核对
SELECT r.id AS role_id, r.tenant_id, r.name, COUNT(*) AS granted
FROM system_role r
JOIN system_role_menu rm ON rm.role_id = r.id AND rm.deleted = b'0' AND rm.menu_id = 1001
WHERE r.deleted = b'0' AND (r.name = 'HR角色' OR r.code LIKE 'hr_role%')
GROUP BY r.id, r.tenant_id, r.name
ORDER BY r.tenant_id;

-- 【4】执行后清理权限缓存：
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern 'menu_role_ids:*' | xargs -r redis-cli del"
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern 'permission_menu_ids:*' | xargs -r redis-cli del"
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern 'user_role_ids:*' | xargs -r redis-cli del"
