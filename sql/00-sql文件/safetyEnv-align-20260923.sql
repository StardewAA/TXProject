-- ============================================================
--  安全环保检测(34000~34999) 菜单结构对齐 —— 以原机器 dump 为基准
--  基准文件: _backup/20260921-tianxin/pre-tianxin-targeted.sql
--  对齐前备份: _backup/menu-before-align-20260923.sql
--  内容: ① 补 6 个二级目录与缺失页面/按钮 ② 叶子归位
--        ③ 删掉本机多余(与原机器冲突)的行 ④ 重建 7 个角色的环保授权
--  ⚠️ 裸 SQL 授权绕过 @CacheEvict，执行后必须清 Redis 权限缓存
-- ============================================================
SET NAMES utf8mb4;
USE `ruoyi-vue-pro`;

-- 可重复执行的前置清理：本脚本负责插入的就是这批固定 id，先删掉再插
DELETE FROM system_role_menu WHERE menu_id IN
 (34010,34011,34012,34013,34014,34015,34302,34303,34370,34371,34380,34381,34382,
  34390,34391,34392,34393,34394,34395,34396,34397,34398,34399);
DELETE FROM system_menu WHERE id IN
 (34010,34011,34012,34013,34014,34015,34302,34303,34370,34371,34380,34381,34382,
  34390,34391,34392,34393,34394,34395,34396,34397,34398,34399);

-- ① 删除本机有、原机器没有的（含 34305/34306，原机器用 34370/34371 表示污染追溯）
-- (无)

-- ② 插入原机器有、本机没有的
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34010,'污染管控','',1,10,34000,'pollution','ep:filter','','',0,b'0',b'0',b'0',1,'2026-09-10 19:45:04',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34011,'危废与化学品','',1,20,34000,'hazchem','ep:box','','',0,b'0',b'0',b'0',1,'2026-09-10 19:45:04',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34012,'排放监测','',1,30,34000,'emission','ep:monitor','','',0,b'0',b'0',b'0',1,'2026-09-10 19:45:04',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34013,'常规检测','',1,40,34000,'inspection','ep:document-checked','','',0,b'0',b'0',b'0',1,'2026-09-10 19:45:04',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34014,'治污设施与报告','',1,50,34000,'facility-report','ep:set-up','','',0,b'0',b'0',b'0',1,'2026-09-10 19:45:04',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34015,'应急管理','',1,60,34000,'emergency-section','ep:first-aid-kit','','',0,b'0',b'0',b'0',1,'2026-09-10 19:45:04',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34302,'污染暂存台账','',2,2,34010,'pollutionLedger','ep:box','mes/safetyEnv/pollutionLedger/index','MesPollutionLedger',0,b'0',b'0',b'0',1,'2026-09-03 15:26:01',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34303,'在库环保视图','',2,6,34010,'inventoryEnv','ep:search','mes/safetyEnv/inventoryEnv/index','MesInventoryEnv',0,b'0',b'0',b'0',1,'2026-09-11 16:27:30',1,'2026-09-11 16:27:30',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34370,'污染追溯','',2,3,34010,'pollutionTrace','ep:set-up','mes/safetyEnv/pollutionTrace/index','MesPollutionTrace',0,b'0',b'0',b'0',1,'2026-09-10 07:49:09',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34371,'查询','mes:set-pollution-trace:query',3,1,34370,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 07:49:09',1,'2026-09-10 07:49:09',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34380,'排放合规流水','',2,4,34010,'pollutionDischarge','ep:histogram','mes/safetyEnv/pollutionDischarge/index','MesPollutionDischarge',0,b'0',b'0',b'0',1,'2026-09-10 08:30:46',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34381,'查询','mes:set-pollution-discharge:query',3,1,34380,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 08:30:46',1,'2026-09-10 08:30:46',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34382,'标记终审','mes:set-pollution-mark:update',3,1,34302,'','','','',0,b'0',b'0',b'0','system','2026-09-10 08:45:42','system','2026-09-10 08:45:42',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34390,'危废管理','',2,1,34011,'hazwaste','ep:document-delete','mes/safetyEnv/hazwaste/index','MesHazwaste',0,b'0',b'0',b'0',1,'2026-09-10 10:48:10',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34391,'查询','mes:set-hazwaste:query',3,1,34390,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 10:48:10',1,'2026-09-10 10:48:10',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34392,'创建','mes:set-hazwaste:create',3,2,34390,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 10:48:10',1,'2026-09-10 10:48:10',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34393,'更新','mes:set-hazwaste:update',3,3,34390,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 10:48:10',1,'2026-09-10 10:48:10',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34394,'删除','mes:set-hazwaste:delete',3,4,34390,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 10:48:10',1,'2026-09-10 10:48:10',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34395,'门卫放行','mes:set-hazwaste:gate',3,5,34390,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 10:48:10',1,'2026-09-10 10:48:10',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34396,'危化品档案','',2,2,34011,'chemical','ep:warning','mes/safetyEnv/chemical/index','MesChemical',0,b'0',b'0',b'0',1,'2026-09-10 11:00:33',1,'2026-09-10 19:45:04',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34397,'查询','mes:set-chemical:query',3,1,34396,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 11:00:33',1,'2026-09-10 11:00:33',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34398,'创建','mes:set-chemical:create',3,2,34396,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 11:00:33',1,'2026-09-10 11:00:33',b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES (34399,'更新','mes:set-chemical:update',3,3,34396,'','','','',0,b'0',b'0',b'0',1,'2026-09-10 11:00:33',1,'2026-09-10 11:00:33',b'0');

