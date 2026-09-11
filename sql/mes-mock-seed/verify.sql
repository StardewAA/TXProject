-- =====================================================================
-- MES mock seed verification script (F) tenant_id=1
-- one COUNT per seeded table; expected rows noted in trailing comment
-- filter: tenant_id=1 (seed rows are the only tenant 1 rows in fresh mes tables)
-- NOTE: remark column carries arbitrary text, so counts do NOT filter on remark
-- =====================================================================

-- ========== md.sql ==========
SELECT 'md' AS domain, 'mes_md_vendor' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_vendor WHERE tenant_id = 1; -- expect 5
SELECT 'md' AS domain, 'mes_md_workstation_worker' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_workstation_worker WHERE tenant_id = 1; -- expect 4
SELECT 'md' AS domain, 'mes_md_item_type' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_item_type WHERE tenant_id = 1; -- expect 5
SELECT 'md' AS domain, 'mes_md_product_bom' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_product_bom WHERE tenant_id = 1; -- expect 5
SELECT 'md' AS domain, 'mes_md_item' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_item WHERE tenant_id = 1; -- expect 8
SELECT 'md' AS domain, 'mes_md_product_sip' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_product_sip WHERE tenant_id = 1; -- expect 4
SELECT 'md' AS domain, 'mes_md_client' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_client WHERE tenant_id = 1; -- expect 5
SELECT 'md' AS domain, 'mes_md_unit_measure' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_unit_measure WHERE tenant_id = 1; -- expect 9
SELECT 'md' AS domain, 'mes_md_workstation' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_workstation WHERE tenant_id = 1; -- expect 6
SELECT 'md' AS domain, 'mes_md_item_batch_config' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_item_batch_config WHERE tenant_id = 1; -- expect 4 (no remark col)
SELECT 'md' AS domain, 'mes_md_product_sop' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_product_sop WHERE tenant_id = 1; -- expect 4
SELECT 'md' AS domain, 'mes_md_workshop' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_workshop WHERE tenant_id = 1; -- expect 4

-- ========== pro.sql ==========
SELECT 'pro' AS domain, 'mes_pro_route' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_route WHERE tenant_id = 1; -- expect 5
SELECT 'pro' AS domain, 'mes_pro_route_product_bom' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_route_product_bom WHERE tenant_id = 1; -- expect 4
SELECT 'pro' AS domain, 'mes_pro_work_record_log' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_work_record_log WHERE tenant_id = 1; -- expect 5
SELECT 'pro' AS domain, 'mes_pro_task' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_task WHERE tenant_id = 1; -- expect 8
SELECT 'pro' AS domain, 'mes_pro_work_record' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_work_record WHERE tenant_id = 1; -- expect 3
SELECT 'pro' AS domain, 'mes_pro_andon_config' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_andon_config WHERE tenant_id = 1; -- expect 4
SELECT 'pro' AS domain, 'mes_pro_route_product' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_route_product WHERE tenant_id = 1; -- expect 4
SELECT 'pro' AS domain, 'mes_pro_process' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_process WHERE tenant_id = 1; -- expect 7
SELECT 'pro' AS domain, 'mes_pro_task_issue' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_task_issue WHERE tenant_id = 1; -- expect 5
SELECT 'pro' AS domain, 'mes_pro_card_process' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_card_process WHERE tenant_id = 1; -- expect 5
SELECT 'pro' AS domain, 'mes_pro_work_order' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_work_order WHERE tenant_id = 1; -- expect 8
SELECT 'pro' AS domain, 'mes_pro_card' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_card WHERE tenant_id = 1; -- expect 5
SELECT 'pro' AS domain, 'mes_pro_andon_record' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_andon_record WHERE tenant_id = 1; -- expect 5
SELECT 'pro' AS domain, 'mes_pro_route_process' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_route_process WHERE tenant_id = 1; -- expect 6
SELECT 'pro' AS domain, 'mes_pro_work_order_bom' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_work_order_bom WHERE tenant_id = 1; -- expect 6
SELECT 'pro' AS domain, 'mes_pro_process_content' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_process_content WHERE tenant_id = 1; -- expect 6
SELECT 'pro' AS domain, 'mes_pro_feedback' AS sub_module, COUNT(*) AS seed_rows FROM mes_pro_feedback WHERE tenant_id = 1; -- expect 6

