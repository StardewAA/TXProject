-- =====================================================================
-- 2026-09-16 报表模板批量生成（MES 域 5 张业务报表）
-- 背景：积木「API数据集」与 Spring Boot 4 不兼容（NoSuchMethodError），
--       改用「SQL 数据集」路线。首张模板（生产日报）由设计器验证通过后，
--       其余 5 张按同一模板结构批量生成，避免重复手工拖拽。
--
-- 结构（与设计器产出完全一致）：
--   jimu_report          报表模板（json_str：表头行 + 表达式行）
--   jimu_report_db       数据集（db_code / db_dyn_sql）
--   jimu_report_db_field 数据集字段
--
-- 注意：SQL 数据集走积木默认数据源（平台主库），租户条件在 SQL 内显式写死
--       tenant_id = 1（单租户部署）；多租户改造时需替换为报表参数。
-- 说明：幂等，按 id 覆盖。
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- 1. 工单完工报表（8 列）
-- ---------------------------------------------------------------------
INSERT INTO `jimu_report`
(`id`, `code`, `name`, `note`, `status`, `type`, `json_str`, `api_url`, `thumb`, `create_by`, `create_time`,
 `update_by`, `update_time`, `del_flag`, `api_method`, `api_code`, `template`, `view_count`, `css_str`, `js_str`,
 `py_str`, `tenant_id`, `update_count`, `submit_form`, `is_multi_sheet`)
VALUES (1261010000000000001, '20260916160001', '工单完工报表', NULL, NULL, 0,
        '{"loopBlockList":[],"querySetting":{"izOpenQueryBar":false,"izDefaultQuery":true},"recordSubTableOrCollection":{"group":[],"record":[],"range":[]},"printConfig":{"paper":"A4","width":210,"height":297,"definition":1,"isBackend":false,"marginX":10,"marginY":10,"layout":"portrait","printCallBackUrl":""},"hidden":{"rows":[],"cols":[],"conditions":{"rows":{},"cols":{}}},"queryFormSetting":{"useQueryForm":false,"dbKey":"","idField":""},"dbexps":[],"dicts":[],"fillFormToolbar":{"show":true,"btnList":["save","print","exportPDF","exportExcel","exportWord"]},"freeze":"A1","dataRectWidth":700,"isViewContentHorizontalCenter":false,"autofilter":{},"validations":[],"cols":{"len":50},"area":{"sri":7,"sci":2,"eri":7,"eci":2,"width":100,"height":25},"pyGroupEngine":false,"submitHandlers":[],"hiddenCells":[],"zonedEditionList":[],"rows":{"0":{"cells":{"0":{"text":"工单号"},"1":{"text":"产品"},"2":{"text":"计划数量"},"3":{"text":"已产数量"},"4":{"text":"进度%"},"5":{"text":"状态"},"6":{"text":"计划日期"},"7":{"text":"完工日期"}}},"1":{"cells":{"0":{"text":"#{mes_work_order_completion.work_order_code}"},"1":{"text":"#{mes_work_order_completion.item_name}"},"2":{"text":"#{mes_work_order_completion.plan_quantity}"},"3":{"text":"#{mes_work_order_completion.produced_quantity}"},"4":{"text":"#{mes_work_order_completion.progress_percent}"},"5":{"text":"#{mes_work_order_completion.status_name}"},"6":{"text":"#{mes_work_order_completion.request_date}"},"7":{"text":"#{mes_work_order_completion.finish_date}"}}},"len":101},"rpbar":{"show":true,"pageSize":"","btnList":[]},"fixedPrintHeadRows":[],"fixedPrintTailRows":[],"displayConfig":{},"fillFormInfo":{"layout":{"direction":"horizontal","width":200,"height":45}},"background":false,"name":"sheet1","styles":[],"fillFormStyle":"default","freezeLineColor":"rgb(185, 185, 185)","merges":[]}',
        NULL, NULL, '1', NOW(), NULL, NOW(), 0, NULL, NULL, 0, 0, NULL, NULL, NULL, 1, 0, 0, NULL)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `json_str` = VALUES(`json_str`), `update_time` = NOW();

