-- 2026-09-21 导入「安全环保」模块引用的共享表缺失行（纯新增，不删除、不覆盖）
--
-- 来源：sql/202609201700/安全环保_202609201707.sql（163 号厂商环境，租户 2010 = 华翰 数据集）
-- 说明：该文件里 tenant 2010 的数据用的是 7xx / 1xxx / 9xxxxxx 主键段，与本地既有的
--       200000xxxx 演示数据完全不相交，因此按主键补缺即可，不会影响既有数据。
-- 生成方式：按主键 LEFT JOIN 判重，逐表 INSERT ... SELECT，可重复执行。

SET NAMES utf8mb4;

-- mes_md_item：补 14 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_md_item` (`id`, `code`, `name`, `specification`, `unit_measure_id`, `item_type_id`, `status`, `safe_stock_flag`, `min_stock`, `max_stock`, `high_value`, `batch_flag`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, s.`name`, s.`specification`, s.`unit_measure_id`, s.`item_type_id`, s.`status`, s.`safe_stock_flag`, s.`min_stock`, s.`max_stock`, s.`high_value`, s.`batch_flag`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_md_item` s
LEFT JOIN `ruoyi-vue-pro`.`mes_md_item` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_md_vendor：补 1 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_md_vendor` (`id`, `code`, `name`, `nickname`, `english_name`, `description`, `logo`, `level`, `score`, `address`, `website`, `email`, `telephone`, `contact1_name`, `contact1_telephone`, `contact1_email`, `contact2_name`, `contact2_telephone`, `contact2_email`, `credit_code`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, s.`name`, s.`nickname`, s.`english_name`, s.`description`, s.`logo`, s.`level`, s.`score`, s.`address`, s.`website`, s.`email`, s.`telephone`, s.`contact1_name`, s.`contact1_telephone`, s.`contact1_email`, s.`contact2_name`, s.`contact2_telephone`, s.`contact2_email`, s.`credit_code`, s.`status`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_md_vendor` s
LEFT JOIN `ruoyi-vue-pro`.`mes_md_vendor` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_batch：补 10 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_batch` (`id`, `code`, `item_id`, `produce_date`, `expire_date`, `receipt_date`, `vendor_id`, `client_id`, `sales_order_code`, `purchase_order_code`, `work_order_id`, `task_id`, `workstation_id`, `tool_id`, `mold_id`, `lot_number`, `quality_status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`, `pollution_status`, `pollution_location`, `pollution_marked`, `pollution_src_record`)
SELECT s.`id`, s.`code`, s.`item_id`, s.`produce_date`, s.`expire_date`, s.`receipt_date`, s.`vendor_id`, s.`client_id`, s.`sales_order_code`, s.`purchase_order_code`, s.`work_order_id`, s.`task_id`, s.`workstation_id`, s.`tool_id`, s.`mold_id`, s.`lot_number`, s.`quality_status`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`, s.`pollution_status`, s.`pollution_location`, s.`pollution_marked`, s.`pollution_src_record`
FROM txgy_stage.`mes_wm_batch` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_batch` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_item_receipt：补 6 行（线上独有列 1 个取默认值：process_instance_id）
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_item_receipt` (`id`, `code`, `name`, `iqc_id`, `notice_id`, `purchase_order_code`, `vendor_id`, `receipt_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, s.`name`, s.`iqc_id`, s.`notice_id`, s.`purchase_order_code`, s.`vendor_id`, s.`receipt_date`, s.`status`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_item_receipt` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_item_receipt` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_item_receipt_detail：补 5 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_item_receipt_detail` (`id`, `line_id`, `receipt_id`, `item_id`, `quantity`, `batch_id`, `warehouse_id`, `location_id`, `area_id`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`line_id`, s.`receipt_id`, s.`item_id`, s.`quantity`, s.`batch_id`, s.`warehouse_id`, s.`location_id`, s.`area_id`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_item_receipt_detail` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_item_receipt_detail` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_item_receipt_line：补 7 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_item_receipt_line` (`id`, `receipt_id`, `arrival_notice_line_id`, `item_id`, `received_quantity`, `batch_id`, `batch_code`, `production_date`, `expire_date`, `lot_number`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`receipt_id`, s.`arrival_notice_line_id`, s.`item_id`, s.`received_quantity`, s.`batch_id`, s.`batch_code`, s.`production_date`, s.`expire_date`, s.`lot_number`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_item_receipt_line` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_item_receipt_line` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_material_stock：补 9 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_material_stock` (`id`, `item_type_id`, `item_id`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `vendor_id`, `quantity`, `receipt_time`, `frozen`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`item_type_id`, s.`item_id`, s.`batch_id`, s.`batch_code`, s.`warehouse_id`, s.`location_id`, s.`area_id`, s.`vendor_id`, s.`quantity`, s.`receipt_time`, s.`frozen`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_material_stock` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_material_stock` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_misc_issue：补 3 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_misc_issue` (`id`, `code`, `name`, `type`, `source_doc_type`, `source_doc_id`, `source_doc_code`, `issue_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, s.`name`, s.`type`, s.`source_doc_type`, s.`source_doc_id`, s.`source_doc_code`, s.`issue_date`, s.`status`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_misc_issue` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_misc_issue` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_misc_issue_detail：补 3 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_misc_issue_detail` (`id`, `issue_id`, `line_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`issue_id`, s.`line_id`, s.`material_stock_id`, s.`item_id`, s.`quantity`, s.`batch_id`, s.`batch_code`, s.`warehouse_id`, s.`location_id`, s.`area_id`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_misc_issue_detail` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_misc_issue_detail` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_misc_issue_line：补 3 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_misc_issue_line` (`id`, `issue_id`, `source_doc_line_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`issue_id`, s.`source_doc_line_id`, s.`material_stock_id`, s.`item_id`, s.`quantity`, s.`batch_id`, s.`batch_code`, s.`warehouse_id`, s.`location_id`, s.`area_id`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_misc_issue_line` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_misc_issue_line` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_misc_receipt：补 1 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_misc_receipt` (`id`, `code`, `name`, `type`, `source_doc_type`, `source_doc_id`, `source_doc_code`, `receipt_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, s.`name`, s.`type`, s.`source_doc_type`, s.`source_doc_id`, s.`source_doc_code`, s.`receipt_date`, s.`status`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_misc_receipt` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_misc_receipt` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_misc_receipt_detail：补 1 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_misc_receipt_detail` (`id`, `receipt_id`, `line_id`, `item_id`, `quantity`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `production_date`, `expire_date`, `production_batch_number`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`receipt_id`, s.`line_id`, s.`item_id`, s.`quantity`, s.`batch_code`, s.`warehouse_id`, s.`location_id`, s.`area_id`, s.`production_date`, s.`expire_date`, s.`production_batch_number`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_misc_receipt_detail` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_misc_receipt_detail` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_misc_receipt_line：补 1 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_misc_receipt_line` (`id`, `receipt_id`, `item_id`, `quantity`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `production_date`, `expire_date`, `lot_number`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`receipt_id`, s.`item_id`, s.`quantity`, s.`batch_code`, s.`warehouse_id`, s.`location_id`, s.`area_id`, s.`production_date`, s.`expire_date`, s.`lot_number`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_misc_receipt_line` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_misc_receipt_line` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_transaction：补 13 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_transaction` (`id`, `type`, `biz_type`, `biz_id`, `biz_code`, `biz_line_id`, `material_stock_id`, `related_transaction_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `transaction_time`, `erp_time`, `receipt_time`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`type`, s.`biz_type`, s.`biz_id`, s.`biz_code`, s.`biz_line_id`, s.`material_stock_id`, s.`related_transaction_id`, s.`item_id`, s.`quantity`, s.`batch_id`, s.`batch_code`, s.`warehouse_id`, s.`location_id`, s.`area_id`, s.`transaction_time`, s.`erp_time`, s.`receipt_time`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_transaction` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_transaction` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_transfer：补 2 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_transfer` (`id`, `code`, `name`, `type`, `delivery_flag`, `recipient_name`, `recipient_telephone`, `destination_address`, `carrier`, `shipping_number`, `confirm_flag`, `transfer_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, s.`name`, s.`type`, s.`delivery_flag`, s.`recipient_name`, s.`recipient_telephone`, s.`destination_address`, s.`carrier`, s.`shipping_number`, s.`confirm_flag`, s.`transfer_date`, s.`status`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_transfer` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_transfer` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_transfer_detail：补 2 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_transfer_detail` (`id`, `line_id`, `transfer_id`, `item_id`, `quantity`, `batch_id`, `to_warehouse_id`, `to_location_id`, `to_area_id`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`line_id`, s.`transfer_id`, s.`item_id`, s.`quantity`, s.`batch_id`, s.`to_warehouse_id`, s.`to_location_id`, s.`to_area_id`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_transfer_detail` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_transfer_detail` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_transfer_line：补 2 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_transfer_line` (`id`, `transfer_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `from_warehouse_id`, `from_location_id`, `from_area_id`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`transfer_id`, s.`material_stock_id`, s.`item_id`, s.`quantity`, s.`batch_id`, s.`from_warehouse_id`, s.`from_location_id`, s.`from_area_id`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_transfer_line` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_transfer_line` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_warehouse：补 2 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_warehouse` (`id`, `code`, `name`, `address`, `area`, `charge_user_id`, `frozen`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, s.`name`, s.`address`, s.`area`, s.`charge_user_id`, s.`frozen`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_warehouse` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_warehouse` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_warehouse_area：补 4 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_warehouse_area` (`id`, `code`, `name`, `location_id`, `area`, `max_load`, `position_x`, `position_y`, `position_z`, `status`, `frozen`, `allow_item_mixing`, `allow_batch_mixing`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, s.`name`, s.`location_id`, s.`area`, s.`max_load`, s.`position_x`, s.`position_y`, s.`position_z`, s.`status`, s.`frozen`, s.`allow_item_mixing`, s.`allow_batch_mixing`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_warehouse_area` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_warehouse_area` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- mes_wm_warehouse_location：补 2 行
INSERT IGNORE INTO `ruoyi-vue-pro`.`mes_wm_warehouse_location` (`id`, `code`, `name`, `warehouse_id`, `area`, `frozen`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, s.`name`, s.`warehouse_id`, s.`area`, s.`frozen`, s.`remark`, s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM txgy_stage.`mes_wm_warehouse_location` s
LEFT JOIN `ruoyi-vue-pro`.`mes_wm_warehouse_location` l ON l.`id` = s.`id`
WHERE l.`id` IS NULL;

-- 合计新增 91 行：mes_md_item=14 mes_md_vendor=1 mes_wm_batch=10 mes_wm_item_receipt=6 mes_wm_item_receipt_detail=5 mes_wm_item_receipt_line=7 mes_wm_material_stock=9 mes_wm_misc_issue=3 mes_wm_misc_issue_detail=3 mes_wm_misc_issue_line=3 mes_wm_misc_receipt=1 mes_wm_misc_receipt_detail=1 mes_wm_misc_receipt_line=1 mes_wm_transaction=13 mes_wm_transfer=2 mes_wm_transfer_detail=2 mes_wm_transfer_line=2 mes_wm_warehouse=2 mes_wm_warehouse_area=4 mes_wm_warehouse_location=2
