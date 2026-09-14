-- =====================================================================
-- MES mock seed ROLLBACK - delete seeded tenant_id=1 rows in reverse order
-- CAUTION: dev/demo only; MES tables should be empty before seeding.
--          DELETE by tenant_id=1; do NOT run if real data exists.
-- =====================================================================
SET FOREIGN_KEY_CHECKS=0;

-- rollback: set.sql
DELETE FROM `mes_pollution_ledger` WHERE tenant_id = 1;
DELETE FROM `mes_set_pollution_check` WHERE tenant_id = 1;

-- rollback: cal.sql
DELETE FROM `mes_cal_holiday` WHERE tenant_id = 1;
DELETE FROM `mes_cal_team_shift` WHERE tenant_id = 1;
DELETE FROM `mes_cal_team_member` WHERE tenant_id = 1;
DELETE FROM `mes_cal_plan_team` WHERE tenant_id = 1;
DELETE FROM `mes_cal_plan_shift` WHERE tenant_id = 1;
DELETE FROM `mes_cal_team` WHERE tenant_id = 1;
DELETE FROM `mes_cal_plan` WHERE tenant_id = 1;

-- rollback: qc.sql
DELETE FROM `mes_qc_indicator_result_detail` WHERE tenant_id = 1;
DELETE FROM `mes_qc_indicator_result` WHERE tenant_id = 1;
DELETE FROM `mes_qc_defect_record` WHERE tenant_id = 1;
DELETE FROM `mes_qc_defect` WHERE tenant_id = 1;
DELETE FROM `mes_qc_rqc_line` WHERE tenant_id = 1;
DELETE FROM `mes_qc_rqc` WHERE tenant_id = 1;
DELETE FROM `mes_qc_oqc_line` WHERE tenant_id = 1;
DELETE FROM `mes_qc_oqc` WHERE tenant_id = 1;
DELETE FROM `mes_qc_ipqc_line` WHERE tenant_id = 1;
DELETE FROM `mes_qc_ipqc` WHERE tenant_id = 1;
DELETE FROM `mes_qc_iqc_line` WHERE tenant_id = 1;
DELETE FROM `mes_qc_iqc` WHERE tenant_id = 1;
DELETE FROM `mes_qc_template_item` WHERE tenant_id = 1;
DELETE FROM `mes_qc_template_indicator` WHERE tenant_id = 1;
DELETE FROM `mes_qc_indicator` WHERE tenant_id = 1;
DELETE FROM `mes_qc_template` WHERE tenant_id = 1;

-- rollback: tm.sql
DELETE FROM `mes_md_workstation_tool` WHERE tenant_id = 1;
DELETE FROM `mes_tm_tool` WHERE tenant_id = 1;
DELETE FROM `mes_tm_tool_type` WHERE tenant_id = 1;

-- rollback: dv.sql
DELETE FROM `mes_md_workstation_machine` WHERE tenant_id = 1;
DELETE FROM `mes_dv_repair` WHERE tenant_id = 1;
DELETE FROM `mes_dv_mainten_record` WHERE tenant_id = 1;
DELETE FROM `mes_dv_check_record` WHERE tenant_id = 1;
DELETE FROM `mes_dv_check_plan_subject` WHERE tenant_id = 1;
DELETE FROM `mes_dv_check_plan_machinery` WHERE tenant_id = 1;
DELETE FROM `mes_dv_check_plan` WHERE tenant_id = 1;
DELETE FROM `mes_dv_machinery` WHERE tenant_id = 1;
DELETE FROM `mes_dv_subject` WHERE tenant_id = 1;
DELETE FROM `mes_dv_machinery_type` WHERE tenant_id = 1;