INSERT INTO `jimu_report_db`
(`id`, `jimu_report_id`, `create_by`, `update_by`, `create_time`, `update_time`, `db_code`, `db_ch_name`, `db_type`,
 `db_table_name`, `db_dyn_sql`, `db_key`, `tb_db_key`, `tb_db_table_name`, `java_type`, `java_value`, `api_url`,
 `api_method`, `is_list`, `is_page`, `db_source`, `db_source_type`, `json_data`, `api_convert`, `iz_shared_source`,
 `jimu_shared_source_id`)
VALUES (1261010000000001001, 1261010000000000001, '1', NULL, NOW(), NOW(), 'mes_work_order_completion', '工单完工报表', 0,
        NULL, 'SELECT w.code AS work_order_code, i.name AS item_name, w.quantity AS plan_quantity, w.quantity_produced AS produced_quantity, ROUND(CASE WHEN w.quantity > 0 THEN w.quantity_produced / w.quantity * 100 ELSE 0 END, 2) AS progress_percent, CASE w.status WHEN 0 THEN ''草稿'' WHEN 1 THEN ''已确认'' WHEN 2 THEN ''已完成'' WHEN 3 THEN ''已取消'' WHEN 10 THEN ''已下发'' WHEN 11 THEN ''生产中'' ELSE ''其他'' END AS status_name, DATE_FORMAT(w.request_date, ''%Y-%m-%d'') AS request_date, DATE_FORMAT(w.finish_date, ''%Y-%m-%d'') AS finish_date FROM mes_pro_work_order w LEFT JOIN mes_md_item i ON i.id = w.product_id WHERE w.deleted = 0 AND w.tenant_id = 1 ORDER BY w.request_date DESC', NULL, NULL, NULL, NULL, NULL, NULL,
        NULL, 1, 1, '', 'mysql', '', '', 0, NULL)
ON DUPLICATE KEY UPDATE `db_dyn_sql` = VALUES(`db_dyn_sql`), `db_ch_name` = VALUES(`db_ch_name`), `update_time` = NOW();

INSERT INTO `jimu_report_db_field`
(`id`, `create_by`, `create_time`, `update_by`, `update_time`, `jimu_report_db_id`, `field_name`, `field_name_physics`,
 `field_text`, `widget_type`, `widget_width`, `order_num`, `search_flag`, `search_mode`, `dict_code`, `search_value`,
 `search_format`, `ext_json`)
VALUES (1261010000000003001, '1', NOW(), NULL, NULL, 1261010000000001001, 'work_order_code', NULL, 'work_order_code', 'String', NULL, 0, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000003002, '1', NOW(), NULL, NULL, 1261010000000001001, 'item_name', NULL, 'item_name', 'String', NULL, 1, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000003003, '1', NOW(), NULL, NULL, 1261010000000001001, 'plan_quantity', NULL, 'plan_quantity', 'String', NULL, 2, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000003004, '1', NOW(), NULL, NULL, 1261010000000001001, 'produced_quantity', NULL, 'produced_quantity', 'String', NULL, 3, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000003005, '1', NOW(), NULL, NULL, 1261010000000001001, 'progress_percent', NULL, 'progress_percent', 'String', NULL, 4, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000003006, '1', NOW(), NULL, NULL, 1261010000000001001, 'status_name', NULL, 'status_name', 'String', NULL, 5, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000003007, '1', NOW(), NULL, NULL, 1261010000000001001, 'request_date', NULL, 'request_date', 'String', NULL, 6, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000003008, '1', NOW(), NULL, NULL, 1261010000000001001, 'finish_date', NULL, 'finish_date', 'String', NULL, 7, NULL, NULL, '', NULL, NULL, '')
ON DUPLICATE KEY UPDATE `field_name` = VALUES(`field_name`), `order_num` = VALUES(`order_num`), `update_time` = NOW();

-- ---------------------------------------------------------------------
-- 2. 不良品统计（7 列）
-- ---------------------------------------------------------------------
INSERT INTO `jimu_report`
(`id`, `code`, `name`, `note`, `status`, `type`, `json_str`, `api_url`, `thumb`, `create_by`, `create_time`,
 `update_by`, `update_time`, `del_flag`, `api_method`, `api_code`, `template`, `view_count`, `css_str`, `js_str`,
 `py_str`, `tenant_id`, `update_count`, `submit_form`, `is_multi_sheet`)
