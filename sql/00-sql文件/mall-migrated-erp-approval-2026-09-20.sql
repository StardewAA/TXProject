-- ERP 审批管理菜单与权限
-- 2026-09-20
-- 说明：
-- 1. ERP 审批页面由前端动态菜单加载，必须在 system_menu 中注册。
-- 2. 不依赖固定自增 ID，兼容不同初始化数据库。
-- 3. 为已有 ERP 角色和包含 ERP 菜单的租户套餐补齐菜单授权。
SET NAMES utf8mb4;

SET @erp_menu_id = (
    SELECT id
    FROM system_menu
    WHERE path = '/erp'
      AND type = 1
      AND deleted = b'0'
    ORDER BY id DESC
    LIMIT 1
);

INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '审批管理', '', 2, 5, @erp_menu_id, 'approval', 'ep:stamp', 'erp/approval/index',
       'ErpApprovalManagement', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
WHERE @erp_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM system_menu
      WHERE component_name = 'ErpApprovalManagement'
        AND deleted = b'0'
  );

SET @approval_menu_id = (
    SELECT id
    FROM system_menu
    WHERE component_name = 'ErpApprovalManagement'
      AND deleted = b'0'
    ORDER BY id DESC
    LIMIT 1
);

UPDATE system_menu
SET parent_id = @erp_menu_id,
    update_time = NOW()
WHERE component_name = 'ErpApprovalManagement'
  AND deleted = b'0'
  AND @erp_menu_id IS NOT NULL;

INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '审批查询', 'erp:approval:query', 3, 1, @approval_menu_id, '',
       '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
WHERE @approval_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM system_menu
      WHERE permission = 'erp:approval:query'
        AND deleted = b'0'
  );

INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '提交审批', 'erp:approval:submit', 3, 2, @approval_menu_id, '',
       '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
WHERE @approval_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM system_menu
      WHERE permission = 'erp:approval:submit'
        AND deleted = b'0'
  );

SET @approval_query_menu_id = (
    SELECT id
    FROM system_menu
    WHERE permission = 'erp:approval:query'
      AND deleted = b'0'
    ORDER BY id DESC
    LIMIT 1
);

SET @approval_submit_menu_id = (
    SELECT id
    FROM system_menu
    WHERE permission = 'erp:approval:submit'
      AND deleted = b'0'
    ORDER BY id DESC
    LIMIT 1
);

SET @erp_purchase_menu_id = (
    SELECT id
    FROM system_menu
    WHERE path = 'purchase'
      AND parent_id = @erp_menu_id
      AND deleted = b'0'
    ORDER BY id DESC
    LIMIT 1
);

SET @erp_sale_menu_id = (
    SELECT id
    FROM system_menu
    WHERE path = 'sale'
      AND parent_id = @erp_menu_id
      AND deleted = b'0'
    ORDER BY id DESC
    LIMIT 1
);

-- 兼容旧记录 parent_id 不正确的情况。
UPDATE system_menu
SET parent_id = @approval_menu_id,
    update_time = NOW()
WHERE permission IN ('erp:approval:query', 'erp:approval:submit')
  AND deleted = b'0'
  AND @approval_menu_id IS NOT NULL;

-- 已拥有 ERP 根菜单、采购或销售菜单的角色自动获得审批菜单和权限。
INSERT INTO system_role_menu
(role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT source.role_id, target.menu_id,
       'system', NOW(), 'system', NOW(), b'0', source.tenant_id
FROM system_role_menu source
JOIN (
    SELECT @approval_menu_id AS menu_id
    UNION ALL
    SELECT @approval_query_menu_id
    UNION ALL
    SELECT @approval_submit_menu_id
) target
WHERE source.menu_id IN (@erp_menu_id, @erp_purchase_menu_id, @erp_sale_menu_id)
  AND source.deleted = b'0'
  AND target.menu_id IS NOT NULL
  AND @erp_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM system_role_menu existing
      WHERE existing.role_id = source.role_id
        AND existing.menu_id = target.menu_id
        AND existing.deleted = b'0'
        AND existing.tenant_id = source.tenant_id
  );

-- 已包含 ERP 根菜单、采购或销售菜单的租户套餐自动获得审批菜单和权限。
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', @approval_menu_id),
    update_time = NOW()
WHERE deleted = b'0'
  AND @erp_menu_id IS NOT NULL
  AND @approval_menu_id IS NOT NULL
  AND (
      JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@erp_menu_id AS JSON))
      OR JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@erp_purchase_menu_id AS JSON))
      OR JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@erp_sale_menu_id AS JSON))
  )
  AND NOT JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@approval_menu_id AS JSON));

UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', @approval_query_menu_id),
    update_time = NOW()
WHERE deleted = b'0'
  AND @approval_query_menu_id IS NOT NULL
  AND (
      JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@erp_menu_id AS JSON))
      OR JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@erp_purchase_menu_id AS JSON))
      OR JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@erp_sale_menu_id AS JSON))
  )
  AND NOT JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@approval_query_menu_id AS JSON));

UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', @approval_submit_menu_id),
    update_time = NOW()
WHERE deleted = b'0'
  AND @approval_submit_menu_id IS NOT NULL
  AND (
      JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@erp_menu_id AS JSON))
      OR JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@erp_purchase_menu_id AS JSON))
      OR JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@erp_sale_menu_id AS JSON))
  )
  AND NOT JSON_CONTAINS(COALESCE(menu_ids, JSON_ARRAY()), CAST(@approval_submit_menu_id AS JSON));
