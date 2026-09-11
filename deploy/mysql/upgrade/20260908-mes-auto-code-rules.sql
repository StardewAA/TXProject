-- ============================================
-- MES 全域自动编码规则补齐脚本
-- 平台：ruoyi-vue-pro txgy-module-mes
-- 执行环境：dev 库（tenant_id=1）
-- 执行人：（人工填）
-- 说明：本平台编码规则真实表为 mes_md_auto_code_rule + mes_md_auto_code_part
--       （不存在 infra_auto_code；prefix/start/step 存于 part 段表）
--       脚本幂等，可重复执行；已存在的 MD_CLIENT_CODE / WM_SN_CODE 自动跳过
-- ============================================

DROP TEMPORARY TABLE IF EXISTS tmp_auto_code;
CREATE TEMPORARY TABLE tmp_auto_code (
  code varchar(64), name varchar(64), prefix varchar(8), serial_len int, descr varchar(200)
) ENGINE=MEMORY;

INSERT INTO tmp_auto_code (code, name, prefix, serial_len, descr) VALUES
('CAL_PLAN_CODE',            '排产计划编码',     'PL-',   6, '排产计划自动生成规则'),
('CAL_TEAM_CODE',            '班组编码',         'TM-',   6, '班组自动生成规则'),
('DV_CHECK_PLAN_CODE',       '设备点检计划编码', 'DCP-',  6, '设备点检计划自动生成规则'),
('DV_MACHINERY_TYPE_CODE',   '设备类型编码',     'DMT-',  6, '设备类型自动生成规则'),
('DV_MACHINERY_CODE',        '设备编码',         'DM-',   6, '设备自动生成规则'),
('DV_REPAIR_CODE',           '设备维修编码',     'DR-',   6, '设备维修自动生成规则'),
('DV_SUBJECT_CODE',          '保养科目编码',     'DS-',   6, '保养科目自动生成规则'),
('MD_CLIENT_CODE',           '客户编码',         'C-',    6, '客户编码自动生成规则'),
('MD_ITEM_TYPE_CODE',        '物料类型编码',     'ITY-',  6, '物料类型自动生成规则'),
('MD_ITEM_CODE',             '物料编码',         'IT-',   6, '物料自动生成规则'),
('MD_VENDOR_CODE',           '供应商编码',       'VD-',   6, '供应商自动生成规则'),
('MD_WORKSTATION_CODE',      '工位编码',         'WS-',   6, '工位自动生成规则'),
('MD_WORKSHOP_CODE',         '车间编码',         'WSP-',  6, '车间自动生成规则'),
('PRO_CARD_CODE',            '生产卡片编码',     'PC-',   6, '生产卡片自动生成规则'),
('PRO_FEEDBACK_CODE',        '报工记录编码',     'FB-',   6, '报工记录自动生成规则'),
('PRO_PROCESS_CODE',         '工序编码',         'PS-',   6, '工序自动生成规则'),
('PRO_ROUTE_CODE',           '工艺路线编码',     'RT-',   6, '工艺路线自动生成规则'),
('PRO_TASK_CODE',            '生产任务编码',     'TK-',   6, '生产任务自动生成规则'),
('PRO_WORK_ORDER_CODE',      '生产工单编码',     'WO-',   6, '生产工单自动生成规则'),
('QC_DEFECT_CODE',           '缺陷编码',         'DF-',   6, '缺陷自动生成规则'),
('QC_INDICATOR_CODE',        '质检指标编码',     'QI-',   6, '质检指标自动生成规则'),
('QC_INDICATOR_RESULT_CODE', '质检结果编码',     'QR-',   6, '质检结果自动生成规则'),
('QC_IQC_CODE',              '来料检验单编码',   'IQC-',  6, '来料检验单自动生成规则'),
('QC_IPQC_CODE',             '制程检验单编码',   'IPQC-', 6, '制程检验单自动生成规则'),
('QC_OQC_CODE',              '出货检验单编码',   'OQC-',  6, '出货检验单自动生成规则'),
('QC_RQC_CODE',              '退货检验单编码',   'RQC-',  6, '退货检验单自动生成规则'),
('TM_TOOL_TYPE_CODE',        '工具类型编码',     'TTY-',  6, '工具类型自动生成规则'),
('TM_TOOL_CODE',             '工具编码',         'TL-',   6, '工具自动生成规则'),
('WM_AREA_CODE',             '库区编码',         'WA-',   6, '库区自动生成规则'),
('WM_LOCATION_CODE',         '库位编码',         'WL-',   6, '库位自动生成规则'),
('WM_ARRIVAL_NOTICE_CODE',   '到货通知单编码',   'AN-',   6, '到货通知单自动生成规则'),
('WM_ITEM_RECEIPT_CODE',     '采购入库单编码',   'IR-',   6, '采购入库单自动生成规则'),
('WM_RETURN_VENDOR_CODE',    '采购退货单编码',   'RV-',   6, '采购退货单自动生成规则'),
('WM_SALES_NOTICE_CODE',     '销售通知单编码',   'SLN-',  6, '销售通知单自动生成规则'),
('WM_RETURN_SALES_CODE',     '销售退货单编码',   'RS-',   6, '销售退货单自动生成规则'),
('WM_RETURN_ISSUE_CODE',     '退料出库单编码',   'RI-',   6, '退料出库单自动生成规则'),
('WM_PRODUCT_ISSUE_CODE',    '生产领料单编码',   'PI-',   6, '生产领料单自动生成规则'),
('WM_PRODUCT_SALES_CODE',    '成品销售单编码',   'PSL-',  6, '成品销售单自动生成规则'),
('PRODUCTRECPT_CODE',        '成品入库单编码',   'PRC-',  6, '成品入库单自动生成规则'),
('WM_MISC_ISSUE_CODE',       '杂发单编码',       'MI-',   6, '杂发单自动生成规则'),
('WM_MISC_RECEIPT_CODE',     '杂收单编码',       'MR-',   6, '杂收单自动生成规则'),
('WM_OUTSOURCE_ISSUE_CODE',  '外协领料单编码',   'OI-',   6, '外协领料单自动生成规则'),
('WM_OUTSOURCE_RECEIPT_CODE','外协入库单编码',   'OR-',   6, '外协入库单自动生成规则'),
('WM_PACKAGE_CODE',          '包装单编码',       'PK-',   6, '包装单自动生成规则'),
('WM_SN_CODE',               'SN 码',            'SN-',   8, 'SN 码自动生成规则'),
('WM_BATCH_CODE',            '批次编码',         'BAT-',  6, '批次自动生成规则'),
('WM_STOCK_TAKING_CODE',     '盘点单编码',       'ST-',   6, '盘点单自动生成规则'),
('WM_STOCK_TAKING_PLAN_CODE','盘点计划编码',     'STP-',  6, '盘点计划自动生成规则'),
('WM_WAREHOUSE_CODE',        '仓库编码',         'WH-',   6, '仓库自动生成规则'),
('TRANSFER_CODE',            '移库单编码',       'TR-',   6, '移库单自动生成规则');

