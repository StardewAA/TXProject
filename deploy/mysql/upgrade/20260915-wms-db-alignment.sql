-- WMS 库表与代码对齐（可重复执行）
--
-- 背景：本地/测试库由 deploy/mysql/init 下的全量 dump 恢复，该 dump 早于 WMS
-- 主数据、质检、出库审批等功能迭代，导致运行期报
-- `Unknown column 'approval_status' in 'field list'` 等错误（前端表现为「系统异常」）。
--
-- 本脚本补齐 DO 中已声明、库中缺失的列与表；全部判断 information_schema 后再执行，
-- 可在任意已初始化过的库上重复执行。

-- 入库/出库补列语句超过 MySQL 默认的 1024 字节，避免 GROUP_CONCAT 截断 DDL。
SET @wms_previous_group_concat_max_len := @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 16384;

-- ===================== 1. wms_item：物料类型 / 质检 / ERP 版本 / 启用状态 =====================
SET @ddl := (
    SELECT CONCAT('ALTER TABLE `wms_item` ',
                  GROUP_CONCAT(CONCAT('ADD COLUMN ', column_def) SEPARATOR ', '))
    FROM (SELECT '`material_type` varchar(32) NULL COMMENT ''物料类型：RAW_MATERIAL 原材料，FINISHED_GOODS 成品''' AS column_def, 'material_type' AS column_name
          UNION ALL SELECT '`quality_required` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否需要质检''', 'quality_required'
          UNION ALL SELECT '`source_version` bigint NOT NULL DEFAULT 0 COMMENT ''ERP 主数据版本''', 'source_version'
          UNION ALL SELECT '`status` tinyint NOT NULL DEFAULT 0 COMMENT ''状态：0 启用，1 停用''', 'status'
          UNION ALL SELECT '`current_quality_report_id` bigint NULL DEFAULT NULL COMMENT ''当前有效质检报告编号''', 'current_quality_report_id') defs
    WHERE NOT EXISTS (SELECT 1
                      FROM information_schema.COLUMNS c
                      WHERE c.TABLE_SCHEMA = DATABASE()
                        AND c.TABLE_NAME = 'wms_item'
                        AND c.COLUMN_NAME = defs.column_name)
);
SET @ddl := IFNULL(@ddl, 'SELECT 1');
PREPARE wms_item_stmt FROM @ddl;
EXECUTE wms_item_stmt;
DEALLOCATE PREPARE wms_item_stmt;

SET @index_exists := (SELECT COUNT(1)
                      FROM information_schema.STATISTICS
                      WHERE TABLE_SCHEMA = DATABASE()
                        AND TABLE_NAME = 'wms_item'
                        AND INDEX_NAME = 'idx_current_quality_report_id');
SET @index_sql := IF(@index_exists = 0,
                     'ALTER TABLE `wms_item` ADD INDEX `idx_current_quality_report_id` (`current_quality_report_id`)',
                     'SELECT 1');
PREPARE wms_item_index_stmt FROM @index_sql;
EXECUTE wms_item_index_stmt;
DEALLOCATE PREPARE wms_item_index_stmt;

-- ===================== 2. wms_merchant：ERP 版本 / 启用状态 =====================
SET @ddl := (
    SELECT CONCAT('ALTER TABLE `wms_merchant` ',
                  GROUP_CONCAT(CONCAT('ADD COLUMN ', column_def) SEPARATOR ', '))
    FROM (SELECT '`source_version` bigint NOT NULL DEFAULT 0 COMMENT ''ERP 主数据版本''' AS column_def, 'source_version' AS column_name
          UNION ALL SELECT '`status` tinyint NOT NULL DEFAULT 0 COMMENT ''状态：0 启用，1 停用''', 'status') defs
    WHERE NOT EXISTS (SELECT 1
                      FROM information_schema.COLUMNS c
                      WHERE c.TABLE_SCHEMA = DATABASE()
                        AND c.TABLE_NAME = 'wms_merchant'
                        AND c.COLUMN_NAME = defs.column_name)
);
SET @ddl := IFNULL(@ddl, 'SELECT 1');
PREPARE wms_merchant_stmt FROM @ddl;
EXECUTE wms_merchant_stmt;
DEALLOCATE PREPARE wms_merchant_stmt;