-- rollback: wm.sql
DELETE FROM `mes_wm_package` WHERE tenant_id = 1;
DELETE FROM `mes_wm_item_consume` WHERE tenant_id = 1;
DELETE FROM `mes_wm_barcode` WHERE tenant_id = 1;
DELETE FROM `mes_wm_barcode_config` WHERE tenant_id = 1;
DELETE FROM `mes_wm_sn` WHERE tenant_id = 1;
DELETE FROM `mes_wm_transfer` WHERE tenant_id = 1;
DELETE FROM `mes_wm_stock_taking_plan` WHERE tenant_id = 1;
DELETE FROM `mes_wm_sales_notice` WHERE tenant_id = 1;
DELETE FROM `mes_wm_product_sales` WHERE tenant_id = 1;
DELETE FROM `mes_wm_return_vendor` WHERE tenant_id = 1;
DELETE FROM `mes_wm_return_sales` WHERE tenant_id = 1;
DELETE FROM `mes_wm_return_issue` WHERE tenant_id = 1;
DELETE FROM `mes_wm_outsource_issue` WHERE tenant_id = 1;
DELETE FROM `mes_wm_misc_issue` WHERE tenant_id = 1;
DELETE FROM `mes_wm_product_issue` WHERE tenant_id = 1;
DELETE FROM `mes_wm_misc_receipt` WHERE tenant_id = 1;
DELETE FROM `mes_wm_outsource_receipt` WHERE tenant_id = 1;
DELETE FROM `mes_wm_product_receipt` WHERE tenant_id = 1;
DELETE FROM `mes_wm_item_receipt` WHERE tenant_id = 1;
DELETE FROM `mes_wm_arrival_notice` WHERE tenant_id = 1;
DELETE FROM `mes_wm_material_stock` WHERE tenant_id = 1;
DELETE FROM `mes_wm_batch` WHERE tenant_id = 1;
DELETE FROM `mes_wm_warehouse` WHERE tenant_id = 1;

-- rollback: pro.sql
-- 注：mes_pro_project 当前为预留表（pro.sql 未铺数、dump 无建表证据），故不删除
DELETE FROM `mes_pro_work_record_log` WHERE tenant_id = 1;
DELETE FROM `mes_pro_work_record` WHERE tenant_id = 1;
DELETE FROM `mes_pro_andon_record` WHERE tenant_id = 1;
DELETE FROM `mes_pro_andon_config` WHERE tenant_id = 1;
DELETE FROM `mes_pro_card_process` WHERE tenant_id = 1;
DELETE FROM `mes_pro_card` WHERE tenant_id = 1;
DELETE FROM `mes_pro_feedback` WHERE tenant_id = 1;
DELETE FROM `mes_pro_task_issue` WHERE tenant_id = 1;
DELETE FROM `mes_pro_task` WHERE tenant_id = 1;
DELETE FROM `mes_pro_work_order_bom` WHERE tenant_id = 1;
DELETE FROM `mes_pro_work_order` WHERE tenant_id = 1;
DELETE FROM `mes_pro_route_product_bom` WHERE tenant_id = 1;
DELETE FROM `mes_pro_route_product` WHERE tenant_id = 1;
DELETE FROM `mes_pro_route_process` WHERE tenant_id = 1;
DELETE FROM `mes_pro_route` WHERE tenant_id = 1;
DELETE FROM `mes_pro_process_content` WHERE tenant_id = 1;
DELETE FROM `mes_pro_process` WHERE tenant_id = 1;

-- rollback: md.sql
DELETE FROM `mes_md_workstation_worker` WHERE tenant_id = 1;
DELETE FROM `mes_md_item_batch_config` WHERE tenant_id = 1;
DELETE FROM `mes_md_product_sop` WHERE tenant_id = 1;
DELETE FROM `mes_md_product_sip` WHERE tenant_id = 1;
DELETE FROM `mes_md_product_bom` WHERE tenant_id = 1;
DELETE FROM `mes_md_item` WHERE tenant_id = 1;
DELETE FROM `mes_md_vendor` WHERE tenant_id = 1;
DELETE FROM `mes_md_client` WHERE tenant_id = 1;
DELETE FROM `mes_md_workstation` WHERE tenant_id = 1;
DELETE FROM `mes_md_workshop` WHERE tenant_id = 1;
DELETE FROM `mes_md_unit_measure` WHERE tenant_id = 1;
DELETE FROM `mes_md_item_type` WHERE tenant_id = 1;

SET FOREIGN_KEY_CHECKS=1;
