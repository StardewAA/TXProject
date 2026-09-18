-- 租户超级管理员菜单同步：只同步租户套餐内权限，不下放平台级租户管理权限。
-- 可重复执行。覆盖企业店铺、企业入驻及以后追加到套餐 119 的业务菜单。
SET NAMES utf8mb4;
START TRANSACTION;

-- 新商城菜单创建时间晚于会员套餐，先补进套餐白名单。
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25000), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25000 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25001), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25001 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25002), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25002 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25003), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25003 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25004), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25004 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25005), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25005 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25100), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25100 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25101), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25101 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25102), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25102 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25103), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25103 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25104), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25104 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25106), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25106 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25107), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25107 AS JSON), '$')=0;
UPDATE system_tenant_package
SET menu_ids = JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 25108), updater='mall-tenant-menu-20260917', update_time=NOW()
WHERE id=119 AND deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(25108 AS JSON), '$')=0;

-- 套餐内全部菜单同步给各租户 tenant_admin；不复制平台 admin 的越权菜单。
INSERT INTO system_role_menu(role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT r.id,j.menu_id,'mall-tenant-menu-20260917',NOW(),'mall-tenant-menu-20260917',NOW(),b'0',r.tenant_id
FROM system_role r
JOIN system_tenant t ON t.id=r.tenant_id AND t.package_id=119 AND t.status=0 AND t.deleted=b'0'
JOIN system_tenant_package p ON p.id=t.package_id AND p.status=0 AND p.deleted=b'0'
JOIN JSON_TABLE(CAST(p.menu_ids AS JSON),'$[*]' COLUMNS(menu_id BIGINT PATH '$')) j
JOIN system_menu m ON m.id=j.menu_id AND m.status=0 AND m.deleted=b'0'
WHERE r.code='tenant_admin' AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id=r.id AND rm.menu_id=j.menu_id AND rm.deleted=b'0'
  );

COMMIT;

SELECT r.tenant_id,r.id AS role_id,COUNT(DISTINCT rm.menu_id) AS active_menu_count
FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id AND rm.deleted=b'0'
WHERE r.code='tenant_admin' AND r.deleted=b'0'
GROUP BY r.tenant_id,r.id ORDER BY r.tenant_id;