VALUES (1261010000000000002, '20260916160002', '不良品统计', NULL, NULL, 0,
        '{"loopBlockList":[],"querySetting":{"izOpenQueryBar":false,"izDefaultQuery":true},"recordSubTableOrCollection":{"group":[],"record":[],"range":[]},"printConfig":{"paper":"A4","width":210,"height":297,"definition":1,"isBackend":false,"marginX":10,"marginY":10,"layout":"portrait","printCallBackUrl":""},"hidden":{"rows":[],"cols":[],"conditions":{"rows":{},"cols":{}}},"queryFormSetting":{"useQueryForm":false,"dbKey":"","idField":""},"dbexps":[],"dicts":[],"fillFormToolbar":{"show":true,"btnList":["save","print","exportPDF","exportExcel","exportWord"]},"freeze":"A1","dataRectWidth":700,"isViewContentHorizontalCenter":false,"autofilter":{},"validations":[],"cols":{"len":50},"area":{"sri":7,"sci":2,"eri":7,"eci":2,"width":100,"height":25},"pyGroupEngine":false,"submitHandlers":[],"hiddenCells":[],"zonedEditionList":[],"rows":{"0":{"cells":{"0":{"text":"物料编码"},"1":{"text":"物料名称"},"2":{"text":"工序"},"3":{"text":"不良数"},"4":{"text":"工废"},"5":{"text":"料废"},"6":{"text":"其他废"}}},"1":{"cells":{"0":{"text":"#{mes_defect_summary.item_code}"},"1":{"text":"#{mes_defect_summary.item_name}"},"2":{"text":"#{mes_defect_summary.process_name}"},"3":{"text":"#{mes_defect_summary.unqualified_quantity}"},"4":{"text":"#{mes_defect_summary.labor_scrap_quantity}"},"5":{"text":"#{mes_defect_summary.material_scrap_quantity}"},"6":{"text":"#{mes_defect_summary.other_scrap_quantity}"}}},"len":101},"rpbar":{"show":true,"pageSize":"","btnList":[]},"fixedPrintHeadRows":[],"fixedPrintTailRows":[],"displayConfig":{},"fillFormInfo":{"layout":{"direction":"horizontal","width":200,"height":45}},"background":false,"name":"sheet1","styles":[],"fillFormStyle":"default","freezeLineColor":"rgb(185, 185, 185)","merges":[]}',
        NULL, NULL, '1', NOW(), NULL, NOW(), 0, NULL, NULL, 0, 0, NULL, NULL, NULL, 1, 0, 0, NULL)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `json_str` = VALUES(`json_str`), `update_time` = NOW();

INSERT INTO `jimu_report_db`
(`id`, `jimu_report_id`, `create_by`, `update_by`, `create_time`, `update_time`, `db_code`, `db_ch_name`, `db_type`,
 `db_table_name`, `db_dyn_sql`, `db_key`, `tb_db_key`, `tb_db_table_name`, `java_type`, `java_value`, `api_url`,
 `api_method`, `is_list`, `is_page`, `db_source`, `db_source_type`, `json_data`, `api_convert`, `iz_shared_source`,
 `jimu_shared_source_id`)
VALUES (1261010000000001002, 1261010000000000002, '1', NULL, NOW(), NOW(), 'mes_defect_summary', '不良品统计', 0,
        NULL, 'SELECT i.code AS item_code, i.name AS item_name, p.name AS process_name, SUM(f.unqualified_quantity) AS unqualified_quantity, SUM(f.labor_scrap_quantity) AS labor_scrap_quantity, SUM(f.material_scrap_quantity) AS material_scrap_quantity, SUM(f.other_scrap_quantity) AS other_scrap_quantity FROM mes_pro_feedback f LEFT JOIN mes_md_item i ON i.id = f.item_id LEFT JOIN mes_pro_process p ON p.id = f.process_id WHERE f.deleted = 0 AND f.status IN (2, 3, 4) AND f.tenant_id = 1 GROUP BY i.code, i.name, p.name ORDER BY unqualified_quantity DESC', NULL, NULL, NULL, NULL, NULL, NULL,
        NULL, 1, 1, '', 'mysql', '', '', 0, NULL)
ON DUPLICATE KEY UPDATE `db_dyn_sql` = VALUES(`db_dyn_sql`), `db_ch_name` = VALUES(`db_ch_name`), `update_time` = NOW();

