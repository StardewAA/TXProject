-- 商城后台客服菜单归组：
-- 客服中心
--   ├─ 客服坐席授权
--   ├─ 店铺客服接待台
--   └─ 客服固定FAQ
--
-- 菜单为全局元数据；角色菜单关系继续按各自 tenant_id 隔离。

START TRANSACTION;

-- 原“客服中心”是旧客服页面。现在将其作为目录使用，保留原菜单编号，
-- 这样已有角色对 2797 的授权不会丢失。
UPDATE system_menu
SET type = 1,
    path = 'kefu',
    component = NULL,
    component_name = NULL,
    sort = 100,
    always_show = b'1',
    updater = 'mall-menu-group-20260922',
    update_time = NOW()
WHERE id = 2797
  AND name = '客服中心'
  AND deleted = b'0';

-- 三个客服业务页面归入客服中心目录，并固定显示顺序。
UPDATE system_menu
SET parent_id = 2797,
    sort = CASE id
             WHEN 25106 THEN 1
             WHEN 25107 THEN 2
             WHEN 25108 THEN 3
           END,
    updater = 'mall-menu-group-20260922',
    update_time = NOW()
WHERE id IN (25106, 25107, 25108)
  AND name IN ('客服坐席授权', '店铺客服接待台', '客服固定FAQ')
  AND deleted = b'0';

-- 已拥有任一子菜单的角色必须同时拥有父目录，否则动态路由树无法展示子项。
INSERT INTO system_role_menu
  (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 2797, 'mall-menu-group-20260922', NOW(),
       'mall-menu-group-20260922', NOW(), b'0', r.tenant_id
FROM system_role_menu rm
JOIN system_role r
  ON r.id = rm.role_id
 AND r.deleted = b'0'
WHERE rm.menu_id IN (25106, 25107, 25108)
  AND rm.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM system_role_menu parent_rm
    WHERE parent_rm.role_id = rm.role_id
      AND parent_rm.menu_id = 2797
      AND parent_rm.deleted = b'0'
  );

COMMIT;

-- 验收：首个结果集应为 1 个目录和 3 个子页面；第二个结果应为 0。
SELECT id, name, parent_id, type, sort, path, component
FROM system_menu
WHERE id IN (2797, 25106, 25107, 25108)
ORDER BY CASE id WHEN 2797 THEN 0 ELSE sort END, id;

SELECT COUNT(*) AS child_role_missing_parent_count
FROM system_role_menu child_rm
WHERE child_rm.menu_id IN (25106, 25107, 25108)
  AND child_rm.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM system_role_menu parent_rm
    WHERE parent_rm.role_id = child_rm.role_id
      AND parent_rm.menu_id = 2797
      AND parent_rm.deleted = b'0'
  );
