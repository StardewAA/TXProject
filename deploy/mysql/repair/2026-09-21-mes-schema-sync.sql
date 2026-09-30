-- 自动生成：MES 缺失表/列（来源：sql/mes_202609202041.sql，已修复中文双重编码）
SET NAMES utf8mb4;

-- 1. 缺失表
-- mes_wms_shortage_inbox
CREATE TABLE IF NOT EXISTS `mes_wms_shortage_inbox` (
  `tenant_id` bigint NOT NULL,
  `event_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_version` bigint NOT NULL,
  `identity_key` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `payload` mediumtext NOT NULL,
  `shortage_status` varchar(16) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `confirm_status` varchar(32) NOT NULL DEFAULT 'AWAITING_CONFIRM' COMMENT 'MES 确认状态：AWAITING_CONFIRM 待确认 / CONFIRMED 已确认 / REJECTED 已驳回',
  `mes_work_order_no` varchar(64) DEFAULT NULL COMMENT 'MES 确认后生成的生产工单号',
  `reject_reason` varchar(512) DEFAULT NULL COMMENT 'MES 驳回原因',
  PRIMARY KEY (`tenant_id`,`event_id`),
  KEY `idx_mes_shortage_updated` (`tenant_id`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;;

-- mes_wms_shortage_read
CREATE TABLE IF NOT EXISTS `mes_wms_shortage_read` (
  `tenant_id` bigint NOT NULL,
  `event_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `user_id` bigint NOT NULL,
  `event_version` bigint NOT NULL,
  PRIMARY KEY (`tenant_id`,`event_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;;

-- mes_wms_shortage_version
CREATE TABLE IF NOT EXISTS `mes_wms_shortage_version` (
  `tenant_id` bigint NOT NULL,
  `event_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_version` bigint NOT NULL,
  `payload` mediumtext NOT NULL,
  PRIMARY KEY (`tenant_id`,`event_id`,`event_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;;

-- mes_work_order_approval_task
CREATE TABLE IF NOT EXISTS `mes_work_order_approval_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `work_order_id` bigint NOT NULL COMMENT '生产工单编号',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BPM 流程实例编号（幂等键）',
  `bpm_status` tinyint DEFAULT NULL COMMENT 'BPM 流程实例状态（2 审批通过 3 驳回）',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批原因/驳回意见',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '任务状态（0待处理 1成功 2失败 3跳过）',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_instance_id` (`process_instance_id`),
  KEY `idx_status_retry` (`status`,`next_retry_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产工单审批结果任务';;

-- 2. 缺失列
ALTER TABLE `mes_pro_work_order` ADD COLUMN `wms_shipment_order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'WMS 领料出库单号';
ALTER TABLE `mes_pro_work_order` ADD COLUMN `wms_shipment_status` tinyint NOT NULL DEFAULT '0' COMMENT 'WMS 领料状态：0未申请 1申请中 2已出库 3失败 4取消';
ALTER TABLE `mes_pro_work_order` ADD COLUMN `wms_shipment_version` int NOT NULL DEFAULT '0' COMMENT 'WMS 领料申请版本，每次申请递增';
ALTER TABLE `mes_pro_work_order` ADD COLUMN `wms_receipt_order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'WMS 完工入库单号';
ALTER TABLE `mes_pro_work_order` ADD COLUMN `wms_receipt_status` tinyint NOT NULL DEFAULT '0' COMMENT 'WMS 入库状态：0未申请 1申请中 2已入库 3失败 4取消';
ALTER TABLE `mes_pro_work_order` ADD COLUMN `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BPM 审批流程实例编号';

-- 3. 缺失表数据
-- mes_wms_shortage_inbox (1 行)
INSERT IGNORE INTO `mes_wms_shortage_inbox` VALUES (1,'a1b2c3d4-0001-4a5b-8c9d-202609160001',1,'fab2f7c1adc4aacf7c70be3cb806f849353a8c1ac6b37558031a8277ce00eb5f','{\"availableQuantity\":60,\"businessType\":\"SALE_SHIPMENT\",\"currentQuantity\":60,\"eventId\":\"a1b2c3d4-0001-4a5b-8c9d-202609160001\",\"eventType\":\"SALES_FINISHED_GOODS_SHORTAGE\",\"eventVersion\":1,\"occurredAt\":\"2026-09-16T18:30:00\",\"requestedQuantity\":100,\"reservationSupported\":false,\"reservedQuantity\":0,\"senderSystem\":\"WMS\",\"shortageQuantity\":40,\"shortageStatus\":\"OPEN\",\"skuCode\":\"预制保温管-08\",\"skuId\":119,\"skuName\":\"预制保温管-08\",\"sourceLineNo\":1,\"sourceOrderNo\":\"ERP-MOCK-SO-001\",\"sourceSystem\":\"ERP\",\"targetSystem\":\"MES\",\"tenantId\":1,\"unit\":\"米\",\"warehouseCode\":\"FG-WH-01\",\"warehouseId\":106,\"warehouseName\":\"成品仓\",\"wmsOrderNo\":\"WMS-ERP-ERP-MOCK-SO-001\"}','OPEN','2026-09-16 19:30:25.000000','AWAITING_CONFIRM',NULL,NULL),(1,'a1b2c3d4-0002-4a5b-8c9d-202609160002',1,'fab2f7c1adc4aacf7c70be3cb806f849353a8c1ac6b37558031a8277ce00eb5f','{\"availableQuantity\":60,\"businessType\":\"SALE_SHIPMENT\",\"currentQuantity\":60,\"eventId\":\"a1b2c3d4-0002-4a5b-8c9d-202609160002\",\"eventType\":\"SALES_FINISHED_GOODS_SHORTAGE\",\"eventVersion\":1,\"occurredAt\":\"2026-09-16T18:30:00\",\"requestedQuantity\":100,\"reservationSupported\":false,\"reservedQuantity\":0,\"senderSystem\":\"WMS\",\"shortageQuantity\":40,\"shortageStatus\":\"OPEN\",\"skuCode\":\"预制保温管-08\",\"skuId\":119,\"skuName\":\"预制保温管-08\",\"sourceLineNo\":1,\"sourceOrderNo\":\"ERP-MOCK-SO-001\",\"sourceSystem\":\"ERP\",\"targetSystem\":\"MES\",\"tenantId\":1,\"unit\":\"米\",\"warehouseCode\":\"FG-WH-01\",\"warehouseId\":106,\"warehouseName\":\"成品仓\",\"wmsOrderNo\":\"WMS-ERP-ERP-MOCK-SO-001\"}','OPEN','2026-09-16 19:33:31.000000','AWAITING_CONFIRM',NULL,NULL),(2010,'a1b2c3d4-e5f6-4789-a012-3456789abcde',1,'0ca8442a224975f3059bf8252de4fe02d8af0920ff5caacdcc20fbab5fa85696','{\"availableQuantity\":80,\"businessType\":\"SALE_SHIPMENT\",\"currentQuantity\":80,\"eventId\":\"a1b2c3d4-e5f6-4789-a012-3456789abcde\",\"eventType\":\"SALES_FINISHED_GOODS_SHORTAGE\",\"eventVersion\":1,\"occurredAt\":\"2026-09-18T11:30:00\",\"requestedQuantity\":100,\"reservationSupported\":false,\"reservedQuantity\":0,\"senderSystem\":\"WMS\",\"shortageQuantity\":20,\"shortageStatus\":\"OPEN\",\"skuCode\":\"ITEM20260916000016\",\"skuId\":1,\"skuName\":\"Test Product 720\",\"sourceLineNo\":1,\"sourceOrderNo\":\"SO-2026-0002\",\"sourceSystem\":\"ERP\",\"targetSystem\":\"MES\",\"tenantId\":2010,\"unit\":\"PCS\",\"warehouseCode\":\"WH001\",\"warehouseId\":1,\"warehouseName\":\"Main Warehouse\",\"wmsOrderNo\":\"WMS-2026-0002\"}','OPEN','2026-09-18 15:10:25.000000','AWAITING_CONFIRM',NULL,NULL),(2010,'f47ac10b-58cc-4372-a567-0e02b2c3d479',1,'45043a1aa8ed72701fbc57b0c74b938608516033c4f88e9c303b2912917071dd','{\"availableQuantity\":80,\"businessType\":\"SALE_SHIPMENT\",\"currentQuantity\":80,\"eventId\":\"f47ac10b-58cc-4372-a567-0e02b2c3d479\",\"eventType\":\"SALES_FINISHED_GOODS_SHORTAGE\",\"eventVersion\":1,\"occurredAt\":\"2026-09-18T10:00:00\",\"requestedQuantity\":100,\"reservationSupported\":false,\"reservedQuantity\":0,\"senderSystem\":\"WMS\",\"shortageQuantity\":20,\"shortageStatus\":\"OPEN\",\"skuCode\":\"SKU001\",\"skuId\":1,\"skuName\":\"Test Material\",\"sourceLineNo\":1,\"sourceOrderNo\":\"SO-2026-0001\",\"sourceSystem\":\"ERP\",\"targetSystem\":\"MES\",\"tenantId\":2010,\"unit\":\"PCS\",\"warehouseCode\":\"WH001\",\"warehouseId\":1,\"warehouseName\":\"Main Warehouse\",\"wmsOrderNo\":\"WMS-2026-0001\"}','OPEN','2026-09-18 15:04:08.000000','AWAITING_CONFIRM',NULL,NULL);

-- mes_wms_shortage_read (1 行)
INSERT IGNORE INTO `mes_wms_shortage_read` VALUES (1,'a1b2c3d4-0001-4a5b-8c9d-202609160001',4001,1),(1,'a1b2c3d4-0002-4a5b-8c9d-202609160002',1,1);

-- mes_wms_shortage_version (1 行)
INSERT IGNORE INTO `mes_wms_shortage_version` VALUES (1,'a1b2c3d4-0001-4a5b-8c9d-202609160001',1,'{\"availableQuantity\":60,\"businessType\":\"SALE_SHIPMENT\",\"currentQuantity\":60,\"eventId\":\"a1b2c3d4-0001-4a5b-8c9d-202609160001\",\"eventType\":\"SALES_FINISHED_GOODS_SHORTAGE\",\"eventVersion\":1,\"occurredAt\":\"2026-09-16T18:30:00\",\"requestedQuantity\":100,\"reservationSupported\":false,\"reservedQuantity\":0,\"senderSystem\":\"WMS\",\"shortageQuantity\":40,\"shortageStatus\":\"OPEN\",\"skuCode\":\"预制保温管-08\",\"skuId\":119,\"skuName\":\"预制保温管-08\",\"sourceLineNo\":1,\"sourceOrderNo\":\"ERP-MOCK-SO-001\",\"sourceSystem\":\"ERP\",\"targetSystem\":\"MES\",\"tenantId\":1,\"unit\":\"米\",\"warehouseCode\":\"FG-WH-01\",\"warehouseId\":106,\"warehouseName\":\"成品仓\",\"wmsOrderNo\":\"WMS-ERP-ERP-MOCK-SO-001\"}'),(1,'a1b2c3d4-0002-4a5b-8c9d-202609160002',1,'{\"availableQuantity\":60,\"businessType\":\"SALE_SHIPMENT\",\"currentQuantity\":60,\"eventId\":\"a1b2c3d4-0002-4a5b-8c9d-202609160002\",\"eventType\":\"SALES_FINISHED_GOODS_SHORTAGE\",\"eventVersion\":1,\"occurredAt\":\"2026-09-16T18:30:00\",\"requestedQuantity\":100,\"reservationSupported\":false,\"reservedQuantity\":0,\"senderSystem\":\"WMS\",\"shortageQuantity\":40,\"shortageStatus\":\"OPEN\",\"skuCode\":\"预制保温管-08\",\"skuId\":119,\"skuName\":\"预制保温管-08\",\"sourceLineNo\":1,\"sourceOrderNo\":\"ERP-MOCK-SO-001\",\"sourceSystem\":\"ERP\",\"targetSystem\":\"MES\",\"tenantId\":1,\"unit\":\"米\",\"warehouseCode\":\"FG-WH-01\",\"warehouseId\":106,\"warehouseName\":\"成品仓\",\"wmsOrderNo\":\"WMS-ERP-ERP-MOCK-SO-001\"}'),(2010,'a1b2c3d4-e5f6-4789-a012-3456789abcde',1,'{\"availableQuantity\":80,\"businessType\":\"SALE_SHIPMENT\",\"currentQuantity\":80,\"eventId\":\"a1b2c3d4-e5f6-4789-a012-3456789abcde\",\"eventType\":\"SALES_FINISHED_GOODS_SHORTAGE\",\"eventVersion\":1,\"occurredAt\":\"2026-09-18T11:30:00\",\"requestedQuantity\":100,\"reservationSupported\":false,\"reservedQuantity\":0,\"senderSystem\":\"WMS\",\"shortageQuantity\":20,\"shortageStatus\":\"OPEN\",\"skuCode\":\"ITEM20260916000016\",\"skuId\":1,\"skuName\":\"Test Product 720\",\"sourceLineNo\":1,\"sourceOrderNo\":\"SO-2026-0002\",\"sourceSystem\":\"ERP\",\"targetSystem\":\"MES\",\"tenantId\":2010,\"unit\":\"PCS\",\"warehouseCode\":\"WH001\",\"warehouseId\":1,\"warehouseName\":\"Main Warehouse\",\"wmsOrderNo\":\"WMS-2026-0002\"}'),(2010,'f47ac10b-58cc-4372-a567-0e02b2c3d479',1,'{\"availableQuantity\":80,\"businessType\":\"SALE_SHIPMENT\",\"currentQuantity\":80,\"eventId\":\"f47ac10b-58cc-4372-a567-0e02b2c3d479\",\"eventType\":\"SALES_FINISHED_GOODS_SHORTAGE\",\"eventVersion\":1,\"occurredAt\":\"2026-09-18T10:00:00\",\"requestedQuantity\":100,\"reservationSupported\":false,\"reservedQuantity\":0,\"senderSystem\":\"WMS\",\"shortageQuantity\":20,\"shortageStatus\":\"OPEN\",\"skuCode\":\"SKU001\",\"skuId\":1,\"skuName\":\"Test Material\",\"sourceLineNo\":1,\"sourceOrderNo\":\"SO-2026-0001\",\"sourceSystem\":\"ERP\",\"targetSystem\":\"MES\",\"tenantId\":2010,\"unit\":\"PCS\",\"warehouseCode\":\"WH001\",\"warehouseId\":1,\"warehouseName\":\"Main Warehouse\",\"wmsOrderNo\":\"WMS-2026-0001\"}');

-- mes_work_order_approval_task (1 行)
INSERT IGNORE INTO `mes_work_order_approval_task` VALUES (1,18,'bd363b48-b4a4-11f1-9e60-72a828bbcfdf',2,NULL,1,0,'2026-09-20 11:39:10',NULL,NULL,'2026-09-20 11:39:10',NULL,'2026-09-20 11:39:28',_binary '\0',2010),(2,61,'b0016987-b4c7-11f1-8bfa-ca5775f4ee27',2,NULL,1,0,'2026-09-20 15:49:01',NULL,NULL,'2026-09-20 15:49:01',NULL,'2026-09-20 15:49:20',_binary '\0',2010);