-- ============================================================
-- 天信管业集团（租户 2025）MES 系统测试数据
-- 基础数据/仓库/设备/工具/生产（防腐保温工序）/质量/排版
-- ============================================================
SET NAMES utf8mb4;

-- 幂等清理
DELETE FROM mes_wm_transaction WHERE tenant_id = 2025;
DELETE FROM mes_wm_transfer_detail WHERE tenant_id = 2025;
DELETE FROM mes_wm_transfer_line WHERE tenant_id = 2025;
DELETE FROM mes_wm_transfer WHERE tenant_id = 2025;
DELETE FROM mes_wm_package_line WHERE tenant_id = 2025;
DELETE FROM mes_wm_package WHERE tenant_id = 2025;
DELETE FROM mes_wm_product_sales_detail WHERE tenant_id = 2025;
DELETE FROM mes_wm_product_sales_line WHERE tenant_id = 2025;
DELETE FROM mes_wm_product_sales WHERE tenant_id = 2025;
DELETE FROM mes_wm_product_receipt_detail WHERE tenant_id = 2025;
DELETE FROM mes_wm_product_receipt_line WHERE tenant_id = 2025;
DELETE FROM mes_wm_product_receipt WHERE tenant_id = 2025;
DELETE FROM mes_wm_product_issue_detail WHERE tenant_id = 2025;
DELETE FROM mes_wm_product_issue_line WHERE tenant_id = 2025;
DELETE FROM mes_wm_product_issue WHERE tenant_id = 2025;
DELETE FROM mes_wm_item_receipt_detail WHERE tenant_id = 2025;
DELETE FROM mes_wm_item_receipt_line WHERE tenant_id = 2025;
DELETE FROM mes_wm_item_receipt WHERE tenant_id = 2025;
DELETE FROM mes_wm_material_stock WHERE tenant_id = 2025;
DELETE FROM mes_wm_batch WHERE tenant_id = 2025;
DELETE FROM mes_wm_warehouse_location WHERE tenant_id = 2025;
DELETE FROM mes_wm_warehouse_area WHERE tenant_id = 2025;
DELETE FROM mes_wm_warehouse WHERE tenant_id = 2025;
DELETE FROM mes_dv_repair_line WHERE tenant_id = 2025;
DELETE FROM mes_dv_repair WHERE tenant_id = 2025;
DELETE FROM mes_dv_mainten_record_line WHERE tenant_id = 2025;
DELETE FROM mes_dv_mainten_record WHERE tenant_id = 2025;
DELETE FROM mes_dv_check_record_line WHERE tenant_id = 2025;
DELETE FROM mes_dv_check_record WHERE tenant_id = 2025;
DELETE FROM mes_dv_check_plan_subject WHERE tenant_id = 2025;
DELETE FROM mes_dv_check_plan_machinery WHERE tenant_id = 2025;
DELETE FROM mes_dv_check_plan WHERE tenant_id = 2025;
DELETE FROM mes_dv_subject WHERE tenant_id = 2025;
DELETE FROM mes_dv_machinery WHERE tenant_id = 2025;
DELETE FROM mes_dv_machinery_type WHERE tenant_id = 2025;
DELETE FROM mes_tm_tool WHERE tenant_id = 2025;
DELETE FROM mes_tm_tool_type WHERE tenant_id = 2025;
DELETE FROM mes_pro_work_record_log WHERE tenant_id = 2025;
DELETE FROM mes_pro_work_record WHERE tenant_id = 2025;
DELETE FROM mes_pro_feedback WHERE tenant_id = 2025;
DELETE FROM mes_pro_card_process WHERE tenant_id = 2025;
DELETE FROM mes_pro_card WHERE tenant_id = 2025;
DELETE FROM mes_pro_task_issue WHERE tenant_id = 2025;
DELETE FROM mes_pro_task WHERE tenant_id = 2025;
DELETE FROM mes_pro_work_order_bom WHERE tenant_id = 2025;
DELETE FROM mes_pro_work_order WHERE tenant_id = 2025;
DELETE FROM mes_pro_route_product_bom WHERE tenant_id = 2025;
DELETE FROM mes_pro_route_product WHERE tenant_id = 2025;
DELETE FROM mes_pro_route_process WHERE tenant_id = 2025;
DELETE FROM mes_pro_route WHERE tenant_id = 2025;
DELETE FROM mes_pro_process_content WHERE tenant_id = 2025;
DELETE FROM mes_pro_process WHERE tenant_id = 2025;
DELETE FROM mes_qc_defect_record WHERE tenant_id = 2025;
DELETE FROM mes_qc_defect WHERE tenant_id = 2025;
DELETE FROM mes_qc_oqc_line WHERE tenant_id = 2025;
DELETE FROM mes_qc_oqc WHERE tenant_id = 2025;
DELETE FROM mes_qc_ipqc_line WHERE tenant_id = 2025;
DELETE FROM mes_qc_ipqc WHERE tenant_id = 2025;
DELETE FROM mes_qc_iqc_line WHERE tenant_id = 2025;
DELETE FROM mes_qc_iqc WHERE tenant_id = 2025;
DELETE FROM mes_qc_indicator_result_detail WHERE tenant_id = 2025;
DELETE FROM mes_qc_indicator_result WHERE tenant_id = 2025;
DELETE FROM mes_qc_template_item WHERE tenant_id = 2025;
DELETE FROM mes_qc_template_indicator WHERE tenant_id = 2025;
DELETE FROM mes_qc_template WHERE tenant_id = 2025;
DELETE FROM mes_qc_indicator WHERE tenant_id = 2025;
DELETE FROM mes_md_workstation_machine WHERE tenant_id = 2025;
DELETE FROM mes_md_workstation_tool WHERE tenant_id = 2025;
DELETE FROM mes_md_workstation_worker WHERE tenant_id = 2025;
DELETE FROM mes_md_workstation WHERE tenant_id = 2025;
DELETE FROM mes_md_workshop WHERE tenant_id = 2025;
DELETE FROM mes_md_product_sip WHERE tenant_id = 2025;
DELETE FROM mes_md_product_sop WHERE tenant_id = 2025;
DELETE FROM mes_md_product_bom WHERE tenant_id = 2025;
DELETE FROM mes_md_client WHERE tenant_id = 2025;
DELETE FROM mes_md_vendor WHERE tenant_id = 2025;
DELETE FROM mes_md_item WHERE tenant_id = 2025;
DELETE FROM mes_md_item_type WHERE tenant_id = 2025;
DELETE FROM mes_md_unit_measure WHERE tenant_id = 2025;
DELETE FROM mes_md_auto_code_rule WHERE tenant_id = 2025;
DELETE FROM mes_cal_holiday WHERE tenant_id = 2025;
DELETE FROM mes_cal_plan_team WHERE tenant_id = 2025;
DELETE FROM mes_cal_plan_shift WHERE tenant_id = 2025;
DELETE FROM mes_cal_plan WHERE tenant_id = 2025;
DELETE FROM mes_cal_team_shift WHERE tenant_id = 2025;
DELETE FROM mes_cal_team_member WHERE tenant_id = 2025;
DELETE FROM mes_cal_team WHERE tenant_id = 2025;

