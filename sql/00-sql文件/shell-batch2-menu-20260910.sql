-- =====================================================================
-- 空壳表落成（第二批）：10 张安全环保表 菜单+按钮+授权（2026-09-10）
-- 结构：目录「安全环保检测」id=34000（勿重建）> 每表一节 34600/34610/...，+1..+5 为按钮
-- 与第一批（shell-modules-menu-20260910.sql，34400~34454）同构，仅换 id 段与表。
-- 授权：60185(tenant_admin)=全量；60466(环保专员)=查询/新增/修改，**不给删除**（同第一批口径）。
-- ⚠ 34600~34699 段此前为空（已查 system_menu 无占用）。排 id 前务必先查库，
--   前车之鉴见 chemical-20260910-menu-fix.sql（34400~34402 曾被误占）。
-- 应用后必须**重启 mes-server**（内存权限缓存，见 docs/开发说明-2026-09-08.md §18.7）。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < shell-batch2-menu-20260910.sql
-- =====================================================================

SET NAMES utf8mb4;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 34600 AND 34699 AND tenant_id=2010;
DELETE FROM system_menu      WHERE id      BETWEEN 34600 AND 34699;

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34600,'粉尘检测记录','',2,201,34000,'dust-record','mes/safetyEnv/dustRecord/index','MesSetDustRecord',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34601,'查询','mes:set-dust-record:query',3,1,34600,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34602,'新增','mes:set-dust-record:create',3,2,34600,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34603,'修改','mes:set-dust-record:update',3,3,34600,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34604,'删除','mes:set-dust-record:delete',3,4,34600,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34610,'噪声检测记录','',2,202,34000,'noise-record','mes/safetyEnv/noiseRecord/index','MesSetNoiseRecord',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34611,'查询','mes:set-noise-record:query',3,1,34610,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34612,'新增','mes:set-noise-record:create',3,2,34610,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34613,'修改','mes:set-noise-record:update',3,3,34610,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34614,'删除','mes:set-noise-record:delete',3,4,34610,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34620,'气体检测记录','',2,203,34000,'gas-record','mes/safetyEnv/gasRecord/index','MesSetGasRecord',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34621,'查询','mes:set-gas-record:query',3,1,34620,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34622,'新增','mes:set-gas-record:create',3,2,34620,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34623,'修改','mes:set-gas-record:update',3,3,34620,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34624,'删除','mes:set-gas-record:delete',3,4,34620,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34630,'电气安全检查','',2,204,34000,'electrical-record','mes/safetyEnv/electricalRecord/index','MesSetElectricalRecord',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34631,'查询','mes:set-electrical-record:query',3,1,34630,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34632,'新增','mes:set-electrical-record:create',3,2,34630,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34633,'修改','mes:set-electrical-record:update',3,3,34630,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34634,'删除','mes:set-electrical-record:delete',3,4,34630,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34640,'消防检查记录','',2,205,34000,'fire-check','mes/safetyEnv/fireCheck/index','MesSetFireCheck',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34641,'查询','mes:set-fire-check:query',3,1,34640,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34642,'新增','mes:set-fire-check:create',3,2,34640,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34643,'修改','mes:set-fire-check:update',3,3,34640,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34644,'删除','mes:set-fire-check:delete',3,4,34640,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34650,'劳保用品检查','',2,206,34000,'ppe-check','mes/safetyEnv/ppeCheck/index','MesSetPpeCheck',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34651,'查询','mes:set-ppe-check:query',3,1,34650,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34652,'新增','mes:set-ppe-check:create',3,2,34650,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34653,'修改','mes:set-ppe-check:update',3,3,34650,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34654,'删除','mes:set-ppe-check:delete',3,4,34650,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34660,'压力容器检查','',2,207,34000,'pressure-vessel','mes/safetyEnv/pressureVessel/index','MesSetPressureVessel',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34661,'查询','mes:set-pressure-vessel:query',3,1,34660,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34662,'新增','mes:set-pressure-vessel:create',3,2,34660,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34663,'修改','mes:set-pressure-vessel:update',3,3,34660,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34664,'删除','mes:set-pressure-vessel:delete',3,4,34660,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34670,'职业危害检测','',2,208,34000,'occupational-hazard','mes/safetyEnv/occupationalHazard/index','MesSetOccupationalHazard',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34671,'查询','mes:set-occupational-hazard:query',3,1,34670,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34672,'新增','mes:set-occupational-hazard:create',3,2,34670,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34673,'修改','mes:set-occupational-hazard:update',3,3,34670,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34674,'删除','mes:set-occupational-hazard:delete',3,4,34670,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34680,'检测计划','',2,209,34000,'plan','mes/safetyEnv/plan/index','MesSetPlan',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34681,'查询','mes:set-plan:query',3,1,34680,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34682,'新增','mes:set-plan:create',3,2,34680,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34683,'修改','mes:set-plan:update',3,3,34680,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34684,'删除','mes:set-plan:delete',3,4,34680,'',0,b'1',b'1',b'1','1','1');

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34690,'检测标准','',2,210,34000,'standard','mes/safetyEnv/standard/index','MesSetStandard',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34691,'查询','mes:set-standard:query',3,1,34690,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34692,'新增','mes:set-standard:create',3,2,34690,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34693,'修改','mes:set-standard:update',3,3,34690,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34694,'删除','mes:set-standard:delete',3,4,34690,'',0,b'1',b'1',b'1','1','1');

