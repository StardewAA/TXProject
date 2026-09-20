-- WMS 共享仓库查询权限
-- 2026-09-20 修订：
-- 1. 不再依赖固定的 WMS 父菜单 ID，不同数据库初始化版本的 ID 不一致。
-- 2. 自动补齐租户套餐和已有 WMS 角色的菜单授权。
SET NAMES utf8mb4;

SET @wms_menu_id = (
    SELECT id
    FROM system_menu
    WHERE path = '/wms'
      AND type = 1
      AND deleted = b'0'
    ORDER BY id DESC
    LIMIT 1
);

INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '共享仓库', '', 2, 30, @wms_menu_id, 'shared-warehouse', 'ep:connection', 'wms/shared-warehouse/index',
       'WmsSharedWarehouse', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
WHERE @wms_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM system_menu
      WHERE component_name = 'WmsSharedWarehouse'
        AND deleted = b'0'
  );

SET @shared_warehouse_menu_id = (
    SELECT id
    FROM system_menu
    WHERE component_name = 'WmsSharedWarehouse'
      AND deleted = b'0'
    ORDER BY id DESC
    LIMIT 1
);

UPDATE system_menu
SET parent_id = @wms_menu_id,
    update_time = NOW()
WHERE component_name = 'WmsSharedWarehouse'
  AND deleted = b'0'
  AND @wms_menu_id IS NOT NULL;

INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '共享仓库查询', 'wms:shared-warehouse:query', 3, 1, @shared_warehouse_menu_id, '',
       '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
WHERE @shared_warehouse_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM system_menu
    WHERE permission = 'wms:shared-warehouse:query' AND deleted = b'0'
);

SET @shared_warehouse_query_menu_id = (
    SELECT id
    FROM system_menu
    WHERE permission = 'wms:shared-warehouse:query'
      AND deleted = b'0'
    ORDER BY id DESC
    LIMIT 1
);

-- 兼容已存在但 parent_id 错误的旧记录。
UPDATE system_menu
SET parent_id = @shared_warehouse_menu_id,
    update_time = NOW()
WHERE permission = 'wms:shared-warehouse:query'
  AND deleted = b'0'
  AND @shared_warehouse_menu_id IS NOT NULL;

-- 已拥有 WMS 根菜单的角色自动获得共享仓库查询权限。
INSERT INTO system_role_menu
(role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT source.role_id, @shared_warehouse_query_menu_id,
       'system', NOW(), 'system', NOW(), b'0', source.tenant_id
FROM system_role_menu source
WHERE source.menu_id = @wms_menu_id
  AND source.deleted = b'0'
  AND @shared_warehouse_query_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM system_role_menu existing
      WHERE existing.role_id = source.role_id
        AND existing.menu_id = @shared_warehouse_query_menu_id
        AND existing.deleted = b'0'
        AND existing.tenant_id = source.tenant_id
  );

-- 已包含 WMS 根菜单的租户套餐自动获得共享仓库菜单。
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(
        COALESCE(menu_ids, JSON_ARRAY()),
        '$',
        @shared_warehouse_menu_id
    ),
    update_time = NOW()
WHERE deleted = b'0'
  AND @wms_menu_id IS NOT NULL
  AND @shared_warehouse_menu_id IS NOT NULL
  AND JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@wms_menu_id AS JSON))
  AND NOT JSON_CONTAINS(
      COALESCE(menu_ids, JSON_ARRAY()),
      CAST(@shared_warehouse_menu_id AS JSON)
  );

UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(
        COALESCE(menu_ids, JSON_ARRAY()),
        '$',
        @shared_warehouse_query_menu_id
    ),
    update_time = NOW()
WHERE deleted = b'0'
  AND @wms_menu_id IS NOT NULL
  AND @shared_warehouse_query_menu_id IS NOT NULL
  AND JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@wms_menu_id AS JSON))
  AND NOT JSON_CONTAINS(
      COALESCE(menu_ids, JSON_ARRAY()),
      CAST(@shared_warehouse_query_menu_id AS JSON)
  );