-- ========== wm.sql ==========
SELECT 'wm' AS domain, 'mes_wm_batch' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_batch WHERE tenant_id = 1; -- expect 6
SELECT 'wm' AS domain, 'mes_wm_package' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_package WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_return_sales' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_return_sales WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_product_issue' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_product_issue WHERE tenant_id = 1; -- expect 5
SELECT 'wm' AS domain, 'mes_wm_stock_taking_plan' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_stock_taking_plan WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_sn' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_sn WHERE tenant_id = 1; -- expect 4 (no remark col)
SELECT 'wm' AS domain, 'mes_wm_item_receipt' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_item_receipt WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_product_sales' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_product_sales WHERE tenant_id = 1; -- expect 5
SELECT 'wm' AS domain, 'mes_wm_warehouse' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_warehouse WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_transfer' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_transfer WHERE tenant_id = 1; -- expect 5
SELECT 'wm' AS domain, 'mes_wm_misc_issue' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_misc_issue WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_barcode' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_barcode WHERE tenant_id = 1; -- expect 5
SELECT 'wm' AS domain, 'mes_wm_outsource_issue' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_outsource_issue WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_product_receipt' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_product_receipt WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_misc_receipt' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_misc_receipt WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_return_issue' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_return_issue WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_outsource_receipt' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_outsource_receipt WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_item_consume' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_item_consume WHERE tenant_id = 1; -- expect 5
SELECT 'wm' AS domain, 'mes_wm_arrival_notice' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_arrival_notice WHERE tenant_id = 1; -- expect 5
SELECT 'wm' AS domain, 'mes_wm_barcode_config' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_barcode_config WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_sales_notice' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_sales_notice WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_return_vendor' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_return_vendor WHERE tenant_id = 1; -- expect 4
SELECT 'wm' AS domain, 'mes_wm_material_stock' AS sub_module, COUNT(*) AS seed_rows FROM mes_wm_material_stock WHERE tenant_id = 1; -- expect 6 (no remark col)

-- ========== dv.sql ==========
SELECT 'dv' AS domain, 'mes_dv_check_plan_subject' AS sub_module, COUNT(*) AS seed_rows FROM mes_dv_check_plan_subject WHERE tenant_id = 1; -- expect 5
SELECT 'dv' AS domain, 'mes_dv_repair' AS sub_module, COUNT(*) AS seed_rows FROM mes_dv_repair WHERE tenant_id = 1; -- expect 6
SELECT 'dv' AS domain, 'mes_md_workstation_machine' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_workstation_machine WHERE tenant_id = 1; -- expect 5
SELECT 'dv' AS domain, 'mes_dv_subject' AS sub_module, COUNT(*) AS seed_rows FROM mes_dv_subject WHERE tenant_id = 1; -- expect 6
SELECT 'dv' AS domain, 'mes_dv_check_plan_machinery' AS sub_module, COUNT(*) AS seed_rows FROM mes_dv_check_plan_machinery WHERE tenant_id = 1; -- expect 5
SELECT 'dv' AS domain, 'mes_dv_check_record' AS sub_module, COUNT(*) AS seed_rows FROM mes_dv_check_record WHERE tenant_id = 1; -- expect 5
SELECT 'dv' AS domain, 'mes_dv_mainten_record' AS sub_module, COUNT(*) AS seed_rows FROM mes_dv_mainten_record WHERE tenant_id = 1; -- expect 5
SELECT 'dv' AS domain, 'mes_dv_check_plan' AS sub_module, COUNT(*) AS seed_rows FROM mes_dv_check_plan WHERE tenant_id = 1; -- expect 5
SELECT 'dv' AS domain, 'mes_dv_machinery_type' AS sub_module, COUNT(*) AS seed_rows FROM mes_dv_machinery_type WHERE tenant_id = 1; -- expect 5
SELECT 'dv' AS domain, 'mes_dv_machinery' AS sub_module, COUNT(*) AS seed_rows FROM mes_dv_machinery WHERE tenant_id = 1; -- expect 6

-- ========== tm.sql ==========
SELECT 'tm' AS domain, 'mes_tm_tool' AS sub_module, COUNT(*) AS seed_rows FROM mes_tm_tool WHERE tenant_id = 1; -- expect 6
SELECT 'tm' AS domain, 'mes_tm_tool_type' AS sub_module, COUNT(*) AS seed_rows FROM mes_tm_tool_type WHERE tenant_id = 1; -- expect 4
SELECT 'tm' AS domain, 'mes_md_workstation_tool' AS sub_module, COUNT(*) AS seed_rows FROM mes_md_workstation_tool WHERE tenant_id = 1; -- expect 4

