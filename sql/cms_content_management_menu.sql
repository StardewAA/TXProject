-- 在 CMS 系统下恢复内容管理目录，并保留原发布管理与组员提交的子功能。
-- “发布内容维护”页面包含：已发布内容、已驳回待修改、归档库、效力核验待办。

INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(70030,'发布管理','cms:knowledge:query',2,30,70000,'publish','ep:promotion','cms/publish/index','CmsPublishManagement',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70040,'内容管理','',1,40,70000,'content','ep:folder-opened',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70051,'发布内容维护','cms:knowledge:query',2,90,70040,'published','ep:document','cms/content-management/index','CmsContentManagement',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE
 name=VALUES(name),permission=VALUES(permission),type=VALUES(type),sort=VALUES(sort),
 parent_id=VALUES(parent_id),path=VALUES(path),icon=VALUES(icon),component=VALUES(component),
 component_name=VALUES(component_name),type=VALUES(type),status=0,visible=b'1',keep_alive=b'1',always_show=b'1',
 updater='1',update_time=NOW(),deleted=b'0';

-- CMS 目录展示五个定版模块，保留专家管理。
UPDATE system_menu
SET visible=b'0',update_time=NOW()
WHERE parent_id=70000 AND id NOT IN (70007,70011,70029,70030,70040) AND deleted=b'0';

UPDATE system_menu
SET visible=b'1',deleted=b'0',update_time=NOW()
WHERE id=70011 OR parent_id=70011 OR (parent_id=70040 AND id NOT IN (70041,70042,70043,70044));

-- 标准规范、材料参数、工艺案例、定额资料四个内容类型仅移除菜单和授权，保留业务代码与历史数据。
UPDATE system_role_menu
SET deleted=b'1',updater='1',update_time=NOW()
WHERE menu_id IN (70041,70042,70043,70044) AND deleted=b'0';

UPDATE system_menu
SET visible=b'0',deleted=b'1',updater='1',update_time=NOW()
WHERE id IN (70041,70042,70043,70044);

-- 保留原按钮权限，并给平台超级管理员补齐新一级模块权限。
INSERT INTO system_role_menu
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'1',NOW(),'1',NOW(),b'0',r.tenant_id
FROM system_role r
JOIN system_menu m ON m.id IN (70011,70012,70013,70014,70015,70030,70031,70032,70033,70034,
                               70040,70045,70046,70047,70048,70049,70051)
 AND m.deleted=b'0'
WHERE r.code='super_admin' AND r.tenant_id=1 AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id=r.id AND rm.menu_id=m.id
      AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
  );
