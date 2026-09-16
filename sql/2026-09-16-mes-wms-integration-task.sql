-- MES ↔ WMS 集成任务表（领料出库 / 完工入库 申请与结果轮询）
-- 范式：业务审批通过 → 落任务 → 定时发送/轮询 → 终态回写业务单据
-- 状态：0待发送 1已受理 2执行中 3成功 4失败 5拒绝 6取消

CREATE TABLE `mes_wms_integration_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_type` varchar(32) NOT NULL COMMENT '业务类型（PRODUCTION_SHIPMENT/PRODUCTION_RECEIPT）',
  `source_order_id` bigint NOT NULL COMMENT 'MES 业务单据编号',
  `source_order_no` varchar(56) NOT NULL COMMENT 'MES 业务单据号',
  `business_chain_no` varchar(64) NOT NULL COMMENT '跨系统追踪链号',
  `request_id` varchar(64) NOT NULL COMMENT '传输幂等键（同租户唯一）',
  `warehouse_code` varchar(20) NOT NULL COMMENT '目标 WMS 仓库编码（拆单后单仓）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0待发送 1已受理 2执行中 3成功 4失败 5拒绝 6取消）',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次处理时间',
  `wms_order_no` varchar(64) DEFAULT NULL COMMENT 'WMS 单据号（受理成功后返回）',
  `execution_status` tinyint DEFAULT NULL COMMENT 'WMS 执行状态（0待执行 1成功 2出库失败）',
  `completed_quantity` decimal(20, 2) DEFAULT NULL COMMENT 'WMS 完成数量（出库=确认出库数，入库=合格数之和）',
  `request_payload` text COMMENT '发送报文快照（JSON）',
  `result_payload` text COMMENT '结果报文快照（JSON）',
  `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request` (`tenant_id`, `request_id`),
  KEY `idx_status_retry` (`status`, `next_retry_time`),
  KEY `idx_source` (`tenant_id`, `order_type`, `source_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES↔WMS 集成任务';