-- ========== qc.sql ==========
SELECT 'qc' AS domain, 'mes_qc_iqc_line' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_iqc_line WHERE tenant_id = 1; -- expect 7
SELECT 'qc' AS domain, 'mes_qc_rqc_line' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_rqc_line WHERE tenant_id = 1; -- expect 6
SELECT 'qc' AS domain, 'mes_qc_template' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_template WHERE tenant_id = 1; -- expect 6
SELECT 'qc' AS domain, 'mes_qc_template_item' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_template_item WHERE tenant_id = 1; -- expect 6
SELECT 'qc' AS domain, 'mes_qc_template_indicator' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_template_indicator WHERE tenant_id = 1; -- expect 7
SELECT 'qc' AS domain, 'mes_qc_ipqc_line' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_ipqc_line WHERE tenant_id = 1; -- expect 7
SELECT 'qc' AS domain, 'mes_qc_iqc' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_iqc WHERE tenant_id = 1; -- expect 6
SELECT 'qc' AS domain, 'mes_qc_oqc' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_oqc WHERE tenant_id = 1; -- expect 5
SELECT 'qc' AS domain, 'mes_qc_defect_record' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_defect_record WHERE tenant_id = 1; -- expect 6
SELECT 'qc' AS domain, 'mes_qc_rqc' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_rqc WHERE tenant_id = 1; -- expect 5
SELECT 'qc' AS domain, 'mes_qc_indicator_result_detail' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_indicator_result_detail WHERE tenant_id = 1; -- expect 4
SELECT 'qc' AS domain, 'mes_qc_oqc_line' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_oqc_line WHERE tenant_id = 1; -- expect 6
SELECT 'qc' AS domain, 'mes_qc_indicator' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_indicator WHERE tenant_id = 1; -- expect 8
SELECT 'qc' AS domain, 'mes_qc_indicator_result' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_indicator_result WHERE tenant_id = 1; -- expect 4
SELECT 'qc' AS domain, 'mes_qc_ipqc' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_ipqc WHERE tenant_id = 1; -- expect 6
SELECT 'qc' AS domain, 'mes_qc_defect' AS sub_module, COUNT(*) AS seed_rows FROM mes_qc_defect WHERE tenant_id = 1; -- expect 7

-- ========== cal.sql ==========
SELECT 'cal' AS domain, 'mes_cal_team_shift' AS sub_module, COUNT(*) AS seed_rows FROM mes_cal_team_shift WHERE tenant_id = 1; -- expect 7
SELECT 'cal' AS domain, 'mes_cal_plan_shift' AS sub_module, COUNT(*) AS seed_rows FROM mes_cal_plan_shift WHERE tenant_id = 1; -- expect 8
SELECT 'cal' AS domain, 'mes_cal_team_member' AS sub_module, COUNT(*) AS seed_rows FROM mes_cal_team_member WHERE tenant_id = 1; -- expect 8
SELECT 'cal' AS domain, 'mes_cal_plan_team' AS sub_module, COUNT(*) AS seed_rows FROM mes_cal_plan_team WHERE tenant_id = 1; -- expect 6
SELECT 'cal' AS domain, 'mes_cal_team' AS sub_module, COUNT(*) AS seed_rows FROM mes_cal_team WHERE tenant_id = 1; -- expect 6
SELECT 'cal' AS domain, 'mes_cal_plan' AS sub_module, COUNT(*) AS seed_rows FROM mes_cal_plan WHERE tenant_id = 1; -- expect 5
SELECT 'cal' AS domain, 'mes_cal_holiday' AS sub_module, COUNT(*) AS seed_rows FROM mes_cal_holiday WHERE tenant_id = 1; -- expect 6

-- ========== set.sql ==========
SELECT 'set' AS domain, 'mes_set_pollution_check' AS sub_module, COUNT(*) AS seed_rows FROM mes_set_pollution_check WHERE tenant_id = 1; -- expect 6
SELECT 'set' AS domain, 'mes_pollution_ledger' AS sub_module, COUNT(*) AS seed_rows FROM mes_pollution_ledger WHERE tenant_id = 1; -- expect 3