-- 授权：管理员 60185 全量（每表 5 个）
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id) VALUES
  (60185,34600,'1','1',2010),
  (60185,34601,'1','1',2010),
  (60185,34602,'1','1',2010),
  (60185,34603,'1','1',2010),
  (60185,34604,'1','1',2010),
  (60185,34610,'1','1',2010),
  (60185,34611,'1','1',2010),
  (60185,34612,'1','1',2010),
  (60185,34613,'1','1',2010),
  (60185,34614,'1','1',2010),
  (60185,34620,'1','1',2010),
  (60185,34621,'1','1',2010),
  (60185,34622,'1','1',2010),
  (60185,34623,'1','1',2010),
  (60185,34624,'1','1',2010),
  (60185,34630,'1','1',2010),
  (60185,34631,'1','1',2010),
  (60185,34632,'1','1',2010),
  (60185,34633,'1','1',2010),
  (60185,34634,'1','1',2010),
  (60185,34640,'1','1',2010),
  (60185,34641,'1','1',2010),
  (60185,34642,'1','1',2010),
  (60185,34643,'1','1',2010),
  (60185,34644,'1','1',2010),
  (60185,34650,'1','1',2010),
  (60185,34651,'1','1',2010),
  (60185,34652,'1','1',2010),
  (60185,34653,'1','1',2010),
  (60185,34654,'1','1',2010),
  (60185,34660,'1','1',2010),
  (60185,34661,'1','1',2010),
  (60185,34662,'1','1',2010),
  (60185,34663,'1','1',2010),
  (60185,34664,'1','1',2010),
  (60185,34670,'1','1',2010),
  (60185,34671,'1','1',2010),
  (60185,34672,'1','1',2010),
  (60185,34673,'1','1',2010),
  (60185,34674,'1','1',2010),
  (60185,34680,'1','1',2010),
  (60185,34681,'1','1',2010),
  (60185,34682,'1','1',2010),
  (60185,34683,'1','1',2010),
  (60185,34684,'1','1',2010),
  (60185,34690,'1','1',2010),
  (60185,34691,'1','1',2010),
  (60185,34692,'1','1',2010),
  (60185,34693,'1','1',2010),
  (60185,34694,'1','1',2010);

-- 授权：环保专员 60466 = 查询/新增/修改（不含删除，每表 4 个）
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id) VALUES
  (60466,34600,'1','1',2010),
  (60466,34601,'1','1',2010),
  (60466,34602,'1','1',2010),
  (60466,34603,'1','1',2010),
  (60466,34610,'1','1',2010),
  (60466,34611,'1','1',2010),
  (60466,34612,'1','1',2010),
  (60466,34613,'1','1',2010),
  (60466,34620,'1','1',2010),
  (60466,34621,'1','1',2010),
  (60466,34622,'1','1',2010),
  (60466,34623,'1','1',2010),
  (60466,34630,'1','1',2010),
  (60466,34631,'1','1',2010),
  (60466,34632,'1','1',2010),
  (60466,34633,'1','1',2010),
  (60466,34640,'1','1',2010),
  (60466,34641,'1','1',2010),
  (60466,34642,'1','1',2010),
  (60466,34643,'1','1',2010),
  (60466,34650,'1','1',2010),
  (60466,34651,'1','1',2010),
  (60466,34652,'1','1',2010),
  (60466,34653,'1','1',2010),
  (60466,34660,'1','1',2010),
  (60466,34661,'1','1',2010),
  (60466,34662,'1','1',2010),
  (60466,34663,'1','1',2010),
  (60466,34670,'1','1',2010),
  (60466,34671,'1','1',2010),
  (60466,34672,'1','1',2010),
  (60466,34673,'1','1',2010),
  (60466,34680,'1','1',2010),
  (60466,34681,'1','1',2010),
  (60466,34682,'1','1',2010),
  (60466,34683,'1','1',2010),
  (60466,34690,'1','1',2010),
  (60466,34691,'1','1',2010),
  (60466,34692,'1','1',2010),
  (60466,34693,'1','1',2010);

-- 校验（撞车检查应为 0）
SELECT '菜单34600-34699' k, COUNT(*) v FROM system_menu WHERE id BETWEEN 34600 AND 34699 AND deleted=0
UNION ALL SELECT '管理员60185绑定', COUNT(*) FROM system_role_menu WHERE role_id=60185 AND menu_id BETWEEN 34600 AND 34699 AND deleted=0
UNION ALL SELECT '环保专员60466绑定', COUNT(*) FROM system_role_menu WHERE role_id=60466 AND menu_id BETWEEN 34600 AND 34699 AND deleted=0
UNION ALL SELECT '段外撞车(应为0)', COUNT(*) FROM system_menu WHERE id BETWEEN 34600 AND 34699 AND deleted=0 AND (id MOD 10) > 5;