-- ③ 叶子归位 / 名称与组件对齐
UPDATE system_menu SET name='污染判定', parent_id=34010, sort=1, component='mes/safetyEnv/pollutionCheck/index', updater='sql', update_time=NOW() WHERE id=34301;
UPDATE system_menu SET name='排污许可证', parent_id=34010, sort=5, component='mes/safetyEnv/pollutionPermit/index', updater='sql', update_time=NOW() WHERE id=34350;
UPDATE system_menu SET name='环保检测报告', parent_id=34014, sort=2, component='mes/safetyEnv/envReport/index', updater='sql', update_time=NOW() WHERE id=34400;
UPDATE system_menu SET name='查询', parent_id=34400, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34401;
UPDATE system_menu SET name='新增', parent_id=34400, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34402;
UPDATE system_menu SET name='修改', parent_id=34400, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34403;
UPDATE system_menu SET name='删除', parent_id=34400, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34404;
UPDATE system_menu SET name='排放口', parent_id=34012, sort=1, component='mes/safetyEnv/emissionOutlet/index', updater='sql', update_time=NOW() WHERE id=34410;
UPDATE system_menu SET name='查询', parent_id=34410, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34411;
UPDATE system_menu SET name='新增', parent_id=34410, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34412;
UPDATE system_menu SET name='修改', parent_id=34410, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34413;
UPDATE system_menu SET name='删除', parent_id=34410, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34414;
UPDATE system_menu SET name='废气监测记录', parent_id=34012, sort=2, component='mes/safetyEnv/exhaustGas/index', updater='sql', update_time=NOW() WHERE id=34420;
UPDATE system_menu SET name='查询', parent_id=34420, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34421;
UPDATE system_menu SET name='新增', parent_id=34420, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34422;
UPDATE system_menu SET name='修改', parent_id=34420, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34423;
UPDATE system_menu SET name='删除', parent_id=34420, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34424;
UPDATE system_menu SET name='废水监测记录', parent_id=34012, sort=3, component='mes/safetyEnv/wastewater/index', updater='sql', update_time=NOW() WHERE id=34430;
UPDATE system_menu SET name='查询', parent_id=34430, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34431;
UPDATE system_menu SET name='新增', parent_id=34430, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34432;
UPDATE system_menu SET name='修改', parent_id=34430, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34433;
UPDATE system_menu SET name='删除', parent_id=34430, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34434;
UPDATE system_menu SET name='碳排放核算', parent_id=34012, sort=4, component='mes/safetyEnv/carbonEmission/index', updater='sql', update_time=NOW() WHERE id=34440;
UPDATE system_menu SET name='查询', parent_id=34440, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34441;
UPDATE system_menu SET name='新增', parent_id=34440, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34442;
UPDATE system_menu SET name='修改', parent_id=34440, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34443;
UPDATE system_menu SET name='删除', parent_id=34440, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34444;
UPDATE system_menu SET name='附件查看', parent_id=34370, sort=10, component='', updater='sql', update_time=NOW() WHERE id=34450;
UPDATE system_menu SET name='附件维护', parent_id=34370, sort=11, component='', updater='sql', update_time=NOW() WHERE id=34451;
UPDATE system_menu SET name='查询', parent_id=34500, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34501;
UPDATE system_menu SET name='新增', parent_id=34500, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34502;
UPDATE system_menu SET name='修改', parent_id=34500, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34503;
UPDATE system_menu SET name='删除', parent_id=34500, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34504;
UPDATE system_menu SET name='发布·备案·修订', parent_id=34500, sort=5, component=NULL, updater='sql', update_time=NOW() WHERE id=34505;
UPDATE system_menu SET name='查询', parent_id=34510, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34511;
UPDATE system_menu SET name='新增', parent_id=34510, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34512;
UPDATE system_menu SET name='修改', parent_id=34510, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34513;
UPDATE system_menu SET name='删除', parent_id=34510, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34514;
UPDATE system_menu SET name='查询', parent_id=34520, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34521;
UPDATE system_menu SET name='新增', parent_id=34520, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34522;
UPDATE system_menu SET name='修改', parent_id=34520, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34523;
UPDATE system_menu SET name='删除', parent_id=34520, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34524;
UPDATE system_menu SET name='完成·闭环', parent_id=34520, sort=5, component=NULL, updater='sql', update_time=NOW() WHERE id=34525;
UPDATE system_menu SET name='查询', parent_id=34530, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34531;
UPDATE system_menu SET name='上报', parent_id=34530, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34532;
UPDATE system_menu SET name='修改', parent_id=34530, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34533;
UPDATE system_menu SET name='删除', parent_id=34530, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34534;
UPDATE system_menu SET name='派发·处置·闭环', parent_id=34530, sort=5, component=NULL, updater='sql', update_time=NOW() WHERE id=34535;
UPDATE system_menu SET name='粉尘检测记录', parent_id=34013, sort=1, component='mes/safetyEnv/dustRecord/index', updater='sql', update_time=NOW() WHERE id=34600;
UPDATE system_menu SET name='查询', parent_id=34600, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34601;
UPDATE system_menu SET name='新增', parent_id=34600, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34602;
UPDATE system_menu SET name='修改', parent_id=34600, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34603;
UPDATE system_menu SET name='删除', parent_id=34600, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34604;
UPDATE system_menu SET name='噪声检测记录', parent_id=34013, sort=2, component='mes/safetyEnv/noiseRecord/index', updater='sql', update_time=NOW() WHERE id=34610;
UPDATE system_menu SET name='查询', parent_id=34610, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34611;
UPDATE system_menu SET name='新增', parent_id=34610, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34612;
UPDATE system_menu SET name='修改', parent_id=34610, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34613;
UPDATE system_menu SET name='删除', parent_id=34610, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34614;
UPDATE system_menu SET name='气体检测记录', parent_id=34013, sort=3, component='mes/safetyEnv/gasRecord/index', updater='sql', update_time=NOW() WHERE id=34620;
UPDATE system_menu SET name='查询', parent_id=34620, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34621;
UPDATE system_menu SET name='新增', parent_id=34620, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34622;
UPDATE system_menu SET name='修改', parent_id=34620, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34623;
UPDATE system_menu SET name='删除', parent_id=34620, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34624;
UPDATE system_menu SET name='电气安全检查', parent_id=34013, sort=4, component='mes/safetyEnv/electricalRecord/index', updater='sql', update_time=NOW() WHERE id=34630;
UPDATE system_menu SET name='查询', parent_id=34630, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34631;
UPDATE system_menu SET name='新增', parent_id=34630, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34632;
UPDATE system_menu SET name='修改', parent_id=34630, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34633;
UPDATE system_menu SET name='删除', parent_id=34630, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34634;
UPDATE system_menu SET name='消防检查记录', parent_id=34013, sort=5, component='mes/safetyEnv/fireCheck/index', updater='sql', update_time=NOW() WHERE id=34640;
UPDATE system_menu SET name='查询', parent_id=34640, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34641;
UPDATE system_menu SET name='新增', parent_id=34640, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34642;
UPDATE system_menu SET name='修改', parent_id=34640, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34643;
UPDATE system_menu SET name='删除', parent_id=34640, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34644;
UPDATE system_menu SET name='劳保用品检查', parent_id=34013, sort=6, component='mes/safetyEnv/ppeCheck/index', updater='sql', update_time=NOW() WHERE id=34650;
UPDATE system_menu SET name='查询', parent_id=34650, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34651;
UPDATE system_menu SET name='新增', parent_id=34650, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34652;
UPDATE system_menu SET name='修改', parent_id=34650, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34653;
UPDATE system_menu SET name='删除', parent_id=34650, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34654;
UPDATE system_menu SET name='压力容器检查', parent_id=34013, sort=7, component='mes/safetyEnv/pressureVessel/index', updater='sql', update_time=NOW() WHERE id=34660;
UPDATE system_menu SET name='查询', parent_id=34660, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34661;
UPDATE system_menu SET name='新增', parent_id=34660, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34662;
UPDATE system_menu SET name='修改', parent_id=34660, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34663;
UPDATE system_menu SET name='删除', parent_id=34660, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34664;
UPDATE system_menu SET name='职业危害检测', parent_id=34013, sort=8, component='mes/safetyEnv/occupationalHazard/index', updater='sql', update_time=NOW() WHERE id=34670;
UPDATE system_menu SET name='查询', parent_id=34670, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34671;
UPDATE system_menu SET name='新增', parent_id=34670, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34672;
UPDATE system_menu SET name='修改', parent_id=34670, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34673;
UPDATE system_menu SET name='删除', parent_id=34670, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34674;
UPDATE system_menu SET name='检测计划', parent_id=34013, sort=10, component='mes/safetyEnv/plan/index', updater='sql', update_time=NOW() WHERE id=34680;
UPDATE system_menu SET name='查询', parent_id=34680, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34681;
UPDATE system_menu SET name='新增', parent_id=34680, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34682;
UPDATE system_menu SET name='修改', parent_id=34680, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34683;
UPDATE system_menu SET name='删除', parent_id=34680, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34684;
UPDATE system_menu SET name='检测标准', parent_id=34013, sort=11, component='mes/safetyEnv/standard/index', updater='sql', update_time=NOW() WHERE id=34690;
UPDATE system_menu SET name='查询', parent_id=34690, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34691;
UPDATE system_menu SET name='新增', parent_id=34690, sort=2, component=NULL, updater='sql', update_time=NOW() WHERE id=34692;
UPDATE system_menu SET name='修改', parent_id=34690, sort=3, component=NULL, updater='sql', update_time=NOW() WHERE id=34693;
UPDATE system_menu SET name='删除', parent_id=34690, sort=4, component=NULL, updater='sql', update_time=NOW() WHERE id=34694;
UPDATE system_menu SET name='查询', parent_id=34700, sort=1, component=NULL, updater='sql', update_time=NOW() WHERE id=34701;

