-- ============================================
-- 域：tm 工具管理
-- 用途：MES mock seed（tenant_id=1，每子模块 3-9 条）
-- 执行：dev 库 ruoyi-vue-pro（依赖 md.sql 工作站；workstation_tool 归入本文件）
-- ============================================
SET FOREIGN_KEY_CHECKS=0;

-- ------------------------------------------------------------
-- 子模块 1：mes_tm_tool_type 工具类型（4 条）
-- code_flag：是否编码管理；mainten_type：1按日期 2按次数
-- ------------------------------------------------------------
INSERT INTO `mes_tm_tool_type` (`code`,`name`,`code_flag`,`mainten_type`,`mainten_period`,`remark`,`tenant_id`) VALUES
(CONCAT('TT-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'焊枪焊把',b'1',1,180,'mock-seed-20260908',1),
(CONCAT('TT-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'喷涂枪具',b'1',1,90,'mock-seed-20260908',1),
(CONCAT('TT-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'量检具',b'1',1,365,'mock-seed-20260908',1),
(CONCAT('TT-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'吊装索具',b'0',2,5000,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_tm_tool 工具台账（6 条）
-- status：1可用 2已领用 3送修 4报废
-- ------------------------------------------------------------
INSERT INTO `mes_tm_tool` (`code`,`name`,`brand`,`specification`,`tool_type_id`,`quantity`,`available_quantity`,`mainten_type`,`next_mainten_period`,`next_mainten_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'CO2焊枪-01','林肯','CBT-500',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '焊枪焊把%' ORDER BY id LIMIT 1),ROUND(RAND()*20+10,0),ROUND(RAND()*10+5,0),1,NULL,DATE_SUB(NOW(), INTERVAL -60+FLOOR(RAND()*30) DAY),1,'mock-seed-20260908',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'TIG焊枪-02','松下','YC-200TS',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '焊枪焊把%' ORDER BY id LIMIT 1),ROUND(RAND()*20+10,0),ROUND(RAND()*10+5,0),2,2000,NULL,1,'mock-seed-20260908',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'无气喷枪-03','固瑞克','GG-291',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '喷涂枪具%' ORDER BY id LIMIT 1),ROUND(RAND()*10+4,0),ROUND(RAND()*5+1,0),1,NULL,DATE_SUB(NOW(), INTERVAL -30+FLOOR(RAND()*20) DAY),2,'已领用至喷涂车间',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'游标卡尺-04','三丰','500-196',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '量检具%' ORDER BY id LIMIT 1),ROUND(RAND()*30+10,0),ROUND(RAND()*15+5,0),1,NULL,DATE_SUB(NOW(), INTERVAL -200+FLOOR(RAND()*50) DAY),3,'送检校准中',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'超声波测厚仪-05','奥林巴斯','38DL',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '量检具%' ORDER BY id LIMIT 1),2,ROUND(RAND()*2,0),1,NULL,DATE_SUB(NOW(), INTERVAL -300+FLOOR(RAND()*60) DAY),1,'mock-seed-20260908',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'吊装钢丝绳-06','国产','D6*19-20',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '吊装索具%' ORDER BY id LIMIT 1),8,8,2,10000,NULL,4,'使用次数到期报废',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_md_workstation_tool 工位绑定工具类型（4 条，依赖 md 工位+tm 工具类型）
-- ------------------------------------------------------------
INSERT INTO `mes_md_workstation_tool` (`workstation_id`,`tool_type_id`,`quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_tm_tool_type WHERE name LIKE '焊枪焊把%' ORDER BY id LIMIT 1),ROUND(RAND()*5+2,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_tm_tool_type WHERE name LIKE '喷涂枪具%' ORDER BY id LIMIT 1),ROUND(RAND()*4+2,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '检测%' ORDER BY id LIMIT 1),(SELECT id FROM mes_tm_tool_type WHERE name LIKE '量检具%' ORDER BY id LIMIT 1),ROUND(RAND()*6+3,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),(SELECT id FROM mes_tm_tool_type WHERE name LIKE '吊装索具%' ORDER BY id LIMIT 1),ROUND(RAND()*4+1,0),'mock-seed-20260908',1);

SET FOREIGN_KEY_CHECKS=1;
