-- ============================================
-- 域：pro 生产管理
-- 用途：MES mock seed（tenant_id=1，每子模块 3-9 条）
-- 执行：dev 库 ruoyi-vue-pro（依赖 md.sql；工序/路线先行后工单/任务/报工）
-- 依赖顺序修正：本文件须在 wm.sql 之前执行（wm 成品入库/领料/消耗引用工单任务）
-- ============================================
SET FOREIGN_KEY_CHECKS=0;

-- ------------------------------------------------------------
-- 子模块 1：mes_pro_process 生产工序（7 条）
-- status：0启用 1停用
-- ------------------------------------------------------------
INSERT INTO `mes_pro_process` (`code`,`name`,`attention`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('PC-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'下料工序','按图纸尺寸切割，管端无毛刺',0,'防腐管段下料-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'坡口加工工序','坡口角度30°±2°，钝边1mm',0,'焊接前坡口-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'组对焊接工序','错边量≤1mm，焊缝成型均匀',0,'手工电弧焊打底+mock-seed',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'表面除锈工序','除锈等级Sa2.5级',0,'喷砂除锈-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'底漆涂装工序','湿膜厚120μm，无流挂',0,'环氧底漆-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'保温层包覆工序','岩棉错缝包覆、密度达标',0,'保温层-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'成品检验包装工序','逐根试压检验后封堵码放',1,'出厂检验包装-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_pro_process_content 工序步骤（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_pro_process_content` (`process_id`,`sort`,`content`,`device`,`material`,`doc_url`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),1,'核对管号与图纸尺寸',NULL,'钢管',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),2,'定尺切割并编号',NULL,'钢管',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),1,'坡口两侧各20mm打磨见金属光泽','角磨机','焊丝ER50-6',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),2,'组对点焊定位后连续施焊','埋弧焊机','焊丝ER50-6',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),1,'搅拌并熟化双组分底漆',NULL,'环氧底漆',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),2,'无气喷涂至规定膜厚','无气喷涂机','环氧底漆',NULL,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_pro_route 工艺路线（5 条）
-- status：0草稿 1启用
-- ------------------------------------------------------------
INSERT INTO `mes_pro_route` (`code`,`name`,`description`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'保温管标准生产工艺','下料→坡口→组对焊接→除锈→底漆→保温包覆→成品检验的完整防腐保温管路线',1,'mock-seed-20260908',1),
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'钢管焊接短管工艺','下料→坡口→焊接→检验',0,'mock-seed-20260908',1),
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'单管涂装返修工艺','除锈→底漆→检验',0,'mock-seed-20260908',1),
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'法兰连接管段试制工艺','下料→组焊法兰→检验（试制）',1,'新产品试制用，待量产固化',1),
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'成品出厂包装工艺','成品检验→封堵→码放发运',1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 4：mes_pro_route_process 路线-工序（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_pro_route_process` (`route_id`,`process_id`,`sort`,`next_process_id`,`link_type`,`prepare_time`,`wait_time`,`color_code`,`key_flag`,`check_flag`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),1,(SELECT id FROM mes_pro_process WHERE name LIKE '坡口加工工序%' ORDER BY id LIMIT 1),3,10,5,'#409EFF',b'0',b'0','mock-seed-20260908',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '坡口加工工序%' ORDER BY id LIMIT 1),2,(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),3,5,5,'#67C23A',b'0',b'0','mock-seed-20260908',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),3,(SELECT id FROM mes_pro_process WHERE name LIKE '表面除锈工序%' ORDER BY id LIMIT 1),3,15,30,'#E6A23C',b'1',b'1','关键焊接工序含检验-mock-seed',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '表面除锈工序%' ORDER BY id LIMIT 1),4,(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),3,10,30,'#F56C6C',b'0',b'0','mock-seed-20260908',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),5,(SELECT id FROM mes_pro_process WHERE name LIKE '保温层包覆工序%' ORDER BY id LIMIT 1),3,5,60,'#909399',b'1',b'0','mock-seed-20260908',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '保温层包覆工序%' ORDER BY id LIMIT 1),6,(SELECT id FROM mes_pro_process WHERE name LIKE '成品检验包装工序%' ORDER BY id LIMIT 1),3,5,60,'#00AEF3',b'0',b'1','成品检验节点-mock-seed',1);

-- ------------------------------------------------------------
-- 子模块 5：mes_pro_route_product 路线产品（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_pro_route_product` (`route_id`,`item_id`,`quantity`,`production_time`,`time_unit_type`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),100,ROUND(RAND()*200+100,2),'MINUTE','每批100米成品管-mock-seed-20260908',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '钢管焊接短管工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),200,ROUND(RAND()*60+30,2),'MINUTE','mock-seed-20260908',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '单管涂装返修工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),300,ROUND(RAND()*60+20,2),'MINUTE','mock-seed-20260908',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '成品出厂包装工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),500,ROUND(RAND()*40+10,2),'MINUTE','mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 6：mes_pro_route_product_bom 路线BOM（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_pro_route_product_bom` (`route_id`,`process_id`,`product_id`,`item_id`,`quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),1.00,'每米成品管用管约1米-mock-seed',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),0.25,'每米成品管底漆用量kg-mock-seed',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '成品检验包装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),2.00,'两端法兰-mock-seed-20260908',1),
((SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '成品检验包装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='螺栓-05' ORDER BY id LIMIT 1),8.00,'每根管法兰螺栓-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 7：mes_pro_work_order 生产工单（8 条）
-- type：1自制 2外协；order_source_type：1销售订单 2计划排产
-- status：0草稿 1生产中 2已完成 3已取消
-- ------------------------------------------------------------
INSERT INTO `mes_pro_work_order` (`code`,`name`,`type`,`order_source_type`,`order_source_code`,`product_id`,`quantity`,`quantity_produced`,`quantity_changed`,`quantity_scheduled`,`client_id`,`vendor_id`,`batch_code`,`request_date`,`parent_id`,`finish_date`,`cancel_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'中石油保温管生产工单-01',1,1,CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*900+100,2),ROUND(RAND()*40,2),0.00,ROUND(RAND()*100+50,2),(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),DATE_SUB(NOW(), INTERVAL -15 DAY),0,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'中石化保温管生产工单-02',1,1,CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*800+200,2),ROUND(RAND()*200,2),0.00,ROUND(RAND()*200+100,2),(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'02'),DATE_SUB(NOW(), INTERVAL -20 DAY),0,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'万华短管生产工单-03',1,1,NULL,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*500+100,2),ROUND(RAND()*100,2),0.00,ROUND(RAND()*50+20,2),(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'03'),DATE_SUB(NOW(), INTERVAL -30 DAY),0,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'青岛港钢管防腐返修工单-04',2,2,NULL,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*300+50,2),ROUND(RAND()*300,2),0.00,ROUND(RAND()*50+20,2),(SELECT id FROM mes_md_client WHERE name LIKE '青岛%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'04'),DATE_SUB(NOW(), INTERVAL -25 DAY),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),NULL,2,'外协涂装完成-mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'延长石油保温管生产工单-05',1,1,CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*1000+100,2),ROUND(RAND()*900+50,2),0.00,ROUND(RAND()*300+100,2),(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'05'),DATE_SUB(NOW(), INTERVAL -40 DAY),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,2,'已完工入库-mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'中石油法兰管段试制工单-06',1,2,NULL,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*50+10,2),0.00,0.00,0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'06'),DATE_SUB(NOW(), INTERVAL 15 DAY),0,NULL,NULL,0,'试制待排产-mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'万华面漆返喷工单-07',2,2,NULL,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*200+50,2),0.00,0.00,0.00,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),NULL,DATE_SUB(NOW(), INTERVAL -10 DAY),0,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),3,'客户取消-mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'中石化保温管追加工单-08',1,1,NULL,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*600+100,2),0.00,ROUND(RAND()*30,2),ROUND(RAND()*50+10,2),(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'08'),DATE_SUB(NOW(), INTERVAL 20 DAY),0,NULL,NULL,0,'计划调整未执行-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 8：mes_pro_work_order_bom 工单BOM（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_pro_work_order_bom` (`work_order_id`,`item_id`,`quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),100.00,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),25.00,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),200.00,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),200.00,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='聚氨酯面漆-07' ORDER BY id LIMIT 1),60.00,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),150.00,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 9：mes_pro_task 生产任务（8 条，引用已铺工单/工序/工位/路线）
-- status：0未开始 1执行中 2已完成 5已取消
-- ------------------------------------------------------------
INSERT INTO `mes_pro_task` (`code`,`name`,`work_order_id`,`workstation_id`,`route_id`,`process_id`,`item_id`,`quantity`,`produced_quantity`,`qualify_quantity`,`unqualify_quantity`,`changed_quantity`,`client_id`,`start_time`,`duration`,`end_time`,`color_code`,`finish_date`,`cancel_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'保温管-01 下料任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),100.00,ROUND(RAND()*80+10,2),ROUND(RAND()*70+10,2),ROUND(RAND()*5,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+5) DAY),3,DATE_SUB(NOW(), INTERVAL -5 DAY),'#409EFF',NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'保温管-01 焊接任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),100.00,ROUND(RAND()*60+5,2),ROUND(RAND()*50+5,2),ROUND(RAND()*4,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+2) DAY),2,DATE_SUB(NOW(), INTERVAL 3 DAY),'#E6A23C',NULL,NULL,1,'关键焊接任务-mock-seed',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'保温管-01 涂装任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),100.00,ROUND(RAND()*30,2),ROUND(RAND()*25,2),ROUND(RAND()*3,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5+1) DAY),1,DATE_SUB(NOW(), INTERVAL 1 DAY),'#909399',NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'保温管-02 下料任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),200.00,ROUND(RAND()*200,2),ROUND(RAND()*180,2),ROUND(RAND()*8,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*12+8) DAY),4,DATE_SUB(NOW(), INTERVAL -2 DAY),'#00AEF3',DATE_SUB(NOW(), INTERVAL 1 DAY),NULL,2,'已完成-mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'保温管-02 焊接任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),200.00,0.00,0.00,0.00,0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL -1 DAY),3,NULL,'#FF6B6B',NULL,NULL,0,'待开工-mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'钢管-03 下料任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '钢管焊接短管工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),300.00,ROUND(RAND()*150+30,2),ROUND(RAND()*140+30,2),ROUND(RAND()*5,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+10) DAY),3,DATE_SUB(NOW(), INTERVAL -5 DAY),'#4ECDC4',DATE_SUB(NOW(), INTERVAL 4 DAY),NULL,2,'已完成-mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'法兰管段试制 组焊任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '法兰管段试制工单-06%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '装配%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '法兰连接管段试制工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),30.00,0.00,0.00,0.00,0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),NULL,1,NULL,'#96CEB4',NULL,NULL,0,'试制未排产-mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'延长保温管 涂装任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '延长石油保温管生产工单-05%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),500.00,ROUND(RAND()*500,2),ROUND(RAND()*480,2),ROUND(RAND()*15,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+15) DAY),5,DATE_SUB(NOW(), INTERVAL -8 DAY),'#F59E0B',DATE_SUB(NOW(), INTERVAL 6 DAY),NULL,5,'工单取消任务同步取消-mock-seed',1);

-- ------------------------------------------------------------
-- 子模块 10：mes_pro_task_issue 任务投料（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_pro_task_issue` (`task_id`,`work_order_id`,`workstation_id`,`source_doc_type`,`source_doc_id`,`source_line_id`,`source_doc_code`,`batch_code`,`item_id`,`unit_measure_id`,`issued_quantity`,`available_quantity`,`used_quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),'ISSUE',1001,1,CONCAT('ISS-',DATE_FORMAT(NOW(),'%y%m%d'),'001'),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_unit_measure WHERE name='米' ORDER BY id LIMIT 1),120.00,60.00,60.00,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 涂装任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),'ISSUE',1002,1,CONCAT('ISS-',DATE_FORMAT(NOW(),'%y%m%d'),'002'),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_unit_measure WHERE name='千克' ORDER BY id LIMIT 1),40.00,20.00,20.00,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_task WHERE name LIKE '保温管-02 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),'ISSUE',1003,1,CONCAT('ISS-',DATE_FORMAT(NOW(),'%y%m%d'),'003'),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'02'),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_unit_measure WHERE name='米' ORDER BY id LIMIT 1),220.00,0.00,220.00,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_task WHERE name LIKE '保温管-02 焊接任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),'ISSUE',1004,1,NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'02'),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_unit_measure WHERE name='米' ORDER BY id LIMIT 1),150.00,150.00,0.00,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_task WHERE name LIKE '钢管-03 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),'ISSUE',1005,1,NULL,NULL,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_unit_measure WHERE name='米' ORDER BY id LIMIT 1),300.00,10.00,290.00,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 11：mes_pro_feedback 生产报工（6 条）
-- type：1数量报工 2批量报工；status：0草稿 1已提交 2审批通过 3待检验 4已完成
-- ------------------------------------------------------------
INSERT INTO `mes_pro_feedback` (`code`,`type`,`channel`,`feedback_time`,`workstation_id`,`route_id`,`process_id`,`work_order_id`,`task_id`,`item_id`,`expire_date`,`lot_number`,`scheduled_quantity`,`feedback_quantity`,`qualified_quantity`,`unqualified_quantity`,`uncheck_quantity`,`labor_scrap_quantity`,`material_scrap_quantity`,`other_scrap_quantity`,`feedback_user_id`,`approve_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),1,'PC',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),100.00,ROUND(RAND()*40+10,2),ROUND(RAND()*45+5,2),ROUND(RAND()*3,2),0.00,ROUND(RAND()*2,2),ROUND(RAND()*1,2),0.00,103,1,4,'首件数量报工-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),2,'PC',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),100.00,ROUND(RAND()*30+5,2),ROUND(RAND()*30+5,2),ROUND(RAND()*2,2),0.00,0.00,0.00,0.00,103,1,4,'批量报工已完工-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),1,'PC',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 焊接任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),100.00,ROUND(RAND()*25+5,2),0.00,0.00,ROUND(RAND()*20+3,2),0.00,0.00,0.00,103,1,3,'待检验报工-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),1,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 涂装任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),100.00,ROUND(RAND()*15+2,2),ROUND(RAND()*15+2,2),0.00,0.00,0.00,0.00,0.00,1,1,2,'审批中-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),1,'APP',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '钢管焊接短管工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '钢管-03 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'03'),300.00,ROUND(RAND()*100+10,2),ROUND(RAND()*100+8,2),ROUND(RAND()*5,2),0.00,ROUND(RAND()*2,2),0.00,0.00,107,1,4,'APP端报工已完成-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),1,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+1) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-02 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'02'),200.00,ROUND(RAND()*80+5,2),ROUND(RAND()*80+3,2),ROUND(RAND()*2,2),0.00,0.00,0.00,0.00,1,NULL,1,'已提交待审批-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 12：mes_pro_card 生产流转卡（5 条）
-- status：0流转中 1已完成 2挂起
-- ------------------------------------------------------------
INSERT INTO `mes_pro_card` (`code`,`work_order_id`,`item_id`,`batch_code`,`transfered_quantity`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),ROUND(RAND()*60+10,2),0,'首张流转卡-mock-seed-20260908',1),
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),ROUND(RAND()*20+5,2),0,'第二张流转卡-mock-seed-20260908',1),
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'02'),ROUND(RAND()*150+50,2),1,'已完工流转卡-mock-seed-20260908',1),
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'03'),ROUND(RAND()*200+100,2),2,'挂起待处理-mock-seed-20260908',1),
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '延长石油保温管生产工单-05%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'05'),ROUND(RAND()*400+100,2),1,'已完工-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 13：mes_pro_card_process 流转卡工序记录（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_pro_card_process` (`card_id`,`sort`,`process_id`,`input_time`,`output_time`,`input_quantity`,`output_quantity`,`unqualified_quantity`,`workstation_id`,`user_id`,`ipqc_id`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_pro_card WHERE batch_code LIKE '%01' ORDER BY id LIMIT 1),1,(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+5) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),50.00,49.00,1.00,(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),103,NULL,'下料完成-mock-seed-20260908',1),
((SELECT id FROM mes_pro_card WHERE batch_code LIKE '%01' ORDER BY id LIMIT 1),2,(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),49.00,47.00,2.00,(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),103,NULL,'焊接完成-mock-seed-20260908',1),
((SELECT id FROM mes_pro_card WHERE batch_code LIKE '%01' ORDER BY id LIMIT 1),3,(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*3) DAY),NULL,47.00,NULL,NULL,(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),1,NULL,'涂装进行中-mock-seed-20260908',1),
((SELECT id FROM mes_pro_card WHERE batch_code LIKE '%03' ORDER BY id LIMIT 1),1,(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+10) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+5) DAY),120.00,120.00,0.00,(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),1,NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_card WHERE batch_code LIKE '%05' ORDER BY id LIMIT 1),1,(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30+10) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*25+5) DAY),200.00,198.00,2.00,(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),1,NULL,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 14：mes_pro_andon_config 安灯呼叫配置（4 条）
-- level：1紧急 2一般 3提示
-- ------------------------------------------------------------
INSERT INTO `mes_pro_andon_config` (`reason`,`level`,`handler_role_id`,`handler_user_id`,`remark`,`tenant_id`) VALUES
('设备故障，需要维修',1,NULL,1,'mock-seed-20260908',1),
('物料不足，需要补料',2,NULL,1,'mock-seed-20260908',1),
('质量异常，需要确认',2,NULL,103,'mock-seed-20260908',1),
('人员不足，需要支援',3,NULL,107,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 15：mes_pro_andon_record 安灯呼叫记录（5 条）
-- status：0待响应 1处理中 2已完成 3已关闭
-- ------------------------------------------------------------
INSERT INTO `mes_pro_andon_record` (`config_id`,`workstation_id`,`user_id`,`work_order_id`,`process_id`,`reason`,`level`,`status`,`handle_time`,`handler_user_id`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '设备故障%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),103,(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),'设备故障，需要维修',1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,'焊机电流不稳已维修',1),
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '物料不足%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),107,(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),'物料不足，需要补料',2,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*2) DAY),'底漆补料中',1),
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '质量异常%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),1,NULL,NULL,'质量异常，需要确认',2,1,NULL,NULL,NULL,'正在处置中',1),
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '质量异常%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),1,(SELECT id FROM mes_pro_work_order WHERE name LIKE '延长石油保温管生产工单-05%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '成品检验包装工序%' ORDER BY id LIMIT 1),'质量异常，需要确认',2,2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,'保温层破损已处理',1),
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '人员不足%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '装配%' ORDER BY id LIMIT 1),107,NULL,NULL,'人员不足，需要支援',3,0,NULL,NULL,NULL,'待响应',1);

