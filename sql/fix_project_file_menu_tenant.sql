-- =====================================================================
-- 修复：项目附件按钮菜单未同步至租户套餐与角色租户副本
-- 现象：租户下「项目附件」上传按钮不显示，接口返回 403「没有该操作权限」
-- 原因：附件按钮菜单（按权限码 mes:pro-project-file:* 定位）新增时，
--       只插入了 tenant_id=0 的系统记录，漏插各租户的 role_menu 副本，
--       且租户套餐 menu_ids 未包含这些菜单，
--       导致租户上下文按 tenant_id 隔离查询时权限码缺失。
-- 执行后：需清理 Redis 权限缓存（menu_role_ids:* / permission_menu_ids:*）
--        或重启 system 服务后重新登录。
-- 幂等：可重复执行（NOT EXISTS 防重）。
-- =====================================================================

-- 1) 租户套餐补入附件按钮菜单（仅处理已包含"成员查询"菜单的套餐，
--    即已开通项目管理模块的套餐）
UPDATE system_tenant_package p
JOIN (
  SELECT GROUP_CONCAT(id) AS file_ids
  FROM system_menu
  WHERE permission LIKE 'mes:pro-project-file:%' AND deleted = 0
) f
SET p.menu_ids = CONCAT(
  IF(RIGHT(p.menu_ids, 1) = ',', p.menu_ids, CONCAT(p.menu_ids, ',')),
  f.file_ids
)
WHERE p.deleted = 0
  AND EXISTS (
    SELECT 1 FROM system_menu m
    WHERE m.permission = 'mes:pro-project-member:query'
      AND m.deleted = 0
      AND FIND_IN_SET(m.id, p.menu_ids)
  )
  AND NOT EXISTS (
    SELECT 1 FROM system_menu fm
    WHERE fm.permission LIKE 'mes:pro-project-file:%'
      AND fm.deleted = 0
      AND FIND_IN_SET(fm.id, p.menu_ids)
  );

-- 2) 为持有成员菜单租户副本的角色，补插附件菜单的租户副本
--    （以 mes:pro-project-member:query 的副本作为"已开通项目管理"的基准）
INSERT INTO system_role_menu
  (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT rm.role_id, f.id, 'sql-fix', NOW(), 'sql-fix', NOW(), 0, rm.tenant_id
FROM system_role_menu rm
JOIN system_menu fm
  ON fm.permission = 'mes:pro-project-member:query' AND fm.deleted = 0
 AND rm.menu_id = fm.id
JOIN system_menu f
  ON f.permission LIKE 'mes:pro-project-file:%' AND f.deleted = 0
WHERE rm.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, menu_id, tenant_id FROM system_role_menu) x
    WHERE x.role_id = rm.role_id AND x.menu_id = f.id AND x.tenant_id = rm.tenant_id
  );
