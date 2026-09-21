-- WMS 协作单 G（成品缺货 → MES 生产补充）状态机表
--
-- 设计依据：存储体系.md 6.3 / 9.1
--   - WMS 计算成品缺口后生成协作单 G 草稿；
--   - WMS 人员完成协作单 G 审批（状态机：DRAFT → APPROVED / REJECTED）后才发送给 MES；
--   - MES 确认后回填 mes_work_order_no；驳回回填 reject_reason。
--
-- 审批采用单据状态机（非 BPM），由后端接口驱动状态流转。

CREATE TABLE IF NOT EXISTS `wms_collaboration_order_g`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键编号',
    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    `g_no`                 varchar(64)   NOT NULL COMMENT '协作单 G 编号',
    `source_order_no`      varchar(64)   NOT NULL COMMENT '来源销售订单号（WMS 出库单业务单号）',
    `wms_order_id`         bigint        NOT NULL COMMENT '来源 WMS 出库单编号',
    `wms_order_no`         varchar(64)   NOT NULL COMMENT '来源 WMS 出库单号',
    `wms_order_line_id`    bigint        NOT NULL COMMENT '来源 WMS 出库单行项编号',
    `sku_id`               bigint        NOT NULL COMMENT '成品 SKU 编号',
    `sku_code`             varchar(64)   NOT NULL COMMENT '成品物料编码（跨模块统一编码）',
    `sku_name`             varchar(255)  NOT NULL DEFAULT '' COMMENT '成品名称',
    `warehouse_id`         bigint        NOT NULL COMMENT '成品仓库编号',
    `warehouse_code`       varchar(64)   NOT NULL DEFAULT '' COMMENT '成品仓库编码',
    `requested_quantity`   decimal(30, 10) NOT NULL DEFAULT 0 COMMENT '需求数量',
    `available_quantity`   decimal(30, 10) NOT NULL DEFAULT 0 COMMENT '可用库存',
    `shortage_quantity`    decimal(30, 10) NOT NULL DEFAULT 0 COMMENT '缺口数量',
    `reserved_quantity`    decimal(30, 10) NOT NULL DEFAULT 0 COMMENT '已预留数量',
    `required_date`        datetime      NULL COMMENT '需求时间',
    `status`               varchar(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / APPROVED 已审批 / REJECTED 已驳回 / SENT 已发送 / CONFIRMED 已确认 / COMPLETED 已完成',
    `mes_work_order_no`    varchar(64)   NULL COMMENT 'MES 确认后回填的正式生产工单号',
    `reject_reason`        varchar(512)  NULL COMMENT '驳回原因',
    `creator`              varchar(64)            DEFAULT '' COMMENT '创建者',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              varchar(64)            DEFAULT '' COMMENT '更新者',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_g_no` (`tenant_id`, `g_no`, `deleted`),
    UNIQUE KEY `uk_order_line` (`tenant_id`, `wms_order_id`, `wms_order_line_id`, `deleted`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='WMS 协作单 G（成品缺货 → MES 生产补充）';