-- ④ 重建这 7 个角色在 34000~34999 的授权
DELETE FROM system_role_menu WHERE role_id IN (60185,60190,60192,60196,60466,60467,60468) AND menu_id BETWEEN 34000 AND 34999;
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
 (60185, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34010, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34011, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34012, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34013, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34014, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34015, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34301, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34302, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34303, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34311, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34312, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34313, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34314, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34315, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34350, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34360, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34361, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34362, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34363, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34370, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34371, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34380, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34381, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34390, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34391, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34392, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34393, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34394, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34395, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34396, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34397, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34398, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34399, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34400, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34401, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34402, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34403, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34404, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34410, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34411, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34412, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34413, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34414, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34420, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34421, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34422, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34423, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34424, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34430, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34431, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34432, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34433, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34434, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34440, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34441, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34442, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34443, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34444, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34450, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34451, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34460, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34461, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34462, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34463, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34464, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34465, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34466, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34467, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34468, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34500, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34501, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34502, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34503, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34504, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34505, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34510, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34511, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34512, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34513, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34514, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34520, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34521, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34522, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34523, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34524, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34525, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34530, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34531, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34532, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34533, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34534, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34535, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34540, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34600, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34601, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34602, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34603, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34604, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34610, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34611, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34612, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34613, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34614, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34620, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34621, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34622, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34623, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34624, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34630, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34631, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34632, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34633, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34634, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34640, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34641, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34642, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34643, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34644, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34650, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34651, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34652, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34653, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34654, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34660, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34661, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34662, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34663, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34664, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34670, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34671, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34672, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34673, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34674, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34680, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34681, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34682, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34683, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34684, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34690, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34691, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34692, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34693, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34694, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34700, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34701, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34710, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34711, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34712, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34713, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60185, 34714, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34010, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34301, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34303, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34311, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34312, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34010, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34301, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34303, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34311, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34313, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34370, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34371, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34010, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34301, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34303, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34311, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34313, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34370, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34371, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34010, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34012, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34013, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34014, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34015, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34301, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34302, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34303, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34311, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34370, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34371, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34380, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34381, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34382, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34400, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34401, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34402, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34403, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34410, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34411, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34412, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34413, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34420, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34421, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34422, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34423, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34430, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34431, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34432, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34433, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34440, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34441, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34442, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34443, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34463, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34464, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34465, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34466, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34467, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34468, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34500, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34501, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34502, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34503, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34505, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34510, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34511, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34512, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34513, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34520, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34521, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34522, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34523, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34525, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34530, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34531, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34532, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34533, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34535, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34540, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34600, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34601, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34602, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34603, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34610, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34611, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34612, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34613, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34620, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34621, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34622, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34623, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34630, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34631, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34632, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34633, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34640, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34641, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34642, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34643, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34650, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34651, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34652, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34653, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34660, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34661, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34662, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34663, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34670, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34671, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34672, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34673, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34680, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34681, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34682, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34683, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34690, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34691, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34692, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34693, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34700, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34701, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34710, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34711, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34712, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34713, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60466, 34714, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60467, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60467, 34011, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60467, 34390, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60467, 34391, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60467, 34395, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60468, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60468, 34011, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60468, 34396, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60468, 34397, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60468, 34461, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60468, 34462, 'sql', NOW(), 'sql', NOW(), 0, 2010);

