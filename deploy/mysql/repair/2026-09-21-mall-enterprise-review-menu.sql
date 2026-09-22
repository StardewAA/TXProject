-- 修复商城后台“企业入驻”菜单层级：
-- 1. 原“企业入驻”页面改为目录；
-- 2. 新增“入驻申请审核”页面；
-- 3. 原审核页面按钮权限迁移到新页面；
-- 4. 仅给已经拥有 product:enterprise:audit 权限的角色开放审核入口。
--
-- 本脚本可重复执行。

SET NAMES utf8mb4;
START TRANSACTION;

SET @enterprise_menu_id := (
    SELECT id
    FROM system_menu
    WHERE deleted = b'0'
      AND (
          component = 'mall/product/enterprise/index'
          OR (id = 25005 AND path = 'enterprise')
      )
    ORDER BY id
    LIMIT 1
);

-- 父节点只负责分组，不能同时承载页面组件，否则存在子菜单时审核页会被路由隐藏。
UPDATE system_menu
SET name = '企业入驻',
    type = 1,
    component = NULL,
    component_name = NULL,
    keep_alive = b'0',
    always_show = b'1',
    updater = 'system',
    update_time = NOW()
WHERE id = @enterprise_menu_id;

INSERT INTO system_menu (
    name, permission, type, sort, parent_id, path, icon, component, component_name,
    status, visible, keep_alive, always_show,
    creator, create_time, updater, update_time, deleted
)
SELECT
    '入驻申请审核', '', 2, 10, @enterprise_menu_id, 'review', 'ep:document-checked',
    'mall/product/enterprise/index', 'ProductEnterprise',
    0, b'1', b'1', b'1',
    'system', NOW(), 'system', NOW(), b'0'
WHERE @enterprise_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM system_menu
      WHERE deleted = b'0'
        AND component = 'mall/product/enterprise/index'
  );

SET @enterprise_review_menu_id := (
    SELECT id
    FROM system_menu
    WHERE deleted = b'0'
      AND parent_id = @enterprise_menu_id
      AND component = 'mall/product/enterprise/index'
    ORDER BY id
    LIMIT 1
);

-- 查询、创建、更新、审核、删除等页面内权限应归属于审核页面，而不是目录。
UPDATE system_menu
SET parent_id = @enterprise_review_menu_id,
    updater = 'system',
    update_time = NOW()
WHERE deleted = b'0'
  AND parent_id = @enterprise_menu_id
  AND type = 3
  AND permission LIKE 'product:enterprise:%';

-- 保持员工授权为第二个业务页面。
UPDATE system_menu
SET sort = 20,
    updater = 'system',
    update_time = NOW()
WHERE deleted = b'0'
  AND parent_id = @enterprise_menu_id
  AND component = 'mall/product/enterprise/member/index';

-- 原本已经具备“企业审核”按钮权限的角色才获得审核页面入口。
-- tenant_id 必须跟随角色写入，避免跨租户角色菜单数据污染。
INSERT INTO system_role_menu (
    role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT DISTINCT
    audit_role.role_id, @enterprise_review_menu_id,
    'system', NOW(), 'system', NOW(), b'0', role_data.tenant_id
FROM system_role_menu audit_role
JOIN system_menu audit_permission
  ON audit_permission.id = audit_role.menu_id
 AND audit_permission.deleted = b'0'
 AND audit_permission.permission = 'product:enterprise:audit'
JOIN system_role role_data
  ON role_data.id = audit_role.role_id
 AND role_data.deleted = b'0'
WHERE audit_role.deleted = b'0'
  AND @enterprise_review_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM system_role_menu existing_mapping
      WHERE existing_mapping.role_id = audit_role.role_id
        AND existing_mapping.menu_id = @enterprise_review_menu_id
        AND existing_mapping.deleted = b'0'
  );

-- 防御性补齐审核角色的父目录授权。
INSERT INTO system_role_menu (
    role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT DISTINCT
    review_role.role_id, @enterprise_menu_id,
    'system', NOW(), 'system', NOW(), b'0', role_data.tenant_id
FROM system_role_menu review_role
JOIN system_role role_data
  ON role_data.id = review_role.role_id
 AND role_data.deleted = b'0'
WHERE review_role.deleted = b'0'
  AND review_role.menu_id = @enterprise_review_menu_id
  AND @enterprise_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM system_role_menu existing_mapping
      WHERE existing_mapping.role_id = review_role.role_id
        AND existing_mapping.menu_id = @enterprise_menu_id
        AND existing_mapping.deleted = b'0'
  );

COMMIT;

-- 验收查询：应看到“企业入驻（目录）”下有“入驻申请审核”和“企业员工授权”。
SELECT id, name, type, sort, parent_id, path, component, permission
FROM system_menu
WHERE deleted = b'0'
  AND (
      id = @enterprise_menu_id
      OR parent_id = @enterprise_menu_id
      OR parent_id = @enterprise_review_menu_id
  )
ORDER BY parent_id, sort, id;
