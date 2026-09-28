-- =====================================================================
-- MES mock seed ENTRY (tenant_id=1) - dependency order
-- run:  cd sql/mes-mock-seed
--   mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < init-all.sql
-- order: md -> pro -> wm -> dv -> tm -> qc -> cal -> set
-- rollback: run rollback.sql
-- =====================================================================

-- ============================================================
-- [section 1/8] md.sql
-- ============================================================
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
(CONCAT('MT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'原材料',0,1,1,0,'mock-seed-20260908',1),
(CONCAT('MT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'半成品',0,1,2,0,'mock-seed-20260908',1),
(CONCAT('MT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'产成品',0,2,3,0,'mock-seed-20260908',1);
-- 辅助材料挂 原材料（拆条避免同表子查询 1093）
SELECT id INTO @md_parent_id FROM `mes_md_item_type` WHERE name='原材料' ORDER BY id LIMIT 1;
INSERT INTO `mes_md_item_type` (`code`,`name`,`parent_id`,`item_or_product`,`sort`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('MT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'辅助材料',@md_parent_id,1,4,1,'mock-seed-20260908',1);
-- 包装材料挂 辅助材料
SELECT id INTO @md_parent_id FROM `mes_md_item_type` WHERE name='辅助材料' ORDER BY id LIMIT 1;
INSERT INTO `mes_md_item_type` (`code`,`name`,`parent_id`,`item_or_product`,`sort`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('MT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'包装材料',@md_parent_id,1,5,1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_md_unit_measure 计量单位（9 条）
-- primary_flag：1主单位 0换算单位
-- ------------------------------------------------------------
INSERT INTO `mes_md_unit_measure` (`code`,`name`,`primary_flag`,`primary_id`,`change_rate`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('UM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'个',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'千克',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'米',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'平方米',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'升',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'吨',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'毫米',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'微米',b'1',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('UM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'级',b'1',NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_md_workshop 车间（4 条）
-- status：0开启 1关闭
-- ------------------------------------------------------------
INSERT INTO `mes_md_workshop` (`code`,`name`,`area`,`charge_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('WS-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'涂装车间-01',ROUND(RAND()*800+200,2),NULL,0,'mock-seed-20260908',1),
(CONCAT('WS-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'金工车间-02',ROUND(RAND()*800+200,2),NULL,0,'mock-seed-20260908',1),
(CONCAT('WS-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'成型车间-03',ROUND(RAND()*800+200,2),NULL,1,'mock-seed-20260908',1),
(CONCAT('WS-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'成品车间-04',ROUND(RAND()*800+200,2),NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 4：mes_md_workstation 工作站（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_md_workstation` (`code`,`name`,`address`,`workshop_id`,`process_id`,`warehouse_id`,`location_id`,`area_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('ST-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊接工位-01','一号厂房A区',(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'装配工位-02','一号厂房B区',(SELECT id FROM mes_md_workshop WHERE name LIKE '金工%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'喷涂工位-03','一号厂房C区',(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'打磨工位-04','二号厂房A区',(SELECT id FROM mes_md_workshop WHERE name LIKE '成型%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'检测工位-05','二号厂房B区',(SELECT id FROM mes_md_workshop WHERE name LIKE '成品%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('ST-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'包装工位-06','二号厂房C区',(SELECT id FROM mes_md_workshop WHERE name LIKE '成品%' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 5：mes_md_client 客户（5 条）
-- type：客户类型（1 普通客户 2 重点客户）
-- ------------------------------------------------------------
INSERT INTO `mes_md_client` (`code`,`name`,`nickname`,`english_name`,`type`,`address`,`website`,`email`,`telephone`,`contact1_name`,`contact1_telephone`,`contact2_name`,`contact2_telephone`,`credit_code`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石油管道-01','中石油管道','CNPC-PIPE',2,'北京市东城区',NULL,'sale1@cnpc-pipe.example.com',NULL,NULL,NULL,NULL,NULL,CONCAT('91',LPAD(FLOOR(RAND()*900000000)+100000000,9,'0')),0,'mock-seed-20260908',1),
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石化炼化-02','中石化炼化','SINOPEC-REF',2,'北京市朝阳区',NULL,NULL,'010-6999',NULL,NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'万华化学-03','万华化学','WANHUA-CHEM',1,'山东省烟台市',NULL,NULL,'0535-',NULL,NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'青岛港务-04','青岛港务','QINGDAO-PORT',1,'山东省青岛市',NULL,NULL,'0532-',NULL,NULL,NULL,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('C-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'延长石油-05','延长石油','YANCHANG-PETRO',1,'陕西省延安市',NULL,NULL,'0911-',NULL,NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 6：mes_md_vendor 供应商（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_md_vendor` (`code`,`name`,`nickname`,`english_name`,`level`,`score`,`address`,`telephone`,`contact1_name`,`contact1_telephone`,`credit_code`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'宝钢钢材-01','宝钢','BAOSTEEL','A',ROUND(RAND()*20+80),'上海市宝山区',NULL,'张经理',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'武钢板材-02','武钢','WISCO','A',ROUND(RAND()*20+80),'湖北省武汉市',NULL,'李经理',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'河钢特钢-03','河钢','HBIS','B',ROUND(RAND()*20+70),'河北省石家庄市',NULL,'王经理',NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'鞍钢型材-04','鞍钢','ANSTEEL','B',ROUND(RAND()*20+70),'辽宁省鞍山市',NULL,'赵经理',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('V-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'烟台防腐涂料-05','烟台涂料','YANTAI-PAINT','C',ROUND(RAND()*20+60),'山东省烟台市',NULL,'孙经理',NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 7：mes_md_item 物料产品（8 条）
-- status：0启用 1停用；batch_flag：1批次管理 0不启用
-- ------------------------------------------------------------
INSERT INTO `mes_md_item` (`code`,`name`,`specification`,`unit_measure_id`,`item_type_id`,`status`,`safe_stock_flag`,`min_stock`,`max_stock`,`high_value`,`batch_flag`,`remark`,`tenant_id`) VALUES
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管-01','DN100*6mm Q235B',(SELECT id FROM mes_md_unit_measure WHERE name='米' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰-02','DN100 PN16 RF',(SELECT id FROM mes_md_unit_measure WHERE name='个' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'弯头-03','DN100 90° R=1.5D',(SELECT id FROM mes_md_unit_measure WHERE name='个' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'0',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'三通-04','DN100 等径',(SELECT id FROM mes_md_unit_measure WHERE name='个' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),1,b'0',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'螺栓-05','M16*70 8.8级',(SELECT id FROM mes_md_unit_measure WHERE name='个' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'环氧底漆-06','A+B 双组分 25kg/组',(SELECT id FROM mes_md_unit_measure WHERE name='千克' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'1',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'聚氨酯面漆-07','B05 灰色 20kg/桶',(SELECT id FROM mes_md_unit_measure WHERE name='千克' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='原材料' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'1',b'1','mock-seed-20260908',1),
(CONCAT('IT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'预制保温管-08','DN100 成品',(SELECT id FROM mes_md_unit_measure WHERE name='米' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item_type WHERE name='产成品' ORDER BY id LIMIT 1),0,b'1',ROUND(RAND()*100+50,2),ROUND(RAND()*500+200,2),b'0',b'1','mock-seed-20260908',1);

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


-- ============================================================
-- [section 2/8] pro.sql
-- ============================================================
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
(CONCAT('PC-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'下料工序','按图纸尺寸切割，管端无毛刺',0,'防腐管段下料-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'坡口加工工序','坡口角度30°±2°，钝边1mm',0,'焊接前坡口-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'组对焊接工序','错边量≤1mm，焊缝成型均匀',0,'手工电弧焊打底+mock-seed',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'表面除锈工序','除锈等级Sa2.5级',0,'喷砂除锈-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'底漆涂装工序','湿膜厚120μm，无流挂',0,'环氧底漆-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温层包覆工序','岩棉错缝包覆、密度达标',0,'保温层-mock-seed-20260908',1),
(CONCAT('PC-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'成品检验包装工序','逐根试压检验后封堵码放',1,'出厂检验包装-mock-seed-20260908',1);

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
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管标准生产工艺','下料→坡口→组对焊接→除锈→底漆→保温包覆→成品检验的完整防腐保温管路线',1,'mock-seed-20260908',1),
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管焊接短管工艺','下料→坡口→焊接→检验',0,'mock-seed-20260908',1),
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'单管涂装返修工艺','除锈→底漆→检验',0,'mock-seed-20260908',1),
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰连接管段试制工艺','下料→组焊法兰→检验（试制）',1,'新产品试制用，待量产固化',1),
(CONCAT('RO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'成品出厂包装工艺','成品检验→封堵→码放发运',1,'mock-seed-20260908',1);

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
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石油保温管生产工单-01',1,1,CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*900+100,2),ROUND(RAND()*40,2),0.00,ROUND(RAND()*100+50,2),(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),DATE_SUB(NOW(), INTERVAL -15 DAY),0,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石化保温管生产工单-02',1,1,CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*800+200,2),ROUND(RAND()*200,2),0.00,ROUND(RAND()*200+100,2),(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'02'),DATE_SUB(NOW(), INTERVAL -20 DAY),0,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'万华短管生产工单-03',1,1,NULL,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*500+100,2),ROUND(RAND()*100,2),0.00,ROUND(RAND()*50+20,2),(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'03'),DATE_SUB(NOW(), INTERVAL -30 DAY),0,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'青岛港钢管防腐返修工单-04',2,2,NULL,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*300+50,2),ROUND(RAND()*300,2),0.00,ROUND(RAND()*50+20,2),(SELECT id FROM mes_md_client WHERE name LIKE '青岛%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'04'),DATE_SUB(NOW(), INTERVAL -25 DAY),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),NULL,2,'外协涂装完成-mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'延长石油保温管生产工单-05',1,1,CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*1000+100,2),ROUND(RAND()*900+50,2),0.00,ROUND(RAND()*300+100,2),(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'05'),DATE_SUB(NOW(), INTERVAL -40 DAY),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,2,'已完工入库-mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石油法兰管段试制工单-06',1,2,NULL,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*50+10,2),0.00,0.00,0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'06'),DATE_SUB(NOW(), INTERVAL 15 DAY),0,NULL,NULL,0,'试制待排产-mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'万华面漆返喷工单-07',2,2,NULL,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*200+50,2),0.00,0.00,0.00,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),NULL,DATE_SUB(NOW(), INTERVAL -10 DAY),0,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),3,'客户取消-mock-seed-20260908',1),
(CONCAT('MO-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石化保温管追加工单-08',1,1,NULL,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*600+100,2),0.00,ROUND(RAND()*30,2),ROUND(RAND()*50+10,2),(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'08'),DATE_SUB(NOW(), INTERVAL 20 DAY),0,NULL,NULL,0,'计划调整未执行-mock-seed-20260908',1);

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
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管-01 下料任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),100.00,ROUND(RAND()*80+10,2),ROUND(RAND()*70+10,2),ROUND(RAND()*5,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+5) DAY),3,DATE_SUB(NOW(), INTERVAL -5 DAY),'#409EFF',NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管-01 焊接任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),100.00,ROUND(RAND()*60+5,2),ROUND(RAND()*50+5,2),ROUND(RAND()*4,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+2) DAY),2,DATE_SUB(NOW(), INTERVAL 3 DAY),'#E6A23C',NULL,NULL,1,'关键焊接任务-mock-seed',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管-01 涂装任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),100.00,ROUND(RAND()*30,2),ROUND(RAND()*25,2),ROUND(RAND()*3,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5+1) DAY),1,DATE_SUB(NOW(), INTERVAL 1 DAY),'#909399',NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管-02 下料任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),200.00,ROUND(RAND()*200,2),ROUND(RAND()*180,2),ROUND(RAND()*8,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*12+8) DAY),4,DATE_SUB(NOW(), INTERVAL -2 DAY),'#00AEF3',DATE_SUB(NOW(), INTERVAL 1 DAY),NULL,2,'已完成-mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管-02 焊接任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),200.00,0.00,0.00,0.00,0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL -1 DAY),3,NULL,'#FF6B6B',NULL,NULL,0,'待开工-mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管-03 下料任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '钢管焊接短管工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),300.00,ROUND(RAND()*150+30,2),ROUND(RAND()*140+30,2),ROUND(RAND()*5,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+10) DAY),3,DATE_SUB(NOW(), INTERVAL -5 DAY),'#4ECDC4',DATE_SUB(NOW(), INTERVAL 4 DAY),NULL,2,'已完成-mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰管段试制 组焊任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油法兰管段试制工单-06%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '装配%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '法兰连接管段试制工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),30.00,0.00,0.00,0.00,0.00,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),NULL,1,NULL,'#96CEB4',NULL,NULL,0,'试制未排产-mock-seed-20260908',1),
(CONCAT('TSK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'延长保温管 涂装任务',(SELECT id FROM mes_pro_work_order WHERE name LIKE '延长石油保温管生产工单-05%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),500.00,ROUND(RAND()*500,2),ROUND(RAND()*480,2),ROUND(RAND()*15,2),0.00,(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+15) DAY),5,DATE_SUB(NOW(), INTERVAL -8 DAY),'#F59E0B',DATE_SUB(NOW(), INTERVAL 6 DAY),NULL,5,'工单取消任务同步取消-mock-seed',1);

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
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),1,'PC',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),100.00,ROUND(RAND()*40+10,2),ROUND(RAND()*45+5,2),ROUND(RAND()*3,2),0.00,ROUND(RAND()*2,2),ROUND(RAND()*1,2),0.00,103,1,4,'首件数量报工-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),2,'PC',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),100.00,ROUND(RAND()*30+5,2),ROUND(RAND()*30+5,2),ROUND(RAND()*2,2),0.00,0.00,0.00,0.00,103,1,4,'批量报工已完工-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),1,'PC',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 焊接任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),100.00,ROUND(RAND()*25+5,2),0.00,0.00,ROUND(RAND()*20+3,2),0.00,0.00,0.00,103,1,3,'待检验报工-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),1,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 涂装任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),100.00,ROUND(RAND()*15+2,2),ROUND(RAND()*15+2,2),0.00,0.00,0.00,0.00,0.00,1,1,2,'审批中-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),1,'APP',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '钢管焊接短管工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '钢管-03 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'03'),300.00,ROUND(RAND()*100+10,2),ROUND(RAND()*100+8,2),ROUND(RAND()*5,2),0.00,ROUND(RAND()*2,2),0.00,0.00,107,1,4,'APP端报工已完成-mock-seed-20260908',1),
(CONCAT('FB-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),1,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+1) DAY),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_route WHERE name LIKE '保温管标准生产工艺%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-02 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'02'),200.00,ROUND(RAND()*80+5,2),ROUND(RAND()*80+3,2),ROUND(RAND()*2,2),0.00,0.00,0.00,0.00,1,NULL,1,'已提交待审批-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 12：mes_pro_card 生产流转卡（5 条）
-- status：0流转中 1已完成 2挂起
-- ------------------------------------------------------------
INSERT INTO `mes_pro_card` (`code`,`work_order_id`,`item_id`,`batch_code`,`transfered_quantity`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),ROUND(RAND()*60+10,2),0,'首张流转卡-mock-seed-20260908',1),
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'01'),ROUND(RAND()*20+5,2),0,'第二张流转卡-mock-seed-20260908',1),
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'02'),ROUND(RAND()*150+50,2),1,'已完工流转卡-mock-seed-20260908',1),
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'03'),ROUND(RAND()*200+100,2),2,'挂起待处理-mock-seed-20260908',1),
(CONCAT('CARD',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '延长石油保温管生产工单-05%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),CONCAT('B',DATE_FORMAT(NOW(),'%y%m%d'),'05'),ROUND(RAND()*400+100,2),1,'已完工-mock-seed-20260908',1);

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
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '设备故障%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),103,(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),'设备故障，需要维修',1,2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,'焊机电流不稳已维修',1),
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '物料不足%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),107,(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),'物料不足，需要补料',2,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*2) DAY),1,'底漆补料中',1),
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '质量异常%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),1,NULL,NULL,'质量异常，需要确认',2,1,NULL,NULL,'正在处置中',1),
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '质量异常%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),1,(SELECT id FROM mes_pro_work_order WHERE name LIKE '延长石油保温管生产工单-05%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '成品检验包装工序%' ORDER BY id LIMIT 1),'质量异常，需要确认',2,2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,'保温层破损已处理',1),
((SELECT id FROM mes_pro_andon_config WHERE reason LIKE '人员不足%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '装配%' ORDER BY id LIMIT 1),107,NULL,NULL,'人员不足，需要支援',3,0,NULL,NULL,'待响应',1);

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


-- ============================================================
-- [section 3/8] wm.sql
-- ============================================================
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
(CONCAT('WH-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'主仓库-01','厂区一号库',ROUND(RAND()*5000+1000,2),NULL,b'0','mock-seed-20260908',1),
(CONCAT('WH-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'原料仓库-02','厂区二号库',ROUND(RAND()*5000+1000,2),NULL,b'0','mock-seed-20260908',1),
(CONCAT('WH-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'成品仓库-03','厂区三号库',ROUND(RAND()*5000+1000,2),NULL,b'0','mock-seed-20260908',1),
(CONCAT('WH-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'危化品临时仓-04','厂区北侧独立库',ROUND(RAND()*1000+200,2),NULL,b'1','mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_wm_batch 批次（6 条，对应 md 6 种物料各 1 批）
-- quality_status：0待检 1合格 2不合格
-- ------------------------------------------------------------
INSERT INTO `mes_wm_batch` (`code`,`item_id`,`produce_date`,`expire_date`,`receipt_date`,`vendor_id`,`client_id`,`work_order_id`,`task_id`,`lot_number`,`quality_status`,`remark`,`tenant_id`) VALUES
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL -180+FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),1,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),1,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='弯头-03' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),0,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='螺栓-05' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),1,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL -90+FLOOR(RAND()*30) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),NULL,NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),1,'mock-seed-20260908',1),
(CONCAT('PL-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*45) DAY),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*45) DAY),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),NULL,NULL,CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),0,'mock-seed-20260908',1);

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
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管到货通知-01',CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'张师傅',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),0,'mock-seed-20260908',1),
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰到货通知-02',CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'李师傅',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),1,'mock-seed-20260908',1),
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'底漆到货通知-03',CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'王师傅',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),2,'mock-seed-20260908',1),
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'弯头到货通知-04',CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('AR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'螺栓到货通知-05',CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),NULL,NULL,1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 5：mes_wm_item_receipt 采购入库单（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_item_receipt` (`code`,`name`,`iqc_id`,`notice_id`,`vendor_id`,`purchase_order_code`,`receipt_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('IR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管采购入库-01',NULL,(SELECT id FROM mes_wm_arrival_notice WHERE name LIKE '钢管%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1),
(CONCAT('IR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰采购入库-02',NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),1,'mock-seed-20260908',1),
(CONCAT('IR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'环氧底漆采购入库-03',NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),2,'mock-seed-20260908',1),
(CONCAT('IR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'螺栓采购入库-04',NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 6：mes_wm_product_receipt 成品入库单（4 条，依赖 pro 工单）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_product_receipt` (`code`,`name`,`work_order_id`,`item_id`,`receipt_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('PR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管成品入库-01',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('PR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管成品入库-02',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,'mock-seed-20260908',1),
(CONCAT('PR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管半成品入库-03',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),2,'mock-seed-20260908',1),
(CONCAT('PR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管半成品入库-04',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 7：mes_wm_outsource_receipt 外协入库单（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_outsource_receipt` (`code`,`name`,`work_order_id`,`vendor_id`,`receipt_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('OR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外协热处理入库-01',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('OR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外协喷砂入库-02',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,'mock-seed-20260908',1),
(CONCAT('OR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外协法兰加工入库-03',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('OR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外协表面处理入库-04',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 8：mes_wm_misc_receipt 杂项入库（4 条）
-- type：1盘盈入库 2其他入库
-- ------------------------------------------------------------
INSERT INTO `mes_wm_misc_receipt` (`code`,`name`,`type`,`source_doc_type`,`source_doc_id`,`source_doc_code`,`receipt_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('MR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'盘盈入库-01',1,2,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1),
(CONCAT('MR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'样管退库-02',2,1,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),1,'mock-seed-20260908',1),
(CONCAT('MR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'工具回收入库-03',2,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1),
(CONCAT('MR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'报废残值入库-04',2,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 9：mes_wm_product_issue 生产领料单（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_product_issue` (`code`,`name`,`workstation_id`,`work_order_id`,`task_id`,`issue_date`,`required_time`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊接工位领料-01',(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL -2 DAY),0,'mock-seed-20260908',1),
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'喷涂工位领料-02',(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL -1 DAY),1,'mock-seed-20260908',1),
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'装配工位领料-03',(SELECT id FROM mes_md_workstation WHERE name LIKE '装配%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL -3 DAY),2,'mock-seed-20260908',1),
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'包装工位领料-04',(SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,0,'mock-seed-20260908',1),
(CONCAT('PI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'打磨工位领料-05',(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_pro_task ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 10：mes_wm_misc_issue 杂项出库（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_misc_issue` (`code`,`name`,`type`,`source_doc_type`,`source_doc_id`,`source_doc_code`,`issue_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('MI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'报废品出库-01',1,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('MI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'赠样出库-02',2,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,'mock-seed-20260908',1),
(CONCAT('MI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'研发领用出库-03',3,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('MI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'办公领用出库-04',3,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 11：mes_wm_outsource_issue 外协领料（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_outsource_issue` (`code`,`name`,`vendor_id`,`work_order_id`,`issue_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('OI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外协热处理发料-01',(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('OI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外协喷砂发料-02',(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,'mock-seed-20260908',1),
(CONCAT('OI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外协法兰加工发料-03',(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1),
(CONCAT('OI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外协涂装发料-04',(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 12：mes_wm_return_issue 生产退料（4 条）
-- type：1退料 2换料
-- ------------------------------------------------------------
INSERT INTO `mes_wm_return_issue` (`code`,`name`,`work_order_id`,`workstation_id`,`type`,`return_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('RI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'余料退库-01',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),0,'mock-seed-20260908',1),
(CONCAT('RI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'不良品退料-02',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '装配%' ORDER BY id LIMIT 1),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,'mock-seed-20260908',1),
(CONCAT('RI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'换料退料-03',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),0,'mock-seed-20260908',1),
(CONCAT('RI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'过期物料退库-04',(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 13：mes_wm_return_sales 销售退货（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_return_sales` (`code`,`name`,`sales_order_code`,`client_id`,`return_date`,`return_reason`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('RS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'销售退货-01',CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'到货碰伤',0,'mock-seed-20260908',1),
(CONCAT('RS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'销售退货-02',CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'尺寸不符',1,'mock-seed-20260908',1),
(CONCAT('RS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'销售退货-03',CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'用户误下单',0,'mock-seed-20260908',1),
(CONCAT('RS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'销售退货-04',CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'运输损耗',0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 14：mes_wm_return_vendor 采购退货（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_return_vendor` (`code`,`name`,`purchase_order_code`,`vendor_id`,`return_date`,`return_reason`,`transport_code`,`transport_telephone`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('RV-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'采购退货-01',CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'壁厚不达标',CONCAT('TR-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),NULL,0,'mock-seed-20260908',1),
(CONCAT('RV-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'采购退货-02',CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'法兰孔径偏差',NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('RV-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'采购退货-03',CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'油漆过期',NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('RV-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'采购退货-04',CONCAT('PO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'弯头椭圆度超差',NULL,NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 15：mes_wm_product_sales 成品销售出库（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_product_sales` (`code`,`name`,`client_id`,`sales_order_code`,`sales_date`,`notice_id`,`contact_name`,`contact_telephone`,`contact_address`,`carrier`,`shipping_number`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管销售出库-01',(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,'马工',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),'北京顺义库',CONCAT('CAR-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),NULL,0,'mock-seed-20260908',1),
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管销售出库-02',(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,NULL,NULL,NULL,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管销售出库-03',(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,NULL,NULL,NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管销售出库-04',(SELECT id FROM mes_md_client WHERE name LIKE '青岛%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,NULL,NULL,NULL,NULL,NULL,2,'mock-seed-20260908',1),
(CONCAT('PS-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰销售出库-05',(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,NULL,NULL,NULL,NULL,NULL,1,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 16：mes_wm_sales_notice 销售通知（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_sales_notice` (`code`,`name`,`sales_order_code`,`client_id`,`sales_date`,`recipient_name`,`recipient_telephone`,`recipient_address`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石油销售发货通知-01',CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),'刘收',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),'北京市顺义区仓储中心',0,'mock-seed-20260908',1),
(CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石化销售发货通知-02',CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,1,'mock-seed-20260908',1),
(CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'万华销售发货通知-03',CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,0,'mock-seed-20260908',1),
(CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'延长销售发货通知-04',CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,2,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 17：mes_wm_stock_taking_plan 盘点计划（4 条）
-- type：1全盘 2抽盘 3循环盘点
-- ------------------------------------------------------------
INSERT INTO `mes_wm_stock_taking_plan` (`code`,`name`,`type`,`start_time`,`end_time`,`blind_flag`,`frozen`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('STK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'原料仓库月度盘点-01',1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),DATE_SUB(NOW(), INTERVAL -5 DAY),b'1',b'0',0,'mock-seed-20260908',1),
(CONCAT('STK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'成品仓库抽盘-02',2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),DATE_SUB(NOW(), INTERVAL -3 DAY),b'0',b'0',1,'mock-seed-20260908',1),
(CONCAT('STK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'主仓库循环盘点-03',3,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),DATE_SUB(NOW(), INTERVAL -2 DAY),b'0',b'0',2,'mock-seed-20260908',1),
(CONCAT('STK-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'危化品专项盘点-04',1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),NULL,b'1',b'1',0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 18：mes_wm_transfer 移库调拨（5 条）
-- type：1内部移库 2调拨；confirm_flag：1已确认
-- ------------------------------------------------------------
INSERT INTO `mes_wm_transfer` (`code`,`name`,`type`,`delivery_flag`,`recipient_name`,`recipient_telephone`,`destination_address`,`carrier`,`shipping_number`,`confirm_flag`,`transfer_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'原料仓至线边库移库-01',1,b'0',NULL,NULL,NULL,NULL,NULL,b'1',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),0,'mock-seed-20260908',1),
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'成品仓至发货区移库-02',1,b'0',NULL,NULL,NULL,NULL,NULL,b'1',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),1,'mock-seed-20260908',1),
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'分公司仓库调拨-03',2,b'0','陈收',CONCAT('13',LPAD(FLOOR(RAND()*999999999),9,'0')),'天津分公司仓库',CONCAT('CAR-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),CONCAT('SH-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),b'0',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),0,'mock-seed-20260908',1),
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'主仓库至危化仓调拨-04',1,b'1',NULL,NULL,NULL,NULL,NULL,b'1',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),0,'mock-seed-20260908',1),
(CONCAT('TR-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'成品仓库调拨备货-05',2,b'0',NULL,NULL,NULL,NULL,NULL,b'0',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),2,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 19：mes_wm_sn 序列号（4 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_sn` (`uuid`,`code`,`item_id`,`batch_code`,`work_order_id`,`tenant_id`) VALUES
(UUID(),CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),1),
(UUID(),CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),1),
(UUID(),CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),1),
(UUID(),CONCAT('SN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_work_order ORDER BY RAND() LIMIT 1),1);

-- ------------------------------------------------------------
-- 子模块 20：mes_wm_barcode_config 条码配置（4 条）
-- biz_type：1物料 2批次 3托盘 4单据
-- ------------------------------------------------------------
INSERT INTO `mes_wm_barcode_config` (`format`,`biz_type`,`content_format`,`content_example`,`auto_generate_flag`,`default_template`,`status`,`remark`,`tenant_id`) VALUES
(3,1,CONCAT('IT',DATE_FORMAT(NOW(),'%y%m%d'),'-####'),'IT260901-0001',b'1',NULL,0,'mock-seed-20260908',1),
(3,2,'PL-####','PL-0001',b'1',NULL,0,'mock-seed-20260908',1),
(1,3,'WH-####','WH-0001',b'0',NULL,1,'mock-seed-20260908',1),
(3,4,'SN-####','SN-0001',b'1',NULL,0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 21：mes_wm_barcode 条码实例（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_wm_barcode` (`config_id`,`format`,`biz_type`,`content`,`biz_id`,`biz_code`,`biz_name`,`status`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=1 ORDER BY id LIMIT 1),3,1,CONCAT('IT',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_md_item ORDER BY id LIMIT 1),(SELECT code FROM mes_md_item ORDER BY id LIMIT 1),(SELECT name FROM mes_md_item ORDER BY id LIMIT 1),0,'mock-seed-20260908',1),
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=2 ORDER BY id LIMIT 1),3,2,CONCAT('PL-',LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_wm_batch ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch ORDER BY id LIMIT 1),(SELECT name FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_batch ORDER BY id LIMIT 1)),0,'mock-seed-20260908',1),
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=3 ORDER BY id LIMIT 1),1,3,CONCAT('WH-',LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_wm_warehouse ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_warehouse ORDER BY id LIMIT 1),(SELECT name FROM mes_wm_warehouse ORDER BY id LIMIT 1),0,'mock-seed-20260908',1),
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=4 ORDER BY id LIMIT 1),3,4,CONCAT('SN-',LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_wm_sn ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_sn ORDER BY id LIMIT 1),(SELECT name FROM mes_md_item WHERE id=(SELECT item_id FROM mes_wm_sn ORDER BY id LIMIT 1)),1,'mock-seed-20260908',1),
((SELECT id FROM mes_wm_barcode_config WHERE biz_type=1 ORDER BY id LIMIT 1),3,1,CONCAT('IT',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*999)+1,3,'0')),(SELECT id FROM mes_md_item ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item ORDER BY RAND() LIMIT 1),(SELECT name FROM mes_md_item ORDER BY RAND() LIMIT 1),0,'mock-seed-20260908',1);

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
(CONCAT('PKG-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),ROUND(RAND()*100+200,1),ROUND(RAND()*40+80,1),ROUND(RAND()*40+80,1),ROUND(RAND()*500+100,2),ROUND(RAND()*520+110,2),NULL,0,'mock-seed-20260908',1),
(CONCAT('PKG-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),ROUND(RAND()*100+200,1),ROUND(RAND()*40+80,1),ROUND(RAND()*40+80,1),ROUND(RAND()*500+100,2),ROUND(RAND()*520+110,2),NULL,1,'mock-seed-20260908',1),
(CONCAT('PKG-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),ROUND(RAND()*100+200,1),ROUND(RAND()*40+80,1),ROUND(RAND()*40+80,1),ROUND(RAND()*500+100,2),ROUND(RAND()*520+110,2),NULL,2,'mock-seed-20260908',1),
(CONCAT('PKG-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),0,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),NULL,(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),ROUND(RAND()*100+200,1),ROUND(RAND()*40+80,1),ROUND(RAND()*40+80,1),ROUND(RAND()*500+100,2),ROUND(RAND()*520+110,2),NULL,0,'mock-seed-20260908',1);

SET FOREIGN_KEY_CHECKS=1;


-- ============================================================
-- [section 4/8] dv.sql
-- ============================================================
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
(CONCAT('DT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊接设备',0,0,1,'mock-seed-20260908',1),
(CONCAT('DT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'防腐喷涂设备',0,0,2,'mock-seed-20260908',1),
(CONCAT('DT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温生产线设备',0,0,3,'mock-seed-20260908',1),
(CONCAT('DT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'检测设备',0,0,4,'mock-seed-20260908',1),
(CONCAT('DT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'起重运输设备',0,0,5,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_dv_subject 点检保养项目（6 条）
-- type：1点检项目 2保养项目
-- ------------------------------------------------------------
INSERT INTO `mes_dv_subject` (`code`,`name`,`type`,`content`,`standard`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('SUB-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊机外观检查',1,'检查焊机外壳、电缆有无破损漏电','外观完好无破损',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'喷枪喷嘴检查',1,'检查喷嘴磨损、堵塞情况','喷嘴无堵塞磨损量达标',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'空压机油位检查',1,'检查空压机油位是否在上下限之间','油位处于上下刻度之间',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'传动链润滑保养',2,'对各传动链条加油润滑，调整张紧度','链条润滑充分无卡滞',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'喷涂泵密封件更换',2,'检查并更换喷涂泵易损密封件','无渗漏无滴漆',0,'mock-seed-20260908',1),
(CONCAT('SUB-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'电气柜除尘紧固',2,'清洁电控柜、紧固接线端子','柜内无积尘、端子紧固',0,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_dv_machinery 设备台账（6 条）
-- status：1运行 2闲置 3维修中
-- ------------------------------------------------------------
INSERT INTO `mes_dv_machinery` (`code`,`name`,`brand`,`specification`,`machinery_type_id`,`workshop_id`,`status`,`last_mainten_time`,`last_check_time`,`remark`,`tenant_id`) VALUES
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'埋弧焊机-01','林肯','DC-1000',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '焊接设备%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'氩弧焊机-02','松下','YC-400TX',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '焊接设备%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'无气喷涂机-03','固瑞克','XTREME',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '防腐喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '涂装%' ORDER BY id LIMIT 1),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'自动缠绕机-04','国产','CR-800',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '保温生产线%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '金工%' ORDER BY id LIMIT 1),2,NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30) DAY),'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'水压试验机-05','国产','SY-10',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '检测设备%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '成品%' ORDER BY id LIMIT 1),1,NULL,NULL,'mock-seed-20260908',1),
(CONCAT('M-',DATE_FORMAT(NOW(),'%y%m'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'桥式起重机-06','德马格','DEMAG-5t',(SELECT id FROM mes_dv_machinery_type WHERE name LIKE '起重%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workshop WHERE name LIKE '成品%' ORDER BY id LIMIT 1),3,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*60) DAY),NULL,'钢丝绳更换检修中-mock-seed',1);

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
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '埋弧焊机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '氩弧焊机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '无气喷涂机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%周点检方案-04' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '水压试验机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%季度保养方案-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '桥式起重机%' ORDER BY id LIMIT 1),'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 6：mes_dv_check_plan_subject 方案关联项目（5 条）
-- ------------------------------------------------------------
INSERT INTO `mes_dv_check_plan_subject` (`plan_id`,`subject_id`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '焊机外观检查%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '空压机油位%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '喷枪喷嘴%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '喷涂泵密封件%' ORDER BY id LIMIT 1),'mock-seed-20260908',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%季度保养方案-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_subject WHERE name LIKE '电气柜除尘%' ORDER BY id LIMIT 1),'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 7：mes_dv_check_record 点检记录（5 条）
-- status：10待执行 20已完成
-- ------------------------------------------------------------
INSERT INTO `mes_dv_check_record` (`plan_id`,`machinery_id`,`check_time`,`user_id`,`status`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '埋弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,20,'埋弧焊机日常点检正常',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%日点检方案-01' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '氩弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,20,'氩弧焊机电缆老化已登记',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%周点检方案-04' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '水压试验机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,10,'待执行点检任务',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '无气喷涂机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,20,'喷涂机压力波动检查完成',1),
(NULL,(SELECT id FROM mes_dv_machinery WHERE name LIKE '桥式起重机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),1,20,'临时点检：钢丝绳磨损在限',1);

-- ------------------------------------------------------------
-- 子模块 8：mes_dv_mainten_record 保养记录（5 条）
-- status：0待执行 1执行中 4草稿 5完成（业务值按代码字典，多用0/4便于演示）
-- ------------------------------------------------------------
INSERT INTO `mes_dv_mainten_record` (`plan_id`,`machinery_id`,`mainten_time`,`user_id`,`status`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%周保养方案-02' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '无气喷涂机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,0,'无气喷涂机密封件更换保养',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '月度保养方案-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '自动缠绕机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,0,'缠绕机传动链润滑保养',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '%季度保养方案-05' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '桥式起重机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,4,'起重机季度保养（草稿）',1),
(NULL,(SELECT id FROM mes_dv_machinery WHERE name LIKE '埋弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,4,'焊机临时保养（无计划草稿）',1),
((SELECT id FROM mes_dv_check_plan WHERE name LIKE '月度保养方案-03' ORDER BY id LIMIT 1),(SELECT id FROM mes_dv_machinery WHERE name LIKE '水压试验机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20) DAY),1,0,'试验机液压系统保养',1);

-- ------------------------------------------------------------
-- 子模块 9：mes_dv_repair 维修工单（6 条）
-- status：0待派工 1待维修 2维修中 3待验收 4已完成；result：1合格 0不合格
-- ------------------------------------------------------------
INSERT INTO `mes_dv_repair` (`code`,`name`,`machinery_id`,`require_date`,`finish_date`,`confirm_date`,`result`,`accepted_user_id`,`confirm_user_id`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'埋弧焊机送丝机构卡阻维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '埋弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,1,1,4,'更换送丝轮及压紧弹簧，验收合格',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'无气喷涂机压力波动维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '无气喷涂机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,1,NULL,1,'检查蓄能器充压，待确认',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'自动缠绕机变频器报警维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '自动缠绕机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,1,NULL,2,'维修中：排查变频器母线电容',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'水压试验机保压不足维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '水压试验机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,1,1,4,'更换高压密封圈，验收合格',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'氩弧焊机高频引弧故障维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '氩弧焊机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,NULL,NULL,0,'待派工',1),
(CONCAT('BX-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'桥式起重机限位失灵维修',(SELECT id FROM mes_dv_machinery WHERE name LIKE '桥式起重机%' ORDER BY id LIMIT 1),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15) DAY),NULL,NULL,NULL,NULL,NULL,0,'待派工：现场已停机挂牌',1);

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


-- ============================================================
-- [section 5/8] tm.sql
-- ============================================================
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
(CONCAT('TT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊枪焊把',b'1',1,180,'mock-seed-20260908',1),
(CONCAT('TT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'喷涂枪具',b'1',1,90,'mock-seed-20260908',1),
(CONCAT('TT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'量检具',b'1',1,365,'mock-seed-20260908',1),
(CONCAT('TT-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'吊装索具',b'0',2,5000,'mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_tm_tool 工具台账（6 条）
-- status：1可用 2已领用 3送修 4报废
-- ------------------------------------------------------------
INSERT INTO `mes_tm_tool` (`code`,`name`,`brand`,`specification`,`tool_type_id`,`quantity`,`available_quantity`,`mainten_type`,`next_mainten_period`,`next_mainten_date`,`status`,`remark`,`tenant_id`) VALUES
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'CO2焊枪-01','林肯','CBT-500',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '焊枪焊把%' ORDER BY id LIMIT 1),ROUND(RAND()*20+10,0),ROUND(RAND()*10+5,0),1,NULL,DATE_SUB(NOW(), INTERVAL -60+FLOOR(RAND()*30) DAY),1,'mock-seed-20260908',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'TIG焊枪-02','松下','YC-200TS',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '焊枪焊把%' ORDER BY id LIMIT 1),ROUND(RAND()*20+10,0),ROUND(RAND()*10+5,0),2,2000,NULL,1,'mock-seed-20260908',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'无气喷枪-03','固瑞克','GG-291',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '喷涂枪具%' ORDER BY id LIMIT 1),ROUND(RAND()*10+4,0),ROUND(RAND()*5+1,0),1,NULL,DATE_SUB(NOW(), INTERVAL -30+FLOOR(RAND()*20) DAY),2,'已领用至喷涂车间',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'游标卡尺-04','三丰','500-196',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '量检具%' ORDER BY id LIMIT 1),ROUND(RAND()*30+10,0),ROUND(RAND()*15+5,0),1,NULL,DATE_SUB(NOW(), INTERVAL -200+FLOOR(RAND()*50) DAY),3,'送检校准中',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'超声波测厚仪-05','奥林巴斯','38DL',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '量检具%' ORDER BY id LIMIT 1),2,ROUND(RAND()*2,0),1,NULL,DATE_SUB(NOW(), INTERVAL -300+FLOOR(RAND()*60) DAY),1,'mock-seed-20260908',1),
(CONCAT('TOOL-',DATE_FORMAT(NOW(),'%y%m%d'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'吊装钢丝绳-06','国产','D6*19-20',(SELECT id FROM mes_tm_tool_type WHERE name LIKE '吊装索具%' ORDER BY id LIMIT 1),8,8,2,10000,NULL,4,'使用次数到期报废',1);

-- ------------------------------------------------------------
-- 子模块 3：mes_md_workstation_tool 工位绑定工具类型（4 条，依赖 md 工位+tm 工具类型）
-- ------------------------------------------------------------
INSERT INTO `mes_md_workstation_tool` (`workstation_id`,`tool_type_id`,`quantity`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_tm_tool_type WHERE name LIKE '焊枪焊把%' ORDER BY id LIMIT 1),ROUND(RAND()*5+2,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_tm_tool_type WHERE name LIKE '喷涂枪具%' ORDER BY id LIMIT 1),ROUND(RAND()*4+2,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '检测%' ORDER BY id LIMIT 1),(SELECT id FROM mes_tm_tool_type WHERE name LIKE '量检具%' ORDER BY id LIMIT 1),ROUND(RAND()*6+3,0),'mock-seed-20260908',1),
((SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),(SELECT id FROM mes_tm_tool_type WHERE name LIKE '吊装索具%' ORDER BY id LIMIT 1),ROUND(RAND()*4+1,0),'mock-seed-20260908',1);

SET FOREIGN_KEY_CHECKS=1;


-- ============================================================
-- [section 6/8] qc.sql
-- ============================================================
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
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'防腐钢管来料检验方案','1',0,'钢管/管件入厂通用检验-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管过程巡检方案','2',0,'下料/焊接/涂装过程巡检-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'预制保温管出厂检验方案','3',0,'成品出厂 OQC-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管退货检验方案','4',0,'退料/退货复检 RQC-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'涂装材料全流程检验方案','1,2,3,4',0,'涂料及成品全流程覆盖-mock-seed-20260908',1),
(CONCAT('QCT-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰螺栓来料抽检方案','1',0,'管件类抽检-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_qc_indicator 质检指标（8 条）
-- type：1数值 2定性；result_type：1数值 2计数 3文本 4布尔 5附件
-- ------------------------------------------------------------
INSERT INTO `mes_qc_indicator` (`code`,`name`,`type`,`tool`,`result_type`,`result_specification`,`remark`,`tenant_id`) VALUES
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外径尺寸',1,'游标卡尺',1,NULL,'DN100±0.5mm-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'壁厚',1,'超声波测厚仪',1,NULL,'≥5.6mm 实测-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊缝外观',2,'目视/放大镜',5,'IMG','无裂纹气孔咬边-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'涂层外观',2,'目视',5,'IMG','无流挂/气泡/露底-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'涂层厚度',1,'涂层测厚仪',1,NULL,'干膜≥80μm-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'附着力',1,'划格器',1,NULL,'≤1级-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'数量清点',2,NULL,2,NULL,'与单据一致-mock-seed-20260908',1),
(CONCAT('QI-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'合格证核对',2,NULL,3,NULL,'炉批号/质保书一致-mock-seed-20260908',1);

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
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管来料检验单-01',(SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),100,(SELECT id FROM mes_wm_arrival_notice WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,CONCAT('AN-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*900+100,2),ROUND(RAND()*8+2,2),ROUND(RAND()*8,2),ROUND(RAND()*1,2),0.00,ROUND(RAND()*3,2),ROUND(RAND()*10+3,2),0,0,FLOOR(RAND()*2),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*45+10) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*40+5) DAY),1,4,'来料外观尺寸抽检合格-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'钢管来料检验单-02',(SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '宝钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*800+200,2),ROUND(RAND()*5+1,2),ROUND(RAND()*4+1,2),0.00,0.00,0.00,0.00,0,0,0,2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*40+5) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*35+3) DAY),104,4,'壁厚不合格已判退-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'环氧底漆来料检验单-03',(SELECT id FROM mes_qc_template WHERE name='涂装材料全流程检验方案' ORDER BY id LIMIT 1),100,(SELECT id FROM mes_wm_arrival_notice WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '烟台%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),ROUND(RAND()*100+50,2),ROUND(RAND()*3+1,2),ROUND(RAND()*3,2),ROUND(RAND()*1,2),0.00,0.00,ROUND(RAND()*5,2),0,0,FLOOR(RAND()*2),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30+5) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*28+2) DAY),1,4,'涂料质保资料齐全-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰来料检验单-04',(SELECT id FROM mes_qc_template WHERE name='法兰螺栓来料抽检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '鞍钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),ROUND(RAND()*500+100,2),ROUND(RAND()*5+1,2),ROUND(RAND()*5,2),ROUND(RAND()*2,2),0.00,ROUND(RAND()*8+2,2),ROUND(RAND()*10,2),0,FLOOR(RAND()*2),FLOOR(RAND()*2),NULL,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+2) DAY),NULL,NULL,0,'待检验-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'螺栓来料检验单-05',(SELECT id FROM mes_qc_template WHERE name='法兰螺栓来料抽检方案' ORDER BY id LIMIT 1),100,(SELECT id FROM mes_wm_arrival_notice WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '河钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='螺栓-05' ORDER BY id LIMIT 1),ROUND(RAND()*2000+500,2),ROUND(RAND()*8+2,2),ROUND(RAND()*8,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+1) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*12+1) DAY),104,4,'抽检合格准予入库-mock-seed-20260908',1),
(CONCAT('IQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'弯头来料检验单-06',(SELECT id FROM mes_qc_template WHERE name='防腐钢管来料检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_vendor WHERE name LIKE '武钢%' ORDER BY id LIMIT 1),CONCAT('VB',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT id FROM mes_md_item WHERE name='弯头-03' ORDER BY id LIMIT 1),ROUND(RAND()*300+50,2),ROUND(RAND()*4+1,2),ROUND(RAND()*4,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+1) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*6+1) DAY),1,4,'mock-seed-20260908',1);

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
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'下料工序首检-01',1,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),304,(SELECT id FROM mes_pro_feedback WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,CONCAT('FB-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 下料任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '打磨%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '下料工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*5+1,2),ROUND(RAND()*5,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30+5) DAY),1,4,'首件合格准予批量生产-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'组焊过程巡检-02',2,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),304,(SELECT id FROM mes_pro_feedback WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石油保温管生产工单-01%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_task WHERE name LIKE '保温管-01 焊接任务%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_workstation WHERE name LIKE '焊接%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '组对焊接工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*10+2,2),ROUND(RAND()*8+1,2),ROUND(RAND()*2,2),ROUND(RAND()*1,2),0.00,0.00,0.00,ROUND(RAND()*5,2),ROUND(RAND()*10+3,2),0,FLOOR(RAND()*2),FLOOR(RAND()*2),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*25+3) DAY),1,4,'发现气孔需补焊后复检-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'涂装工序巡检-03',4,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '中石化保温管生产工单-02%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_workstation WHERE name LIKE '喷涂%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '底漆涂装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*8+1,2),ROUND(RAND()*8,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+2) DAY),104,4,'膜厚抽检达标-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'包装工序自检-04',3,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '延长石油保温管生产工单-05%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_workstation WHERE name LIKE '包装%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '成品检验包装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*6+1,2),ROUND(RAND()*6,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+1) DAY),1,4,'封堵码放规范-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外协返修过程检验-05',2,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '青岛港钢管防腐返修工单-04%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_workstation WHERE name LIKE '检测%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '表面除锈工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),ROUND(RAND()*5+1,2),ROUND(RAND()*4+1,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,NULL,NULL,NULL,0,'待检验-mock-seed-20260908',1),
(CONCAT('IPQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'万华短管末检-06',3,(SELECT id FROM mes_qc_template WHERE name='保温管过程巡检方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_pro_work_order WHERE name LIKE '万华短管生产工单-03%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_workstation WHERE name LIKE '检测%' ORDER BY id LIMIT 1),(SELECT id FROM mes_pro_process WHERE name LIKE '成品检验包装工序%' ORDER BY id LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),ROUND(RAND()*4+1,2),ROUND(RAND()*4,2),0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+1) DAY),1,4,'末检合格-mock-seed-20260908',1);

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
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石油保温管出货检验单-01',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),118,(SELECT id FROM mes_wm_product_sales WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,CONCAT('SO-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),(SELECT id FROM mes_md_client WHERE name LIKE '中石油%' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),5,0,ROUND(RAND()*900+100,2),ROUND(RAND()*5+1,2),ROUND(RAND()*5,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*25+5) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*22+2) DAY),1,4,'成品检验合格准予发运-mock-seed-20260908',1),
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石化保温管出货检验单-02',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_client WHERE name LIKE '中石化%' ORDER BY id LIMIT 1),(SELECT code FROM mes_wm_batch WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),5,0,ROUND(RAND()*800+100,2),ROUND(RAND()*5+1,2),ROUND(RAND()*4,2),ROUND(RAND()*1,2),0.00,ROUND(RAND()*3,2),ROUND(RAND()*5,2),0,0,FLOOR(RAND()*2),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+3) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*18+1) DAY),104,4,'个别露底已隔离返修-mock-seed-20260908',1),
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'万华化学钢管出货检验单-03',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_client WHERE name LIKE '万华%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),3,0,ROUND(RAND()*500+50,2),ROUND(RAND()*3+1,2),ROUND(RAND()*3,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*12+1) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+1) DAY),1,4,'mock-seed-20260908',1),
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'青岛港务短管出货检验单-04',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,(SELECT id FROM mes_md_client WHERE name LIKE '青岛%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),3,0,ROUND(RAND()*300+50,2),ROUND(RAND()*3+1,2),0.00,0.00,0.00,0.00,0.00,0,0,0,NULL,NULL,NULL,1,0,'待检验-mock-seed-20260908',1),
(CONCAT('OQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'延长石油保温管出货检验单-05',(SELECT id FROM mes_qc_template WHERE name='预制保温管出厂检验方案' ORDER BY id LIMIT 1),118,(SELECT id FROM mes_wm_product_sales WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,(SELECT id FROM mes_md_client WHERE name LIKE '延长%' ORDER BY id LIMIT 1),NULL,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),5,0,ROUND(RAND()*600+100,2),ROUND(RAND()*5+1,2),ROUND(RAND()*5,2),0.00,0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+1) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*6+1) DAY),1,4,'mock-seed-20260908',1);

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
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温管退料复检单-01',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),116,(SELECT id FROM mes_wm_return_issue WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,1,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,ROUND(RAND()*40+10,2),ROUND(RAND()*40,2),ROUND(RAND()*2,2),0.00,0.00,ROUND(RAND()*3,2),0,0,FLOOR(RAND()*2),1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30+5) DAY),1,4,'退料合格可再入库-mock-seed-20260908',1),
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'中石化销售退货检验单-02',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),119,(SELECT id FROM mes_wm_return_sales WHERE tenant_id=1 ORDER BY RAND() LIMIT 1),NULL,NULL,2,(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),NULL,ROUND(RAND()*30+5,2),ROUND(RAND()*25+3,2),ROUND(RAND()*3,2),0.00,ROUND(RAND()*5,2),ROUND(RAND()*8,2),0,FLOOR(RAND()*2),FLOOR(RAND()*2),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+2) DAY),104,4,'涂层破损批量退货-mock-seed-20260908',1),
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰退料检验单-03',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,1,(SELECT id FROM mes_md_item WHERE name='法兰-02' ORDER BY id LIMIT 1),NULL,ROUND(RAND()*20+5,2),ROUND(RAND()*20,2),ROUND(RAND()*1,2),0.00,0.00,0.00,0,0,0,1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+1) DAY),1,4,'可复用-mock-seed-20260908',1),
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'面漆退料检验单-04',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,1,(SELECT id FROM mes_md_item WHERE name='聚氨酯面漆-07' ORDER BY id LIMIT 1),NULL,ROUND(RAND()*15+3,2),ROUND(RAND()*15,2),ROUND(RAND()*2,2),0.00,ROUND(RAND()*4,2),ROUND(RAND()*6,2),0,0,FLOOR(RAND()*2),2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+1) DAY),1,4,'超过保质期判废-mock-seed-20260908',1),
(CONCAT('RQC-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'万华钢管退货待检单-05',(SELECT id FROM mes_qc_template WHERE name='保温管退货检验方案' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,2,(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0,0,NULL,NULL,NULL,0,'待检验-mock-seed-20260908',1);

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
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'外径尺寸超差',1,2,'超出公差±0.5mm-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'壁厚不足',1,2,'低于标准下限-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊缝气孔夹渣',2,2,'影响承压-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'涂层流挂露底',2,3,'外观缺陷-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'涂层附着力差',2,2,'划格剥落-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'法兰密封面损伤',2,1,'致命缺陷-mock-seed-20260908',1),
(CONCAT('DEF-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'保温层破损',2,3,'外护破损进水-mock-seed-20260908',1);

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


-- ============================================================
-- [section 7/8] cal.sql
-- ============================================================
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
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'下料车间九月三班倒计划-01',2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*40+30) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*6) DAY),3,3,1,1,'下料车间连续生产三班倒-mock-seed-20260908',1),
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊接车间九月两班倒计划-02',1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*40+25) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*6) DAY),2,3,1,1,'焊接车间两班倒-mock-seed-20260908',1),
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'喷涂车间九月单班计划-03',2,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*35+20) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5) DAY),1,4,1,1,'喷涂车间单白班-mock-seed-20260908',1),
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'打磨车间九月两班倒计划-04',1,DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*30+20) DAY),DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*4) DAY),2,3,1,1,'打磨车间两班倒-mock-seed-20260908',1),
(CONCAT('PLAN-',DATE_FORMAT(NOW(),'%y%m%d'),'-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'下料车间十月两班倒计划-05',1,DATE_ADD(NOW(), INTERVAL FLOOR(RAND()*10+5) DAY),DATE_ADD(NOW(), INTERVAL FLOOR(RAND()*20+15) DAY),2,3,1,0,'下月排班草稿-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_cal_team 班组（6 条）
-- ------------------------------------------------------------
INSERT INTO `mes_cal_team` (`code`,`name`,`calendar_type`,`remark`,`tenant_id`) VALUES
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'下料甲班',2,'负责下料/切割-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'下料乙班',1,'负责下料/切割-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊接甲班',1,'负责组对焊接-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'焊接乙班',2,'负责组对焊接-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'喷涂班',2,'负责防腐涂装-mock-seed-20260908',1),
(CONCAT('TEAM-',LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'打磨班',1,'负责表面处理-mock-seed-20260908',1);

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


-- ============================================================
-- [section 8/8] set.sql
-- ============================================================
-- ============================================
-- 域：set 安全环保（污染监控）
-- 用途：MES mock seed（tenant_id=1，每子模块 3-9 条）
-- 执行：dev 库 ruoyi-vue-pro
-- 前置：需先执行 pollution-sql 的
--       set-pollution-check-20260903-p0.sql（判定表）
--       + pollution-landing-20260903.sql（台账表 mes_pollution_ledger）
-- 规则：AI 初筛 CLEAN/POLLUTED/UNCERTAIN；人工复核收口 CLEAN/POLLUTED，空=待复核；
--       复核为 POLLUTED 的行才登记台账（uk_source_check_id 一对一）
-- ============================================
SET FOREIGN_KEY_CHECKS=0;

-- ------------------------------------------------------------
-- 子模块 1：mes_set_pollution_check 污染判定记录（6 条）
-- stage：PURCHASE_INBOUND 采购入库 / MATERIAL_ISSUE 生产领用 /
--        WASTE_INTERMEDIATE 中间废弃物 / FINISHED_PRODUCT 成品
-- ------------------------------------------------------------
INSERT INTO `mes_set_pollution_check` (`record_no`,`stage`,`biz_no`,`batch_no`,`item_code`,`item_name`,`item_spec`,`ai_result`,`ai_confidence`,`ai_reason`,`suggested_storage`,`review_result`,`review_by`,`review_time`,`disposition`,`storage_method`,`location`,`marked`,`remark`,`tenant_id`) VALUES
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'PURCHASE_INBOUND','WM-AR-MOCK-01',(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1) ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),'环氧底漆-06','A+B 双组分 25kg/组','POLLUTED',86,'检测到桶体漆液渗漏残留，判定有污染风险','危废暂存库 密封托盘存放','POLLUTED','周安环',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+5) DAY),'CONTROLLED_STORAGE','危废库密封桶+托盘',CONCAT('危废库A区-',LPAD(FLOOR(RAND()*9)+1,2,'0')),b'1','入库发现渗漏桶隔离-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'PURCHASE_INBOUND','WM-AR-MOCK-02',(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1) ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),'钢管-01','DN100*6mm Q235B','CLEAN',92,'表面无油污/无锈蚀污染物，图像特征正常','常规原料库 按批次上架','CLEAN','张质检',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+8) DAY),'NORMAL_INBOUND','常规库位上架',CONCAT('原料库B-',LPAD(FLOOR(RAND()*9)+1,2,'0')),b'0','无污染准予入库-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'MATERIAL_ISSUE','WM-MI-MOCK-01',(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='聚氨酯面漆-07' ORDER BY id LIMIT 1) ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item WHERE name='聚氨酯面漆-07' ORDER BY id LIMIT 1),'聚氨酯面漆-07','B05 灰色 20kg/桶','CLEAN',88,'开桶无滴漏，VOC快检低于阈值','涂装线漆料间','CLEAN','李车间',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+10) DAY),'ISSUE_ALLOWED','领用至涂装车间漆料暂存间','涂装车间漆料间-01',b'0','准予领用-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'WASTE_INTERMEDIATE','WM-RET-MOCK-01',CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT code FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),'钢管打磨铁屑-01','DN100 打磨工序收尘渣','UNCERTAIN',71,'图像特征不清晰：疑似含油切削液污染，需人工核实','建议危废袋单独盛装待复核','CLEAN','周安环',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+3) DAY),'REUSE','分拣后回用（纯铁屑）','固废分拣区-02',b'0','人工确认无含油，按一般固废分拣回用-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'FINISHED_PRODUCT','PR-FB-MOCK-01',(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),'预制保温管-08','DN100 成品','POLLUTED',78,'表面检测到不明油污覆盖，成品批次受污染','隔离存放 禁止发运','POLLUTED','张质检',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5+2) DAY),'ISOLATE_STORAGE','隔离区存放，返工处理','成品隔离区-01',b'1','成品油污污染已隔离-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*90000000)+10000000,8,'0')),'WASTE_INTERMEDIATE','WM-OR-MOCK-01',CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT code FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),'废乳化液-02','机加工乳化液废液','POLLUTED',93,'乳化液废液属于危废（HW09），特征明显','危废桶收集 交有资质单位处置','POLLUTED','王环保',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*3+1) DAY),'MARKED_STORAGE','危废桶盛装 贴危废标签','危废库C区-01',b'1','已联单登记-mock-seed-20260908',1);

-- ------------------------------------------------------------
-- 子模块 2：mes_pollution_ledger 污染/危废暂存台账（3 条）
-- 只登记人工复核为 POLLUTED 的判定记录（与 check 一对一）
-- status：STORED 暂存 / PROCESSING 处置中 / DISPOSED 已处置
-- ------------------------------------------------------------
INSERT INTO `mes_pollution_ledger` (`source_check_id`,`source_record_no`,`stage`,`biz_no`,`batch_no`,`item_code`,`item_name`,`item_spec`,`disposition`,`storage_method`,`location`,`marked`,`status`,`status_by`,`status_time`,`remark`,`tenant_id`) VALUES
((SELECT id FROM mes_set_pollution_check WHERE biz_no='WM-AR-MOCK-01' ORDER BY id LIMIT 1),(SELECT record_no FROM mes_set_pollution_check WHERE biz_no='WM-AR-MOCK-01' ORDER BY id LIMIT 1),'PURCHASE_INBOUND','WM-AR-MOCK-01',(SELECT batch_no FROM mes_set_pollution_check WHERE biz_no='WM-AR-MOCK-01' ORDER BY id LIMIT 1),(SELECT item_code FROM mes_set_pollution_check WHERE biz_no='WM-AR-MOCK-01' ORDER BY id LIMIT 1),(SELECT item_name FROM mes_set_pollution_check WHERE biz_no='WM-AR-MOCK-01' ORDER BY id LIMIT 1),(SELECT item_spec FROM mes_set_pollution_check WHERE biz_no='WM-AR-MOCK-01' ORDER BY id LIMIT 1),'CONTROLLED_STORAGE','危废库密封桶+托盘',CONCAT('危废库A区-',LPAD(FLOOR(RAND()*9)+1,2,'0')),b'1','PROCESSING','周安环',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*3+1) DAY),'渗漏漆桶待转移处置-mock-seed-20260908',1),
((SELECT id FROM mes_set_pollution_check WHERE biz_no='PR-FB-MOCK-01' ORDER BY id LIMIT 1),(SELECT record_no FROM mes_set_pollution_check WHERE biz_no='PR-FB-MOCK-01' ORDER BY id LIMIT 1),'FINISHED_PRODUCT','PR-FB-MOCK-01',(SELECT batch_no FROM mes_set_pollution_check WHERE biz_no='PR-FB-MOCK-01' ORDER BY id LIMIT 1),(SELECT item_code FROM mes_set_pollution_check WHERE biz_no='PR-FB-MOCK-01' ORDER BY id LIMIT 1),(SELECT item_name FROM mes_set_pollution_check WHERE biz_no='PR-FB-MOCK-01' ORDER BY id LIMIT 1),(SELECT item_spec FROM mes_set_pollution_check WHERE biz_no='PR-FB-MOCK-01' ORDER BY id LIMIT 1),'ISOLATE_STORAGE','隔离区存放，返工处理','成品隔离区-01',b'1','STORED','张质检',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*2+1) DAY),'待返工方案审批-mock-seed-20260908',1),
((SELECT id FROM mes_set_pollution_check WHERE biz_no='WM-OR-MOCK-01' ORDER BY id LIMIT 1),(SELECT record_no FROM mes_set_pollution_check WHERE biz_no='WM-OR-MOCK-01' ORDER BY id LIMIT 1),'WASTE_INTERMEDIATE','WM-OR-MOCK-01',(SELECT batch_no FROM mes_set_pollution_check WHERE biz_no='WM-OR-MOCK-01' ORDER BY id LIMIT 1),(SELECT item_code FROM mes_set_pollution_check WHERE biz_no='WM-OR-MOCK-01' ORDER BY id LIMIT 1),(SELECT item_name FROM mes_set_pollution_check WHERE biz_no='WM-OR-MOCK-01' ORDER BY id LIMIT 1),(SELECT item_spec FROM mes_set_pollution_check WHERE biz_no='WM-OR-MOCK-01' ORDER BY id LIMIT 1),'MARKED_STORAGE','危废桶盛装 贴危废标签','危废库C区-01',b'1','DISPOSED','王环保',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5+2) DAY),'已交有资质单位处置-mock-seed-20260908',1);

SET FOREIGN_KEY_CHECKS=1;

