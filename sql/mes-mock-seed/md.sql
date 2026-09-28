-- ============================================
-- 域：md 基础数据
-- 用途：MES mock seed（tenant_id=1，每子模块 3-9 条）
-- 执行：dev 库 ruoyi-vue-pro（建议先执行 rollback.sql 清空再铺）
-- 随机规则：code 前缀+打散起始+0补齐；时间/数值 RAND；外键同表 RAND
-- 说明：workstation_machine(依赖 dv)→dv.sql、workstation_tool(依赖 tm)→tm.sql
--       product_sip/sop 的 process_id 由 pro.sql 铺工序后回填或保持 NULL
-- ============================================
SET FOREIGN_KEY_CHECKS=0;

-- ------------------------------------------------------------
-- 子模块 1：mes_md_item_type 物料分类（5 条）
-- item_or_product：1物料 2产品
-- ------------------------------------------------------------
INSERT INTO `mes_md_item_type` (`code`,`name`,`parent_id`,`item_or_product`,`sort`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('MT-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'原材料',NULL,1,1,0,'mock-seed-20260908',1),
(CONCAT('MT-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'半成品',NULL,1,2,0,'mock-seed-20260908',1),
(CONCAT('MT-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'产成品',NULL,2,3,0,'mock-seed-20260908',1),
(CONCAT('MT-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'辅助材料',(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),1,4,1,'mock-seed-20260908',1),
(CONCAT('MT-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'包装材料',(SELECT id FROM mes_md_item_type WHERE name='辅助材料' ORDER BY id LIMIT 1),1,5,1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_md_unit_measure 计量单位（9 条）
-- primary_flag：1主单位 0换算单位
-- ------------------------------------------------------------
INSERT INTO `mes_md_unit_measure` (`code`,`name`,`primary_flag`,`primary_id`,`change_rate`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('UM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'个',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'千克',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'米',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'平方米',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'升',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'吨',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'毫米',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'微米',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'级',b'1',NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_md_workshop 车间（4 条）
-- status：0开启 1关闭
-- ------------------------------------------------------------
INSERT INTO `mes_md_workshop` (`code`,`name`,`area`,`charge_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('WS-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'涂装车间-01',ROUND(RAND()*800+200,2),NULL,0,'mock-seed-20260908',1),
(CONCAT('WS-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'金工车间-02',ROUND(RAND()*800+200,2),NULL,0,'mock-seed-20260908',1),
(CONCAT('WS-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'成型车间-03',ROUND(RAND()*800+200,2),NULL,1,'mock-seed-20260908',1),
(CONCAT('WS-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'成品车间-04',ROUND(RAND()*800+200,2),NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 4：mes_md_workstation 工作站（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_md_workstation` (`code`,`name`,`address`,`workshop_id`,`process_id`,`warehouse_id`,`location_id`,`area_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('ST-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'焊接工位-01','一号厂房A区',(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'装配工位-02','一号厂房B区',(SELECT id FROM mes_md_workshop WHERE name LIKE '金工%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'喷涂工位-03','一号厂房C区',(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'打磨工位-04','二号厂房A区',(SELECT id FROM mes_md_workshop WHERE name LIKE '成型%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'检测工位-05','二号厂房B区',(SELECT id FROM mes_md_workshop WHERE name LIKE '成品%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*900)+100,3,'0')),'包装工位-06','二号厂房C区',(SELECT id FROM mes_md_workshop WHERE name LIKE '成品%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 5：mes_md_client 客户（5 条）
-- type：客户类型（1 普通客户 2 重点客户）
-- ------------------------------------------------------------
INSERT INTO `mes_md_client` (`code`,`name`,`nickname`,`english_name`,`type`,`address`,`website`,`email`,`telephone`,`contact1_name`,`contact1_telephone`,`contact2_name`,`contact2_telephone`,`credit_code`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'中石油管道-01','中石油管道','CNPC-PIPE',2,'北京市东城区',NULL,'sale1@cnpc-pipe.example.com',NULL,NULL,NULL,NULL,NULL,CONCAT('91',LPAD(FLOOR(RAND()*900000000)+100000000,9,'0')),0,'mock-seed-20260908',1),
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'中石化炼化-02','中石化炼化','SINOPEC-REF',2,'北京市朝阳区',NULL,NULL,'010-6999',NULL,NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'万华化学-03','万华化学','WANHUA-CHEM',1,'山东省烟台市',NULL,NULL,'0535-',NULL,NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'青岛港务-04','青岛港务','QINGDAO-PORT',1,'山东省青岛市',NULL,NULL,'0532-',NULL,NULL,NULL,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'延长石油-05','延长石油','YANCHANG-PETRO',1,'陕西省延安市',NULL,NULL,'0911-',NULL,NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 6：mes_md_vendor 供应商（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_md_vendor` (`code`,`name`,`nickname`,`english_name`,`level`,`score`,`address`,`telephone`,`contact1_name`,`contact1_telephone`,`credit_code`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'宝钢钢材-01','宝钢','BAOSTEEL','A',ROUND(RAND()*20+80),'上海市宝山区',NULL,'张经理',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'武钢板材-02','武钢','WISCO','A',ROUND(RAND()*20+80),'湖北省武汉市',NULL,'李经理',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'河钢特钢-03','河钢','HBIS','B',ROUND(RAND()*20+70),'河北省石家庄市',NULL,'王经理',NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'鞍钢型材-04','鞍钢','ANSTEEL','B',ROUND(RAND()*20+70),'辽宁省鞍山市',NULL,'赵经理',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'烟台防腐涂料-05','烟台涂料','YANTAI-PAINT','C',ROUND(RAND()*20+60),'山东省烟台市',NULL,'孙经理',NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 7：mes_md_item 物料产品（8 条）
-- status：0启用 1停用；batch_flag：1批次管理 0不启用
-- ------------------------------------------------------------
INSERT INTO `mes_md_item` (`code`,`name`,`specification`,`unit_measure_id`,`item_type_id`,`status`,`safe_stock_flag`,`min_stock`,`max_stock`,`high_value`,`batch_flag`,`remark`,`tenant_id`) VALUES
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'钢管-01','DN100*6mm Q235B',(SELECT id FROM mes_md_unit_measure WHERE name='米' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'法兰-02','DN100 PN16 RF',(SELECT id FROM mes_md_unit_measure WHERE name='个' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'弯头-03','DN100 90° R=1.5D',(SELECT id FROM mes_md_unit_measure WHERE name='个' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'0',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'三通-04','DN100 等径',(SELECT id FROM mes_md_unit_measure WHERE name='个' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),1,b'0',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'螺栓-05','M16*70 8.8级',(SELECT id FROM mes_md_unit_measure WHERE name='个' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'环氧底漆-06','A+B 双组分 25kg/组',(SELECT id FROM mes_md_unit_measure WHERE name='千克' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'1',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'聚氨酯面漆-07','B05 灰色 20kg/桶',(SELECT id FROM mes_md_unit_measure WHERE name='千克' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'1',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*9000)+1000,4,'0')),'预制保温管-08','DN100 成品',(SELECT id FROM mes_md_unit_measure WHERE name='米' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='产成品' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 8：mes_md_product_bom 产品BOM（5 条）
-- item_id：产品；bom_item_id：组成物料（固定引用本脚本物料名）
-- ------------------------------------------------------------
INSERT INTO `mes_md_product_bom` (`item_id`,`bom_item_id`,`quantity`,`status`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*5+1,2),0,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),ROUND(RAND()*2+0.5,2),0,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='聚氨酯面漆-07' ORDER BY id LIMIT 1),ROUND(RAND()*2+0.5,2),0,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),ROUND(RAND()*4+2,0),1,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='螺栓-05' ORDER BY id LIMIT 1),ROUND(RAND()*24+8,0),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 9：mes_md_product_sip 产品检验规范（4 条，process_id 待 pro 域工序）
-- ------------------------------------------------------------
INSERT INTO `mes_md_product_sip` (`item_id`,`sort`,`process_id`,`title`,`description`,`url`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),1,NULL,'外观检验规范','目视检查表面无气泡、无流挂、无露底',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),2,NULL,'尺寸检验规范','外径、壁厚按图纸公差±0.5mm',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),3,NULL,'附着力检验规范','划格法附着力≥1级',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),4,NULL,'压力检验规范','1.6MPa 水压保压30min无渗漏',NULL,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 10：mes_md_product_sop 产品作业规范（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_md_product_sop` (`item_id`,`sort`,`process_id`,`title`,`description`,`url`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),1,NULL,'钢管表面预处理作业指导','除锈等级Sa2.5，表面清洁无油污',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),2,NULL,'底漆涂装作业指导','湿膜厚度120μm，表干30min',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),3,NULL,'保温层包覆作业指导','岩棉密度≥120kg/m³，错缝包覆',NULL,'mock-seed-20260908',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),4,NULL,'成品检验包装作业指导','逐根检验，两端封堵后码放',NULL,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 11：mes_md_item_batch_config 物料批次属性配置（4 条，UNIQUE item_id）
-- ------------------------------------------------------------
INSERT INTO `mes_md_item_batch_config` (`item_id`,`produce_date_flag`,`expire_date_flag`,`receipt_date_flag`,`vendor_flag`,`client_flag`,`sales_order_code_flag`,`purchase_order_code_flag`,`work_order_flag`,`task_flag`,`workstation_flag`,`tool_flag`,`mold_flag`,`lot_number_flag`,`quality_status_flag`,`tenant_id`) VALUES
((SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),b'1',b'0',b'1',b'1',b'0',b'0',b'1',b'1',b'1',b'0',b'0',b'0',b'1',b'1',1),
((SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),b'1',b'1',b'1',b'1',b'0',b'0',b'1',b'0',b'0',b'0',b'0',b'0',b'1',b'1',1),
((SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),b'1',b'0',b'1',b'0',b'1',b'1',b'0',b'1',b'1',b'0',b'0',b'0',b'1',b'1',1),
((SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),b'1',b'0',b'1',b'1',b'0',b'0',b'1',b'1',b'0',b'0',b'0',b'0',b'1',b'0',1);

-- ------------------------------------------------------------
-- 子模块 12：mes_md_workstation_worker 工位人员配置（4 条）
-- post_id NOT NULL：取 system_post 首个启用岗位兜底 1
-- ------------------------------------------------------------
INSERT INTO `mes_md_workstation_worker` (`workstation_id`,`post_id`,`quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),COALESCE((SELECT id FROM system_post WHERE tenant_id=1 AND status=0 ORDER BY id LIMIT 1),1),ROUND(RAND()*4+2,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '装配%' ORDER BY id LIMIT 1),COALESCE((SELECT id FROM system_post WHERE tenant_id=1 AND status=0 ORDER BY id LIMIT 1),1),ROUND(RAND()*4+2,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),COALESCE((SELECT id FROM system_post WHERE tenant_id=1 AND status=0 ORDER BY id LIMIT 1),1),ROUND(RAND()*4+2,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),COALESCE((SELECT id FROM system_post WHERE tenant_id=1 AND status=0 ORDER BY id LIMIT 1),1),ROUND(RAND()*4+2,0),'mock-seed-20260908',1);

SET FOREIGN_KEY_CHECKS=1;
