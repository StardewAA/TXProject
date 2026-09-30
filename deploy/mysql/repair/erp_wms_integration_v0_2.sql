-- ERP/WMS integration contract v0.2
-- Execute once after a full database backup. No demo/business data is included.

ALTER TABLE `erp_product`
    ADD COLUMN `material_code` varchar(64) NULL COMMENT '跨模块统一物料编码' AFTER `id`,
    ADD COLUMN `material_type` varchar(32) NOT NULL DEFAULT 'FINISHED_GOODS' COMMENT 'RAW_MATERIAL/FINISHED_GOODS' AFTER `material_code`,
    ADD COLUMN `quality_required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需要质检' AFTER `material_type`,
    ADD COLUMN `sync_version` bigint NOT NULL DEFAULT 1 COMMENT '主数据同步版本' AFTER `quality_required`;

UPDATE `erp_product`
SET `material_code` = CONCAT('ERP-MAT-', LPAD(`id`, 12, '0'))
WHERE `material_code` IS NULL OR `material_code` = '';

ALTER TABLE `erp_product`
    MODIFY COLUMN `material_code` varchar(64) NOT NULL COMMENT '跨模块统一物料编码',
    ADD UNIQUE KEY `uk_erp_product_tenant_material_code` (`tenant_id`, `material_code`, `deleted`);

ALTER TABLE `erp_product_unit`
    ADD COLUMN `code` varchar(32) NULL COMMENT '跨模块统一单位编码' AFTER `id`;

UPDATE `erp_product_unit`
SET `code` = CONCAT('UNIT-', LPAD(`id`, 8, '0'))
WHERE `code` IS NULL OR `code` = '';

ALTER TABLE `erp_product_unit`
    MODIFY COLUMN `code` varchar(32) NOT NULL COMMENT '跨模块统一单位编码',
    ADD UNIQUE KEY `uk_erp_product_unit_tenant_code` (`tenant_id`, `code`, `deleted`);

ALTER TABLE `erp_customer`
    ADD COLUMN `partner_code` varchar(64) NULL COMMENT '跨模块统一往来企业编码' AFTER `id`,
    ADD COLUMN `sync_version` bigint NOT NULL DEFAULT 1 COMMENT '主数据同步版本' AFTER `partner_code`;

UPDATE `erp_customer`
SET `partner_code` = CONCAT('ERP-CUS-', LPAD(`id`, 12, '0'))
WHERE `partner_code` IS NULL OR `partner_code` = '';

ALTER TABLE `erp_customer`
    MODIFY COLUMN `partner_code` varchar(64) NOT NULL COMMENT '跨模块统一往来企业编码',
    ADD UNIQUE KEY `uk_erp_customer_tenant_partner_code` (`tenant_id`, `partner_code`, `deleted`);

ALTER TABLE `erp_supplier`
    ADD COLUMN `partner_code` varchar(64) NULL COMMENT '跨模块统一往来企业编码' AFTER `id`,
    ADD COLUMN `sync_version` bigint NOT NULL DEFAULT 1 COMMENT '主数据同步版本' AFTER `partner_code`;

UPDATE `erp_supplier`
SET `partner_code` = CONCAT('ERP-SUP-', LPAD(`id`, 12, '0'))
WHERE `partner_code` IS NULL OR `partner_code` = '';

ALTER TABLE `erp_supplier`
    MODIFY COLUMN `partner_code` varchar(64) NOT NULL COMMENT '跨模块统一往来企业编码',
    ADD UNIQUE KEY `uk_erp_supplier_tenant_partner_code` (`tenant_id`, `partner_code`, `deleted`);

ALTER TABLE `erp_warehouse`
    ADD COLUMN `wms_warehouse_code` varchar(64) NULL COMMENT 'WMS 权威仓库编码；为空表示尚未建立仓库映射' AFTER `id`,
    ADD UNIQUE KEY `uk_erp_warehouse_tenant_wms_code` (`tenant_id`, `wms_warehouse_code`, `deleted`);

ALTER TABLE `erp_sale_order`
    ADD COLUMN `business_chain_no` varchar(64) NULL COMMENT '全局业务链编号' AFTER `process_instance_id`,
    ADD COLUMN `warehouse_status` varchar(32) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT 'WMS 仓储履约状态' AFTER `business_chain_no`,
    ADD COLUMN `inventory_decision_no` varchar(64) NULL COMMENT 'WMS 库存判断编号' AFTER `warehouse_status`,
    ADD COLUMN `reservation_no` varchar(64) NULL COMMENT 'WMS 库存预留编号' AFTER `inventory_decision_no`,
    ADD COLUMN `wms_shipment_order_no` varchar(64) NULL COMMENT 'WMS 销售出库单号' AFTER `reservation_no`,
    ADD COLUMN `wms_event_version` bigint NOT NULL DEFAULT 0 COMMENT '最后处理的 WMS 事件版本' AFTER `wms_shipment_order_no`,
    ADD COLUMN `wms_failure_reason` varchar(1024) NULL COMMENT 'WMS 处理失败或驳回原因' AFTER `wms_event_version`,
    ADD UNIQUE KEY `uk_erp_sale_order_tenant_chain` (`tenant_id`, `business_chain_no`, `deleted`);

ALTER TABLE `erp_sale_order_items`
    ADD COLUMN `warehouse_code` varchar(64) NULL COMMENT 'WMS 统一仓库编码' AFTER `product_unit_id`,
    ADD COLUMN `reserved_count` decimal(24, 6) NOT NULL DEFAULT 0 COMMENT 'WMS 已预留数量' AFTER `count`,
    ADD COLUMN `shortage_count` decimal(24, 6) NOT NULL DEFAULT 0 COMMENT 'WMS 剩余缺口数量' AFTER `reserved_count`;

ALTER TABLE `erp_purchase_in`
    ADD COLUMN `business_chain_no` varchar(64) NULL COMMENT '全局业务链编号' AFTER `status`,
    ADD COLUMN `wms_status` varchar(32) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT 'WMS 入库处理状态' AFTER `business_chain_no`,
    ADD COLUMN `wms_order_no` varchar(64) NULL COMMENT 'WMS 入库单号' AFTER `wms_status`,
    ADD COLUMN `wms_event_version` bigint NOT NULL DEFAULT 0 COMMENT 'WMS 最后事件版本' AFTER `wms_order_no`,
    ADD COLUMN `wms_failure_reason` varchar(1024) NULL COMMENT 'WMS 失败原因' AFTER `wms_event_version`;

ALTER TABLE `erp_purchase_in_items`
    ADD COLUMN `wms_actual_count` decimal(24, 6) NOT NULL DEFAULT 0 COMMENT 'WMS 实收入库数量' AFTER `count`,
    ADD COLUMN `qualified_count` decimal(24, 6) NOT NULL DEFAULT 0 COMMENT 'WMS 质检合格数量' AFTER `wms_actual_count`,
    ADD COLUMN `unqualified_count` decimal(24, 6) NOT NULL DEFAULT 0 COMMENT 'WMS 质检不合格数量' AFTER `qualified_count`;

ALTER TABLE `erp_sale_return`
    ADD COLUMN `business_chain_no` varchar(64) NULL COMMENT '全局业务链编号' AFTER `status`,
    ADD COLUMN `wms_status` varchar(32) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT 'WMS 客退入库处理状态' AFTER `business_chain_no`,
    ADD COLUMN `wms_order_no` varchar(64) NULL COMMENT 'WMS 客退入库单号' AFTER `wms_status`,
    ADD COLUMN `wms_event_version` bigint NOT NULL DEFAULT 0 COMMENT 'WMS 最后事件版本' AFTER `wms_order_no`,
    ADD COLUMN `wms_failure_reason` varchar(1024) NULL COMMENT 'WMS 失败原因' AFTER `wms_event_version`;

ALTER TABLE `erp_sale_return_items`
    ADD COLUMN `wms_actual_count` decimal(24, 6) NOT NULL DEFAULT 0 COMMENT 'WMS 客退实收入库数量' AFTER `count`;

ALTER TABLE `erp_purchase_return`
    ADD COLUMN `business_chain_no` varchar(64) NULL COMMENT '全局业务链编号' AFTER `status`,
    ADD COLUMN `wms_status` varchar(32) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT 'WMS 采购退货出库处理状态' AFTER `business_chain_no`,
    ADD COLUMN `wms_order_no` varchar(64) NULL COMMENT 'WMS 采购退货出库单号' AFTER `wms_status`,
    ADD COLUMN `wms_event_version` bigint NOT NULL DEFAULT 0 COMMENT 'WMS 最后事件版本' AFTER `wms_order_no`,
    ADD COLUMN `wms_failure_reason` varchar(1024) NULL COMMENT 'WMS 失败原因' AFTER `wms_event_version`;

ALTER TABLE `erp_purchase_return_items`
    ADD COLUMN `wms_actual_count` decimal(24, 6) NOT NULL DEFAULT 0 COMMENT 'WMS 采购退货实发数量' AFTER `count`;

CREATE TABLE `erp_wms_integration_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `request_id` varchar(64) NOT NULL COMMENT '幂等请求编号',
    `direction` varchar(16) NOT NULL COMMENT 'ERP_TO_WMS/WMS_TO_ERP',
    `aggregate_type` varchar(32) NOT NULL COMMENT '业务聚合类型',
    `aggregate_no` varchar(64) NOT NULL COMMENT '业务聚合编号',
    `event_version` bigint NOT NULL DEFAULT 1 COMMENT '事件版本',
    `processing_status` varchar(32) NOT NULL COMMENT '处理状态',
    `request_payload` longtext NULL COMMENT '请求 JSON',
    `response_payload` longtext NULL COMMENT '响应 JSON',
    `error_message` varchar(2048) NULL COMMENT '失败原因',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_time` datetime NULL COMMENT '下次重试时间',
    `creator` varchar(64) NULL DEFAULT NULL COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) NULL DEFAULT NULL COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_erp_wms_request` (`tenant_id`, `request_id`, `deleted`),
    KEY `idx_erp_wms_aggregate` (`tenant_id`, `aggregate_type`, `aggregate_no`),
    KEY `idx_erp_wms_retry` (`processing_status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP/WMS 跨模块幂等与补偿记录';

CREATE TABLE `erp_wms_collaboration_b` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `collaboration_no` varchar(64) NOT NULL COMMENT '协作单 B 编号',
    `business_chain_no` varchar(64) NOT NULL COMMENT '全局业务链编号',
    `source_production_order_no` varchar(64) NOT NULL COMMENT '来源生产工单号',
    `source_collaboration_g_no` varchar(64) NULL COMMENT '来源协作单 G 编号',
    `source_sale_order_no` varchar(64) NULL COMMENT '来源销售订单号',
    `technical_status` varchar(32) NOT NULL DEFAULT 'RECEIVED' COMMENT '技术接收状态',
    `business_status` varchar(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION' COMMENT 'ERP 业务确认状态',
    `event_version` bigint NOT NULL DEFAULT 1 COMMENT '最后事件版本',
    `required_at` datetime NULL COMMENT '需求时间',
    `purchase_order_id` bigint NULL COMMENT '生成的 ERP 采购订单 ID',
    `purchase_order_no` varchar(64) NULL COMMENT '生成的 ERP 采购订单号',
    `reason` varchar(1024) NULL COMMENT '驳回或异常原因',
    `creator` varchar(64) NULL DEFAULT NULL COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) NULL DEFAULT NULL COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_erp_collaboration_b_no` (`tenant_id`, `collaboration_no`, `deleted`),
    KEY `idx_erp_collaboration_b_chain` (`tenant_id`, `business_chain_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 接收的 WMS 协作单 B';

CREATE TABLE `erp_wms_collaboration_b_item` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `collaboration_id` bigint NOT NULL COMMENT '协作单 B ID',
    `line_no` int NOT NULL COMMENT '明细行号',
    `material_code` varchar(64) NOT NULL COMMENT '统一物料编码',
    `sku_code` varchar(64) NOT NULL COMMENT 'WMS SKU 编码',
    `material_name_snapshot` varchar(100) NOT NULL COMMENT '物料名称快照',
    `specification_snapshot` varchar(100) NULL COMMENT '规格快照',
    `unit_code` varchar(32) NOT NULL COMMENT '统一单位编码',
    `unit_name_snapshot` varchar(32) NOT NULL COMMENT '单位名称快照',
    `shortage_quantity` decimal(24, 6) NOT NULL COMMENT '缺口数量',
    `creator` varchar(64) NULL DEFAULT NULL COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) NULL DEFAULT NULL COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_erp_collaboration_b_item_line` (`tenant_id`, `collaboration_id`, `line_no`, `deleted`),
    KEY `idx_erp_collaboration_b_item_material` (`tenant_id`, `material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 接收的 WMS 协作单 B 明细';

ALTER TABLE `wms_warehouse`
    ADD COLUMN `warehouse_type` varchar(32) NOT NULL DEFAULT 'RAW_MATERIAL' COMMENT '原料仓/成品仓' AFTER `name`,
    ADD COLUMN `address` varchar(255) NULL COMMENT '仓库地址' AFTER `warehouse_type`,
    ADD COLUMN `principal` varchar(64) NULL COMMENT '负责人' AFTER `address`,
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '启用状态' AFTER `principal`,
    ADD COLUMN `data_version` bigint NOT NULL DEFAULT 1 COMMENT '主数据版本' AFTER `status`;

ALTER TABLE `wms_item`
    ADD COLUMN `material_type` varchar(32) NOT NULL DEFAULT 'FINISHED_GOODS' COMMENT '原料/成品类型' AFTER `code`,
    ADD COLUMN `quality_required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需要质检' AFTER `material_type`,
    ADD COLUMN `source_version` bigint NOT NULL DEFAULT 1 COMMENT 'ERP 主数据版本' AFTER `quality_required`,
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '启用状态' AFTER `source_version`;

ALTER TABLE `wms_merchant`
    ADD COLUMN `source_version` bigint NOT NULL DEFAULT 1 COMMENT 'ERP 主数据版本' AFTER `type`,
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '启用状态' AFTER `source_version`;