INSERT INTO mes_md_workshop (id, code, name, area, charge_user_id, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'WS-PF', '防腐车间', 1200.00, 81020, 0, '3PE 防腐钢管生产线', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'WS-BW', '保温车间', 1500.00, 81020, 0, '预制直埋保温管生产线', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'WS-JG', '机加工车间', 800.00, 81019, 0, '管材下料与管件加工', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'WS-QC', '成品检验区', 500.00, 81022, 0, '成品检验与包装', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_workstation (id, code, name, address, workshop_id, process_id, warehouse_id, location_id, area_id, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'ST-01', '钢管上料工位', '机加工车间 1 跨', 2000002, 2000001, 2000000, 2000000, 2000000, 0, '钢管上料与校直', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'ST-02', '抛丸除锈工位', '防腐车间 1 线', 2000000, 2000002, 2000000, 2000000, 2000000, 0, '抛丸除锈至 Sa2.5', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'ST-03', '3PE 涂敷工位', '防腐车间 1 线', 2000000, 2000003, 2000000, 2000000, 2000000, 0, '环氧粉末+胶粘剂+聚乙烯三层结构涂敷', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'ST-04', '电火花检漏工位', '防腐车间 1 线', 2000000, 2000004, 2000000, 2000000, 2000000, 0, '电火花检漏 25kV', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'ST-05', '聚氨酯发泡工位', '保温车间 1 线', 2000001, 2000005, 2000000, 2000000, 2000000, 0, '聚氨酯硬泡连续发泡', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'ST-06', 'HDPE 外护套工位', '保温车间 1 线', 2000001, 2000006, 2000000, 2000000, 2000000, 0, 'HDPE 外护管安装/热缩', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'ST-07', '成品检验工位', '成品检验区', 2000003, 2000007, 2000000, 2000000, 2000000, 0, '导热系数/密度/外观检验', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'ST-08', '打标入库工位', '成品检验区', 2000003, 2000008, 2000000, 2000000, 2000000, 0, '标识打印与入库', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_item_type (id, code, name, parent_id, item_or_product, sort, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'RAW', '原料', 0, 1, 1, 0, '原材料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'SEMI', '半成品', 0, 1, 2, 0, '半成品', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'FIN', '成品', 0, 2, 3, 0, '产成品', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'AUX', '辅料', 0, 1, 4, 0, '辅料与耗材', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'PKG', '包装材料', 0, 1, 5, 0, '包装与标识材料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'RAW-PU', '发泡原料', 0, 1, 6, 0, '聚氨酯组合料等危化原料', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_unit_measure (id, code, name, primary_flag, primary_id, change_rate, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'M', '米', 1, NULL, 1, 0, '长度单位', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'M3', '立方米', 1, NULL, 1, 0, '体积单位', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'BARREL', '桶', 1, NULL, 1, 0, '涂料计量', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'SET', '组', 1, NULL, 1, 0, '成套计量', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'SUIT', '套', 1, NULL, 1, 0, '套件计量', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'M2', '平方米', 1, NULL, 1, 0, '面积单位', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'KG', '千克', 1, NULL, 1, 0, '质量单位', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'PCS', '根/支', 1, NULL, 1, 0, '件数单位', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_item (id, code, name, specification, unit_measure_id, item_type_id, status, safe_stock_flag, min_stock, max_stock, high_value, batch_flag, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'MAT-PIPE-219', '无缝钢管 219×6', 'GB/T 8163-2018', 2000000, 2000000, 0, 1, 0, 100, 0, 1, '直埋保温管工作钢管基材', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'MAT-PIPE-426', '无缝钢管 426×8', 'GB/T 9711-2017', 2000000, 2000000, 0, 1, 0, 100, 0, 1, '3PE 防腐钢管基管', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'MAT-PU-A', '聚氨酯组合料（白料）', 'GB/T 50538-2020', 2000005, 2000006, 0, 1, 0, 100, 1, 1, '异氰酸酯/多元醇组合料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'MAT-PU-B', '聚氨酯组合料（黑料）', 'GB/T 50538-2020', 2000005, 2000006, 0, 1, 0, 100, 1, 1, 'MDI 黑料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'MAT-HDPE', 'HDPE 粒料', 'GB/T 29047-2012', 2000000, 2000006, 0, 1, 0, 100, 0, 1, '外护管挤塑原料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'MAT-EP', '环氧粉末涂料', 'GB/T 37594-2019', 2000000, 2000006, 0, 1, 0, 100, 0, 1, 'FBE 环氧粉末', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'MAT-AD', '胶粘剂（PE 共聚物）', 'GB/T 23257-2017', 2000000, 2000006, 0, 1, 0, 100, 0, 1, '3PE 胶粘剂层原料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'MAT-YW', '岩棉管壳 DN57×50', 'GB/T 11835-2016', 2000000, 2000001, 0, 1, 0, 100, 0, 1, '中温管道保温材料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000008, 'MAT-GSL', '硅酸铝针刺毯', 'GB/T 3003-2017', 2000000, 2000001, 0, 1, 0, 100, 0, 1, '高温隔热材料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000009, 'MAT-HXFZ', '环氧富锌底漆 20kg', 'HG/T 3668-2020', 2000000, 2000002, 0, 1, 0, 100, 0, 1, '重防腐底漆', '1', NOW(), '1', NOW(), b'0', 2025),
(2000010, 'MAT-BLLP', '玻璃鳞片胶泥 25kg', 'HG/T 2669-2014', 2000000, 2000002, 0, 1, 0, 100, 0, 1, '耐蚀衬里胶泥', '1', NOW(), '1', NOW(), b'0', 2025),
(2000011, 'SEMI-PIPE-3PE', '3PE 防腐半成品钢管', 'GB/T 23257-2017', 2000001, 2000000, 0, 1, 0, 100, 0, 1, '已完成 3PE 涂敷待检钢管', '1', NOW(), '1', NOW(), b'0', 2025),
(2000012, 'FIN-BW-200', '预制直埋保温管 DN200', 'CJ/T 114-2000', 2000002, 2000000, 0, 1, 0, 100, 1, 1, '直埋供热保温管成品', '1', NOW(), '1', NOW(), b'0', 2025),
(2000013, 'FIN-FF-400', '3PE 防腐钢管 DN400×8', 'GB/T 23257-2017', 2000002, 2000000, 0, 1, 0, 100, 1, 1, '3PE 防腐钢管成品', '1', NOW(), '1', NOW(), b'0', 2025),
(2000014, 'MAT-DXTP', '镀锌铁皮护壳 0.5mm', 'GB/T 2518-2019', 2000000, 2000005, 0, 1, 0, 100, 0, 1, '外护金属制品', '1', NOW(), '1', NOW(), b'0', 2025),
(2000015, 'MAT-RSSD', '热收缩带补口 300mm', 'GB/T 23257-2017', 2000000, 2000000, 0, 1, 0, 100, 0, 1, '现场补口材料', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_vendor (id, code, name, nickname, english_name, description, logo, level, score, address, website, email, telephone, contact1_name, contact1_telephone, contact1_email, contact2_name, contact2_telephone, contact2_email, credit_code, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'V-001', '河北华美节能科技集团有限公司', '华美', 'HUAMEI', '岩棉/玻璃棉制造商', NULL, 1, 5, '河北省廊坊市', 'www.huamei.com', 'buy@huamei.com', '0316-5851000', '刘广军', '13803160001', 'liugj@huamei.com', '王强', '13803160002', 'wangq@huamei.com', '91130500MA0XXXXX01', 0, '岩棉供应商', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'V-002', '山东鲁阳节能材料股份有限公司', '鲁阳', 'LUYANG', '硅酸铝制造商', NULL, 1, 5, '山东省沂源县', 'www.luyang.com', 'buy@luyang.com', '0533-3241000', '张传军', '13805330002', 'zcj@luyang.com', '李娜', '13805330003', 'lina@luyang.com', '91370300MA0XXXXX02', 0, '硅酸铝供应商', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'V-003', '江苏雅克科技股份有限公司', '雅克', 'YOKE', '聚氨酯组合料制造商', NULL, 1, 4, '江苏省宜兴市', 'www.yoke.com', 'buy@yoke.com', '0510-8786000', '陈俊', '13805100003', 'chenj@yoke.com', NULL, NULL, NULL, '91320200MA0XXXXX03', 0, 'PU 原料供应商', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'V-004', '天津灯塔涂料有限公司', '灯塔', 'DENGTA', '防腐涂料制造商', NULL, 1, 4, '天津市北辰区', 'www.dengta.com', 'buy@dengta.com', '022-2688000', '王立新', '13802200004', 'wlx@dengta.com', NULL, NULL, NULL, '91120100MA0XXXXX04', 0, '防腐涂料供应商', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'V-005', '宝鸡石油钢管有限责任公司', '宝管', 'BSG', '钢管制造商', NULL, 1, 5, '陕西省宝鸡市', 'www.bsg.com', 'buy@bsg.com', '0917-3398000', '孙建国', '13809170006', 'sjg@bsg.com', NULL, NULL, NULL, '91610300MA0XXXXX05', 0, '钢管基管供应商', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'V-006', '青岛汇通防腐材料有限公司', '汇通', 'HUITONG', '防腐补口材料制造商', NULL, 1, 3, '山东省青岛市', 'www.huitong.com', 'buy@huitong.com', '0532-8877000', '李强', '13805320007', 'liq@huitong.com', NULL, NULL, NULL, '91370200MA0XXXXX06', 0, '补口材料供应商', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'V-007', '山西潞安特种纤维有限公司', '潞安', 'LUAN', '气凝胶制造商', NULL, 1, 4, '山西省长治市', 'www.luan.com', 'buy@luan.com', '0355-2099000', '周永刚', '13803550008', 'zyg@luan.com', NULL, NULL, NULL, '91140000MA0XXXXX07', 0, '气凝胶供应商', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'V-008', '廊坊市中油管道防腐工程有限公司', '中油防腐', 'ZYPF', '3PE 防腐加工协作', NULL, 1, 3, '河北省廊坊市', 'www.zyff.com', 'coop@zyff.com', '0316-6099000', '赵东升', '13803160009', 'zds@zyff.com', NULL, NULL, NULL, '91131000MA0XXXXX08', 0, '外协加工单位', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_client (id, code, name, nickname, english_name, description, logo, type, address, website, email, telephone, contact1_name, contact1_telephone, contact1_email, contact2_name, contact2_telephone, contact2_email, credit_code, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'C-001', '延安市热力集团', '延热', 'YANRELI', '市政供热管网客户', NULL, 1, '延安市宝塔区', 'www.yananreli.com', 'info@yananreli.com', '0911-2118000', '刘建国', '13909130001', 'liujg@yananreli.com', NULL, NULL, NULL, '91610600MA0XXXXX01', 0, '直埋保温管客户', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'C-002', '延长石油（集团）有限责任公司', '延长石油', 'YXPC', '油气田客户', NULL, 1, '延安市宝塔区', 'www.sxycpc.com', 'buy@sxycpc.com', '029-8886000', '郭志强', '13909130015', 'guozq@yxpc.com', NULL, NULL, NULL, '91610600MA0XXXXX02', 0, '3PE 钢管客户', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'C-003', '兰州石化维修改造公司', '兰化检修', 'LZSH', '石化检修客户', NULL, 1, '兰州市西固区', 'www.lzshwx.com', 'wx@lzshwx.com', '0931-7988000', '郑海龙', '13909130010', 'zhenghl@lzshwx.com', NULL, NULL, NULL, '91620100MA0XXXXX03', 0, '防腐保温大修客户', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'C-004', '宁夏电力检修工程有限公司', '宁电检修', 'NXDJ', '电力检修客户', NULL, 1, '银川市兴庆区', 'www.nxdj.com', 'wx@nxdj.com', '0951-4108000', '白建军', '13909130018', 'baijj@nxdj.com', NULL, NULL, NULL, '91640100MA0XXXXX04', 0, '烟道防腐客户', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'C-005', '秦川化工产业园发展有限公司', '秦川化工', 'QCHG', '化工园区客户', NULL, 1, '渭南市蒲城县', 'www.qinchuanchem.com', 'procure@qinchuanchem.com', '0913-2266000', '张海涛', '13909130002', 'zhanght@qinchuanchem.com', NULL, NULL, NULL, '91610500MA0XXXXX05', 0, '储罐防腐衬里客户', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'C-006', '陇原能源发电有限责任公司', '陇原能源', 'LYNY', '火电客户', NULL, 1, '兰州市西固区', 'www.longyuanpower.com', 'buy@longyuanpower.com', '0931-4567000', '王永刚', '13909130003', 'wangyg@longyuanpower.com', NULL, NULL, NULL, '91620100MA0XXXXX06', 0, '蒸汽管道保温客户', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_product_bom (id, item_id, bom_item_id, quantity, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000012, 2000000, 1.00, 0, '直埋保温管工作钢管', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000012, 2000002, 4.50, 0, '聚氨酯白料 kg/米', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000012, 2000003, 4.50, 0, '聚氨酯黑料 kg/米', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000012, 2000004, 1.80, 0, 'HDPE 外护粒料 kg/米', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000013, 2000001, 1.00, 0, '3PE 钢管基管 米/米', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000013, 2000005, 0.55, 0, '环氧粉末 kg/米', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 2000013, 2000006, 0.45, 0, '胶粘剂 kg/米', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 2000013, 2000004, 2.60, 0, 'HDPE 外护粒料 kg/米', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_product_sop (id, item_id, sort, process_id, title, description, url, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000012, 1, 2000002, '抛丸除锈作业指导书', '除锈等级 Sa2.5，表面粗糙度 40-70μm，锚纹深度检查', '/sop/shotblast.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000012, 2, 2000005, '聚氨酯发泡作业指导书', '黑白料 1:1，料温 20-25℃，发泡密度≥60kg/m³', '/sop/pu-foam.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000012, 3, 2000006, 'HDPE 外护套安装作业指导书', '外护管对中、热缩带搭接 50mm，外观无皱褶', '/sop/hdpe-jacket.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000013, 1, 2000003, '3PE 涂敷作业指导书', '环氧粉末≥150μm，胶粘剂≥170μm，聚乙烯≥2.5mm', '/sop/3pe-coat.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000013, 2, 2000004, '电火花检漏作业指导书', '检漏电压 25kV，无漏点', '/sop/spark-test.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000012, 4, 2000009, '排版套料作业指导书', '按管径排版，套料利用率≥95%，余料登记', '/sop/nesting.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_product_sip (id, item_id, sort, process_id, title, description, url, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000012, 1, 2000007, '直埋保温管成品检验规程', '导热系数≤0.033，密度≥60kg/m³，外护层无划伤', '/sip/bw-final.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000013, 1, 2000007, '3PE 钢管成品检验规程', '涂层厚度≥4.2mm，附着力 1 级，电火花无漏点', '/sip/3pe-final.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 1, 2000001, '钢管来料检验规程', '壁厚公差±10%，椭圆度≤1%，材质单齐全', '/sip/pipe-iqc.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_auto_code_rule (id, code, name, description, max_length, padded, padded_char, padded_method, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'WO', '生产工单编码', 'WO-{yyyyMMdd}-{seq}', 20, 1, '0', 1, 0, '工单编码规则', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'TASK', '生产任务编码', 'TS-{yyyyMMdd}-{seq}', 20, 1, '0', 1, 0, '任务编码规则', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'CARD', '流转卡编码', 'CD-{yyyyMMdd}-{seq}', 20, 1, '0', 1, 0, '流转卡编码规则', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'BATCH', '批次编码', 'B-{yyyyMMdd}-{seq}', 20, 1, '0', 1, 0, '批次编码规则', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'PACK', '装箱编码', 'PK-{yyyyMMdd}-{seq}', 20, 1, '0', 1, 0, '装箱/排版编码规则', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_warehouse (id, code, name, address, area, charge_user_id, frozen, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'WH-MAT', '延安材料总库', '延安市宝塔区新材料产业园', 6000, 81024, 0, '保温/防腐材料库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'WH-FIN', '延安成品库', '延安市宝塔区预制基地', 8000, 81024, 0, '预制保温管/3PE 钢管成品堆场', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'WH-SITE', '榆林项目现场库', '榆林市榆阳区煤化工园区', 2000, 81025, 0, '项目现场周转库', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_warehouse_location (id, code, name, warehouse_id, area, frozen, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'LOC-A-01', 'A 区货位 01', 2000000, 200.00, 0, '保温材料货位', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'LOC-A-02', 'A 区货位 02', 2000000, 200.00, 0, '保温材料货位', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'LOC-B-01', 'B 区货位 01', 2000000, 200.00, 0, '防腐涂料货位', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'LOC-B-02', 'B 区货位 02', 2000000, 200.00, 0, '防腐涂料货位', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'LOC-C-01', 'C 区货位 01', 2000000, 150.00, 0, '危化品专区', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'YD-01', '一号堆场', 2000001, 4000.00, 0, '直埋保温管堆场', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'YD-02', '二号堆场', 2000001, 4000.00, 0, '3PE 钢管堆场', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'XC-01', '现场库区 01', 2000002, 2000.00, 0, '项目现场材料', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_warehouse_area (id, code, name, location_id, area, max_load, position_x, position_y, position_z, status, frozen, allow_item_mixing, allow_batch_mixing, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'AR-A', 'A 区', 2000000, 1000.00, 5000.00, 1, 1, 1, 0, 0, 1, 1, '保温材料区', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'AR-B', 'B 区', 2000002, 800.00, 4000.00, 1, 1, 1, 0, 0, 1, 1, '防腐涂料区', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'AR-C', 'C 区（危化品）', 2000004, 300.00, 1000.00, 1, 1, 1, 0, 0, 0, 0, '危化品专区', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'AR-YD', '成品堆场区', 2000005, 8000.00, 50000.00, 1, 1, 1, 0, 0, 1, 1, '成品堆场', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'AR-XC', '现场库区', 2000007, 2000.00, 10000.00, 1, 1, 1, 0, 0, 1, 1, '项目现场库区', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_batch (id, code, item_id, produce_date, expire_date, receipt_date, vendor_id, client_id, sales_order_code, purchase_order_code, work_order_id, task_id, workstation_id, tool_id, mold_id, lot_number, quality_status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'B2026060501', 2000000, '2026-06-01', NULL, '2026-06-05', 2000004, NULL, NULL, 'PO-2026-0001', NULL, NULL, NULL, NULL, NULL, 'LOT-001', 0, '宝管基管批次', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'B2026061201', 2000009, '2026-06-05', '2027-06-05', '2026-06-12', 2000003, NULL, NULL, 'PO-2026-0002', NULL, NULL, NULL, NULL, NULL, 'LOT-002', 0, '富锌底漆批次', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'B2026061801', 2000002, '2026-06-10', '2026-12-10', '2026-06-18', 2000002, NULL, NULL, 'PO-2026-0003', NULL, NULL, NULL, NULL, NULL, 'LOT-003', 0, 'PU 白料批次', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'B2026061802', 2000003, '2026-06-10', '2026-12-10', '2026-06-18', 2000002, NULL, NULL, 'PO-2026-0003', NULL, NULL, NULL, NULL, NULL, 'LOT-004', 0, 'PU 黑料批次', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'B2026062501', 2000001, '2026-06-18', NULL, '2026-06-25', 2000004, NULL, NULL, 'PO-2026-0004', NULL, NULL, NULL, NULL, NULL, 'LOT-005', 0, '426 基管批次', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'B2026070201', 2000008, '2026-06-25', NULL, '2026-07-02', 2000001, NULL, NULL, 'PO-2026-0005', NULL, NULL, NULL, NULL, NULL, 'LOT-006', 0, '硅酸铝毯批次', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'B2026071001', 2000005, '2026-07-05', '2027-07-05', '2026-07-10', 2000003, NULL, NULL, 'PO-2026-0006', NULL, NULL, NULL, NULL, NULL, 'LOT-007', 0, '环氧粉末批次', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'B2026071501', 2000004, '2026-07-08', NULL, '2026-07-15', 2000005, NULL, NULL, 'PO-2026-0007', NULL, NULL, NULL, NULL, NULL, 'LOT-008', 0, 'HDPE 粒料批次', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_material_stock (id, item_type_id, item_id, batch_id, batch_code, warehouse_id, location_id, area_id, vendor_id, quantity, receipt_time, frozen, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, 2000000, 'B2026060501', 2000000, 2000000, 2000000, 2000004, 120.00, '2026-06-05', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000001, 2000001, 'B2026062501', 2000000, 2000005, 2000005, 2000004, 80.00, '2026-06-25', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 2000009, 2000002, 'B2026061201', 2000000, 2000002, 2000002, 2000003, 65.00, '2026-06-12', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000000, 2000002, 2000000, 'B2026061801', 2000000, 2000000, 2000000, 2000002, 300.00, '2026-06-18', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000000, 2000003, 2000000, 'B2026061802', 2000000, 2000000, 2000000, 2000002, 300.00, '2026-06-18', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000000, 2000008, 2000000, 'B2026070201', 2000000, 2000000, 2000000, 2000001, 180.00, '2026-07-02', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 2000000, 2000005, 2000000, 'B2026071001', 2000000, 2000000, 2000000, 2000003, 150.00, '2026-07-10', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 2000000, 2000004, 2000000, 'B2026071501', 2000000, 2000000, 2000000, 2000005, 500.00, '2026-07-15', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000008, 2000000, 2000007, 2000000, 'B2026060501', 2000000, 2000000, 2000000, 2000001, 200.00, '2026-06-05', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000009, 2000000, 2000010, 2000000, 'B2026061201', 2000000, 2000001, 2000001, 2000003, 90.00, '2026-06-12', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000010, 2000000, 2000007, 2000007, 'B2026060501', 2000000, 2000000, 2000000, 2000001, 80.00, '2026-06-05', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000011, 2000000, 2000009, 2000007, 'B2026061201', 2000000, 2000000, 2000000, 2000003, 20.00, '2026-06-12', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000012, 2000000, 2000000, 2000005, 'B2026060501', 2000000, 2000000, 2000000, 2000004, 480.00, '2026-06-05', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(2000013, 2000000, 2000001, 2000006, 'B2026062501', 2000000, 2000000, 2000000, 2000004, 350.00, '2026-06-25', 0, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_item_receipt (id, code, name, iqc_id, notice_id, purchase_order_code, vendor_id, receipt_date, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'IR-2026-0001', '岩棉管壳采购入库', NULL, NULL, 'PO-2026-0001', 2000001, '2026-06-05', 20, '采购入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'IR-2026-0002', '环氧富锌底漆采购入库', NULL, NULL, 'PO-2026-0002', 2000003, '2026-06-12', 20, '采购入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'IR-2026-0003', 'PU 组合料采购入库', NULL, NULL, 'PO-2026-0003', 2000002, '2026-06-18', 20, '采购入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'IR-2026-0004', '426 基管采购入库', NULL, NULL, 'PO-2026-0004', 2000004, '2026-06-25', 20, '采购入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'IR-2026-0005', '硅酸铝毯采购入库', NULL, NULL, 'PO-2026-0005', 2000001, '2026-07-02', 20, '采购入库', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_item_receipt_line (id, receipt_id, arrival_notice_line_id, item_id, received_quantity, batch_id, batch_code, production_date, expire_date, lot_number, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, NULL, 2000007, 200.00, 2000000, 'B2026060501', '2026-06-01', NULL, 'LOT-001', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, NULL, 2000009, 120.00, 2000001, 'B2026061201', '2026-06-05', '2027-06-05', 'LOT-002', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, NULL, 2000002, 600.00, 2000002, 'B2026061801', '2026-06-10', '2026-12-10', 'LOT-003', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000003, NULL, 2000001, 100.00, 2000003, 'B2026062501', '2026-06-18', NULL, 'LOT-005', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000004, NULL, 2000008, 180.00, 2000004, 'B2026070201', '2026-06-25', NULL, 'LOT-006', NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_item_receipt_detail (id, line_id, receipt_id, item_id, quantity, batch_id, warehouse_id, location_id, area_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, 2000007, 200.00, 2000000, 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000001, 2000009, 120.00, 2000001, 2000002, 2000002, 2000001, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000002, 2000002, 600.00, 2000002, 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000003, 2000003, 2000001, 100.00, 2000003, 2000001, 2000005, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000004, 2000004, 2000008, 180.00, 2000004, 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_product_issue (id, code, name, workstation_id, work_order_id, task_id, issue_date, required_time, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'MI-2026-0001', '直埋保温管 WO-20260801 领料', 2000004, 2000000, NULL, '2026-08-01', '2026-08-01 08:00:00', 20, '聚氨酯发泡领料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'MI-2026-0002', '直埋保温管 WO-20260801 领料', 2000005, 2000000, NULL, '2026-08-01', '2026-08-01 08:10:00', 20, 'HDPE 外护套领料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'MI-2026-0003', '3PE 钢管 WO-20260802 领料', 2000002, 2000000, NULL, '2026-08-02', '2026-08-02 08:00:00', 20, '3PE 涂敷领料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'MI-2026-0004', '3PE 钢管 WO-20260802 领料', 2000001, 2000000, NULL, '2026-08-02', '2026-08-02 08:05:00', 20, '基管领料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'MI-2026-0005', '直埋保温管 WO-20260803 领料', 2000000, 2000000, NULL, '2026-08-05', '2026-08-05 08:00:00', 20, '工作钢管领料', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_product_issue_line (id, issue_id, item_id, quantity, batch_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000002, 600.00, 2000002, 'PU 白料领用', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000003, 600.00, 2000003, 'PU 黑料领用', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000001, 2000004, 240.00, 2000007, 'HDPE 粒料领用', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000002, 2000005, 80.00, 2000006, '环氧粉末领用', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000003, 2000001, 100.00, 2000004, '426 基管领用', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000004, 2000000, 80.00, 2000000, '219 基管领用', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_product_issue_detail (id, issue_id, line_id, material_stock_id, item_id, quantity, batch_id, batch_code, warehouse_id, location_id, area_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, 2000003, 2000002, 600.00, 2000002, 'B2026061801', 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000001, 2000004, 2000003, 600.00, 2000003, 'B2026061802', 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000001, 2000002, 2000007, 2000004, 240.00, 2000007, 'B2026071501', 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000002, 2000003, 2000006, 2000005, 80.00, 2000006, 'B2026071001', 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000003, 2000004, 2000001, 2000001, 100.00, 2000001, 'B2026062501', 2000000, 2000005, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000004, 2000005, 2000000, 2000000, 80.00, 2000000, 'B2026060501', 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_product_receipt (id, code, name, work_order_id, item_id, receipt_date, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'PR-2026-0001', '直埋保温管成品入库', 2000000, 2000012, '2026-08-10', 20, '成品入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'PR-2026-0002', '3PE 钢管成品入库', 2000001, 2000013, '2026-08-12', 20, '成品入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'PR-2026-0003', '直埋保温管成品入库', 2000002, 2000012, '2026-08-15', 20, '成品入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'PR-2026-0004', '3PE 钢管成品入库', 2000003, 2000013, '2026-08-18', 20, '成品入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'PR-2026-0005', '直埋保温管成品入库', 2000004, 2000012, '2026-08-20', 20, '成品入库', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_product_receipt_line (id, receipt_id, item_id, material_stock_id, quantity, batch_id, batch_code, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000012, 2000012, 600.00, 2000000, 'B2026081001', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000013, 2000013, 300.00, 2000001, 'B2026081201', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000012, 2000012, 450.00, 2000002, 'B2026081501', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000003, 2000013, 2000013, 260.00, 2000003, 'B2026081801', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000004, 2000012, 2000012, 500.00, 2000004, 'B2026082001', NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_product_receipt_detail (id, line_id, receipt_id, item_id, quantity, batch_id, warehouse_id, location_id, area_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, 2000012, 600.00, 2000000, 2000001, 2000005, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000001, 2000013, 300.00, 2000001, 2000001, 2000006, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000002, 2000012, 450.00, 2000002, 2000001, 2000005, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000003, 2000003, 2000013, 260.00, 2000003, 2000001, 2000006, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000004, 2000004, 2000012, 500.00, 2000004, 2000001, 2000005, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_product_sales (id, code, name, client_id, sales_order_code, sales_date, notice_id, contact_name, contact_telephone, contact_address, carrier, shipping_number, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(100100, 'SO-2026-0001', '延安热力直埋保温管发货', 2000000, 'SO-2026-0001', '2026-08-12', NULL, '刘建国', '13909130001', '延安市宝塔区', '陕J·88888', 'YA001', 20, '成品发货', '1', NOW(), '1', NOW(), b'0', 2025),
(100101, 'SO-2026-0002', '延长石油 3PE 钢管发货', 2000001, 'SO-2026-0002', '2026-08-14', NULL, '郭志强', '13909130015', '延安市宝塔区', '陕J·77777', 'YA002', 20, '成品发货', '1', NOW(), '1', NOW(), b'0', 2025),
(100102, 'SO-2026-0003', '兰州石化富锌底漆发货', 2000002, 'SO-2026-0003', '2026-08-16', NULL, '郑海龙', '13909130010', '兰州市西固区', '陕J·66666', 'YA003', 20, '材料发货', '1', NOW(), '1', NOW(), b'0', 2025),
(100103, 'SO-2026-0004', '宁夏电力鳞片胶泥发货', 2000003, 'SO-2026-0004', '2026-08-18', NULL, '白建军', '13909130018', '银川市兴庆区', '陕J·55555', 'YA004', 20, '材料发货', '1', NOW(), '1', NOW(), b'0', 2025),
(100104, 'SO-2026-0005', '秦川化工硅酸铝毯发货', 2000004, 'SO-2026-0005', '2026-08-20', NULL, '张海涛', '13909130002', '渭南市蒲城县', '陕J·44444', 'YA005', 20, '材料发货', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_product_sales_line (id, sales_id, notice_line_id, item_id, quantity, batch_id, batch_code, material_stock_id, oqc_check_flag, oqc_id, quality_status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(100100, 100100, NULL, 2000012, 600.00, 2000000, 'B2026081001', 2000012, 1, NULL, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(100101, 100101, NULL, 2000013, 300.00, 2000001, 'B2026081201', 2000013, 1, NULL, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(100102, 100102, NULL, 2000009, 65.00, 2000001, 'B2026061201', 2000002, 1, NULL, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(100103, 100103, NULL, 2000010, 90.00, 2000001, 'B2026061201', 2000001, 1, NULL, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(100104, 100104, NULL, 2000008, 180.00, 2000000, 'B2026070201', 2000000, 1, NULL, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_product_sales_detail (id, line_id, sales_id, item_id, quantity, material_stock_id, batch_id, batch_code, warehouse_id, location_id, area_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(100100, 100100, 100100, 2000012, 600.00, 2000012, 2000000, 'B2026081001', 2000001, 2000005, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(100101, 100101, 100101, 2000013, 300.00, 2000013, 2000001, 'B2026081201', 2000001, 2000006, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(100102, 100102, 100102, 2000009, 65.00, 2000002, 2000001, 'B2026061201', 2000000, 2000002, 2000001, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(100103, 100103, 100103, 2000010, 90.00, 2000003, 2000001, 'B2026061201', 2000000, 2000002, 2000001, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(100104, 100104, 100104, 2000008, 180.00, 2000004, 2000000, 'B2026070201', 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_transfer (id, code, name, type, delivery_flag, recipient_name, recipient_telephone, destination_address, carrier, shipping_number, confirm_flag, transfer_date, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(9002000, 'TR-2026-0001', '岩棉管壳调拨至榆林现场', 10, 0, '邓超', '13909120026', '榆林市榆阳区煤化工园区', '陕J·33333', 'T001', 1, '2026-08-05', 20, '项目调拨', '1', NOW(), '1', NOW(), b'0', 2025),
(9002001, 'TR-2026-0002', '富锌底漆调拨至榆林现场', 10, 0, '邓超', '13909120026', '榆林市榆阳区煤化工园区', '陕J·33334', 'T002', 1, '2026-08-06', 20, '项目调拨', '1', NOW(), '1', NOW(), b'0', 2025),
(9002002, 'TR-2026-0003', '热收缩带调拨至榆林现场', 10, 0, '邓超', '13909120026', '榆林市榆阳区煤化工园区', '陕J·33335', 'T003', 1, '2026-08-08', 20, '项目调拨', '1', NOW(), '1', NOW(), b'0', 2025),
(9002003, 'TR-2026-0004', '直埋保温管调拨至榆林现场', 10, 0, '邓超', '13909120026', '榆林市榆阳区煤化工园区', '陕J·33336', 'T004', 1, '2026-08-10', 20, '项目调拨', '1', NOW(), '1', NOW(), b'0', 2025),
(9002004, 'TR-2026-0005', '硅酸铝毯调拨至榆林现场', 10, 0, '邓超', '13909120026', '榆林市榆阳区煤化工园区', '陕J·33337', 'T005', 0, '2026-08-12', 10, '待确认调拨', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_transfer_line (id, transfer_id, material_stock_id, item_id, quantity, batch_id, from_warehouse_id, from_location_id, from_area_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(9002000, 9002000, 2000008, 2000007, 80.00, 2000000, 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(9002001, 9002001, 2000011, 2000009, 20.00, 2000001, 2000002, 2000002, 2000001, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(9002002, 9002002, 2000012, 2000015, 300.00, 2000002, 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(9002003, 9002003, 2000013, 2000000, 120.00, 2000003, 2000001, 2000005, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(9002004, 9002004, 2000014, 2000008, 40.00, 2000004, 2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_transfer_detail (id, line_id, transfer_id, item_id, quantity, batch_id, to_warehouse_id, to_location_id, to_area_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(9002000, 9002000, 9002000, 2000007, 80.00, 2000000, 2000002, 2000007, 2000004, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(9002001, 9002001, 9002001, 2000009, 20.00, 2000001, 2000002, 2000007, 2000004, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(9002002, 9002002, 9002002, 2000015, 300.00, 2000002, 2000002, 2000007, 2000004, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(9002003, 9002003, 9002003, 2000000, 120.00, 2000003, 2000002, 2000007, 2000004, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(9002004, 9002004, 9002004, 2000008, 40.00, 2000004, 2000002, 2000007, 2000004, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_package (id, code, parent_id, package_date, sales_order_code, invoice_code, client_id, length, width, height, size_unit_id, net_weight, gross_weight, weight_unit_id, inspector_user_id, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'PK-2026081001', 0, '2026-08-10', 'SO-2026-0001', 'FA2026081001', 2000000, 12.0, 2.4, 2.4, 2000000, 5000.0, 5200.0, 2000006, 81023, 20, '直埋保温管整托排版包装', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'PK-2026081201', 0, '2026-08-12', 'SO-2026-0002', 'FA2026081201', 2000001, 12.0, 2.6, 2.6, 2000000, 8800.0, 9100.0, 2000006, 81023, 20, '3PE 钢管整托排版包装', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'PK-2026081501', 0, '2026-08-15', 'SO-2026-0003', 'FA2026081501', 2000002, 1.2, 1.0, 1.0, 2000001, 780.0, 800.0, 2000006, 81023, 20, '涂料桶托盘排版', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'PK-2026081801', 0, '2026-08-18', 'SO-2026-0004', 'FA2026081801', 2000003, 1.2, 1.0, 1.0, 2000001, 950.0, 980.0, 2000006, 81023, 20, '鳞片胶泥托盘排版', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'PK-2026082001', 0, '2026-08-20', 'SO-2026-0005', 'FA2026082001', 2000004, 12.0, 2.2, 2.2, 2000000, 4200.0, 4350.0, 2000006, 81023, 20, '硅酸铝毯托盘排版', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_package_line (id, package_id, material_stock_id, item_id, quantity, work_order_id, expire_date, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000012, 2000012, 50.00, 2000000, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000013, 2000013, 25.00, 2000001, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000002, 2000009, 20.00, 2000002, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000003, 2000001, 2000010, 15.00, 2000003, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000004, 2000000, 2000008, 10.00, 2000004, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_wm_transaction (id, type, quantity, biz_type, biz_id, biz_code, biz_line_id, material_stock_id, related_transaction_id, item_id, batch_id, batch_code, warehouse_id, location_id, area_id, transaction_time, erp_time, receipt_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
( 2000000, 10, 50.00, 0, 2000000, 'TXN-2026-0001', 2000000, 2000000, 2000000, 2000000, 2000000, 'B2026060101', 2000001, 2000005, 2000003, '2026-07-10 10:00:00', NULL, '2026-07-10 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000001, 20, 50.00, 10, 2000001, 'TXN-2026-0002', 2000000, 2000001, 2000000, 2000001, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-11 10:00:00', NULL, '2026-07-11 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000002, 10, 50.00, 0, 2000002, 'TXN-2026-0003', 2000000, 2000002, 2000000, 2000002, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-12 10:00:00', NULL, '2026-07-12 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000003, 20, 50.00, 10, 2000003, 'TXN-2026-0004', 2000000, 2000003, 2000000, 2000003, 2000000, 'B2026060101', 2000001, 2000005, 2000003, '2026-07-13 10:00:00', NULL, '2026-07-13 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000004, 10, 50.00, 0, 2000004, 'TXN-2026-0005', 2000000, 2000004, 2000000, 2000004, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-14 10:00:00', NULL, '2026-07-14 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000005, 20, 50.00, 10, 2000000, 'TXN-2026-0006', 2000000, 2000005, 2000000, 2000005, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-15 10:00:00', NULL, '2026-07-15 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000006, 10, 50.00, 0, 2000001, 'TXN-2026-0007', 2000000, 2000006, 2000000, 2000006, 2000000, 'B2026060101', 2000001, 2000005, 2000003, '2026-07-16 10:00:00', NULL, '2026-07-16 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000007, 20, 50.00, 10, 2000002, 'TXN-2026-0008', 2000000, 2000007, 2000000, 2000007, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-17 10:00:00', NULL, '2026-07-17 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000008, 10, 50.00, 0, 2000003, 'TXN-2026-0009', 2000000, 2000008, 2000000, 2000008, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-18 10:00:00', NULL, '2026-07-18 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000009, 20, 50.00, 10, 2000004, 'TXN-2026-0010', 2000000, 2000009, 2000000, 2000009, 2000000, 'B2026060101', 2000001, 2000005, 2000003, '2026-07-19 10:00:00', NULL, '2026-07-19 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000010, 10, 50.00, 0, 2000000, 'TXN-2026-0011', 2000000, 2000010, 2000000, 2000010, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-20 10:00:00', NULL, '2026-07-20 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000011, 20, 50.00, 10, 2000001, 'TXN-2026-0012', 2000000, 2000011, 2000000, 2000011, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-21 10:00:00', NULL, '2026-07-21 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000012, 10, 50.00, 0, 2000002, 'TXN-2026-0013', 2000000, 2000012, 2000000, 2000012, 2000000, 'B2026060101', 2000001, 2000005, 2000003, '2026-07-22 10:00:00', NULL, '2026-07-22 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000013, 20, 50.00, 10, 2000003, 'TXN-2026-0014', 2000000, 2000013, 2000000, 2000013, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-23 10:00:00', NULL, '2026-07-23 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000014, 10, 50.00, 0, 2000004, 'TXN-2026-0015', 2000000, 2000014, 2000000, 2000014, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-24 10:00:00', NULL, '2026-07-24 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000015, 20, 50.00, 10, 2000000, 'TXN-2026-0016', 2000000, 2000015, 2000000, 2000015, 2000000, 'B2026060101', 2000001, 2000005, 2000003, '2026-07-25 10:00:00', NULL, '2026-07-25 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000016, 10, 50.00, 0, 2000001, 'TXN-2026-0017', 2000000, 2000000, 2000000, 2000000, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-26 10:00:00', NULL, '2026-07-26 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000017, 20, 50.00, 10, 2000002, 'TXN-2026-0018', 2000000, 2000001, 2000000, 2000001, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-27 10:00:00', NULL, '2026-07-27 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000018, 10, 50.00, 0, 2000003, 'TXN-2026-0019', 2000000, 2000002, 2000000, 2000002, 2000000, 'B2026060101', 2000001, 2000005, 2000003, '2026-07-28 10:00:00', NULL, '2026-07-28 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025),
( 2000019, 20, 50.00, 10, 2000004, 'TXN-2026-0020', 2000000, 2000003, 2000000, 2000003, 2000000, 'B2026060101', 2000000, 2000000, 2000000, '2026-07-29 10:00:00', NULL, '2026-07-29 10:00:00', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_machinery_type (id, code, name, parent_id, status, sort, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'MT-SHOT', '抛丸清理机', 0, 0, 1, '钢管外壁抛丸除锈', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'MT-3PE', '3PE 挤出涂敷线', 0, 0, 2, '环氧粉末+胶粘剂+聚乙烯涂敷', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'MT-FOAM', '聚氨酯发泡机', 0, 0, 3, '直埋保温管连续发泡', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'MT-SPARK', '电火花检漏仪', 0, 0, 4, '防腐层针孔检测', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'MT-BEND', '液压弯管机', 0, 0, 5, '管件弯制', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'MT-CRANE', '桥式起重机', 0, 0, 6, '成品吊运与装卸', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_machinery (id, code, name, brand, specification, machinery_type_id, workshop_id, status, last_mainten_time, last_check_time, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'MC-SHOT-01', 'Q6925 钢管外壁抛丸机', '青岛青力', 'Q6925', 2000000, 2000000, 0, '2026-07-01', '2026-08-01', '除锈生产线主机', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'MC-SHOT-02', 'Q6930 大型抛丸机', '青岛青力', 'Q6930', 2000000, 2000000, 0, '2026-06-15', '2026-08-01', '大管径除锈', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'MC-3PE-01', '3PE 三层结构挤出涂敷线', '江苏华光', 'HG-3PE1200', 2000001, 2000000, 0, '2026-07-10', '2026-08-02', 'DN100-DN1200 涂敷', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'MC-FOAM-01', '高压聚氨酯发泡机', '烟台东宇', 'DY-PU800', 2000002, 2000001, 0, '2026-07-20', '2026-08-03', '连续发泡生产线', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'MC-FOAM-02', '聚氨酯喷涂机', '固瑞克', 'GRACO XP70', 2000002, 2000001, 0, '2026-08-01', '2026-08-05', '现场喷涂备用', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'MC-SPARK-01', '电火花检漏仪', '英国 BUCKLEYS', 'DC30', 2000003, 2000000, 0, '2026-07-05', '2026-08-01', '25kV 检漏', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'MC-BEND-01', '液压弯管机', '上海冲剪', 'DW-168', 2000004, 2000002, 0, '2026-06-20', '2026-07-30', '管件弯制', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'MC-CRANE-01', '32t 桥式起重机', '河南矿山', 'QD32t', 2000005, 2000001, 0, '2026-06-10', '2026-07-28', '成品库吊装', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_subject (id, code, name, type, content, standard, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'SJ-01', '抛丸机叶轮磨损检查', 1, '叶轮片磨损≤1/3，无裂纹', '目视+卡尺', 0, '每周检查', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'SJ-02', '抛丸机除尘系统', 1, '除尘效率正常，无泄漏', '目视', 0, '每日检查', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'SJ-03', '挤出机温控精度', 1, '各区温度偏差±5℃', '数显', 0, '每班检查', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'SJ-04', '发泡机料温与配比', 1, '白黑料 1:1，料温 20-25℃', '流量计', 0, '每班检查', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'SJ-05', '电火花检漏仪校准', 1, '输出电压 25kV±5%', '校验台', 0, '每月检查', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'SJ-06', '起重机钢丝绳与限位', 1, '钢丝绳无断丝，限位可靠', '目视+试验', 0, '每周检查', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'SJ-07', '液压系统压力与泄漏', 1, '系统压力正常，无渗漏', '压力表', 0, '每周检查', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'SJ-08', '车间消防与危化品存放', 1, '灭火器压力正常，危化品分区存放', '目视', 0, '每日检查', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_check_plan (id, code, name, type, start_date, end_date, cycle_type, cycle_count, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'CP-DAILY', '设备日点检计划', 1, '2026-08-01', '2026-08-31', 1, 1, 0, '每日班前点检', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'CP-WEEK', '设备周保养计划', 2, '2026-08-01', '2026-08-31', 2, 1, 0, '每周保养', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'CP-MONTH', '设备月保养计划', 3, '2026-08-01', '2026-08-31', 3, 1, 0, '月度保养', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_check_plan_machinery (id, plan_id, machinery_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000001, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000001, 2000002, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000001, 2000005, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000002, 2000007, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_check_plan_subject (id, plan_id, subject_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000001, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 2000002, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000001, 2000003, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000001, 2000005, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000002, 2000004, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_check_record (id, plan_id, machinery_id, check_time, user_id, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, '2026-08-01 08:20:00', 81023, 1, '点检正常', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000003, '2026-08-01 08:25:00', 81023, 1, '发泡机点检正常', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000001, 2000002, '2026-08-02 09:00:00', 81019, 1, '挤出线保养完成', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_check_record_line (id, record_id, subject_id, check_status, check_result, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, 1, '正常', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000001, 1, '正常', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000001, 2000003, 1, '正常', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000001, 2000002, 1, '正常', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000002, 2000002, 1, '温控精度达标', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000002, 2000005, 1, '限位可靠', NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_mainten_record (id, plan_id, machinery_id, mainten_time, user_id, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000001, 2000002, '2026-08-02 10:00:00', 81019, 1, '挤出线一级保养', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000005, '2026-08-03 10:00:00', 81023, 1, '电火花仪校准保养', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000007, '2026-08-04 10:00:00', 81024, 1, '起重机月度保养', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_mainten_record_line (id, record_id, subject_id, status, result, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000003, 1, '齿轮箱换油完成', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000002, 1, '温控元件校验完成', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000001, 2000004, 1, '输出电压校准合格', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000002, 2000005, 1, '钢丝绳涂油检查完成', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000002, 2000006, 1, '限位开关测试合格', NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_repair (id, code, name, machinery_id, require_date, finish_date, confirm_date, result, accepted_user_id, confirm_user_id, source_doc_type, source_doc_id, source_doc_code, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'RP-2026-0001', '抛丸机叶轮更换', 2000000, '2026-07-20', '2026-07-21', '2026-07-21 16:00:00', 1, 81023, 81019, 1, 2000000, 'MC-SHOT-01', 20, '叶轮磨损更换', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'RP-2026-0002', '发泡机料泵维修', 2000003, '2026-07-25', '2026-07-26', '2026-07-26 15:00:00', 1, 81020, 81019, 2, 2000003, 'MC-FOAM-01', 20, '料泵密封更换', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'RP-2026-0003', '起重机小车限位修复', 2000007, '2026-08-05', '2026-08-06', NULL, NULL, 81024, NULL, NULL, 2000007, 'MC-CRANE-01', 10, '限位开关故障待修', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_dv_repair_line (id, repair_id, subject_id, malfunction, malfunction_url, description, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, '叶轮磨损严重', NULL, '更换 8 片叶轮并做动平衡', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000003, '料泵密封渗漏', NULL, '更换机械密封，试机正常', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000005, '限位开关失灵', NULL, '待更换限位开关', NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_tm_tool_type (id, code, name, code_flag, mainten_type, mainten_period, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'TT-SPRAY', '喷涂工具', 0, 1, 30, '喷砂/喷漆工具', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'TT-TORQUE', '扭矩工具', 0, 2, 90, '扭矩扳手', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'TT-MEASURE', '检测量具', 0, 3, 180, '测厚仪/检漏仪探头', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'TT-CUT', '切割工具', 0, 4, 60, '切割机/角磨机', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'TT-FOAM', '发泡工具', 0, 5, 30, '发泡枪及配件', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_tm_tool (id, code, name, brand, specification, tool_type_id, quantity, available_quantity, mainten_type, next_mainten_period, next_mainten_date, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'TL-01', '喷砂枪', '诺顿', 'P2', 2000000, 10, 10, 1, 30, '2026-09-01', 0, '抛丸工位用', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'TL-02', '无气喷涂机', '固瑞克', 'XTREME X70', 2000000, 4, 4, 1, 30, '2026-09-01', 0, '涂装工位用', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'TL-03', '扭矩扳手 300N·m', '史丹利', 'MXL-300', 2000001, 6, 6, 2, 90, '2026-10-01', 0, '外护管紧固', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'TL-04', '涂层测厚仪', '德国 EPK', 'MINITEST 600', 2000002, 3, 3, 3, 180, '2026-12-01', 0, '涂层厚度检测', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'TL-05', '电火花检漏探头', 'BUCKLEYS', 'DC30-P', 2000002, 2, 2, 3, 180, '2026-12-01', 0, '针孔检测', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'TL-06', '切割机', '博世', 'GCO 2000', 2000003, 5, 5, 4, 60, '2026-09-15', 0, '管件切割', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'TL-07', '角磨机', '博世', 'GWS 750', 2000003, 8, 8, 4, 60, '2026-09-15', 0, '焊口打磨', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'TL-08', '聚氨酯发泡枪', '格雷斯', 'GX-7', 2000004, 4, 4, 5, 30, '2026-09-01', 0, '现场补口发泡', '1', NOW(), '1', NOW(), b'0', 2025),
(2000008, 'TL-09', '红外测温枪', '福禄克', '62 MAX', 2000002, 5, 5, 3, 180, '2026-12-01', 0, '发泡温度监控', '1', NOW(), '1', NOW(), b'0', 2025),
(2000009, 'TL-10', '热成像仪', '海康', 'HM-TP31', 2000002, 1, 1, 3, 180, '2026-12-01', 0, '保温热损耗检测', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_process (id, code, name, attention, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'P-01', '钢管检验与下料', '核对材质单、壁厚公差±10%', 0, '防腐保温首道工序', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'P-02', '抛丸除锈', '除锈等级 Sa2.5，粗糙度 40-70μm', 0, '基管表面处理', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'P-03', '3PE 涂敷', '环氧粉末≥150μm+胶粘剂≥170μm+聚乙烯≥2.5mm', 0, '三层结构防腐涂敷', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'P-04', '电火花检漏', '检漏电压 25kV，无漏点', 0, '防腐层完整性检测', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'P-05', '聚氨酯发泡保温', '黑白料 1:1，密度≥60kg/m³，导热系数≤0.033', 0, '直埋管保温层成型', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'P-06', 'HDPE 外护套安装', '外护管对中，热缩搭接≥50mm，外观无皱褶', 0, '外护层安装', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'P-07', '成品检验', '导热系数/密度/外观/外护层厚度', 0, '出厂前终检', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'P-08', '标识与包装入库', '喷码标识、排版装箱、入库', 0, '完工入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000008, 'P-09', '排版套料', '按管径/长度排版，套料利用率≥95%', 0, '排版管理工序', '1', NOW(), '1', NOW(), b'0', 2025),
(2000009, 'P-10', '现场补口施工', '热收缩带/电热熔套补口，外观检查', 0, '管网施工现场补口', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_process_content (id, process_id, sort, content, device, material, doc_url, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 1, '核对到货钢管材质单与炉批号，测量壁厚、椭圆度，合格后按排版单下料', '切割机', '无缝钢管 219×6', '/doc/process-01.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 1, '抛丸除锈至 Sa2.5，粗糙度 40-70μm，除锈后 4 小时内涂装', '抛丸机 Q6925', '钢丸 G25', '/doc/process-02.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 1, '环氧粉末静电喷涂≥150μm，胶粘剂侧向挤出≥170μm，聚乙烯层≥2.5mm', '3PE 涂敷线', '环氧粉末/胶粘剂/HDPE', '/doc/process-03.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000003, 1, '25kV 电火花检漏，无漏点；发现漏点用热缩带修补后复检', '电火花检漏仪', '', '/doc/process-04.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000004, 1, '白黑料 1:1 混合，料温 20-25℃，一次发泡成型，密度≥60kg/m³', '聚氨酯发泡机', 'PU 组合料', '/doc/process-05.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000005, 1, 'HDPE 外护管对中安装，电热熔/热缩搭接≥50mm，表面无皱褶划伤', '外护套工位', 'HDPE 外护管/热缩带', '/doc/process-06.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 2000006, 1, '终检：导热系数≤0.033、密度≥60kg/m³、外护层厚度与外观', '成品检验工位', '', '/doc/process-07.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 2000007, 1, '喷码标识（规格/批次/日期），按排版单装箱，办理入库', '打标入库工位', '', '/doc/process-08.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000008, 2000008, 1, '根据订单管径与长度排版，余料登记，套料利用率≥95%', '技术室', '', '/doc/process-09.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000009, 2000009, 1, '现场补口：管端打磨→涂底胶→热收缩带/电热熔套→外观检查', '现场补口机组', '热收缩带/电热熔套', '/doc/process-10.pdf', NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_route (id, code, name, description, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'RT-BW-200', '预制直埋保温管标准路线', '下料→除锈→发泡→外护套→检验→标识入库（含排版）', 0, 'DN200 直埋保温管生产工艺路线', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'RT-3PE-400', '3PE 防腐钢管标准路线', '下料→除锈→3PE 涂敷→电火花检漏→检验→标识入库', 0, 'DN400 3PE 防腐钢管工艺路线', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'RT-SITE', '现场补口施工路线', '现场验收→补口→外观检查→验收交付', 0, '供热管网现场补口施工路线', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_route_process (id, route_id, process_id, sort, next_process_id, link_type, prepare_time, wait_time, color_code, key_flag, check_flag, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000008, 1, 2000000, 0, 1, 1, '#FFC000', 1, 0, '排版套料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000000, 2, 2000001, 0, 1, 1, '#00B050', 1, 0, '钢管检验下料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 2000001, 3, 2000002, 0, 1, 1, '#00B050', 1, 0, '抛丸除锈', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000000, 2000004, 4, 2000003, 0, 1, 1, '#00B050', 1, 0, '聚氨酯发泡保温', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000000, 2000005, 5, 2000004, 0, 1, 1, '#00B050', 1, 0, 'HDPE 外护套安装', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000000, 2000006, 6, 2000005, 0, 1, 1, '#0070C0', 0, 1, '成品检验', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 2000000, 2000007, 7, 2000006, 0, 1, 1, '#00B050', 1, 0, '标识与包装入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 2000001, 2000000, 1, 2000000, 0, 1, 1, '#00B050', 1, 0, '钢管检验下料', '1', NOW(), '1', NOW(), b'0', 2025),
(2000008, 2000001, 2000001, 2, 2000001, 0, 1, 1, '#00B050', 1, 0, '抛丸除锈', '1', NOW(), '1', NOW(), b'0', 2025),
(2000009, 2000001, 2000002, 3, 2000002, 0, 1, 1, '#FFC000', 1, 0, '3PE 涂敷', '1', NOW(), '1', NOW(), b'0', 2025),
(2000010, 2000001, 2000003, 4, 2000003, 0, 1, 1, '#FFC000', 0, 1, '电火花检漏', '1', NOW(), '1', NOW(), b'0', 2025),
(2000011, 2000001, 2000006, 5, 2000004, 0, 1, 1, '#0070C0', 0, 1, '成品检验', '1', NOW(), '1', NOW(), b'0', 2025),
(2000012, 2000001, 2000007, 6, 2000005, 0, 1, 1, '#00B050', 1, 0, '标识与包装入库', '1', NOW(), '1', NOW(), b'0', 2025),
(2000013, 2000002, 2000009, 1, 2000000, 0, 1, 1, '#7030A0', 1, 0, '现场补口施工', '1', NOW(), '1', NOW(), b'0', 2025),
(2000014, 2000002, 2000006, 2, 2000001, 0, 1, 1, '#0070C0', 0, 1, '补口外观检查', '1', NOW(), '1', NOW(), b'0', 2025),
(2000015, 2000002, 2000007, 3, 2000002, 0, 1, 1, '#00B050', 1, 0, '验收交付', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_route_product (id, route_id, item_id, quantity, production_time, time_unit_type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000012, 1.00, 8, 'HOUR', '直埋保温管成品 米/工时', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000013, 1.00, 6, 'HOUR', '3PE 钢管成品 米/工时', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000015, 10.00, 1, 'HOUR', '补口施工 米/工时', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_route_product_bom (id, route_id, process_id, product_id, item_id, quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000004, 2000012, 2000000, 1.00, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000004, 2000012, 2000002, 4.50, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 2000004, 2000012, 2000003, 4.50, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000000, 2000005, 2000012, 2000004, 1.80, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000001, 2000002, 2000013, 2000005, 0.55, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000001, 2000002, 2000013, 2000006, 0.45, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_work_order (id, code, name, type, order_source_type, order_source_code, product_id, quantity, quantity_produced, quantity_changed, quantity_scheduled, client_id, vendor_id, batch_code, request_date, parent_id, finish_date, cancel_date, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'WO-2026080001', '直埋保温管 DN200 生产工单 0801', 1, 20, 'SO-2026-0001', 2000012, 600, 600, 0, 600, 2000000, 2000000, 'B2026081001', '2026-08-20', 0, NULL, NULL, 10, '延安热力订单生产', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'WO-2026080002', '3PE 防腐钢管 DN400 生产工单 0802', 1, 20, 'SO-2026-0002', 2000013, 300, 300, 0, 300, 2000001, 2000001, 'B2026081201', '2026-08-22', 0, NULL, NULL, 10, '延长石油订单生产', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'WO-2026080003', '直埋保温管 DN200 生产工单 0803', 1, 10, 'SO-2026-0005', 2000012, 450, 450, 0, 450, 2000000, 2000000, 'B2026081501', '2026-08-25', 0, NULL, NULL, 10, '秦川化工订单生产', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'WO-2026080004', '3PE 防腐钢管 DN400 生产工单 0804', 1, 10, 'SO-2026-0003', 2000013, 260, 260, 0, 260, 2000001, 2000001, 'B2026081801', '2026-08-28', 0, NULL, NULL, 10, '兰州石化订单生产', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'WO-2026080005', '直埋保温管 DN200 生产工单 0805', 1, 10, 'SO-2026-0004', 2000012, 500, 500, 0, 500, 2000000, 2000000, 'B2026082001', '2026-08-30', 0, NULL, NULL, 10, '宁夏电力订单生产', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_work_order_bom (id, work_order_id, item_id, quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, 600.00, '工作钢管用量', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000002, 2700.00, 'PU 白料用量 kg', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 2000003, 2700.00, 'PU 黑料用量 kg', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000000, 2000004, 1080.00, 'HDPE 粒料用量 kg', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000001, 2000001, 300.00, '426 基管用量', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000001, 2000005, 165.00, '环氧粉末用量 kg', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 2000001, 2000006, 135.00, '胶粘剂用量 kg', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 2000001, 2000004, 780.00, 'HDPE 粒料用量 kg', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_task (id, code, name, work_order_id, workstation_id, route_id, process_id, item_id, quantity, produced_quantity, qualify_quantity, unqualify_quantity, changed_quantity, client_id, start_time, duration, end_time, color_code, finish_date, cancel_date, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'TS-2026080001', '下料任务 0801', 2000000, 2000007, 2000000, 2000000, 2000000, 600, 600, 600, 0, 0, 2000000, '2026-08-05 08:00:00', 8, '2026-08-05 16:00:00', '#00B050', '2026-08-05 16:00:00', NULL, 20, '下料完成', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'TS-2026080002', '除锈任务 0801', 2000000, 2000001, 2000000, 2000001, 2000000, 600, 600, 600, 0, 0, 2000000, '2026-08-06 08:00:00', 8, '2026-08-06 16:00:00', '#00B050', '2026-08-06 16:00:00', NULL, 20, '除锈完成', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'TS-2026080003', '发泡任务 0801', 2000000, 2000004, 2000000, 2000004, 2000000, 600, 600, 590, 10, 0, 2000000, '2026-08-07 08:00:00', 8, '2026-08-07 16:00:00', '#00B050', '2026-08-07 16:00:00', NULL, 20, '发泡完成 10 米待修', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'TS-2026080004', '外护套任务 0801', 2000000, 2000005, 2000000, 2000005, 2000000, 590, 590, 590, 0, 0, 2000000, '2026-08-08 08:00:00', 8, '2026-08-08 16:00:00', '#00B050', '2026-08-08 16:00:00', NULL, 20, '外护套完成', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'TS-2026080005', '检验任务 0801', 2000000, 2000006, 2000000, 2000006, 2000000, 590, 590, 590, 0, 0, 2000000, '2026-08-09 08:00:00', 4, '2026-08-09 12:00:00', '#00B050', '2026-08-09 12:00:00', NULL, 20, '检验合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'TS-2026080006', '3PE 除锈任务 0802', 2000001, 2000001, 2000001, 2000001, 2000001, 300, 300, 300, 0, 0, 2000001, '2026-08-06 08:00:00', 6, '2026-08-06 14:00:00', '#00B050', '2026-08-06 14:00:00', NULL, 20, '除锈完成', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'TS-2026080007', '3PE 涂敷任务 0802', 2000001, 2000002, 2000001, 2000002, 2000001, 300, 300, 295, 5, 0, 2000001, '2026-08-07 08:00:00', 8, '2026-08-07 16:00:00', '#FFC000', '2026-08-07 16:00:00', NULL, 20, '涂敷完成 5 米返修', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'TS-2026080008', '电火花检漏任务 0802', 2000001, 2000003, 2000001, 2000003, 2000001, 300, 300, 295, 5, 0, 2000001, '2026-08-08 08:00:00', 4, '2026-08-08 12:00:00', '#FFC000', '2026-08-08 12:00:00', NULL, 20, '检漏 5 米漏点修补后合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000008, 'TS-2026080009', '直埋保温管下料任务 0803', 2000002, 2000000, 2000000, 2000000, 2000000, 450, 200, 200, 0, 0, 2000000, '2026-08-10 08:00:00', 8, NULL, '#00B0F0', NULL, NULL, 10, '生产中', '1', NOW(), '1', NOW(), b'0', 2025),
(2000009, 'TS-2026080010', '直埋保温管除锈任务 0803', 2000002, 2000001, 2000000, 2000001, 2000000, 450, 150, 150, 0, 0, 2000000, '2026-08-11 08:00:00', 8, NULL, '#00B0F0', NULL, NULL, 10, '生产中', '1', NOW(), '1', NOW(), b'0', 2025),
(2000010, 'TS-2026080011', '3PE 下料任务 0804', 2000003, 2000000, 2000001, 2000000, 2000001, 260, 0, 0, 0, 0, 2000001, '2026-08-12 08:00:00', 6, NULL, '#0070C0', NULL, NULL, 10, '待投产', '1', NOW(), '1', NOW(), b'0', 2025),
(2000011, 'TS-2026080012', '直埋保温管发泡任务 0803', 2000002, 2000004, 2000000, 2000004, 2000000, 450, 0, 0, 0, 0, 2000000, '2026-08-13 08:00:00', 8, NULL, '#0070C0', NULL, NULL, 10, '待投产', '1', NOW(), '1', NOW(), b'0', 2025),
(2000012, 'TS-2026080013', '3PE 涂敷任务 0804', 2000003, 2000002, 2000001, 2000002, 2000001, 260, 0, 0, 0, 0, 2000001, '2026-08-14 08:00:00', 8, NULL, '#0070C0', NULL, NULL, 10, '待投产', '1', NOW(), '1', NOW(), b'0', 2025),
(2000013, 'TS-2026080014', '直埋保温管外护套任务 0805', 2000004, 2000005, 2000000, 2000005, 2000000, 500, 0, 0, 0, 0, 2000000, '2026-08-15 08:00:00', 8, NULL, '#0070C0', NULL, NULL, 10, '待投产', '1', NOW(), '1', NOW(), b'0', 2025),
(2000014, 'TS-2026080015', '现场补口任务 0805', 2000004, 2000009, 2000002, 2000009, 2000015, 2000, 800, 800, 0, 0, 2000002, '2026-08-16 08:00:00', 16, NULL, '#7030A0', NULL, NULL, 10, '现场补口施工中', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_task_issue (id, task_id, work_order_id, workstation_id, source_doc_id, source_doc_code, source_doc_type, batch_code, source_line_id, item_id, unit_measure_id, issued_quantity, available_quantity, used_quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, 2000007, 2000000, 'WO-2026080001', 10, 'B2026060501', NULL, 2000000, 2000000, 600.00, 600.00, 0.00, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000000, 2000001, 2000000, 'WO-2026080001', 10, 'B2026060501', NULL, 2000000, 2000000, 600.00, 600.00, 0.00, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000000, 2000004, 2000000, 'WO-2026080001', 10, 'B2026061801', NULL, 2000002, 2000000, 600.00, 600.00, 0.00, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000003, 2000000, 2000005, 2000000, 'WO-2026080001', 10, 'B2026071501', NULL, 2000004, 2000000, 240.00, 240.00, 0.00, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000005, 2000001, 2000001, 2000001, 'WO-2026080002', 10, 'B2026062501', NULL, 2000001, 2000000, 300.00, 300.00, 0.00, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000006, 2000001, 2000002, 2000001, 'WO-2026080002', 10, 'B2026071001', NULL, 2000005, 2000000, 165.00, 165.00, 0.00, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_card (id, code, work_order_id, batch_code, item_id, transfered_quantity, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'CD-2026080001', 2000000, 'B2026081001', 2000012, 600.00, 20, '直埋保温管流转卡', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'CD-2026080002', 2000001, 'B2026081201', 2000013, 300.00, 20, '3PE 钢管流转卡', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'CD-2026080003', 2000002, 'B2026081501', 2000012, 450.00, 10, '直埋保温管流转卡', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'CD-2026080004', 2000003, 'B2026081801', 2000013, 260.00, 10, '3PE 钢管流转卡', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'CD-2026080005', 2000004, 'B2026082001', 2000012, 500.00, 10, '直埋保温管流转卡', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_card_process (id, card_id, sort, process_id, input_time, output_time, input_quantity, output_quantity, unqualified_quantity, workstation_id, user_id, ipqc_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 1, 2000008, '2026-08-05 09:00:00', '2026-08-05 12:00:00', 600.00, 600.00, 0.00, 2000007, 81020, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2, 2000001, '2026-08-06 09:00:00', '2026-08-06 16:00:00', 600.00, 600.00, 0.00, 2000001, 81020, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 3, 2000004, '2026-08-07 09:00:00', '2026-08-07 16:00:00', 600.00, 590.00, 10.00, 2000004, 81020, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000000, 4, 2000005, '2026-08-08 09:00:00', '2026-08-08 16:00:00', 590.00, 590.00, 0.00, 2000005, 81020, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000000, 5, 2000006, '2026-08-09 09:00:00', '2026-08-09 12:00:00', 590.00, 590.00, 0.00, 2000006, 81023, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000001, 1, 2000000, '2026-08-06 09:00:00', '2026-08-06 12:00:00', 300.00, 300.00, 0.00, 2000000, 81020, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 2000001, 2, 2000001, '2026-08-06 13:00:00', '2026-08-06 16:00:00', 300.00, 300.00, 0.00, 2000001, 81020, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 2000001, 3, 2000002, '2026-08-07 09:00:00', '2026-08-07 16:00:00', 300.00, 295.00, 5.00, 2000002, 81020, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000008, 2000001, 4, 2000003, '2026-08-08 09:00:00', '2026-08-08 12:00:00', 300.00, 295.00, 5.00, 2000003, 81023, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_feedback (id, code, type, channel, feedback_time, workstation_id, route_id, process_id, work_order_id, task_id, item_id, expire_date, lot_number, scheduled_quantity, feedback_quantity, qualified_quantity, unqualified_quantity, uncheck_quantity, labor_scrap_quantity, material_scrap_quantity, other_scrap_quantity, feedback_user_id, approve_user_id, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'FB-2026080001', 1, 1, '2026-08-05 12:00:00', 2000007, 2000000, 2000000, 2000000, 2000000, 2000000, NULL, NULL, 600.00, 600.00, 600.00, 0.00, 0.00, 0.00, 0.00, 0.00, 81020, 81016, 20, '下料报工合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'FB-2026080002', 1, 1, '2026-08-06 16:00:00', 2000001, 2000000, 2000001, 2000000, 2000000, 2000000, NULL, NULL, 600.00, 600.00, 600.00, 0.00, 0.00, 0.00, 0.00, 0.00, 81020, 81016, 20, '除锈报工合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'FB-2026080003', 1, 1, '2026-08-07 16:00:00', 2000004, 2000000, 2000004, 2000000, 2000000, 2000000, '2026-08-10', NULL, 600.00, 590.00, 590.00, 10.00, 5.00, 5.00, 0.00, 0.00, 81020, 81016, 20, '发泡报工 10 米不合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'FB-2026080004', 1, 1, '2026-08-08 16:00:00', 2000005, 2000000, 2000005, 2000000, 2000000, 2000000, NULL, NULL, 590.00, 590.00, 590.00, 0.00, 0.00, 0.00, 0.00, 0.00, 81020, 81016, 20, '外护套报工合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'FB-2026080005', 1, 1, '2026-08-09 12:00:00', 2000006, 2000000, 2000006, 2000000, 2000000, 2000000, NULL, NULL, 590.00, 590.00, 590.00, 0.00, 0.00, 0.00, 0.00, 0.00, 81023, 81016, 20, '成品检验报工合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'FB-2026080006', 1, 1, '2026-08-07 16:00:00', 2000002, 2000001, 2000002, 2000001, 2000001, 2000001, NULL, NULL, 300.00, 295.00, 295.00, 5.00, 2.00, 3.00, 0.00, 0.00, 81020, 81018, 20, '3PE 涂敷报工 5 米返修', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'FB-2026080007', 1, 1, '2026-08-08 12:00:00', 2000003, 2000001, 2000003, 2000001, 2000001, 2000001, NULL, NULL, 300.00, 295.00, 295.00, 5.00, 0.00, 5.00, 0.00, 0.00, 81023, 81018, 20, '检漏返修后合格', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_pro_work_record (id, user_id, workstation_id, type, clock_in_time, clock_out_time, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 81020, 2000007, 1, '2026-08-05 08:00:00', '2026-08-05 17:00:00', '下料班', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 81021, 2000001, 1, '2026-08-06 08:00:00', '2026-08-06 17:00:00', '除锈班', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 81019, 2000004, 1, '2026-08-07 08:00:00', '2026-08-07 17:00:00', '发泡班', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 81022, 2000002, 1, '2026-08-07 08:00:00', '2026-08-07 17:00:00', '3PE 涂敷班', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 81023, 2000006, 1, '2026-08-09 08:00:00', '2026-08-09 12:00:00', '检验班', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_indicator (id, code, name, type, tool, result_type, result_specification, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'IND-01', '导热系数', 1, '导热系数测定仪', 1, '≤0.033 W/(m·K)', '保温性能核心指标', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'IND-02', '芯密度', 1, '电子天平+量具', 1, '≥60 kg/m³', '保温层密度', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'IND-03', '压缩强度', 1, '万能试验机', 1, '≥0.3 MPa', '保温层强度', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'IND-04', '涂层厚度', 1, '涂层测厚仪', 1, '总厚≥4.2mm', '3PE 防腐层厚度', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'IND-05', '附着力', 1, '拉开法测试仪', 1, '≥5 MPa', '防腐层附着力', '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 'IND-06', '电火花漏点', 2, '电火花检漏仪', 2, '25kV 无漏点', '防腐层完整性', '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 'IND-07', '外护层厚度', 1, '测厚仪', 1, '≥3.6mm', 'HDPE 外护层', '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 'IND-08', '吸水率', 1, '煮沸法', 1, '≤3%', '保温层吸水率', '1', NOW(), '1', NOW(), b'0', 2025),
(2000008, 'IND-09', '阻燃等级', 2, '氧指数仪', 2, 'B1 级', '保温材料阻燃', '1', NOW(), '1', NOW(), b'0', 2025),
(2000009, 'IND-10', '渣球含量', 1, '筛分法', 1, '≤10%', '岩棉制品指标', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_template (id, code, name, types, remark, creator, create_time, updater, update_time, deleted, tenant_id, status) VALUES
(2000000, 'TPL-BW', '直埋保温管成品检验方案', '3', '终检：导热系数/密度/外观/外护层', '1', NOW(), '1', NOW(), b'0', 2025, 0),
(2000001, 'TPL-3PE', '3PE 防腐钢管检验方案', '2,3', '涂层厚度/附着力/电火花检漏', '1', NOW(), '1', NOW(), b'0', 2025, 0),
(2000002, 'TPL-IQC', '原材料来料检验方案', '1', '钢管材质/涂料批次/岩棉密度', '1', NOW(), '1', NOW(), b'0', 2025, 0);

INSERT INTO mes_qc_template_indicator (id, template_id, indicator_id, check_method, standard_value, unit_measure_id, threshold_max, threshold_min, doc_url, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, '仪器法', 0.033, 2000001, 0.033, 0, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000001, '称量法', 60, 2000006, 100, 60, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 2000006, '测厚仪', 3.6, 2000000, 100, 3.6, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000001, 2000003, '测厚仪', 4.2, 2000000, 100, 4.2, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000001, 2000004, '拉开法', 5, 2000006, 100, 5, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000005, 2000001, 2000005, '电火花', 25, 2000007, 25, 0, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000006, 2000002, 2000009, '筛分法', 10, 2000006, 10, 0, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000007, 2000002, 2000008, '氧指数', 32, 2000007, 32, 28, NULL, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_template_item (id, template_id, item_id, quantity_check, quantity_unqualified, critical_rate, major_rate, minor_rate, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000012, 1, 0, 0.01, 0.02, 0.03, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000013, 1, 0, 0.01, 0.02, 0.03, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000000, 1, 0, 0.01, 0.02, 0.03, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_iqc (id, code, name, template_id, source_doc_id, source_doc_type, source_line_id, source_doc_code, vendor_id, vendor_batch, item_id, received_quantity, check_quantity, qualified_quantity, unqualified_quantity, critical_rate, major_rate, minor_rate, critical_quantity, major_quantity, minor_quantity, check_result, receive_date, inspect_date, inspector_user_id, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'IQC-2026080001', '钢管基管来料检验', 2000002, 2000000, 100, 2000000, 'PO-2026-0001', 2000004, 'B2026060501', 2000000, 200.00, 10.00, 200.00, 0.00, 0.01, 0.02, 0.03, 0, 0, 0, 1, '2026-06-05', '2026-06-05 11:00:00', 81023, 20, '基管来料检验合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'IQC-2026080002', '富锌底漆来料检验', 2000002, 2000001, 100, 2000001, 'PO-2026-0002', 2000003, 'B2026061201', 2000009, 120.00, 5.00, 120.00, 0.00, 0.01, 0.02, 0.03, 0, 0, 0, 1, '2026-06-12', '2026-06-12 11:30:00', 81023, 20, '涂料来料检验合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'IQC-2026080003', '硅酸铝毯来料检验', 2000002, 2000004, 100, 2000004, 'PO-2026-0005', 2000001, 'B2026070201', 2000008, 180.00, 8.00, 180.00, 0.00, 0.01, 0.02, 0.03, 0, 0, 0, 1, '2026-07-02', '2026-07-02 11:00:00', 81023, 20, '硅酸铝毯来料检验合格', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_iqc_line (id, iqc_id, indicator_id, tool, check_method, standard_value, unit_measure_id, max_threshold, min_threshold, critical_quantity, major_quantity, minor_quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000009, '卡尺', '壁厚公差±10%', 10, 2000000, 10, -10, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 2000000, '仪器法', '固体含量≥70%', 70, 2000006, 100, 70, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000002, 2000009, '筛分法', '渣球含量≤10%', 10, 2000006, 10, 0, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_ipqc (id, code, name, type, template_id, source_doc_id, source_doc_type, source_line_id, source_doc_code, work_order_id, task_id, workstation_id, process_id, item_id, check_quantity, qualified_quantity, unqualified_quantity, labor_scrap_quantity, material_scrap_quantity, other_scrap_quantity, critical_rate, major_rate, minor_rate, critical_quantity, major_quantity, minor_quantity, check_result, inspect_date, inspector_user_id, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'IPQC-2026080001', '发泡过程检验', 1, 2000001, 2000000, 304, 2000002, 'WO-2026080001', 2000000, 2000002, 2000004, 2000004, 2000012, 20.00, 20.00, 0.00, 0.00, 0.00, 0.00, 0.01, 0.02, 0.03, 0, 0, 0, 1, '2026-08-07 10:00:00', 81023, 20, '过程检验合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'IPQC-2026080002', '3PE 涂敷过程检验', 1, 2000001, 2000001, 304, 2000006, 'WO-2026080002', 2000001, 2000006, 2000002, 2000002, 2000013, 15.00, 15.00, 0.00, 0.00, 0.00, 0.00, 0.01, 0.02, 0.03, 0, 0, 0, 1, '2026-08-07 14:00:00', 81023, 20, '涂层厚度过程合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'IPQC-2026080003', '外护套过程检验', 1, 2000000, 2000000, 304, 2000003, 'WO-2026080001', 2000000, 2000003, 2000005, 2000005, 2000012, 10.00, 10.00, 0.00, 0.00, 0.00, 0.00, 0.01, 0.02, 0.03, 0, 0, 0, 1, '2026-08-08 10:00:00', 81023, 20, '外护套过程合格', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_ipqc_line (id, ipqc_id, indicator_id, tool, check_method, standard_value, unit_measure_id, max_threshold, min_threshold, critical_quantity, major_quantity, minor_quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000001, '称量法', '密度≥60kg/m³', 60, 2000006, 100, 60, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000000, '仪器法', '导热系数≤0.033', 0.033, 2000001, 0.033, 0, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000001, 2000003, '测厚仪', '3PE 总厚≥4.2mm', 4.2, 2000000, 100, 4.2, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000002, 2000006, '测厚仪', '外护层≥3.6mm', 3.6, 2000000, 100, 3.6, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_oqc (id, code, name, template_id, source_doc_id, source_doc_type, source_doc_code, source_line_id, client_id, batch_code, item_id, min_check_quantity, max_unqualified_quantity, out_quantity, check_quantity, qualified_quantity, unqualified_quantity, critical_rate, major_rate, minor_rate, critical_quantity, major_quantity, minor_quantity, check_result, out_date, inspect_date, inspector_user_id, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'OQC-2026080001', '直埋保温管出货检验', 2000000, 100100, 118, 'SO-2026-0001', 100100, 2000000, 'B2026081001', 2000012, 10.00, 2.00, 600.00, 10.00, 10.00, 0.00, 0.01, 0.02, 0.03, 0, 0, 0, 1, '2026-08-10', '2026-08-10 10:00:00', 81023, 20, '出货检验合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'OQC-2026080002', '3PE 钢管出货检验', 2000001, 100101, 118, 'SO-2026-0002', 100101, 2000001, 'B2026081201', 2000013, 10.00, 2.00, 300.00, 10.00, 10.00, 0.00, 0.01, 0.02, 0.03, 0, 0, 0, 1, '2026-08-12', '2026-08-12 10:00:00', 81023, 20, '出货检验合格', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'OQC-2026080003', '直埋保温管出货检验', 2000000, 100104, 118, 'SO-2026-0005', 100104, 2000000, 'B2026081501', 2000012, 10.00, 2.00, 450.00, 10.00, 10.00, 0.00, 0.01, 0.02, 0.03, 0, 0, 0, 1, '2026-08-15', '2026-08-15 10:00:00', 81023, 20, '出货检验合格', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_oqc_line (id, oqc_id, indicator_id, tool, check_method, standard_value, unit_measure_id, max_threshold, min_threshold, critical_quantity, major_quantity, minor_quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, '仪器法', '导热系数≤0.033', 0.033, 2000001, 0.033, 0, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000001, '称量法', '密度≥60kg/m³', 60, 2000006, 100, 60, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000001, 2000003, '测厚仪', '3PE 总厚≥4.2mm', 4.2, 2000000, 100, 4.2, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000001, 2000005, '电火花', '25kV 无漏点', 25, 2000007, 25, 0, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 2000002, 2000006, '测厚仪', '外护层≥3.6mm', 3.6, 2000000, 100, 3.6, 0, 0, 0, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_defect (id, code, name, type, level, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'DEF-01', '防腐层漏点', 1, 1, '电火花检漏发现针孔', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'DEF-02', '保温层空鼓', 1, 2, '发泡不均导致空鼓', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 'DEF-03', '外护管划伤', 1, 2, '吊装运输划伤', '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 'DEF-04', '保温密度不达标', 1, 1, '密度低于 60kg/m³', '1', NOW(), '1', NOW(), b'0', 2025),
(2000004, 'DEF-05', '涂层厚度不足', 1, 1, '3PE 总厚低于 4.2mm', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_qc_defect_record (id, qc_type, qc_id, line_id, name, level, quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 1, 2000002, 2000002, '发泡密度不达标', 1, 10.00, '10 米保温层密度不达标返修', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 1, 2000006, 2000006, '3PE 涂层漏点', 1, 5.00, '5 米电火花漏点修补', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 3, 2000002, 2000002, '外护管划伤', 2, 2.00, '2 处划伤补修', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_cal_team (id, code, name, calendar_type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'TEAM-A', '防腐一班', 1, '3PE 防腐线白班', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 'TEAM-B', '保温一班', 1, '保温线白班', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_cal_team_member (id, team_id, user_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 81020, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 81021, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000001, 81020, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000001, 81019, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_cal_plan (id, code, name, calendar_type, start_date, end_date, shift_type, shift_method, shift_count, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 'PLAN-202608', '2026 年 8 月排班计划', 1, '2026-08-01', '2026-08-31', 1, 1, 1, 0, '两班制排班', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_cal_plan_shift (id, plan_id, sort, name, start_time, end_time, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 1, '白班', '08:00', '17:00', '正常班', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2, '中班', '17:00', '24:00', '加班班次', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000000, 3, '夜班', '00:00', '08:00', '夜班', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_cal_plan_team (id, plan_id, team_id, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000001, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_cal_team_shift (id, plan_id, team_id, shift_id, day, sort, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000000, 2000000, 2000000, '2026-08-01', 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000000, 2000001, 2000000, '2026-08-01', 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_cal_holiday (id, day, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, '2026-10-01', 1, '国庆节放假', '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, '2026-10-02', 1, '国庆节放假', '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, '2026-10-03', 1, '国庆节放假', '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_workstation_machine (id, workstation_id, machinery_id, quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000001, 2000000, 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000002, 2000002, 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000003, 2000005, 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000004, 2000003, 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_workstation_tool (id, workstation_id, tool_type_id, quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000001, 2000000, 2, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000002, 2000001, 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000004, 2000007, 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000006, 2000003, 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

INSERT INTO mes_md_workstation_worker (id, workstation_id, post_id, quantity, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(2000000, 2000007, 71017, 2, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000001, 2000001, 71017, 2, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000002, 2000004, 71017, 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(2000003, 2000006, 71019, 1, NULL, '1', NOW(), '1', NOW(), b'0', 2025);

