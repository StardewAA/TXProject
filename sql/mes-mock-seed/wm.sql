-- ============================================
-- 域：wm 仓库管理
-- 用途：MES mock seed（tenant_id=1，每子模块 3-9 条）
-- 执行：dev 库 ruoyi-vue-pro
-- 依赖：需先执行 md.sql + pro.sql（物料消耗/成品入库等引用工单任务）
-- 随机规则：code 前缀+打散+0补齐；时间过去90天；外键随机
-- ============================================
SET FOREIGN_KEY_CHECKS=0;

-- ------------------------------------------------------------
-- 子模块 1：mes_wm_warehouse 仓库档案（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_warehouse` (`code`,`name`,`address`,`area`,`charge_user_id`,`frozen`,`remark`,`tenant_id`) VALUES
(CONCAT('WH-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'主仓库-01','厂区一号库',ROUND(RAND()*5000+1000,2),NULL,b'0','mock-seed-20260908',1),
(CONCAT('WH-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'原料仓库-02','厂区二号库',ROUND(RAND()*5000+1000,2),NULL,b'0','mock-seed-20260908',1),
(CONCAT('WH-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'成品仓库-03','厂区三号库',ROUND(RAND()*5000+1000,2),NULL,b'0','mock-seed-20260908',1),
(CONCAT('WH-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'危化品临时仓-04','厂区北侧独立库',ROUND(RAND()*1000+200,2),NULL,b'1','mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_wm_batch 批次（6 条，对应 md 6 种物料各 1 批）
-- quality_status：0待检 1合格 2不合格
-- ------------------------------------------------------------
INSERT INTO `mes_wm_batch` (`code`,`item_id`,`produce_date`,`expire_date`,`receipt_date`,`vendor_id`,`client_id`,`work_order_id`,`task_id`,`lot_number`,`quality_status`,`remark`,`tenant_id`) VALUES
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL -180+FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),1,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),1,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),(SELECT id FROM mes_md_item WHERE name='弯头-03' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),0,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),(SELECT id FROM mes_md_item WHERE name='螺栓-05' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),1,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL -90+FLOOR(RAND()*30) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),1,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*45) DAY),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*45) DAY),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_wm_material_stock 物料库存（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_material_stock` (`item_type_id`,`item_id`,`batch_id`,`batch_code`,`warehouse_id`,`location_id`,`area_id`,`vendor_id`,`quantity`,`receipt_time`,`frozen`,`tenant_id`) VALUES
((SELECT item_type_id FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 0)),(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 0),(SELECT id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 0),(SELECT code FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 0),(SELECT id FROM mes_wm_warehouse WHERE name LIKE '原料仓库%' ORDER BY id LIMIT 1),NULL,NULL,NULL,ROUND(RAND()*800+50,2),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),b'0',1),
((SELECT item_type_id FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 1)),(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 1),(SELECT id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 1),(SELECT code FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 1),(SELECT id FROM mes_wm_warehouse WHERE name LIKE '原料仓库%' ORDER BY id LIMIT 1),NULL,NULL,NULL,ROUND(RAND()*800+50,2),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),b'0',1),
((SELECT item_type_id FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 2)),(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 2),(SELECT id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 2),(SELECT code FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 2),(SELECT id FROM mes_wm_warehouse WHERE name LIKE '原料仓库%' ORDER BY id LIMIT 1),NULL,NULL,NULL,ROUND(RAND()*500+20,2),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),b'0',1),
((SELECT item_type_id FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 3)),(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 3),(SELECT id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 3),(SELECT code FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 3),(SELECT id FROM mes_wm_warehouse WHERE name LIKE '主仓库%' ORDER BY id LIMIT 1),NULL,NULL,NULL,ROUND(RAND()*800+50,2),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),b'0',1),
((SELECT item_type_id FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 4)),(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 4),(SELECT id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 4),(SELECT code FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 4),(SELECT id FROM mes_wm_warehouse WHERE name LIKE '危化品%' ORDER BY id LIMIT 1),NULL,NULL,NULL,ROUND(RAND()*200+10,2),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),b'0',1),
((SELECT item_type_id FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 5)),(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 5),(SELECT id FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 5),(SELECT code FROM mes_wm_batch ORDER BY id LIMIT 1 OFFSET 5),(SELECT id FROM mes_wm_warehouse WHERE name LIKE '成品仓库%' ORDER BY id LIMIT 1),NULL,NULL,NULL,ROUND(RAND()*400+30,2),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*45) DAY),b'0',1);

