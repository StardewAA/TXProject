-- ============================================
-- 域：qc 质量管理
-- 用途：MES mock seed（tenant_id=1，每子模块 3-9 条）
-- 执行：dev 库 ruoyi-vue-pro（依赖 md.sql/pro.sql/wm.sql 先行铺基础数据）
-- 说明：模板/指标先行，检验单头→行→缺陷记录/结果明细层层引用本文件自铺数据；
--       status 0草稿 4已完成；check_result 1合格 2不合格；qc_type 1IQC 2IPQC 3OQC 4RQC
-- ============================================
SET FOREIGN_KEY_CHECKS=0;

-- ------------------------------------------------------------
-- 子模块 1：mes_qc_template 质检方案（6 条）
-- types：1 IQC 2 IPQC 3 OQC 4 RQC（逗号分隔）
-- ------------------------------------------------------------
INSERT INTO `mes_qc_template` (`code`,`name`,`types`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'防腐钢管来料检验方案','1',0,'钢管/管件入厂通用检验-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'保温管过程巡检方案','2',0,'下料/焊接/涂装过程巡检-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'预制保温管出厂检验方案','3',0,'成品出厂 OQC-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'保温管退货检验方案','4',0,'退料/退货复检 RQC-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'涂装材料全流程检验方案','1,2,3,4',0,'涂料及成品全流程覆盖-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'法兰螺栓来料抽检方案','1',0,'管件类抽检-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_qc_indicator 质检指标（8 条）
-- type：1数值 2定性；result_type：1数值 2计数 3文本 4布尔 5附件
-- ------------------------------------------------------------
INSERT INTO `mes_qc_indicator` (`code`,`name`,`type`,`tool`,`result_type`,`result_specification`,`remark`,`tenant_id`) VALUES
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'外径尺寸',1,'游标卡尺',1,NULL,'DN100±0.5mm-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'壁厚',1,'超声波测厚仪',1,NULL,'≥5.6mm 实测-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'焊缝外观',2,'目视/放大镜',5,'IMG','无裂纹气孔咬边-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'涂层外观',2,'目视',5,'IMG','无流挂/气泡/露底-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'涂层厚度',1,'涂层测厚仪',1,NULL,'干膜≥80μm-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'附着力',1,'划格器',1,NULL,'≤1级-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'数量清点',2,NULL,2,NULL,'与单据一致-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'合格证核对',2,NULL,3,NULL,'炉批号/质保书一致-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_qc_template_indicator 方案-指标（7 条）
-- ------------------------------------------------------------
INSERT INTO `mes_qc_template_indicator` (`template_id`,`indicator_id`,`check_method`,`standard_value`,`unit_measure_id`,`threshold_max`,`threshold_min`,`doc_url`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='外径尺寸' ORDER BY id LIMIT 1),'游标卡尺测量外径，记录实测值',114.0000,(SELECT id FROM mes_md_unit_measure WHERE name='毫米' ORDER BY id LIMIT 1),0.5000,-0.5000,NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='壁厚' ORDER BY id LIMIT 1),'超声测厚，取3点最小值',6.0000,(SELECT id FROM mes_md_unit_measure WHERE name='毫米' ORDER BY id LIMIT 1),0.5000,-0.4000,NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='焊缝外观' ORDER BY id LIMIT 1),'目视+5倍放大镜检查焊缝',NULL,NULL,NULL,NULL,NULL,'关键焊缝逐道检查-mock-seed',1),
((SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层厚度' ORDER BY id LIMIT 1),'干膜测厚仪测两端及中部',100.0000,(SELECT id FROM mes_md_unit_measure WHERE name='微米' ORDER BY id LIMIT 1),30.0000,-20.0000,NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层外观' ORDER BY id LIMIT 1),'目视抽检表面质量',NULL,NULL,NULL,NULL,NULL,'出厂全数外观-mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='附着力' ORDER BY id LIMIT 1),'划格法检测附着力等级',1.0000,(SELECT id FROM mes_md_unit_measure WHERE name='级' ORDER BY id LIMIT 1),NULL,NULL,NULL,'≤1级-mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='涂装材料全流程检验方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='合格证核对' ORDER BY id LIMIT 1),'核对合格证/质保书与炉批号',NULL,NULL,NULL,NULL,NULL,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 4：mes_qc_template_item 方案-产品（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_qc_template_item` (`template_id`,`item_id`,`quantity_check`,`quantity_unqualified`,`critical_rate`,`major_rate`,`minor_rate`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),5,1,0.00,5.00,20.00,'DN100钢管-mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),3,1,0.00,5.00,15.00,'法兰-mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),5,0,0.00,3.00,10.00,'巡检对象-mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),5,0,0.00,3.00,10.00,'成品-mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),3,0,0.00,5.00,20.00,'退货复检-mock-seed-20260908',1),
((SELECT id FROM mes_qc_template WHERE name='法兰螺栓来料抽检方案' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='螺栓-05' ORDER BY id LIMIT 1),8,1,0.00,5.00,10.00,'紧固件抽检-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 5：mes_qc_iqc 来料检验单（6 条）
-- status 0草稿 4已完成；check_result 1合格 2不合格 空未判
-- ------------------------------------------------------------
INSERT INTO `mes_qc_iqc` (`code`,`name`,`template_id`,`source_doc_type`,`source_doc_id`,`source_line_id`,`source_doc_code`,`vendor_id`,`vendor_batch`,`item_id`,`received_quantity`,`check_quantity`,`qualified_quantity`,`unqualified_quantity`,`critical_rate`,`major_rate`,`minor_rate`,`critical_quantity`,`major_quantity`,`minor_quantity`,`check_result`,`receive_date`,`inspect_date`,`inspector_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'钢管来料检验单-01',(SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),100,(SELECT id FROM mes_wm_arrival_notice WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,CONCAT('AN-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*900+100,2),ROUND(RAND()*8+2,2),ROUND(RAND()*8,2),ROUND(RAND()*1,2),0.00,ROUND(RAND()*3,2),ROUND(RAND()*10+3,2),0,0,FLOOR(RAND()*2),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*45+10) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*40+5) DAY),1,4,'来料外观尺寸抽检合格-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'钢管来料检验单-02',(SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*800+200,2),ROUND(RAND()*5+1,2),ROUND(RAND()*4+1,2),0.00,0.00,0.00,0.00,0,0,0,2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*40+5) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*35+3) DAY),104,4,'壁厚不合格已判退-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'环氧底漆来料检验单-03',(SELECT id FROM mes_qc_template WHERE name='涂装材料全流程检验方案' ORDER BY id LIMIT 1),100,(SELECT id FROM mes_wm_arrival_notice WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),ROUND(RAND()*100+50,2),ROUND(RAND()*3+1,2),ROUND(RAND()*3,2),ROUND(RAND()*1,2),0.00,0.00,ROUND(RAND()*5,2),0,0,FLOOR(RAND()*2),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30+5) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*28+2) DAY),1,4,'涂料质保资料齐全-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'法兰来料检验单-04',(SELECT id FROM mes_qc_template WHERE name='法兰螺栓来料抽检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),ROUND(RAND()*500+100,2),ROUND(RAND()*5+1,2),ROUND(RAND()*5,2),ROUND(RAND()*2,2),0.00,ROUND(RAND()*8+2,2),ROUND(RAND()*10,2),0,FLOOR(RAND()*2),FLOOR(RAND()*2),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+2) DAY),NULL,NULL,0,'待检验-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'螺栓来料检验单-05',(SELECT id FROM mes_qc_template WHERE name='法兰螺栓来料抽检方案' ORDER BY id LIMIT 1),100,(SELECT id FROM mes_wm_arrival_notice WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='螺栓-05' ORDER BY id LIMIT 1),ROUND(RAND()*2000+500,2),ROUND(RAND()*8+2,2),ROUND(RAND()*8,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+1) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*12+1) DAY),104,4,'抽检合格准予入库-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'弯头来料检验单-06',(SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='弯头-03' ORDER BY id LIMIT 1),ROUND(RAND()*300+50,2),ROUND(RAND()*4+1,2),ROUND(RAND()*4,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+1) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*6+1) DAY),1,4,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 6：mes_qc_iqc_line 来料检验单行（7 条）
-- ------------------------------------------------------------
INSERT INTO `mes_qc_iqc_line` (`iqc_id`,`indicator_id`,`tool`,`check_method`,`standard_value`,`unit_measure_id`,`max_threshold`,`min_threshold`,`critical_quantity`,`major_quantity`,`minor_quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='外径尺寸' ORDER BY id LIMIT 1),'游标卡尺','测量外径三处取均值',114.0000,(SELECT id FROM mes_md_unit_measure WHERE name='毫米' ORDER BY id LIMIT 1),0.5000,-0.5000,0,0,0,'实测114.02mm合格-mock-seed-20260908',1),
((SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='壁厚' ORDER BY id LIMIT 1),'超声波测厚仪','测两端及中部三点',6.0000,(SELECT id FROM mes_md_unit_measure WHERE name='毫米' ORDER BY id LIMIT 1),0.5000,-0.4000,0,0,0,'最小5.9mm-mock-seed-20260908',1),
((SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='合格证核对' ORDER BY id LIMIT 1),NULL,'核对炉批号与质保书',NULL,NULL,NULL,NULL,0,0,0,'一致-mock-seed-20260908',1),
((SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='壁厚' ORDER BY id LIMIT 1),'超声波测厚仪','测两端及中部三点',6.0000,(SELECT id FROM mes_md_unit_measure WHERE name='毫米' ORDER BY id LIMIT 1),0.5000,-0.4000,0,1,0,'3号管实测5.1mm低于下限-mock-seed-20260908',1),
((SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='外径尺寸' ORDER BY id LIMIT 1),'游标卡尺','测量外径',114.0000,(SELECT id FROM mes_md_unit_measure WHERE name='毫米' ORDER BY id LIMIT 1),0.5000,-0.5000,0,0,0,'合格-mock-seed-20260908',1),
((SELECT id FROM mes_qc_iqc WHERE name='环氧底漆来料检验单-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='合格证核对' ORDER BY id LIMIT 1),NULL,'核对合格证批次',NULL,NULL,NULL,NULL,0,0,0,'资料齐全-mock-seed-20260908',1),
((SELECT id FROM mes_qc_iqc WHERE name='螺栓来料检验单-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='数量清点' ORDER BY id LIMIT 1),NULL,'抽样开箱清点',NULL,NULL,NULL,NULL,0,0,0,'数量与送货单一致-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 7：mes_qc_ipqc 过程检验单（6 条）
-- type：1首检 2巡检 3末检 4自检
-- ------------------------------------------------------------
INSERT INTO `mes_qc_ipqc` (`code`,`name`,`type`,`template_id`,`source_doc_type`,`source_doc_id`,`source_line_id`,`source_doc_code`,`work_order_id`,`task_id`,`workstation_id`,`process_id`,`item_id`,`check_quantity`,`qualified_quantity`,`unqualified_quantity`,`labor_scrap_quantity`,`material_scrap_quantity`,`other_scrap_quantity`,`critical_rate`,`major_rate`,`minor_rate`,`critical_quantity`,`major_quantity`,`minor_quantity`,`check_result`,`inspect_date`,`inspector_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'下料工序首检-01',1,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),304,(SELECT id FROM mes_pro_feedback WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,CONCAT('FB-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*5+1,2),ROUND(RAND()*5,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30+5) DAY),1,4,'首件合格准予批量生产-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'组焊过程巡检-02',2,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),304,(SELECT id FROM mes_pro_feedback WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 焊接任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*10+2,2),ROUND(RAND()*8+1,2),ROUND(RAND()*2,2),ROUND(RAND()*1,2),0.00,0.00,0.00,ROUND(RAND()*5,2),ROUND(RAND()*10+3,2),0,FLOOR(RAND()*2),FLOOR(RAND()*2),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*25+3) DAY),1,4,'发现气孔需补焊后复检-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'涂装工序巡检-03',4,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*8+1,2),ROUND(RAND()*8,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+2) DAY),104,4,'膜厚抽检达标-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'包装工序自检-04',3,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '延长石油保温管生产工单-05%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '成品检验包装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*6+1,2),ROUND(RAND()*6,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+1) DAY),1,4,'封堵码放规范-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'外协返修过程检验-05',2,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '青岛港钢管防腐返修工单-04%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_workstation WHERE name LIKE '检测%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '表面除锈工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*5+1,2),ROUND(RAND()*4+1,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,NULL,NULL,NULL,0,'待检验-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'万华短管末检-06',3,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_workstation WHERE name LIKE '检测%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '成品检验包装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*4+1,2),ROUND(RAND()*4,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+1) DAY),1,4,'末检合格-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 8：mes_qc_ipqc_line 过程检验单行（7 条）
-- ------------------------------------------------------------
INSERT INTO `mes_qc_ipqc_line` (`ipqc_id`,`indicator_id`,`tool`,`check_method`,`standard_value`,`unit_measure_id`,`max_threshold`,`min_threshold`,`critical_quantity`,`major_quantity`,`minor_quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_qc_ipqc WHERE name='下料工序首检-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='外径尺寸' ORDER BY id LIMIT 1),'游标卡尺','首件三根全尺寸测量',114.0000,(SELECT id FROM mes_md_unit_measure WHERE name='毫米' ORDER BY id LIMIT 1),0.5000,-0.5000,0,0,0,'首件合格-mock-seed-20260908',1),
((SELECT id FROM mes_qc_ipqc WHERE name='组焊过程巡检-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='焊缝外观' ORDER BY id LIMIT 1),NULL,'5倍放大镜逐道检查',NULL,NULL,NULL,NULL,0,1,1,'2道焊缝气孔需补焊-mock-seed-20260908',1),
((SELECT id FROM mes_qc_ipqc WHERE name='组焊过程巡检-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='合格证核对' ORDER BY id LIMIT 1),NULL,'核对母材炉批号',NULL,NULL,NULL,NULL,0,0,0,'一致-mock-seed-20260908',1),
((SELECT id FROM mes_qc_ipqc WHERE name='涂装工序巡检-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层厚度' ORDER BY id LIMIT 1),'涂层测厚仪','测两端中部',100.0000,(SELECT id FROM mes_md_unit_measure WHERE name='微米' ORDER BY id LIMIT 1),30.0000,-20.0000,0,0,0,'实测95μm-mock-seed-20260908',1),
((SELECT id FROM mes_qc_ipqc WHERE name='涂装工序巡检-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层外观' ORDER BY id LIMIT 1),NULL,'目视检查',NULL,NULL,NULL,NULL,0,0,0,'无流挂气泡-mock-seed-20260908',1),
((SELECT id FROM mes_qc_ipqc WHERE name='外协返修过程检验-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层厚度' ORDER BY id LIMIT 1),'涂层测厚仪','抽测10点',100.0000,(SELECT id FROM mes_md_unit_measure WHERE name='微米' ORDER BY id LIMIT 1),30.0000,-20.0000,0,0,0,'返修后达标-mock-seed-20260908',1),
((SELECT id FROM mes_qc_ipqc WHERE name='万华短管末检-06' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='数量清点' ORDER BY id LIMIT 1),NULL,'报工数量核对',NULL,NULL,NULL,NULL,0,0,0,'一致-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 9：mes_qc_oqc 出货检验单（5 条）
-- out_quantity 本次出货数量 NOT NULL
-- ------------------------------------------------------------
INSERT INTO `mes_qc_oqc` (`code`,`name`,`template_id`,`source_doc_type`,`source_doc_id`,`source_line_id`,`source_doc_code`,`client_id`,`batch_code`,`item_id`,`min_check_quantity`,`max_unqualified_quantity`,`out_quantity`,`check_quantity`,`qualified_quantity`,`unqualified_quantity`,`critical_rate`,`major_rate`,`minor_rate`,`critical_quantity`,`major_quantity`,`minor_quantity`,`check_result`,`out_date`,`inspect_date`,`inspector_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'中石油保温管出货检验单-01',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),118,(SELECT id FROM mes_wm_product_sales WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,CONCAT('SO-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),5,0,ROUND(RAND()*900+100,2),ROUND(RAND()*5+1,2),ROUND(RAND()*5,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*25+5) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*22+2) DAY),1,4,'成品检验合格准予发运-mock-seed-20260908',1),
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'中石化保温管出货检验单-02',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),5,0,ROUND(RAND()*800+100,2),ROUND(RAND()*5+1,2),ROUND(RAND()*4,2),ROUND(RAND()*1,2),0.00,ROUND(RAND()*3,2),ROUND(RAND()*5,2),0,0,FLOOR(RAND()*2),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+3) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*18+1) DAY),104,4,'个别露底已隔离返修-mock-seed-20260908',1),
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'万华化学钢管出货检验单-03',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),3,0,ROUND(RAND()*500+50,2),ROUND(RAND()*3+1,2),ROUND(RAND()*3,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*12+1) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+1) DAY),1,4,'mock-seed-20260908',1),
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'青岛港务短管出货检验单-04',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_client WHERE name LIKE '青岛%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),3,0,ROUND(RAND()*300+50,2),ROUND(RAND()*3+1,2),0.00,0.00,0.00,0.00,0.00,0,0,0,NULL,NULL,NULL,0,'待检验-mock-seed-20260908',1),
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'延长石油保温管出货检验单-05',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),118,(SELECT id FROM mes_wm_product_sales WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),5,0,ROUND(RAND()*600+100,2),ROUND(RAND()*5+1,2),ROUND(RAND()*5,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+1) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*6+1) DAY),1,4,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 10：mes_qc_oqc_line 出货检验单行（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_qc_oqc_line` (`oqc_id`,`indicator_id`,`tool`,`check_method`,`standard_value`,`unit_measure_id`,`max_threshold`,`min_threshold`,`critical_quantity`,`major_quantity`,`minor_quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_qc_oqc WHERE name='中石油保温管出货检验单-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层外观' ORDER BY id LIMIT 1),NULL,'逐根目视外观检查',NULL,NULL,NULL,NULL,0,0,0,'全部合格-mock-seed-20260908',1),
((SELECT id FROM mes_qc_oqc WHERE name='中石油保温管出货检验单-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='附着力' ORDER BY id LIMIT 1),'划格器','划格法测附着力',1.0000,(SELECT id FROM mes_md_unit_measure WHERE name='级' ORDER BY id LIMIT 1),NULL,NULL,0,0,0,'≤1级-mock-seed-20260908',1),
((SELECT id FROM mes_qc_oqc WHERE name='中石油保温管出货检验单-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='合格证核对' ORDER BY id LIMIT 1),NULL,'核对出厂合格证',NULL,NULL,NULL,NULL,0,0,0,'齐全-mock-seed-20260908',1),
((SELECT id FROM mes_qc_oqc WHERE name='中石化保温管出货检验单-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层外观' ORDER BY id LIMIT 1),NULL,'抽检外观',NULL,NULL,NULL,NULL,0,1,0,'2根露底已挑出-mock-seed-20260908',1),
((SELECT id FROM mes_qc_oqc WHERE name='万华化学钢管出货检验单-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='数量清点' ORDER BY id LIMIT 1),NULL,'核对装车数量',NULL,NULL,NULL,NULL,0,0,0,'与发货单一致-mock-seed-20260908',1),
((SELECT id FROM mes_qc_oqc WHERE name='延长石油保温管出货检验单-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层外观' ORDER BY id LIMIT 1),NULL,'目视抽检',NULL,NULL,NULL,NULL,0,0,0,'合格-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 11：mes_qc_rqc 退货检验单（5 条）
-- type：1生产退料 2销售退货
-- ------------------------------------------------------------
INSERT INTO `mes_qc_rqc` (`code`,`name`,`template_id`,`source_doc_type`,`source_doc_id`,`source_line_id`,`source_doc_code`,`type`,`item_id`,`batch_code`,`check_quantity`,`qualified_quantity`,`unqualified_quantity`,`critical_rate`,`major_rate`,`minor_rate`,`critical_quantity`,`major_quantity`,`minor_quantity`,`check_result`,`inspect_date`,`inspector_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'保温管退料复检单-01',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),116,(SELECT id FROM mes_wm_return_issue WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,1,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,ROUND(RAND()*40+10,2),ROUND(RAND()*40,2),ROUND(RAND()*2,2),0.00,0.00,ROUND(RAND()*3,2),0,0,FLOOR(RAND()*2),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30+5) DAY),1,4,'退料合格可再入库-mock-seed-20260908',1),
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'中石化销售退货检验单-02',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),119,(SELECT id FROM mes_wm_return_sales WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,2,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,ROUND(RAND()*30+5,2),ROUND(RAND()*25+3,2),ROUND(RAND()*3,2),0.00,ROUND(RAND()*5,2),ROUND(RAND()*8,2),0,FLOOR(RAND()*2),FLOOR(RAND()*2),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+2) DAY),104,4,'涂层破损批量退货-mock-seed-20260908',1),
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'法兰退料检验单-03',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,1,(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),NULL,ROUND(RAND()*20+5,2),ROUND(RAND()*20,2),ROUND(RAND()*1,2),0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+1) DAY),1,4,'可复用-mock-seed-20260908',1),
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'面漆退料检验单-04',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,1,(SELECT id FROM mes_md_item WHERE name='聚氨酯面漆-07' ORDER BY id LIMIT 1),NULL,ROUND(RAND()*15+3,2),ROUND(RAND()*15,2),ROUND(RAND()*2,2),0.00,ROUND(RAND()*4,2),ROUND(RAND()*6,2),0,0,FLOOR(RAND()*2),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+1) DAY),1,4,'超过保质期判废-mock-seed-20260908',1),
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'万华钢管退货待检单-05',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,2,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0,0,NULL,NULL,NULL,0,'待检验-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 12：mes_qc_rqc_line 退货检验行（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_qc_rqc_line` (`rqc_id`,`indicator_id`,`tool`,`check_method`,`standard_value`,`unit_measure_id`,`max_threshold`,`min_threshold`,`critical_quantity`,`major_quantity`,`minor_quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_qc_rqc WHERE name='保温管退料复检单-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层外观' ORDER BY id LIMIT 1),NULL,'逐根检查涂层',NULL,NULL,NULL,NULL,0,0,0,'退料完好可复用-mock-seed-20260908',1),
((SELECT id FROM mes_qc_rqc WHERE name='保温管退料复检单-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='数量清点' ORDER BY id LIMIT 1),NULL,'与退料单核对',NULL,NULL,NULL,NULL,0,0,0,'数量一致-mock-seed-20260908',1),
((SELECT id FROM mes_qc_rqc WHERE name='中石化销售退货检验单-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层外观' ORDER BY id LIMIT 1),NULL,'目视检查破损情况',NULL,NULL,NULL,NULL,0,1,1,'运输磕碰破损-mock-seed-20260908',1),
((SELECT id FROM mes_qc_rqc WHERE name='中石化销售退货检验单-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='合格证核对' ORDER BY id LIMIT 1),NULL,'核对原批次合格证',NULL,NULL,NULL,NULL,0,0,0,'可追溯-mock-seed-20260908',1),
((SELECT id FROM mes_qc_rqc WHERE name='面漆退料检验单-04' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='合格证核对' ORDER BY id LIMIT 1),NULL,'核对生产日期保质期',NULL,NULL,NULL,NULL,0,1,0,'超保质期-mock-seed-20260908',1),
((SELECT id FROM mes_qc_rqc WHERE name='法兰退料检验单-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='数量清点' ORDER BY id LIMIT 1),NULL,'清点数量',NULL,NULL,NULL,NULL,0,0,0,'与退料单一致-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 13：mes_qc_defect 缺陷类型（7 条）
-- type：1尺寸类 2外观/表面类；level：1致命 2严重 3轻微
-- ------------------------------------------------------------
INSERT INTO `mes_qc_defect` (`code`,`name`,`type`,`level`,`remark`,`tenant_id`) VALUES
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'外径尺寸超差',1,2,'超出公差±0.5mm-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'壁厚不足',1,2,'低于标准下限-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'焊缝气孔夹渣',2,2,'影响承压-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'涂层流挂露底',2,3,'外观缺陷-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'涂层附着力差',2,2,'划格剥落-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'法兰密封面损伤',2,1,'致命缺陷-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'保温层破损',2,3,'外护破损进水-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 14：mes_qc_defect_record 质检缺陷记录（6 条）
-- qc_type：1IQC 2IPQC 3OQC 4RQC；level 1致命 2严重 3轻微
-- ------------------------------------------------------------
INSERT INTO `mes_qc_defect_record` (`qc_type`,`qc_id`,`line_id`,`name`,`level`,`quantity`,`remark`,`tenant_id`) VALUES
(1,(SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_iqc_line WHERE iqc_id=(SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-02' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),'壁厚不足，3号管实测5.1mm低于下限',2,1,'已判退-mock-seed-20260908',1),
(1,(SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_iqc_line WHERE iqc_id=(SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-01' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),'外径轻微毛刺',3,1,'打磨处理-mock-seed-20260908',1),
(2,(SELECT id FROM mes_qc_ipqc WHERE name='组焊过程巡检-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_ipqc_line WHERE ipqc_id=(SELECT id FROM mes_qc_ipqc WHERE name='组焊过程巡检-02' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),'焊缝气孔需补焊',2,2,'已补焊复检-mock-seed-20260908',1),
(2,(SELECT id FROM mes_qc_ipqc WHERE name='组焊过程巡检-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_ipqc_line WHERE ipqc_id=(SELECT id FROM mes_qc_ipqc WHERE name='组焊过程巡检-02' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),'轻微飞溅未清理',3,1,'打磨清除-mock-seed-20260908',1),
(3,(SELECT id FROM mes_qc_oqc WHERE name='中石化保温管出货检验单-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_oqc_line WHERE oqc_id=(SELECT id FROM mes_qc_oqc WHERE name='中石化保温管出货检验单-02' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),'局部露底',3,2,'已隔离返修-mock-seed-20260908',1),
(4,(SELECT id FROM mes_qc_rqc WHERE name='中石化销售退货检验单-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_rqc_line WHERE rqc_id=(SELECT id FROM mes_qc_rqc WHERE name='中石化销售退货检验单-02' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),'运输磕碰涂层破损',2,3,'退货索赔处理-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 15：mes_qc_indicator_result 检验结果记录（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_qc_indicator_result` (`code`,`qc_id`,`qc_type`,`item_id`,`sn`,`remark`,`tenant_id`) VALUES
('QR-MOCK-001',(SELECT id FROM mes_qc_iqc WHERE name='钢管来料检验单-01' ORDER BY id LIMIT 1),1,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),'SN-001','来料实测记录-mock-seed-20260908',1),
('QR-MOCK-002',(SELECT id FROM mes_qc_ipqc WHERE name='下料工序首检-01' ORDER BY id LIMIT 1),2,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),'SN-002','首检实测记录-mock-seed-20260908',1),
('QR-MOCK-003',(SELECT id FROM mes_qc_oqc WHERE name='中石油保温管出货检验单-01' ORDER BY id LIMIT 1),3,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),'SN-003','出货抽检记录-mock-seed-20260908',1),
('QR-MOCK-004',(SELECT id FROM mes_qc_rqc WHERE name='法兰退料检验单-03' ORDER BY id LIMIT 1),4,(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),NULL,'退料复检记录-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 16：mes_qc_indicator_result_detail 检验结果明细（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_qc_indicator_result_detail` (`result_id`,`indicator_id`,`value`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_qc_indicator_result WHERE code='QR-MOCK-001' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='外径尺寸' ORDER BY id LIMIT 1),'114.02','实测值-mock-seed-20260908',1),
((SELECT id FROM mes_qc_indicator_result WHERE code='QR-MOCK-001' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='壁厚' ORDER BY id LIMIT 1),'5.90','实测值-mock-seed-20260908',1),
((SELECT id FROM mes_qc_indicator_result WHERE code='QR-MOCK-002' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='涂层厚度' ORDER BY id LIMIT 1),'96','实测值-mock-seed-20260908',1),
((SELECT id FROM mes_qc_indicator_result WHERE code='QR-MOCK-003' ORDER BY id LIMIT 1),(SELECT id FROM mes_qc_indicator WHERE name='附着力' ORDER BY id LIMIT 1),'1','等级合格-mock-seed-20260908',1);

SET FOREIGN_KEY_CHECKS=1;