INSERT INTO `jimu_report_db_field`
(`id`, `create_by`, `create_time`, `update_by`, `update_time`, `jimu_report_db_id`, `field_name`, `field_name_physics`,
 `field_text`, `widget_type`, `widget_width`, `order_num`, `search_flag`, `search_mode`, `dict_code`, `search_value`,
 `search_format`, `ext_json`)
VALUES (1261010000000004001, '1', NOW(), NULL, NULL, 1261010000000001002, 'item_code', NULL, 'item_code', 'String', NULL, 0, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000004002, '1', NOW(), NULL, NULL, 1261010000000001002, 'item_name', NULL, 'item_name', 'String', NULL, 1, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000004003, '1', NOW(), NULL, NULL, 1261010000000001002, 'process_name', NULL, 'process_name', 'String', NULL, 2, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000004004, '1', NOW(), NULL, NULL, 1261010000000001002, 'unqualified_quantity', NULL, 'unqualified_quantity', 'String', NULL, 3, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000004005, '1', NOW(), NULL, NULL, 1261010000000001002, 'labor_scrap_quantity', NULL, 'labor_scrap_quantity', 'String', NULL, 4, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000004006, '1', NOW(), NULL, NULL, 1261010000000001002, 'material_scrap_quantity', NULL, 'material_scrap_quantity', 'String', NULL, 5, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000004007, '1', NOW(), NULL, NULL, 1261010000000001002, 'other_scrap_quantity', NULL, 'other_scrap_quantity', 'String', NULL, 6, NULL, NULL, '', NULL, NULL, '')
ON DUPLICATE KEY UPDATE `field_name` = VALUES(`field_name`), `order_num` = VALUES(`order_num`), `update_time` = NOW();

-- ---------------------------------------------------------------------
-- 3. 物料耗用报表（6 列）
-- ---------------------------------------------------------------------
INSERT INTO `jimu_report`
(`id`, `code`, `name`, `note`, `status`, `type`, `json_str`, `api_url`, `thumb`, `create_by`, `create_time`,
 `update_by`, `update_time`, `del_flag`, `api_method`, `api_code`, `template`, `view_count`, `css_str`, `js_str`,
 `py_str`, `tenant_id`, `update_count`, `submit_form`, `is_multi_sheet`)
VALUES (1261010000000000003, '20260916160003', '物料耗用报表', NULL, NULL, 0,
        '{"loopBlockList":[],"querySetting":{"izOpenQueryBar":false,"izDefaultQuery":true},"recordSubTableOrCollection":{"group":[],"record":[],"range":[]},"printConfig":{"paper":"A4","width":210,"height":297,"definition":1,"isBackend":false,"marginX":10,"marginY":10,"layout":"portrait","printCallBackUrl":""},"hidden":{"rows":[],"cols":[],"conditions":{"rows":{},"cols":{}}},"queryFormSetting":{"useQueryForm":false,"dbKey":"","idField":""},"dbexps":[],"dicts":[],"fillFormToolbar":{"show":true,"btnList":["save","print","exportPDF","exportExcel","exportWord"]},"freeze":"A1","dataRectWidth":700,"isViewContentHorizontalCenter":false,"autofilter":{},"validations":[],"cols":{"len":50},"area":{"sri":7,"sci":2,"eri":7,"eci":2,"width":100,"height":25},"pyGroupEngine":false,"submitHandlers":[],"hiddenCells":[],"zonedEditionList":[],"rows":{"0":{"cells":{"0":{"text":"工单号"},"1":{"text":"工序"},"2":{"text":"日期"},"3":{"text":"物料编码"},"4":{"text":"物料名称"},"5":{"text":"耗用数量"}}},"1":{"cells":{"0":{"text":"#{mes_material_consumption.work_order_code}"},"1":{"text":"#{mes_material_consumption.process_name}"},"2":{"text":"#{mes_material_consumption.consume_date}"},"3":{"text":"#{mes_material_consumption.item_code}"},"4":{"text":"#{mes_material_consumption.item_name}"},"5":{"text":"#{mes_material_consumption.consume_quantity}"}}},"len":101},"rpbar":{"show":true,"pageSize":"","btnList":[]},"fixedPrintHeadRows":[],"fixedPrintTailRows":[],"displayConfig":{},"fillFormInfo":{"layout":{"direction":"horizontal","width":200,"height":45}},"background":false,"name":"sheet1","styles":[],"fillFormStyle":"default","freezeLineColor":"rgb(185, 185, 185)","merges":[]}',
        NULL, NULL, '1', NOW(), NULL, NOW(), 0, NULL, NULL, 0, 0, NULL, NULL, NULL, 1, 0, 0, NULL)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `json_str` = VALUES(`json_str`), `update_time` = NOW();