-- ===================== 3. wms_warehouse：仓库类型 / 负责人 / 启用状态 =====================
SET @ddl := (
    SELECT CONCAT('ALTER TABLE `wms_warehouse` ',
                  GROUP_CONCAT(CONCAT('ADD COLUMN ', column_def) SEPARATOR ', '))
    FROM (SELECT '`warehouse_type` varchar(32) NULL COMMENT ''仓库类型：RAW_MATERIAL 原料仓，FINISHED_GOODS 成品仓''' AS column_def, 'warehouse_type' AS column_name
          UNION ALL SELECT '`organization_name` varchar(128) NULL COMMENT ''所属组织/部门''', 'organization_name'
          UNION ALL SELECT '`city` varchar(64) NULL COMMENT ''所在城市''', 'city'
          UNION ALL SELECT '`address` varchar(255) NULL COMMENT ''仓库地址''', 'address'
          UNION ALL SELECT '`principal` varchar(64) NULL COMMENT ''负责人''', 'principal'
          UNION ALL SELECT '`principal_phone` varchar(32) NULL COMMENT ''负责人联系电话''', 'principal_phone'
          UNION ALL SELECT '`status` tinyint NOT NULL DEFAULT 0 COMMENT ''状态：0 启用，1 停用''', 'status'
          UNION ALL SELECT '`data_version` bigint NOT NULL DEFAULT 0 COMMENT ''乐观锁主数据版本''', 'data_version') defs
    WHERE NOT EXISTS (SELECT 1
                      FROM information_schema.COLUMNS c
                      WHERE c.TABLE_SCHEMA = DATABASE()
                        AND c.TABLE_NAME = 'wms_warehouse'
                        AND c.COLUMN_NAME = defs.column_name)
);
SET @ddl := IFNULL(@ddl, 'SELECT 1');
PREPARE wms_warehouse_stmt FROM @ddl;
EXECUTE wms_warehouse_stmt;
DEALLOCATE PREPARE wms_warehouse_stmt;

-- ===================== 4. wms_receipt_order：集成、审批与执行字段 =====================
SET @ddl := (
    SELECT CONCAT('ALTER TABLE `wms_receipt_order` ',
                  GROUP_CONCAT(CONCAT('ADD COLUMN ', column_def) SEPARATOR ', '))
    FROM (SELECT '`simulation` bit(1) NULL DEFAULT b''0'' COMMENT ''是否模拟数据''' AS column_def, 'simulation' AS column_name
          UNION ALL SELECT '`notification_status` varchar(32) NULL COMMENT ''外部通知状态''', 'notification_status'
          UNION ALL SELECT '`notification_error` varchar(1024) NULL COMMENT ''外部通知失败原因''', 'notification_error'
          UNION ALL SELECT '`notification_attempts` int NOT NULL DEFAULT 0 COMMENT ''外部通知尝试次数''', 'notification_attempts'
          UNION ALL SELECT '`next_notification_time` datetime NULL COMMENT ''下次外部通知时间''', 'next_notification_time'
          UNION ALL SELECT '`approval_status` int NOT NULL DEFAULT -1 COMMENT ''BPM 审批状态：-1 未提交，1 审批中，2 已通过，3 已拒绝，4 已取消''', 'approval_status'
          UNION ALL SELECT '`process_instance_id` varchar(64) NULL COMMENT ''BPM 流程实例编号''', 'process_instance_id'
          UNION ALL SELECT '`approval_time` datetime NULL COMMENT ''审批完成时间''', 'approval_time'
          UNION ALL SELECT '`approval_reason` varchar(512) NULL COMMENT ''审批结果原因''', 'approval_reason'
          UNION ALL SELECT '`execution_status` int NOT NULL DEFAULT 0 COMMENT ''执行状态''', 'execution_status'
          UNION ALL SELECT '`execution_time` datetime NULL COMMENT ''执行完成时间''', 'execution_time'
          UNION ALL SELECT '`execution_error` varchar(1024) NULL COMMENT ''执行失败原因''', 'execution_error'
          UNION ALL SELECT '`source_module` varchar(32) NULL COMMENT ''集成来源模块''', 'source_module'
          UNION ALL SELECT '`business_chain_no` varchar(64) NULL COMMENT ''跨模块业务链编号''', 'business_chain_no') defs
    WHERE NOT EXISTS (SELECT 1
                      FROM information_schema.COLUMNS c
                      WHERE c.TABLE_SCHEMA = DATABASE()
                        AND c.TABLE_NAME = 'wms_receipt_order'
                        AND c.COLUMN_NAME = defs.column_name)
);
SET @ddl := IFNULL(@ddl, 'SELECT 1');
PREPARE wms_receipt_order_stmt FROM @ddl;
EXECUTE wms_receipt_order_stmt;
DEALLOCATE PREPARE wms_receipt_order_stmt;

-- ===================== 5. wms_movement_order：库区字段 =====================
SET @ddl := (
    SELECT CONCAT('ALTER TABLE `wms_movement_order` ',
                  GROUP_CONCAT(CONCAT('ADD COLUMN ', column_def) SEPARATOR ', '))
    FROM (SELECT '`source_area_id` bigint NOT NULL DEFAULT 0 COMMENT ''来源库区编号''' AS column_def, 'source_area_id' AS column_name
          UNION ALL SELECT '`target_area_id` bigint NOT NULL DEFAULT 0 COMMENT ''目标库区编号''', 'target_area_id') defs
    WHERE NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                      WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'wms_movement_order'
                        AND c.COLUMN_NAME = defs.column_name)
);
SET @ddl := IFNULL(@ddl, 'SELECT 1');
PREPARE wms_movement_order_stmt FROM @ddl;
EXECUTE wms_movement_order_stmt;
DEALLOCATE PREPARE wms_movement_order_stmt;