-- 校验：各角色环保菜单数（应与原机器一致）
SELECT role_id, COUNT(*) AS cnt FROM system_role_menu WHERE role_id IN (60185,60190,60192,60196,60466,60467,60468) AND menu_id BETWEEN 34000 AND 34999 AND deleted=0 GROUP BY role_id;

-- 校验：仍挂在不存在父节点下的菜单（应为空）
SELECT m.id, m.name, m.parent_id FROM system_menu m LEFT JOIN system_menu p ON p.id = m.parent_id AND p.deleted = 0 WHERE m.deleted = 0 AND m.parent_id <> 0 AND p.id IS NULL;

-- ⑤ 修正本次新插入行的 bit 列
-- 原机器 dump 里 visible/keep_alive/always_show 这几个 bit(1) 列是「零长度空值」，
-- 本机的 MyBatis 映射出来是 false，而前端 generate-menus.ts 是 `hideInMenu: !menu.visible`
-- —— 照抄 dump 会让新菜单全部被隐藏。故按本项目其它菜单脚本的约定统一置 b'1'。
UPDATE system_menu
SET visible = b'1', keep_alive = b'1', always_show = b'1', updater = 'sql', update_time = NOW()
WHERE id IN (34010,34011,34012,34013,34014,34015,
             34302,34303,34370,34371,34380,34381,34382,
             34390,34391,34392,34393,34394,34395,34396,34397,34398,34399);

-- 校验：范围内不应再有 visible=0 的菜单
SELECT id, name FROM system_menu
WHERE deleted = 0 AND id BETWEEN 34000 AND 34999 AND visible + 0 = 0;