INSERT INTO `jimu_report_db`
(`id`, `jimu_report_id`, `create_by`, `update_by`, `create_time`, `update_time`, `db_code`, `db_ch_name`, `db_type`,
 `db_table_name`, `db_dyn_sql`, `db_key`, `tb_db_key`, `tb_db_table_name`, `java_type`, `java_value`, `api_url`,
 `api_method`, `is_list`, `is_page`, `db_source`, `db_source_type`, `json_data`, `api_convert`, `iz_shared_source`,
 `jimu_shared_source_id`)
VALUES (1261010000000001003, 1261010000000000003, '1', NULL, NOW(), NOW(), 'mes_material_consumption', '物料耗用报表', 0,
        NULL, 'SELECT w.code AS work_order_code, p.name AS process_name, DATE_FORMAT(c.consume_date, ''%Y-%m-%d'') AS consume_date, i.code AS item_code, i.name AS item_name, SUM(d.quantity) AS consume_quantity FROM mes_wm_item_consume_detail d JOIN mes_wm_item_consume c ON c.id = d.consume_id LEFT JOIN mes_pro_work_order w ON w.id = c.work_order_id LEFT JOIN mes_pro_process p ON p.id = c.process_id LEFT JOIN mes_md_item i ON i.id = d.item_id WHERE d.deleted = 0 AND c.deleted = 0 AND c.tenant_id = 1 GROUP BY w.code, p.name, DATE_FORMAT(c.consume_date, ''%Y-%m-%d''), i.code, i.name ORDER BY consume_date DESC', NULL, NULL, NULL, NULL, NULL, NULL,
        NULL, 1, 1, '', 'mysql', '', '', 0, NULL)
ON DUPLICATE KEY UPDATE `db_dyn_sql` = VALUES(`db_dyn_sql`), `db_ch_name` = VALUES(`db_ch_name`), `update_time` = NOW();

INSERT INTO `jimu_report_db_field`
(`id`, `create_by`, `create_time`, `update_by`, `update_time`, `jimu_report_db_id`, `field_name`, `field_name_physics`,
 `field_text`, `widget_type`, `widget_width`, `order_num`, `search_flag`, `search_mode`, `dict_code`, `search_value`,
 `search_format`, `ext_json`)
VALUES (1261010000000005001, '1', NOW(), NULL, NULL, 1261010000000001003, 'work_order_code', NULL, 'work_order_code', 'String', NULL, 0, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000005002, '1', NOW(), NULL, NULL, 1261010000000001003, 'process_name', NULL, 'process_name', 'String', NULL, 1, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000005003, '1', NOW(), NULL, NULL, 1261010000000001003, 'consume_date', NULL, 'consume_date', 'String', NULL, 2, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000005004, '1', NOW(), NULL, NULL, 1261010000000001003, 'item_code', NULL, 'item_code', 'String', NULL, 3, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000005005, '1', NOW(), NULL, NULL, 1261010000000001003, 'item_name', NULL, 'item_name', 'String', NULL, 4, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000005006, '1', NOW(), NULL, NULL, 1261010000000001003, 'consume_quantity', NULL, 'consume_quantity', 'String', NULL, 5, NULL, NULL, '', NULL, NULL, '')
ON DUPLICATE KEY UPDATE `field_name` = VALUES(`field_name`), `order_num` = VALUES(`order_num`), `update_time` = NOW();

-- ---------------------------------------------------------------------
-- 4. 设备运行与维修统计（6 列）
-- ---------------------------------------------------------------------
INSERT INTO `jimu_report`
(`id`, `code`, `name`, `note`, `status`, `type`, `json_str`, `api_url`, `thumb`, `create_by`, `create_time`,
 `update_by`, `update_time`, `del_flag`, `api_method`, `api_code`, `template`, `view_count`, `css_str`, `js_str`,
 `py_str`, `tenant_id`, `update_count`, `submit_form`, `is_multi_sheet`)