-- ===================== 6. wms_shipment_order：集成、审批与执行字段 =====================
SET @ddl := (
    SELECT CONCAT('ALTER TABLE `wms_shipment_order` ',
                  GROUP_CONCAT(CONCAT('ADD COLUMN ', column_def) SEPARATOR ', '))
    FROM (SELECT '`simulation` bit(1) NULL DEFAULT b''0'' COMMENT ''是否模拟数据''' AS column_def, 'simulation' AS column_name
          UNION ALL SELECT '`notification_status` varchar(32) NULL COMMENT ''外部通知状态''', 'notification_status'
          UNION ALL SELECT '`notification_error` varchar(1024) NULL COMMENT ''外部通知失败原因''', 'notification_error'
          UNION ALL SELECT '`notification_attempts` int NOT NULL DEFAULT 0 COMMENT ''外部通知尝试次数''', 'notification_attempts'
          UNION ALL SELECT '`next_notification_time` datetime NULL COMMENT ''下次外部通知时间''', 'next_notification_time'
          UNION ALL SELECT '`approval_status` int NOT NULL DEFAULT -1 COMMENT ''BPM 审批状态：-1 未提交，1 审批中，2 已通过，3 已拒绝，4 已取消''' AS column_def, 'approval_status' AS column_name
          UNION ALL SELECT '`process_instance_id` varchar(64) NULL COMMENT ''BPM 流程实例编号''', 'process_instance_id'
          UNION ALL SELECT '`approval_time` datetime NULL COMMENT ''审批完成时间''', 'approval_time'
          UNION ALL SELECT '`approval_reason` varchar(512) NULL COMMENT ''审批结果原因''', 'approval_reason'
          UNION ALL SELECT '`execution_status` int NOT NULL DEFAULT 0 COMMENT ''执行状态''', 'execution_status'
          UNION ALL SELECT '`execution_time` datetime NULL COMMENT ''执行完成时间''', 'execution_time'
          UNION ALL SELECT '`execution_error` varchar(1024) NULL COMMENT ''执行失败原因''', 'execution_error'
          UNION ALL SELECT '`source_module` varchar(32) NULL COMMENT ''集成来源模块''', 'source_module'
          UNION ALL SELECT '`business_chain_no` varchar(64) NULL COMMENT ''跨模块业务链编号''', 'business_chain_no') defs
    WHERE NOT EXISTS (SELECT 1
                      FROM information_schema.COLUMNS c
                      WHERE c.TABLE_SCHEMA = DATABASE()
                        AND c.TABLE_NAME = 'wms_shipment_order'
                        AND c.COLUMN_NAME = defs.column_name)
);
SET @ddl := IFNULL(@ddl, 'SELECT 1');
PREPARE wms_shipment_order_stmt FROM @ddl;
EXECUTE wms_shipment_order_stmt;
DEALLOCATE PREPARE wms_shipment_order_stmt;

-- ===================== 5. 质检报告表 =====================
CREATE TABLE IF NOT EXISTS `wms_item_quality_report`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键编号',
    `item_id`     bigint       NOT NULL COMMENT '商品编号',
    `status`      tinyint      NOT NULL DEFAULT 0 COMMENT '质检状态：0 待质检，1 合格，2 异常',
    `image_urls`  json         NULL COMMENT '质检报告图片 URL 数组',
    `remark`      varchar(500) NULL COMMENT '异常说明/质检备注',
    `creator`     varchar(64)           DEFAULT '',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(64)           DEFAULT '',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)       NOT NULL DEFAULT b'0',
    `tenant_id`   bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_item_id` (`item_id`),
    KEY `idx_status` (`status`),
    KEY `idx_tenant_item_id` (`tenant_id`, `item_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='WMS 商品质检报告';

-- ===================== 6. 外部集成请求日志表 =====================
CREATE TABLE IF NOT EXISTS `wms_integration_request`
(
    `id`                    bigint        NOT NULL AUTO_INCREMENT COMMENT '主键编号',
    `request_id`            varchar(64)   NOT NULL COMMENT '外部请求编号（幂等键）',
    `operation_type`        varchar(32)   NOT NULL COMMENT '操作类型',
    `payload_digest`        varchar(64)   NOT NULL COMMENT '请求体摘要',
    `aggregate_type`        varchar(32)   NULL COMMENT '聚合根类型',
    `aggregate_no`          varchar(64)   NULL COMMENT '聚合根编号',
    `processing_status`     varchar(32)   NOT NULL COMMENT '处理状态',
    `processed_event_version` bigint      NULL COMMENT '已处理事件版本',
    `result_code`           int           NULL COMMENT '处理结果码',
    `response_payload`      text          NULL COMMENT '响应报文',
    `failure_reason`        varchar(1024) NULL COMMENT '失败原因',
    `creator`               varchar(64)            DEFAULT '',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`               varchar(64)            DEFAULT '',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id`             bigint        NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wms_integration_request` (`tenant_id`, `request_id`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='WMS 外部集成请求';

SET SESSION group_concat_max_len = @wms_previous_group_concat_max_len;
