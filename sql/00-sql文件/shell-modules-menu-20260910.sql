-- 空壳表落成：6 张安全环保表的菜单与授权（2026-09-10）
-- 菜单 id 段 34400-34454（34400/34410/…每表一节，+1..+4 为按钮）；
-- ⚠️ 二期/三期（应急等）请从 34500 起，勿占用本段。
-- 授权：60185 企业超级管理员=全量；60466 环保专员=查询/新增/修改（不给删除）。
-- 幂等：先按 id 段删除再插入。生成：codegen（inline）

SET NAMES utf8mb4;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 34400 AND 34454 AND tenant_id=2010;
DELETE FROM system_menu WHERE id BETWEEN 34400 AND 34454;

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34400,'环保检测报告','',2,195,34000,'env-report','mes/safetyEnv/envReport/index','MesSetEnvReport',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34401,'查询','mes:set-env-report:query',3,1,34400,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34402,'新增','mes:set-env-report:create',3,2,34400,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34403,'修改','mes:set-env-report:update',3,3,34400,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34404,'删除','mes:set-env-report:delete',3,4,34400,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34410,'排放口','',2,196,34000,'emission-outlet','mes/safetyEnv/emissionOutlet/index','MesSetEmissionOutlet',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34411,'查询','mes:set-emission-outlet:query',3,1,34410,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34412,'新增','mes:set-emission-outlet:create',3,2,34410,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34413,'修改','mes:set-emission-outlet:update',3,3,34410,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34414,'删除','mes:set-emission-outlet:delete',3,4,34410,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34420,'废气监测记录','',2,197,34000,'exhaust-gas','mes/safetyEnv/exhaustGas/index','MesSetExhaustGas',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34421,'查询','mes:set-exhaust-gas:query',3,1,34420,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34422,'新增','mes:set-exhaust-gas:create',3,2,34420,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34423,'修改','mes:set-exhaust-gas:update',3,3,34420,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34424,'删除','mes:set-exhaust-gas:delete',3,4,34420,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34430,'废水监测记录','',2,198,34000,'wastewater','mes/safetyEnv/wastewater/index','MesSetWastewater',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34431,'查询','mes:set-wastewater:query',3,1,34430,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34432,'新增','mes:set-wastewater:create',3,2,34430,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34433,'修改','mes:set-wastewater:update',3,3,34430,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34434,'删除','mes:set-wastewater:delete',3,4,34430,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34440,'碳排放核算','',2,199,34000,'carbon-emission','mes/safetyEnv/carbonEmission/index','MesSetCarbonEmission',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34441,'查询','mes:set-carbon-emission:query',3,1,34440,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34442,'新增','mes:set-carbon-emission:create',3,2,34440,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34443,'修改','mes:set-carbon-emission:update',3,3,34440,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34444,'删除','mes:set-carbon-emission:delete',3,4,34440,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34450,'危化品安全检查','',2,200,34000,'chemical-safety','mes/safetyEnv/chemicalSafety/index','MesSetChemicalSafety',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34451,'查询','mes:set-chemical-safety:query',3,1,34450,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34452,'新增','mes:set-chemical-safety:create',3,2,34450,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34453,'修改','mes:set-chemical-safety:update',3,3,34450,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34454,'删除','mes:set-chemical-safety:delete',3,4,34450,'',0,b'1',b'1',b'1','1','1');

-- 授权 60185（全量）
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id) VALUES
  (60185,34400,'1','1',2010),
  (60185,34401,'1','1',2010),
  (60185,34402,'1','1',2010),
  (60185,34403,'1','1',2010),
  (60185,34404,'1','1',2010),
  (60185,34410,'1','1',2010),
  (60185,34411,'1','1',2010),
  (60185,34412,'1','1',2010),
  (60185,34413,'1','1',2010),
  (60185,34414,'1','1',2010),
  (60185,34420,'1','1',2010),
  (60185,34421,'1','1',2010),
  (60185,34422,'1','1',2010),
  (60185,34423,'1','1',2010),
  (60185,34424,'1','1',2010),
  (60185,34430,'1','1',2010),
  (60185,34431,'1','1',2010),
  (60185,34432,'1','1',2010),
  (60185,34433,'1','1',2010),
  (60185,34434,'1','1',2010),
  (60185,34440,'1','1',2010),
  (60185,34441,'1','1',2010),
  (60185,34442,'1','1',2010),
  (60185,34443,'1','1',2010),
  (60185,34444,'1','1',2010),
  (60185,34450,'1','1',2010),
  (60185,34451,'1','1',2010),
  (60185,34452,'1','1',2010),
  (60185,34453,'1','1',2010),
  (60185,34454,'1','1',2010);

-- 授权 60466（环保专员：菜单 + 查询/新增/修改）
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id) VALUES
  (60466,34400,'1','1',2010),
  (60466,34401,'1','1',2010),
  (60466,34402,'1','1',2010),
  (60466,34403,'1','1',2010),
  (60466,34410,'1','1',2010),
  (60466,34411,'1','1',2010),
  (60466,34412,'1','1',2010),
  (60466,34413,'1','1',2010),
  (60466,34420,'1','1',2010),
  (60466,34421,'1','1',2010),
  (60466,34422,'1','1',2010),
  (60466,34423,'1','1',2010),
  (60466,34430,'1','1',2010),
  (60466,34431,'1','1',2010),
  (60466,34432,'1','1',2010),
  (60466,34433,'1','1',2010),
  (60466,34440,'1','1',2010),
  (60466,34441,'1','1',2010),
  (60466,34442,'1','1',2010),
  (60466,34443,'1','1',2010),
  (60466,34450,'1','1',2010),
  (60466,34451,'1','1',2010),
  (60466,34452,'1','1',2010),
  (60466,34453,'1','1',2010);