VALUES (1261010000000000004, '20260916160004', '设备运行与维修统计', NULL, NULL, 0,
        '{"loopBlockList":[],"querySetting":{"izOpenQueryBar":false,"izDefaultQuery":true},"recordSubTableOrCollection":{"group":[],"record":[],"range":[]},"printConfig":{"paper":"A4","width":210,"height":297,"definition":1,"isBackend":false,"marginX":10,"marginY":10,"layout":"portrait","printCallBackUrl":""},"hidden":{"rows":[],"cols":[],"conditions":{"rows":{},"cols":{}}},"queryFormSetting":{"useQueryForm":false,"dbKey":"","idField":""},"dbexps":[],"dicts":[],"fillFormToolbar":{"show":true,"btnList":["save","print","exportPDF","exportExcel","exportWord"]},"freeze":"A1","dataRectWidth":700,"isViewContentHorizontalCenter":false,"autofilter":{},"validations":[],"cols":{"len":50},"area":{"sri":7,"sci":2,"eri":7,"eci":2,"width":100,"height":25},"pyGroupEngine":false,"submitHandlers":[],"hiddenCells":[],"zonedEditionList":[],"rows":{"0":{"cells":{"0":{"text":"设备编码"},"1":{"text":"设备名称"},"2":{"text":"维修次数"},"3":{"text":"维修工时"},"4":{"text":"最近点检"},"5":{"text":"最近保养"}}},"1":{"cells":{"0":{"text":"#{mes_machinery_efficiency.machinery_code}"},"1":{"text":"#{mes_machinery_efficiency.machinery_name}"},"2":{"text":"#{mes_machinery_efficiency.repair_count}"},"3":{"text":"#{mes_machinery_efficiency.repair_hours}"},"4":{"text":"#{mes_machinery_efficiency.last_check_time}"},"5":{"text":"#{mes_machinery_efficiency.last_mainten_time}"}}},"len":101},"rpbar":{"show":true,"pageSize":"","btnList":[]},"fixedPrintHeadRows":[],"fixedPrintTailRows":[],"displayConfig":{},"fillFormInfo":{"layout":{"direction":"horizontal","width":200,"height":45}},"background":false,"name":"sheet1","styles":[],"fillFormStyle":"default","freezeLineColor":"rgb(185, 185, 185)","merges":[]}',
        NULL, NULL, '1', NOW(), NULL, NOW(), 0, NULL, NULL, 0, 0, NULL, NULL, NULL, 1, 0, 0, NULL)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `json_str` = VALUES(`json_str`), `update_time` = NOW();

INSERT INTO `jimu_report_db`
(`id`, `jimu_report_id`, `create_by`, `update_by`, `create_time`, `update_time`, `db_code`, `db_ch_name`, `db_type`,
 `db_table_name`, `db_dyn_sql`, `db_key`, `tb_db_key`, `tb_db_table_name`, `java_type`, `java_value`, `api_url`,
 `api_method`, `is_list`, `is_page`, `db_source`, `db_source_type`, `json_data`, `api_convert`, `iz_shared_source`,
 `jimu_shared_source_id`)
VALUES (1261010000000001004, 1261010000000000004, '1', NULL, NOW(), NOW(), 'mes_machinery_efficiency', '设备运行与维修统计', 0,
        NULL, 'SELECT m.code AS machinery_code, m.name AS machinery_name, COUNT(r.id) AS repair_count, SUM(CASE WHEN r.finish_date IS NOT NULL AND r.require_date IS NOT NULL THEN TIMESTAMPDIFF(HOUR, r.require_date, r.finish_date) ELSE 0 END) AS repair_hours, DATE_FORMAT(m.last_check_time, ''%Y-%m-%d %H:%i'') AS last_check_time, DATE_FORMAT(m.last_mainten_time, ''%Y-%m-%d %H:%i'') AS last_mainten_time FROM mes_dv_machinery m LEFT JOIN mes_dv_repair r ON r.machinery_id = m.id AND r.deleted = 0 WHERE m.deleted = 0 AND m.tenant_id = 1 GROUP BY m.code, m.name, m.last_check_time, m.last_mainten_time ORDER BY repair_hours DESC', NULL, NULL, NULL, NULL, NULL, NULL,
        NULL, 1, 1, '', 'mysql', '', '', 0, NULL)
