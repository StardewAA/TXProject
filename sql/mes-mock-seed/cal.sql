-- ============================================
-- 域：cal 排班管理
-- 用途：MES mock seed（tenant_id=1，每子模块 3-9 条）
-- 执行：dev 库 ruoyi-vue-pro（独立域，无跨文件强依赖）
-- 说明：班组/排班计划/班次/组员/排班明细/假期；
--       shift_type：1单班 2两班 3三班；status 0草稿 1启用
-- ============================================
SET FOREIGN_KEY_CHECKS=0;

-- ------------------------------------------------------------
-- 子模块 1：mes_cal_plan 排班计划（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_cal_plan` (`code`,`name`,`calendar_type`,`start_date`,`end_date`,`shift_type`,`shift_method`,`shift_count`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'下料车间九月三班倒计划-01',2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*40+30) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*6) DAY),3,3,1,1,'下料车间连续生产三班倒-mock-seed-20260908',1),
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'焊接车间九月两班倒计划-02',1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*40+25) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*6) DAY),2,3,1,1,'焊接车间两班倒-mock-seed-20260908',1),
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'喷涂车间九月单班计划-03',2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*35+20) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,4,1,1,'喷涂车间单白班-mock-seed-20260908',1),
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'打磨车间九月两班倒计划-04',1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30+20) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*4) DAY),2,3,1,1,'打磨车间两班倒-mock-seed-20260908',1),
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'下料车间十月两班倒计划-05',1,DATE_ADD(NOW(), INTERVAL FLOOR(RAND()*10+5) DAY),DATE_ADD(NOW(), INTERVAL FLOOR(RAND()*20+15) DAY),2,3,1,0,'下月排班草稿-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_cal_team 班组（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_cal_team` (`code`,`name`,`calendar_type`,`remark`,`tenant_id`) VALUES
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'下料甲班',2,'负责下料/切割-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'下料乙班',1,'负责下料/切割-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'焊接甲班',1,'负责组对焊接-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'焊接乙班',2,'负责组对焊接-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'喷涂班',2,'负责防腐涂装-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'打磨班',1,'负责表面处理-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_cal_plan_shift 计划班次（8 条）
-- 白班/中班/夜班按计划铺排
-- ------------------------------------------------------------
INSERT INTO `mes_cal_plan_shift` (`plan_id`,`sort`,`name`,`start_time`,`end_time`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1),1,'白班','08:00','16:00','早班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1),2,'中班','16:00','00:00','中班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1),3,'夜班','00:00','08:00','夜班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='焊接车间九月两班倒计划-02' ORDER BY id LIMIT 1),1,'白班','08:00','20:00','白班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='焊接车间九月两班倒计划-02' ORDER BY id LIMIT 1),2,'夜班','20:00','08:00','夜班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='喷涂车间九月单班计划-03' ORDER BY id LIMIT 1),1,'白班','08:00','17:00','单班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='打磨车间九月两班倒计划-04' ORDER BY id LIMIT 1),1,'白班','08:00','20:00','白班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='打磨车间九月两班倒计划-04' ORDER BY id LIMIT 1),2,'夜班','20:00','08:00','夜班-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 4：mes_cal_plan_team 计划-班组关联（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_cal_plan_team` (`plan_id`,`team_id`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='下料甲班' ORDER BY id LIMIT 1),'三班轮转甲班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='下料乙班' ORDER BY id LIMIT 1),'三班轮转乙班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='焊接车间九月两班倒计划-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='焊接甲班' ORDER BY id LIMIT 1),'两班倒-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='喷涂车间九月单班计划-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='喷涂班' ORDER BY id LIMIT 1),'单班-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='打磨车间九月两班倒计划-04' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='打磨班' ORDER BY id LIMIT 1),'两班倒-mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='下料车间十月两班倒计划-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='下料乙班' ORDER BY id LIMIT 1),'下月两班倒-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 5：mes_cal_team_member 班组成员（8 条）
-- user_id 取租户1启用状态的真实用户
-- ------------------------------------------------------------
INSERT INTO `mes_cal_team_member` (`team_id`,`user_id`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_cal_team WHERE name='下料甲班' ORDER BY id LIMIT 1),(SELECT id FROM system_users WHERE tenant_id=1 AND status=0 ORDER BY RAND() LIMIT 1),'班组长-mock-seed-20260908',1),
((SELECT id FROM mes_cal_team WHERE name='下料甲班' ORDER BY id LIMIT 1),(SELECT id FROM system_users WHERE tenant_id=1 AND status=0 ORDER BY RAND() LIMIT 1),'下料工-mock-seed-20260908',1),
((SELECT id FROM mes_cal_team WHERE name='下料乙班' ORDER BY id LIMIT 1),(SELECT id FROM system_users WHERE tenant_id=1 AND status=0 ORDER BY RAND() LIMIT 1),'班组长-mock-seed-20260908',1),
((SELECT id FROM mes_cal_team WHERE name='下料乙班' ORDER BY id LIMIT 1),(SELECT id FROM system_users WHERE tenant_id=1 AND status=0 ORDER BY RAND() LIMIT 1),'下料工-mock-seed-20260908',1),
((SELECT id FROM mes_cal_team WHERE name='焊接甲班' ORDER BY id LIMIT 1),(SELECT id FROM system_users WHERE tenant_id=1 AND status=0 ORDER BY RAND() LIMIT 1),'焊工-mock-seed-20260908',1),
((SELECT id FROM mes_cal_team WHERE name='焊接乙班' ORDER BY id LIMIT 1),(SELECT id FROM system_users WHERE tenant_id=1 AND status=0 ORDER BY RAND() LIMIT 1),'焊工-mock-seed-20260908',1),
((SELECT id FROM mes_cal_team WHERE name='喷涂班' ORDER BY id LIMIT 1),(SELECT id FROM system_users WHERE tenant_id=1 AND status=0 ORDER BY RAND() LIMIT 1),'喷漆工-mock-seed-20260908',1),
((SELECT id FROM mes_cal_team WHERE name='打磨班' ORDER BY id LIMIT 1),(SELECT id FROM system_users WHERE tenant_id=1 AND status=0 ORDER BY RAND() LIMIT 1),'打磨工-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 6：mes_cal_team_shift 班组排班明细（7 条）
-- ------------------------------------------------------------
INSERT INTO `mes_cal_team_shift` (`plan_id`,`team_id`,`shift_id`,`day`,`sort`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='下料甲班' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_plan_shift WHERE plan_id=(SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1) AND name='白班' ORDER BY id LIMIT 1),DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*9+2) DAY),1,'mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='下料乙班' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_plan_shift WHERE plan_id=(SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1) AND name='中班' ORDER BY id LIMIT 1),DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*9+2) DAY),2,'mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='下料甲班' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_plan_shift WHERE plan_id=(SELECT id FROM mes_cal_plan WHERE name='下料车间九月三班倒计划-01' ORDER BY id LIMIT 1) AND name='夜班' ORDER BY id LIMIT 1),DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*8+1) DAY),3,'mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='焊接车间九月两班倒计划-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='焊接甲班' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_plan_shift WHERE plan_id=(SELECT id FROM mes_cal_plan WHERE name='焊接车间九月两班倒计划-02' ORDER BY id LIMIT 1) AND name='白班' ORDER BY id LIMIT 1),DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*9+2) DAY),1,'mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='焊接车间九月两班倒计划-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='焊接甲班' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_plan_shift WHERE plan_id=(SELECT id FROM mes_cal_plan WHERE name='焊接车间九月两班倒计划-02' ORDER BY id LIMIT 1) AND name='夜班' ORDER BY id LIMIT 1),DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*8+1) DAY),2,'mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='喷涂车间九月单班计划-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='喷涂班' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_plan_shift WHERE plan_id=(SELECT id FROM mes_cal_plan WHERE name='喷涂车间九月单班计划-03' ORDER BY id LIMIT 1) AND name='白班' ORDER BY id LIMIT 1),DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*8+2) DAY),1,'mock-seed-20260908',1),
((SELECT id FROM mes_cal_plan WHERE name='打磨车间九月两班倒计划-04' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_team WHERE name='打磨班' ORDER BY id LIMIT 1),(SELECT id FROM mes_cal_plan_shift WHERE plan_id=(SELECT id FROM mes_cal_plan WHERE name='打磨车间九月两班倒计划-04' ORDER BY id LIMIT 1) AND name='白班' ORDER BY id LIMIT 1),DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*7+2) DAY),1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 7：mes_cal_holiday 假期设置（6 条）
-- type：2 休息日
-- ------------------------------------------------------------
INSERT INTO `mes_cal_holiday` (`day`,`type`,`remark`,`tenant_id`) VALUES
(DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*45+3) DAY),2,'周末休息-mock-seed-20260908',1),
(DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*40+3) DAY),2,'设备检修日-mock-seed-20260908',1),
(DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*35+2) DAY),2,'公共假期-mock-seed-20260908',1),
(DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*30+2) DAY),2,'调休日-mock-seed-20260908',1),
(DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*25+1) DAY),2,'生产线停机保养-mock-seed-20260908',1),
(DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*15+1) DAY),2,'高温停产日-mock-seed-20260908',1);

SET FOREIGN_KEY_CHECKS=1;
