-- =============================================================================
-- 治污设施台账 + 活性炭更换作业票 建表
-- 日期：2026-09-17
-- 背景：方案文档第五章「治污设施运行管控」与「活性炭更换闭环」，
--       换下来的废活性炭是危废 HW49，须称重建档并回流危废台账（mes_pollution_ledger）。
-- 注：设施表的 machinery_id 指向它服务的【生产设备】（一台治污设施可服务多条产线），
--     故不加唯一约束；设施自身在设备台账里的档案关系本轮不做。
-- =============================================================================

CREATE TABLE `mes_set_facility` (
    `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `code`              varchar(64)   NOT NULL COMMENT '设施编号',
    `name`              varchar(200)  NOT NULL COMMENT '设施名称',
    `facility_type`     varchar(32)   NOT NULL COMMENT '设施类型：BAG_DUST 布袋除尘 / CARBON_CO 活性炭+催化燃烧 / POWDER_DUST 粉末防爆除尘',
    `machinery_id`      bigint        NULL COMMENT '关联生产设备编号（选填，一台治污设施可服务多条产线）',
    `workshop_id`       bigint        NULL COMMENT '所属车间编号',
    `outlet_code`       varchar(64)   NULL COMMENT '关联排放口编号（DA001/DA002/DA003）',
    `design_capacity`   varchar(128)  NULL COMMENT '设计处理能力（如 20000 m³/h）',
    `carbon_load`       decimal(12,2) NULL COMMENT '活性炭装填量 kg',
    `carbon_cycle_days` int           NULL COMMENT '更换周期（天）',
    `last_carbon_time`  datetime      NULL COMMENT '最近换炭时间（换炭作业票提交时回写）',
    `waste_location`    varchar(200)  NULL COMMENT '本设施产废的默认暂存去向',
    `status`            varchar(16)   NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING 运行 / STOPPED 停运',
    `photo_url`         varchar(500)  NULL COMMENT '铭牌照片',
    `remark`            varchar(500)  NULL COMMENT '备注',
    `creator`           varchar(64)   NULL DEFAULT '',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`           varchar(64)   NULL DEFAULT '',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id`         bigint        NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_code` (`code`),
    KEY `idx_facility_type` (`facility_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 安全环保检测-治污设施台账';

CREATE TABLE `mes_set_carbon_replace` (
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `code`               varchar(64)   NOT NULL COMMENT '作业票号 ZY-yyyyMMddHHmmssNNN',
    `facility_id`        bigint        NOT NULL COMMENT '治污设施编号',
    `replace_time`       datetime      NOT NULL COMMENT '更换时间',
    `old_carbon_weight`  decimal(12,3) NULL COMMENT '旧活性炭净重 kg（提交时必填）',
    `new_carbon_batch_no` varchar(64)  NULL COMMENT '新活性炭批号',
    `new_carbon_load`    decimal(12,3) NULL COMMENT '新活性炭装填量 kg',
    `iodine_value`       decimal(8,2)  NULL COMMENT '碘值 mg/g',
    `photo_urls`         varchar(1000) NULL COMMENT '换炭照片（多个逗号分隔）',
    `weight_source`      varchar(16)   NOT NULL DEFAULT 'MANUAL' COMMENT '称重来源：MANUAL 手工录入（预留 AUTO 设备直采）',
    `waste_location`     varchar(200)  NULL COMMENT '本次产废去向（留空取设施默认值）',
    `status`             varchar(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / SUBMITTED 已提交',
    `operator`           varchar(64)   NULL COMMENT '操作人（提交时写入）',
    `remark`             varchar(500)  NULL COMMENT '备注',
    `creator`            varchar(64)   NULL DEFAULT '',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`            varchar(64)   NULL DEFAULT '',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id`          bigint        NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_code` (`code`),
    KEY `idx_facility_id` (`facility_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 安全环保检测-活性炭更换作业票';
