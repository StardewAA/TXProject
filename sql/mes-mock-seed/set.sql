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
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'PURCHASE_INBOUND','WM-AR-MOCK-01',(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1) ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),'环氧底漆-06','A+B 双组分 25kg/组','POLLUTED',86,'检测到桶体漆液渗漏残留，判定有污染风险','危废暂存库 密封托盘存放','POLLUTED','周安环',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10+5) DAY),'CONTROLLED_STORAGE','危废库密封桶+托盘',CONCAT('危废库A区-',LPAD(FLOOR(RAND()*9)+1,2,'0')),b'1','入库发现渗漏桶隔离-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'PURCHASE_INBOUND','WM-AR-MOCK-02',(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1) ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),'钢管-01','DN100*6mm Q235B','CLEAN',92,'表面无油污/无锈蚀污染物，图像特征正常','常规原料库 按批次上架','CLEAN','张质检',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*15+8) DAY),'NORMAL_INBOUND','常规库位上架',CONCAT('原料库B-',LPAD(FLOOR(RAND()*9)+1,2,'0')),b'0','无污染准予入库-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'MATERIAL_ISSUE','WM-MI-MOCK-01',(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='聚氨酯面漆-07' ORDER BY id LIMIT 1) ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item WHERE name='聚氨酯面漆-07' ORDER BY id LIMIT 1),'聚氨酯面漆-07','B05 灰色 20kg/桶','CLEAN',88,'开桶无滴漏，VOC快检低于阈值','涂装线漆料间','CLEAN','李车间',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*20+10) DAY),'ISSUE_ALLOWED','领用至涂装车间漆料暂存间','涂装车间漆料间-01',b'0','准予领用-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'WASTE_INTERMEDIATE','WM-RET-MOCK-01',CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT code FROM mes_md_item WHERE name='钢管-01' ORDER BY id LIMIT 1),'钢管打磨铁屑-01','DN100 打磨工序收尘渣','UNCERTAIN',71,'图像特征不清晰：疑似含油切削液污染，需人工核实','建议危废袋单独盛装待复核','CLEAN','周安环',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*8+3) DAY),'REUSE','分拣后回用（纯铁屑）','固废分拣区-02',b'0','人工确认无含油，按一般固废分拣回用-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'FINISHED_PRODUCT','PR-FB-MOCK-01',(SELECT code FROM mes_wm_batch WHERE item_id=(SELECT id FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1) ORDER BY RAND() LIMIT 1),(SELECT code FROM mes_md_item WHERE name='预制保温管-08' ORDER BY id LIMIT 1),'预制保温管-08','DN100 成品','POLLUTED',78,'表面检测到不明油污覆盖，成品批次受污染','隔离存放 禁止发运','POLLUTED','张质检',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*5+2) DAY),'ISOLATE_STORAGE','隔离区存放，返工处理','成品隔离区-01',b'1','成品油污污染已隔离-mock-seed-20260908',1),
(CONCAT('PC-',DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'),LPAD(FLOOR(RAND()*900)+100,3,'0')),'WASTE_INTERMEDIATE','WM-OR-MOCK-01',CONCAT('LOT',LPAD(FLOOR(RAND()*9999)+1000,4,'0')),(SELECT code FROM mes_md_item WHERE name='环氧底漆-06' ORDER BY id LIMIT 1),'废乳化液-02','机加工乳化液废液','POLLUTED',93,'乳化液废液属于危废（HW09），特征明显','危废桶收集 交有资质单位处置','POLLUTED','王环保',DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*3+1) DAY),'MARKED_STORAGE','危废桶盛装 贴危废标签','危废库C区-01',b'1','已联单登记-mock-seed-20260908',1);

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