-- ------------------------------------------------------------
-- 子模块 16：mes_pro_work_record 上下工快照（3 条，uk_user_id 唯一）
-- type：1上工 2下工
-- ------------------------------------------------------------
INSERT INTO `mes_pro_work_record` (`user_id`,`workstation_id`,`type`,`clock_in_time`,`clock_out_time`,`remark`,`tenant_id`) VALUES
(1,(SELECT id FROM mes_md_workstation WHERE name LIKE '检测%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5+1) DAY),NULL,'admin在线-mock-seed-20260908',1),
(103,(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5+1) DAY),NULL,'mock-seed-20260908',1),
(107,(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5+1) DAY),NULL,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 17：mes_pro_work_record_log 上下工流水（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_pro_work_record_log` (`user_id`,`workstation_id`,`type`,`remark`,`tenant_id`) VALUES
(103,(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),1,'mock-seed-20260908',1),
(103,(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),2,'mock-seed-20260908',1),
(107,(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),1,'mock-seed-20260908',1),
(107,(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),2,'mock-seed-20260908',1),
(1,(SELECT id FROM mes_md_workstation WHERE name LIKE '检测%' ORDER BY id LIMIT 1),1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 可选：mes_pro_project 项目（表为后续版本新增，dump 无建表证据，已在 DO 定义）
-- 若 dev 存在 mes_pro_project 表，取消下面注释执行：
-- INSERT INTO `mes_pro_project` (`code`,`name`,`order_source_code`,`source_type`,`status`,`priority`,`owner`,`dept`,`plan_start_time`,`plan_end_time`,`actual_start_time`,`actual_end_time`,`duration_days`,`remark`,`tenant_id`) VALUES ...;
-- ------------------------------------------------------------

SET FOREIGN_KEY_CHECKS=1;
