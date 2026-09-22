-- 第 22.6 节增量：平台租户目录后台管理菜单与权限。
--
-- 边界：本脚本只添加 mall_portal_tenant 公开元数据的管理入口；
-- 不创建或更新 system_tenant、企业、店铺、商品、订单等经营数据。
-- 权限只默认授予 1 号租户的平台超级管理员，不进入租户套餐，
-- 不下放给各经营租户的 tenant_admin / 商家角色。

SET NAMES utf8mb4;
START TRANSACTION;

SET @portal_admin_parent_exists := (
  SELECT COUNT(*) FROM system_menu WHERE id=2362 AND deleted=b'0'
);
SET @portal_admin_parent_guard := IF(
  @portal_admin_parent_exists=1,
  'SELECT 1',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''平台租户目录菜单安装中止：商城系统父菜单 2362 不存在'''
);
PREPARE portal_admin_parent_stmt FROM @portal_admin_parent_guard;
EXECUTE portal_admin_parent_stmt;
DEALLOCATE PREPARE portal_admin_parent_stmt;

-- 固定 ID 便于多环境管理；已被其他功能占用时必须中止，禁止覆盖。
SET @portal_admin_menu_collision := (
  SELECT COUNT(*) FROM system_menu
  WHERE id BETWEEN 25230 AND 25232
    AND COALESCE(creator,'')<>'mall-section22-portal-admin'
);
SET @portal_admin_menu_guard := IF(
  @portal_admin_menu_collision=0,
  'SELECT 1',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''平台租户目录菜单安装中止：25230-25232 已被其他功能占用'''
);
PREPARE portal_admin_menu_stmt FROM @portal_admin_menu_guard;
EXECUTE portal_admin_menu_stmt;
DEALLOCATE PREPARE portal_admin_menu_stmt;

INSERT INTO system_menu
 (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,
  visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
 (25230,'平台租户目录','',2,75,2362,'portal-tenant','ep:guide',
  'mall/product/portal-tenant/index','MallProductPortalTenant',0,b'1',b'1',b'1',
  'mall-section22-portal-admin',NOW(),'mall-section22-portal-admin',NOW(),b'0'),
 (25231,'平台租户目录查询','product:portal-tenant:query',3,1,25230,'','','','',
  0,b'1',b'1',b'1','mall-section22-portal-admin',NOW(),'mall-section22-portal-admin',NOW(),b'0'),
 (25232,'平台租户目录更新','product:portal-tenant:update',3,2,25230,'','','','',
  0,b'1',b'1',b'1','mall-section22-portal-admin',NOW(),'mall-section22-portal-admin',NOW(),b'0')
ON DUPLICATE KEY UPDATE
 name=VALUES(name),permission=VALUES(permission),type=VALUES(type),sort=VALUES(sort),
 parent_id=VALUES(parent_id),path=VALUES(path),icon=VALUES(icon),component=VALUES(component),
 component_name=VALUES(component_name),status=0,visible=b'1',keep_alive=VALUES(keep_alive),
 always_show=VALUES(always_show),deleted=b'0',updater='mall-section22-portal-admin',update_time=NOW();

-- 先恢复同一角色的历史逻辑删除关系，再插入缺失关系，保证重复执行安全。
UPDATE system_role_menu mapping
JOIN system_role role_data
  ON role_data.id=mapping.role_id AND role_data.tenant_id=mapping.tenant_id
SET mapping.deleted=b'0',mapping.updater='mall-section22-portal-admin',mapping.update_time=NOW()
WHERE mapping.menu_id IN (25230,25231,25232)
  AND role_data.tenant_id=1 AND role_data.code='super_admin'
  AND role_data.status=0 AND role_data.deleted=b'0';

SET @portal_admin_role_menu_base_id := (SELECT COALESCE(MAX(id),0) FROM system_role_menu);
INSERT INTO system_role_menu
 (id,role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT @portal_admin_role_menu_base_id
         + ROW_NUMBER() OVER (ORDER BY role_data.id,granted_menu.menu_id),
       role_data.id,granted_menu.menu_id,
       'mall-section22-portal-admin',NOW(),'mall-section22-portal-admin',NOW(),b'0',role_data.tenant_id
FROM system_role role_data
CROSS JOIN (
  SELECT 25230 menu_id UNION ALL SELECT 25231 UNION ALL SELECT 25232
) granted_menu
WHERE role_data.tenant_id=1 AND role_data.code='super_admin'
  AND role_data.status=0 AND role_data.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu existing_mapping
    WHERE existing_mapping.role_id=role_data.id
      AND existing_mapping.menu_id=granted_menu.menu_id
  );

COMMIT;

-- 验收：只应看到页面、查询、更新三个菜单，无新增/删除经营数据权限。
SELECT id,name,permission,type,parent_id,path,component,status,deleted
FROM system_menu WHERE id BETWEEN 25230 AND 25232 ORDER BY id;

SELECT role_data.tenant_id,role_data.code,mapping.menu_id
FROM system_role_menu mapping
JOIN system_role role_data ON role_data.id=mapping.role_id
WHERE mapping.menu_id BETWEEN 25230 AND 25232 AND mapping.deleted=b'0'
ORDER BY role_data.tenant_id,role_data.code,mapping.menu_id;