-- ------------------------------------------------------------
-- 子模块 4：mes_wm_arrival_notice 到货通知（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_arrival_notice` (`code`,`name`,`purchase_order_code`,`vendor_id`,`arrival_date`,`contact_name`,`contact_telephone`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'钢管到货通知-01',CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'张师傅',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),0,'mock-seed-20260908',1),
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'法兰到货通知-02',CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'李师傅',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),1,'mock-seed-20260908',1),
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'底漆到货通知-03',CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'王师傅',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),2,'mock-seed-20260908',1),
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'弯头到货通知-04',CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'螺栓到货通知-05',CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),NULL,NULL,1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 5：mes_wm_item_receipt 采购入库单（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_item_receipt` (`code`,`name`,`iqc_id`,`notice_id`,`vendor_id`,`purchase_order_code`,`receipt_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('IR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'钢管采购入库-01',NULL,(SELECT id FROM mes_wm_arrival_notice WHERE name LIKE '钢管%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1),
(CONCAT('IR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'法兰采购入库-02',NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),1,'mock-seed-20260908',1),
(CONCAT('IR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'环氧底漆采购入库-03',NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),2,'mock-seed-20260908',1),
(CONCAT('IR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'螺栓采购入库-04',NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 6：mes_wm_product_receipt 成品入库单（4 条，依赖 pro 工单）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_product_receipt` (`code`,`name`,`work_order_id`,`item_id`,`receipt_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('PR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'保温管成品入库-01',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('PR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'保温管成品入库-02',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,'mock-seed-20260908',1),
(CONCAT('PR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'钢管半成品入库-03',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),2,'mock-seed-20260908',1),
(CONCAT('PR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'钢管半成品入库-04',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 7：mes_wm_outsource_receipt 外协入库单（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_outsource_receipt` (`code`,`name`,`work_order_id`,`vendor_id`,`receipt_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('OR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'外协热处理入库-01',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('OR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'外协喷砂入库-02',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,'mock-seed-20260908',1),
(CONCAT('OR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'外协法兰加工入库-03',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('OR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'外协表面处理入库-04',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 8：mes_wm_misc_receipt 杂项入库（4 条）
-- type：1盘盈入库 2其他入库
-- ------------------------------------------------------------
INSERT INTO `mes_wm_misc_receipt` (`code`,`name`,`type`,`source_doc_type`,`source_doc_id`,`source_doc_code`,`receipt_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('MR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'盘盈入库-01',1,2,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1),
(CONCAT('MR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'样管退库-02',2,1,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),1,'mock-seed-20260908',1),
(CONCAT('MR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'工具回收入库-03',2,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1),
(CONCAT('MR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'报废残值入库-04',2,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 9：mes_wm_product_issue 生产领料单（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_product_issue` (`code`,`name`,`workstation_id`,`work_order_id`,`task_id`,`issue_date`,`required_time`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'焊接工位领料-01',(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL -2 DAY),0,'mock-seed-20260908',1),
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'喷涂工位领料-02',(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL -1 DAY),1,'mock-seed-20260908',1),
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'装配工位领料-03',(SELECT id FROM mes_md_workstation WHERE name LIKE '装配%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL -3 DAY),2,'mock-seed-20260908',1),
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'包装工位领料-04',(SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,0,'mock-seed-20260908',1),
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'打磨工位领料-05',(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 10：mes_wm_misc_issue 杂项出库（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_misc_issue` (`code`,`name`,`type`,`source_doc_type`,`source_doc_id`,`source_doc_code`,`issue_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('MI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'报废品出库-01',1,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('MI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'赠样出库-02',2,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,'mock-seed-20260908',1),
(CONCAT('MI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'研发领用出库-03',3,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('MI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'办公领用出库-04',3,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 11：mes_wm_outsource_issue 外协领料（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_outsource_issue` (`code`,`name`,`vendor_id`,`work_order_id`,`issue_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('OI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'外协热处理发料-01',(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('OI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'外协喷砂发料-02',(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,'mock-seed-20260908',1),
(CONCAT('OI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'外协法兰加工发料-03',(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('OI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'外协涂装发料-04',(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 12：mes_wm_return_issue 生产退料（4 条）
-- type：1退料 2换料
-- ------------------------------------------------------------
INSERT INTO `mes_wm_return_issue` (`code`,`name`,`work_order_id`,`workstation_id`,`type`,`return_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('RI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'余料退库-01',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),0,'mock-seed-20260908',1),
(CONCAT('RI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'不良品退料-02',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '装配%' ORDER BY id LIMIT 1),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,'mock-seed-20260908',1),
(CONCAT('RI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'换料退料-03',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),0,'mock-seed-20260908',1),
(CONCAT('RI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'过期物料退库-04',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 13：mes_wm_return_sales 销售退货（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_return_sales` (`code`,`name`,`sales_order_code`,`client_id`,`return_date`,`return_reason`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('RS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'销售退货-01',CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'到货碰伤',0,'mock-seed-20260908',1),
(CONCAT('RS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'销售退货-02',CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'尺寸不符',1,'mock-seed-20260908',1),
(CONCAT('RS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'销售退货-03',CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'用户误下单',0,'mock-seed-20260908',1),
(CONCAT('RS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'销售退货-04',CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'运输损耗',0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 14：mes_wm_return_vendor 采购退货（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_return_vendor` (`code`,`name`,`purchase_order_code`,`vendor_id`,`return_date`,`return_reason`,`transport_code`,`transport_telephone`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('RV-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'采购退货-01',CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'壁厚不达标',CONCAT('TR-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),NULL,0,'mock-seed-20260908',1),
(CONCAT('RV-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'采购退货-02',CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'法兰孔径偏差',NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('RV-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'采购退货-03',CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'油漆过期',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('RV-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'采购退货-04',CONCAT('PO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'弯头椭圆度超差',NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 15：mes_wm_product_sales 成品销售出库（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_product_sales` (`code`,`name`,`client_id`,`sales_order_code`,`sales_date`,`notice_id`,`contact_name`,`contact_telephone`,`contact_address`,`carrier`,`shipping_number`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'保温管销售出库-01',(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,'马工',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),'北京顺义库',CONCAT('CAR-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),NULL,0,'mock-seed-20260908',1),
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'保温管销售出库-02',(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,NULL,NULL,NULL,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'钢管销售出库-03',(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,NULL,NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'钢管销售出库-04',(SELECT id FROM mes_md_client WHERE name LIKE '青岛%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,NULL,NULL,NULL,NULL,NULL,2,'mock-seed-20260908',1),
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'法兰销售出库-05',(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,NULL,NULL,NULL,NULL,NULL,1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 16：mes_wm_sales_notice 销售通知（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_sales_notice` (`code`,`name`,`sales_order_code`,`client_id`,`sales_date`,`recipient_name`,`recipient_telephone`,`recipient_address`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'中石油销售发货通知-01',CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'刘收',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),'北京市顺义区仓储中心',0,'mock-seed-20260908',1),
(CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'中石化销售发货通知-02',CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'万华销售发货通知-03',CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'延长销售发货通知-04',CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,2,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 17：mes_wm_stock_taking_plan 盘点计划（4 条）
-- type：1全盘 2抽盘 3循环盘点
-- ------------------------------------------------------------
INSERT INTO `mes_wm_stock_taking_plan` (`code`,`name`,`type`,`start_time`,`end_time`,`blind_flag`,`frozen`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('STK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'原料仓库月度盘点-01',1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),DATE_SUB(NOW(), INTERVAL -5 DAY),b'1',b'0',0,'mock-seed-20260908',1),
(CONCAT('STK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'成品仓库抽盘-02',2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),DATE_SUB(NOW(), INTERVAL -3 DAY),b'0',b'0',1,'mock-seed-20260908',1),
(CONCAT('STK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'主仓库循环盘点-03',3,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),DATE_SUB(NOW(), INTERVAL -2 DAY),b'0',b'0',2,'mock-seed-20260908',1),
(CONCAT('STK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'危化品专项盘点-04',1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,b'1',b'1',0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 18：mes_wm_transfer 移库调拨（5 条）
-- type：1内部移库 2调拨；confirm_flag：1已确认
-- ------------------------------------------------------------
INSERT INTO `mes_wm_transfer` (`code`,`name`,`type`,`delivery_flag`,`recipient_name`,`recipient_telephone`,`destination_address`,`carrier`,`shipping_number`,`confirm_flag`,`transfer_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'原料仓至线边库移库-01',1,b'0',NULL,NULL,NULL,NULL,NULL,b'1',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),0,'mock-seed-20260908',1),
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'成品仓至发货区移库-02',1,b'0',NULL,NULL,NULL,NULL,NULL,b'1',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),1,'mock-seed-20260908',1),
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'分公司仓库调拨-03',2,b'0','陈收',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),'天津分公司仓库',CONCAT('CAR-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),CONCAT('SH-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),b'0',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),0,'mock-seed-20260908',1),
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'主仓库至危化仓调拨-04',1,b'1',NULL,NULL,NULL,NULL,NULL,b'1',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),0,'mock-seed-20260908',1),
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'成品仓库调拨备货-05',2,b'0',NULL,NULL,NULL,NULL,NULL,b'0',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),2,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 19：mes_wm_sn 序列号（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_sn` (`uuid`,`code`,`item_id`,`batch_code`,`work_order_id`,`tenant_id`) VALUES
(UUID(),CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900000)+100000,6,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),1),
(UUID(),CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900000)+100000,6,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),1),
(UUID(),CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900000)+100000,6,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),1),
(UUID(),CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900000)+100000,6,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),1);

-- ------------------------------------------------------------
-- 子模块 20：mes_wm_barcode_config 条码配置（4 条）
-- biz_type：1物料 2批次 3托盘 4单据
-- ------------------------------------------------------------
INSERT INTO `mes_wm_barcode_config` (`format`,`biz_type`,`content_format`,`content_example`,`auto_generate_flag`,`default_template`,`status`,`remark`,`tenant_id`) VALUES
('CODE128',1,CONCAT('IT',DATE_FORMAT(NOW(),'%y%m%d'),'-####'),'IT260901-0001',b'1',NULL,0,'mock-seed-20260908',1),
('CODE128',2,'PL-####','PL-0001',b'1',NULL,0,'mock-seed-20260908',1),
('QR_CODE',3,'WH-####','WH-0001',b'0',NULL,1,'mock-seed-20260908',1),
('CODE128',4,'SN-####','SN-0001',b'1',NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 21：mes_wm_barcode 条码实例（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_barcode` (`config_id`,`format`,`biz_type`,`content`,`biz_id`,`biz_code`,`biz_name`,`status`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=1 ORDER BY id LIMIT 1),'CODE128',1,CONCAT('IT',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_md_item ORDER BY id LIMIT 1),(SELECT code FROM mes_md_item ORDER BY id LIMIT 1),(SELECT name FROM mes_md_item ORDER BY id LIMIT 1),0,'mock-seed-20260908',1),
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=2 ORDER BY id LIMIT 1),'CODE128',2,CONCAT('PL-',LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_wm_batch ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch ORDER BY id LIMIT 1),(SELECT name FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1)),0,'mock-seed-20260908',1),
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=3 ORDER BY id LIMIT 1),'QR_CODE',3,CONCAT('WH-',LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_wm_warehouse ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_warehouse ORDER BY id LIMIT 1),(SELECT name FROM mes_wm_warehouse ORDER BY id LIMIT 1),0,'mock-seed-20260908',1),
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=4 ORDER BY id LIMIT 1),'CODE128',4,CONCAT('SN-',LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_wm_sn ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_sn ORDER BY id LIMIT 1),(SELECT name FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_sn ORDER BY id LIMIT 1)),1,'mock-seed-20260908',1),
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=1 ORDER BY id LIMIT 1),'CODE128',1,CONCAT('IT',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_md_item ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item ORDER BY RAND() LIMIT 1),(SELECT name FROM mes_md_item ORDER BY RAND() LIMIT 1),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 22：mes_wm_item_consume 物料消耗（5 条，依赖 pro 工单任务报工）
-- 列全部 NOT NULL：work_order_id/task_id/workstation_id/process_id/feedback_id/consume_date
-- ------------------------------------------------------------
INSERT INTO `mes_wm_item_consume` (`work_order_id`,`task_id`,`workstation_id`,`process_id`,`feedback_id`,`consume_date`,`status`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_process ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_feedback ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),0,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_process ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_feedback ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),0,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_process ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_feedback ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),1,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_process ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_feedback ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),1,'mock-seed-20260908',1),
((SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_process ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_feedback ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 23：mes_wm_package 包装（4 条）
-- parent_id：0 顶层包装
-- ------------------------------------------------------------
INSERT INTO `mes_wm_package` (`code`,`parent_id`,`package_date`,`sales_order_code`,`invoice_code`,`client_id`,`length`,`width`,`height`,`net_weight`,`gross_weight`,`inspector_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('PKG-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),ROUND(RAND()*100+200,1),ROUND(RAND()*40+80,1),ROUND(RAND()*40+80,1),ROUND(RAND()*500+100,2),ROUND(RAND()*520+110,2),NULL,0,'mock-seed-20260908',1),
(CONCAT('PKG-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),ROUND(RAND()*100+200,1),ROUND(RAND()*40+80,1),ROUND(RAND()*40+80,1),ROUND(RAND()*500+100,2),ROUND(RAND()*520+110,2),NULL,1,'mock-seed-20260908',1),
(CONCAT('PKG-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),ROUND(RAND()*100+200,1),ROUND(RAND()*40+80,1),ROUND(RAND()*40+80,1),ROUND(RAND()*500+100,2),ROUND(RAND()*520+110,2),NULL,2,'mock-seed-20260908',1),
(CONCAT('PKG-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*900)+100,3,'0')),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),ROUND(RAND()*100+200,1),ROUND(RAND()*40+80,1),ROUND(RAND()*40+80,1),ROUND(RAND()*500+100,2),ROUND(RAND()*520+110,2),NULL,0,'mock-seed-20260908',1);

SET FOREIGN_KEY_CHECKS=1;
