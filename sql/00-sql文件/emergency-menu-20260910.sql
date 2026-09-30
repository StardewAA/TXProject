-- 应急管理模块：菜单与授权（2026-09-10）
-- 菜单 id 段 34500-34540（34500/34510/34520/34530/34540 各一节，+1..+5 为按钮）。
--   34500 应急预案  34510 应急物资  34520 应急演练  34530 应急事件  34540 应急处置卡
-- 授权：60185 企业超级管理员=全量；60466 环保专员=查询/新增/修改 + 备案修订(file)/闭环(close)，
--       不给删除（沿用 shell-modules-menu-20260910.sql 的口径；file/close 是本职动作，不给就干不了活）。
--       60468 危化品安全员不授菜单：其职责是库位校验/五双签字，应急处置卡本就免鉴权（§八.3 扫码即看）。
-- 幂等：先按 id 段删除再插入。
-- ⚠️ 菜单/角色改动后必须重启 mes-server：它有一层自己的内存权限缓存，
--    只清 Redis（menu_role_ids:* / permission_menu_ids:*）不生效。

SET NAMES utf8mb4;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 34500 AND 34540 AND tenant_id = 2010;
DELETE FROM system_menu WHERE id BETWEEN 34500 AND 34540;

-- ========== 应急预案 ==========
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34500,'应急预案','',2,211,34000,'emergency-plan','mes/safetyEnv/emergencyplan/index','MesSetEmergencyPlan',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34501,'查询','mes:set-emergency-plan:query',3,1,34500,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34502,'新增','mes:set-emergency-plan:create',3,2,34500,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34503,'修改','mes:set-emergency-plan:update',3,3,34500,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34504,'删除','mes:set-emergency-plan:delete',3,4,34500,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34505,'发布·备案·修订','mes:set-emergency-plan:file',3,5,34500,'',0,b'1',b'1',b'1','1','1');

-- ========== 应急物资 ==========
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34510,'应急物资','',2,212,34000,'emergency-material','mes/safetyEnv/emergencymaterial/index','MesSetEmergencyMaterial',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34511,'查询','mes:set-emergency-material:query',3,1,34510,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34512,'新增','mes:set-emergency-material:create',3,2,34510,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34513,'修改','mes:set-emergency-material:update',3,3,34510,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34514,'删除','mes:set-emergency-material:delete',3,4,34510,'',0,b'1',b'1',b'1','1','1');

-- ========== 应急演练 ==========
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34520,'应急演练','',2,213,34000,'emergency-drill','mes/safetyEnv/emergencydrill/index','MesSetEmergencyDrill',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34521,'查询','mes:set-emergency-drill:query',3,1,34520,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34522,'新增','mes:set-emergency-drill:create',3,2,34520,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34523,'修改','mes:set-emergency-drill:update',3,3,34520,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34524,'删除','mes:set-emergency-drill:delete',3,4,34520,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34525,'完成·闭环','mes:set-emergency-drill:close',3,5,34520,'',0,b'1',b'1',b'1','1','1');

-- ========== 应急事件 ==========
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34530,'应急事件','',2,214,34000,'emergency-event','mes/safetyEnv/emergencyevent/index','MesSetEmergencyEvent',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34531,'查询','mes:set-emergency-event:query',3,1,34530,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34532,'上报','mes:set-emergency-event:create',3,2,34530,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34533,'修改','mes:set-emergency-event:update',3,3,34530,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34534,'删除','mes:set-emergency-event:delete',3,4,34530,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34535,'派发·处置·闭环','mes:set-emergency-event:close',3,5,34530,'',0,b'1',b'1',b'1','1','1');

-- ========== 应急处置卡（只读，后端免鉴权；菜单仅提供入口） ==========
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34540,'应急处置卡','',2,215,34000,'emergency-sop','mes/safetyEnv/emergencysop/index','MesSetEmergencySop',0,b'1',b'1',b'1','1','1');

-- 授权 60185（企业超级管理员，全量）
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id) VALUES
  (60185,34500,'1','1',2010),(60185,34501,'1','1',2010),(60185,34502,'1','1',2010),
  (60185,34503,'1','1',2010),(60185,34504,'1','1',2010),(60185,34505,'1','1',2010),
  (60185,34510,'1','1',2010),(60185,34511,'1','1',2010),(60185,34512,'1','1',2010),
  (60185,34513,'1','1',2010),(60185,34514,'1','1',2010),
  (60185,34520,'1','1',2010),(60185,34521,'1','1',2010),(60185,34522,'1','1',2010),
  (60185,34523,'1','1',2010),(60185,34524,'1','1',2010),(60185,34525,'1','1',2010),
  (60185,34530,'1','1',2010),(60185,34531,'1','1',2010),(60185,34532,'1','1',2010),
  (60185,34533,'1','1',2010),(60185,34534,'1','1',2010),(60185,34535,'1','1',2010),
  (60185,34540,'1','1',2010);

-- 授权 60466（环保专员：查询/新增/修改 + 备案修订/闭环，无删除）
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id) VALUES
  (60466,34500,'1','1',2010),(60466,34501,'1','1',2010),(60466,34502,'1','1',2010),
  (60466,34503,'1','1',2010),(60466,34505,'1','1',2010),
  (60466,34510,'1','1',2010),(60466,34511,'1','1',2010),(60466,34512,'1','1',2010),
  (60466,34513,'1','1',2010),
  (60466,34520,'1','1',2010),(60466,34521,'1','1',2010),(60466,34522,'1','1',2010),
  (60466,34523,'1','1',2010),(60466,34525,'1','1',2010),
  (60466,34530,'1','1',2010),(60466,34531,'1','1',2010),(60466,34532,'1','1',2010),
  (60466,34533,'1','1',2010),(60466,34535,'1','1',2010),
  (60466,34540,'1','1',2010);