ON DUPLICATE KEY UPDATE `db_dyn_sql` = VALUES(`db_dyn_sql`), `db_ch_name` = VALUES(`db_ch_name`), `update_time` = NOW();

INSERT INTO `jimu_report_db_field`
(`id`, `create_by`, `create_time`, `update_by`, `update_time`, `jimu_report_db_id`, `field_name`, `field_name_physics`,
 `field_text`, `widget_type`, `widget_width`, `order_num`, `search_flag`, `search_mode`, `dict_code`, `search_value`,
 `search_format`, `ext_json`)
VALUES (1261010000000006001, '1', NOW(), NULL, NULL, 1261010000000001004, 'machinery_code', NULL, 'machinery_code', 'String', NULL, 0, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000006002, '1', NOW(), NULL, NULL, 1261010000000001004, 'machinery_name', NULL, 'machinery_name', 'String', NULL, 1, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000006003, '1', NOW(), NULL, NULL, 1261010000000001004, 'repair_count', NULL, 'repair_count', 'String', NULL, 2, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000006004, '1', NOW(), NULL, NULL, 1261010000000001004, 'repair_hours', NULL, 'repair_hours', 'String', NULL, 3, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000006005, '1', NOW(), NULL, NULL, 1261010000000001004, 'last_check_time', NULL, 'last_check_time', 'String', NULL, 4, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000006006, '1', NOW(), NULL, NULL, 1261010000000001004, 'last_mainten_time', NULL, 'last_mainten_time', 'String', NULL, 5, NULL, NULL, '', NULL, NULL, '')
ON DUPLICATE KEY UPDATE `field_name` = VALUES(`field_name`), `order_num` = VALUES(`order_num`), `update_time` = NOW();

-- ---------------------------------------------------------------------
-- 5. 生产报工明细台账（8 列）
-- ---------------------------------------------------------------------
INSERT INTO `jimu_report`
(`id`, `code`, `name`, `note`, `status`, `type`, `json_str`, `api_url`, `thumb`, `create_by`, `create_time`,
 `update_by`, `update_time`, `del_flag`, `api_method`, `api_code`, `template`, `view_count`, `css_str`, `js_str`,
 `py_str`, `tenant_id`, `update_count`, `submit_form`, `is_multi_sheet`)
VALUES (1261010000000000005, '20260916160005', '生产报工明细台账', NULL, NULL, 0,
        '{"loopBlockList":[],"querySetting":{"izOpenQueryBar":false,"izDefaultQuery":true},"recordSubTableOrCollection":{"group":[],"record":[],"range":[]},"printConfig":{"paper":"A4","width":210,"height":297,"definition":1,"isBackend":false,"marginX":10,"marginY":10,"layout":"portrait","printCallBackUrl":""},"hidden":{"rows":[],"cols":[],"conditions":{"rows":{},"cols":{}}},"queryFormSetting":{"useQueryForm":false,"dbKey":"","idField":""},"dbexps":[],"dicts":[],"fillFormToolbar":{"show":true,"btnList":["save","print","exportPDF","exportExcel","exportWord"]},"freeze":"A1","dataRectWidth":700,"isViewContentHorizontalCenter":false,"autofilter":{},"validations":[],"cols":{"len":50},"area":{"sri":7,"sci":2,"eri":7,"eci":2,"width":100,"height":25},"pyGroupEngine":false,"submitHandlers":[],"hiddenCells":[],"zonedEditionList":[],"rows":{"0":{"cells":{"0":{"text":"报工单号"},"1":{"text":"工单号"},"2":{"text":"工序"},"3":{"text":"产品"},"4":{"text":"报工数量"},"5":{"text":"合格数"},"6":{"text":"不良数"},"7":{"text":"报工时间"}}},"1":{"cells":{"0":{"text":"#{mes_feedback_detail.feedback_code}"},"1":{"text":"#{mes_feedback_detail.work_order_code}"},"2":{"text":"#{mes_feedback_detail.process_name}"},"3":{"text":"#{mes_feedback_detail.item_name}"},"4":{"text":"#{mes_feedback_detail.feedback_quantity}"},"5":{"text":"#{mes_feedback_detail.qualified_quantity}"},"6":{"text":"#{mes_feedback_detail.unqualified_quantity}"},"7":{"text":"#{mes_feedback_detail.feedback_time}"}}},"len":101},"rpbar":{"show":true,"pageSize":"","btnList":[]},"fixedPrintHeadRows":[],"fixedPrintTailRows":[],"displayConfig":{},"fillFormInfo":{"layout":{"direction":"horizontal","width":200,"height":45}},"background":false,"name":"sheet1","styles":[],"fillFormStyle":"default","freezeLineColor":"rgb(185, 185, 185)","merges":[]}',
        NULL, NULL, '1', NOW(), NULL, NOW(), 0, NULL, NULL, 0, 0, NULL, NULL, NULL, 1, 0, 0, NULL)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `json_str` = VALUES(`json_str`), `update_time` = NOW();