-- 1) 规则头（幂等）
INSERT INTO mes_md_auto_code_rule (code, name, description, max_length, padded, padded_char, padded_method, status, creator, updater, deleted, tenant_id)
SELECT t.code, t.name, t.descr, CHAR_LENGTH(t.prefix) + t.serial_len, b'1', '0', 1, 0, '1', '1', b'0', 1
FROM tmp_auto_code t
WHERE NOT EXISTS (SELECT 1 FROM mes_md_auto_code_rule r WHERE r.code = t.code AND r.deleted = 0 AND r.tenant_id = 1);

-- 2) 段一：固定字符前缀（幂等）
INSERT INTO mes_md_auto_code_part (rule_id, sort, type, length, date_format, fix_character, serial_start_no, serial_step, cycle_flag, creator, updater, deleted, tenant_id)
SELECT r.id, 1, 3, CHAR_LENGTH(t.prefix), NULL, t.prefix, NULL, NULL, b'0', '1', '1', b'0', 1
FROM tmp_auto_code t
JOIN mes_md_auto_code_rule r ON r.code = t.code AND r.deleted = 0 AND r.tenant_id = 1
WHERE NOT EXISTS (SELECT 1 FROM mes_md_auto_code_part p WHERE p.rule_id = r.id AND p.sort = 1 AND p.deleted = 0);

-- 3) 段二：流水号（幂等）
INSERT INTO mes_md_auto_code_part (rule_id, sort, type, length, date_format, fix_character, serial_start_no, serial_step, cycle_flag, creator, updater, deleted, tenant_id)
SELECT r.id, 2, 4, t.serial_len, NULL, NULL, 1, 1, b'0', '1', '1', b'0', 1
FROM tmp_auto_code t
JOIN mes_md_auto_code_rule r ON r.code = t.code AND r.deleted = 0 AND r.tenant_id = 1
WHERE NOT EXISTS (SELECT 1 FROM mes_md_auto_code_part p WHERE p.rule_id = r.id AND p.sort = 2 AND p.deleted = 0);

-- 验证
SELECT r.code, r.name, r.max_length, r.status, p.sort, p.type, p.fix_character, p.serial_start_no, p.serial_step
FROM mes_md_auto_code_rule r
LEFT JOIN mes_md_auto_code_part p ON p.rule_id = r.id AND p.deleted = 0
WHERE r.deleted = 0 AND r.tenant_id = 1
ORDER BY r.code, p.sort;

DROP TEMPORARY TABLE tmp_auto_code;
