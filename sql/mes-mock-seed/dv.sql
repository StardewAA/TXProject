-- ============================================
-- 域：dv 设备管理
-- 用途：MES mock seed（tenant_id=1，每子模块 3-9 条）
-- 执行：dev 库 ruoyi-vue-pro（依赖 md.sql：车间/工作站已铺）
-- 说明：mes_md_workstation_machine 原属 md 控制器，因依赖 dv_machinery 并入本文件
--       check/mainten/repair 的 line 明细表未铺（有菜单需求可在 rollback 后追加）
-- ============================================
SET FOREIGN_KEY_CHECKS=0;

-- ------------------------------------------------------------
-- 子模块 1：mes_dv_machinery_type 设备类型（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_dv_machinery_type` (`code`,`name`,`parent_id`,`status`,`sort`,`remark`,`tenant_id`) VALUES
(CONCAT('DT-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'焊接设备',0,0,1,'mock-seed-20260908',1),
(CONCAT('DT-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'防腐喷涂设备',0,0,2,'mock-seed-20260908',1),
(CONCAT('DT-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'保温生产线设备',0,0,3,'mock-seed-20260908',1),
(CONCAT('DT-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'检测设备',0,0,4,'mock-seed-20260908',1),
(CONCAT('DT-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'起重运输设备',0,0,5,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_dv_subject 点检保养项目（6 条）
-- type：1点检项目 2保养项目
-- ------------------------------------------------------------
INSERT INTO `mes_dv_subject` (`code`,`name`,`type`,`content`,`standard`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('SUB-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'焊机外观检查',1,'检查焊机外壳、电缆有无破损漏电','外观完好无破损',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'喷枪喷嘴检查',1,'检查喷嘴磨损、堵塞情况','喷嘴无堵塞磨损量达标',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'空压机油位检查',1,'检查空压机油位是否在上下限之间','油位处于上下刻度之间',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'传动链润滑保养',2,'对各传动链条加油润滑，调整张紧度','链条润滑充分无卡滞',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'喷涂泵密封件更换',2,'检查并更换喷涂泵易损密封件','无渗漏无滴漆',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'电气柜除尘紧固',2,'清洁电控柜、紧固接线端子','柜内无积尘、端子紧固',0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_dv_machinery 设备台账（6 条）
-- status：1运行 2闲置 3维修中
-- ------------------------------------------------------------
INSERT INTO `mes_dv_machinery` (`code`,`name`,`brand`,`specification`,`machinery_type_id`,`workshop_id`,`status`,`last_mainten_time`,`last_check_time`,`remark`,`tenant_id`) VALUES
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'埋弧焊机-01','林肯','DC-1000',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '焊接设备%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'氩弧焊机-02','松下','YC-400TX',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '焊接设备%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'无气喷涂机-03','固瑞克','XTREME',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '防腐喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'自动缠绕机-04','国产','CR-800',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '保温生产线%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '金工%' ORDER BY id LIMIT 1),2,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'水压试验机-05','国产','SY-10',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '检测设备%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '成品%' ORDER BY id LIMIT 1),1,NULL,NULL,'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'桥式起重机-06','德马格','DEMAG-5t',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '起重%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '成品%' ORDER BY id LIMIT 1),3,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),NULL,'钢丝绳更换检修中-mock-seed',1);

-- ------------------------------------------------------------
-- 子模块 4：mes_dv_check_plan 点检保养方案（5 条）
-- type：1点检 2保养；cycle_type：1日 2周 3月 4季度
-- ------------------------------------------------------------
INSERT INTO `mes_dv_check_plan` (`code`,`name`,`type`,`start_date`,`end_date`,`cycle_type`,`cycle_count`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('CP-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90)+10,2,'0')),'焊接设备日点检方案-01',1,DATE_SUB(NOW(), INTERVAL 30 DAY),DATE_SUB(NOW(), INTERVAL -335 DAY),1,1,0,'mock-seed-20260908',1),
(CONCAT('CP-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90)+10,2,'0')),'喷涂设备周保养方案-02',2,DATE_SUB(NOW(), INTERVAL 30 DAY),DATE_SUB(NOW(), INTERVAL -335 DAY),2,1,0,'mock-seed-20260908',1),
(CONCAT('CP-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90)+10,2,'0')),'空压机月度保养方案-03',2,DATE_SUB(NOW(), INTERVAL 60 DAY),DATE_SUB(NOW(), INTERVAL -305 DAY),3,1,1,'mock-seed-20260908',1),
(CONCAT('CP-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90)+10,2,'0')),'检测设备周点检方案-04',1,DATE_SUB(NOW(), INTERVAL 15 DAY),DATE_SUB(NOW(), INTERVAL -350 DAY),2,1,0,'mock-seed-20260908',1),
(CONCAT('CP-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90)+10,2,'0')),'起重设备季度保养方案-05',2,DATE_SUB(NOW(), INTERVAL 90 DAY),DATE_SUB(NOW(), INTERVAL -275 DAY),4,1,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 5：mes_dv_check_plan_machinery 方案关联设备（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_dv_check_plan_machinery` (`plan_id`,`machinery_id`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '埋弧焊机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '氩弧焊机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '无气喷涂机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '周点检方案-04' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '水压试验机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '季度保养方案-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '桥式起重机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 6：mes_dv_check_plan_subject 方案关联项目（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_dv_check_plan_subject` (`plan_id`,`subject_id`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '焊机外观检查%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '空压机油位%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '喷枪喷嘴%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '喷涂泵密封件%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '季度保养方案-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '电气柜除尘%' ORDER BY id LIMIT 1),'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 7：mes_dv_check_record 点检记录（5 条）
-- status：10待执行 20已完成
-- ------------------------------------------------------------
INSERT INTO `mes_dv_check_record` (`plan_id`,`machinery_id`,`check_time`,`user_id`,`status`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '埋弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,20,'埋弧焊机日常点检正常',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '氩弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,20,'氩弧焊机电缆老化已登记',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '周点检方案-04' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '水压试验机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,10,'待执行点检任务',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '无气喷涂机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,20,'喷涂机压力波动检查完成',1),
(NULL,(SELECT id FROM mes_dv_machinery WHERE name LIKE '桥式起重机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,20,'临时点检：钢丝绳磨损在限',1);

-- ------------------------------------------------------------
-- 子模块 8：mes_dv_mainten_record 保养记录（5 条）
-- status：0待执行 1执行中 4草稿 5完成（业务值按代码字典，多用0/4便于演示）
-- ------------------------------------------------------------
INSERT INTO `mes_dv_mainten_record` (`plan_id`,`machinery_id`,`mainten_time`,`user_id`,`status`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '无气喷涂机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,0,'无气喷涂机密封件更换保养',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '月度保养方案-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '自动缠绕机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,0,'缠绕机传动链润滑保养',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '季度保养方案-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '桥式起重机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,4,'起重机季度保养（草稿）',1),
(NULL,(SELECT id FROM mes_dv_machinery WHERE name LIKE '埋弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,4,'焊机临时保养（无计划草稿）',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '月度保养方案-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '水压试验机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,0,'试验机液压系统保养',1);

-- ------------------------------------------------------------
-- 子模块 9：mes_dv_repair 维修工单（6 条）
-- status：0待派工 1待维修 2维修中 3待验收 4已完成；result：1合格 0不合格
-- ------------------------------------------------------------
INSERT INTO `mes_dv_repair` (`code`,`name`,`machinery_id`,`require_date`,`finish_date`,`confirm_date`,`result`,`accepted_user_id`,`confirm_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'埋弧焊机送丝机构卡阻维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '埋弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,1,1,4,'更换送丝轮及压紧弹簧，验收合格',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'无气喷涂机压力波动维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '无气喷涂机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,1,NULL,1,'检查蓄能器充压，待确认',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'自动缠绕机变频器报警维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '自动缠绕机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,1,NULL,2,'维修中：排查变频器母线电容',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'水压试验机保压不足维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '水压试验机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,1,1,4,'更换高压密封圈，验收合格',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'氩弧焊机高频引弧故障维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '氩弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,NULL,NULL,0,'待派工',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'桥式起重机限位失灵维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '桥式起重机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,NULL,NULL,0,'待派工：现场已停机挂牌',1);

-- ------------------------------------------------------------
-- 子模块 10：mes_md_workstation_machine 工位绑定设备（5 条，依赖 md 工位+dv 设备）
-- ------------------------------------------------------------
INSERT INTO `mes_md_workstation_machine` (`workstation_id`,`machinery_id`,`quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '埋弧焊机%' ORDER BY id LIMIT 1),ROUND(RAND()*2+1,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '氩弧焊机%' ORDER BY id LIMIT 1),ROUND(RAND()*2+1,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '无气喷涂机%' ORDER BY id LIMIT 1),ROUND(RAND()*2+1,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '自动缠绕机%' ORDER BY id LIMIT 1),ROUND(RAND()*2+1,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '检测%' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '水压试验机%' ORDER BY id LIMIT 1),ROUND(RAND()*2+1,0),'mock-seed-20260908',1);

SET FOREIGN_KEY_CHECKS=1;