INSERT INTO `jimu_report_db`
(`id`, `jimu_report_id`, `create_by`, `update_by`, `create_time`, `update_time`, `db_code`, `db_ch_name`, `db_type`,
 `db_table_name`, `db_dyn_sql`, `db_key`, `tb_db_key`, `tb_db_table_name`, `java_type`, `java_value`, `api_url`,
 `api_method`, `is_list`, `is_page`, `db_source`, `db_source_type`, `json_data`, `api_convert`, `iz_shared_source`,
 `jimu_shared_source_id`)
VALUES (1261010000000001005, 1261010000000000005, '1', NULL, NOW(), NOW(), 'mes_feedback_detail', '生产报工明细台账', 0,
        NULL, 'SELECT f.code AS feedback_code, w.code AS work_order_code, p.name AS process_name, i.name AS item_name, f.feedback_quantity, f.qualified_quantity, f.unqualified_quantity, DATE_FORMAT(f.feedback_time, ''%Y-%m-%d %H:%i:%s'') AS feedback_time FROM mes_pro_feedback f LEFT JOIN mes_pro_work_order w ON w.id = f.work_order_id LEFT JOIN mes_pro_process p ON p.id = f.process_id LEFT JOIN mes_md_item i ON i.id = f.item_id WHERE f.deleted = 0 AND f.status IN (2, 3, 4) AND f.tenant_id = 1 ORDER BY f.feedback_time DESC', NULL, NULL, NULL, NULL, NULL, NULL,
        NULL, 1, 1, '', 'mysql', '', '', 0, NULL)
ON DUPLICATE KEY UPDATE `db_dyn_sql` = VALUES(`db_dyn_sql`), `db_ch_name` = VALUES(`db_ch_name`), `update_time` = NOW();

INSERT INTO `jimu_report_db_field`
(`id`, `create_by`, `create_time`, `update_by`, `update_time`, `jimu_report_db_id`, `field_name`, `field_name_physics`,
 `field_text`, `widget_type`, `widget_width`, `order_num`, `search_flag`, `search_mode`, `dict_code`, `search_value`,
 `search_format`, `ext_json`)
VALUES (1261010000000007001, '1', NOW(), NULL, NULL, 1261010000000001005, 'feedback_code', NULL, 'feedback_code', 'String', NULL, 0, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000007002, '1', NOW(), NULL, NULL, 1261010000000001005, 'work_order_code', NULL, 'work_order_code', 'String', NULL, 1, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000007003, '1', NOW(), NULL, NULL, 1261010000000001005, 'process_name', NULL, 'process_name', 'String', NULL, 2, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000007004, '1', NOW(), NULL, NULL, 1261010000000001005, 'item_name', NULL, 'item_name', 'String', NULL, 3, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000007005, '1', NOW(), NULL, NULL, 1261010000000001005, 'feedback_quantity', NULL, 'feedback_quantity', 'String', NULL, 4, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000007006, '1', NOW(), NULL, NULL, 1261010000000001005, 'qualified_quantity', NULL, 'qualified_quantity', 'String', NULL, 5, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000007007, '1', NOW(), NULL, NULL, 1261010000000001005, 'unqualified_quantity', NULL, 'unqualified_quantity', 'String', NULL, 6, NULL, NULL, '', NULL, NULL, ''),
       (1261010000000007008, '1', NOW(), NULL, NULL, 1261010000000001005, 'feedback_time', NULL, 'feedback_time', 'String', NULL, 7, NULL, NULL, '', NULL, NULL, '')
ON DUPLICATE KEY UPDATE `field_name` = VALUES(`field_name`), `order_num` = VALUES(`order_num`), `update_time` = NOW();

COMMIT;
