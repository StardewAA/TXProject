-- 与 deploy/mysql/repair/2026-09-24-hrm-sandbox-record.sql 内容一致
-- HRM：沙箱（演示/培训）数据登记表（QA 报告 I-025）
--
-- 背景：实施/培训要在系统里跑通完整业务主线，产生的员工、请假、职位、考勤组等记录会留在正式台账里，
--       培训完只能靠人工回忆逐条删。这里加一张「登记表」：沙箱生成的数据登记在册，支持一键清场。
--
-- 设计取舍：不往业务表加 is_sandbox 列（避免改动核心表结构、避免历史数据回填），
--           改为旁路登记表，清场时按 (biz_type, biz_id) 逐条软删除并清空登记。

CREATE TABLE IF NOT EXISTS `hrm_sandbox_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `biz_type` varchar(32) NOT NULL COMMENT '业务类型：post / leave / employee / attendance_group',
    `biz_id` bigint NOT NULL COMMENT '业务主键',
    `biz_name` varchar(255) DEFAULT NULL COMMENT '业务名称（便于人工核对）',
    `description` varchar(255) DEFAULT NULL COMMENT '说明（生成来源/批次）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_hrm_sandbox_record_type_id` (`biz_type`, `biz_id`),
    KEY `idx_hrm_sandbox_record_tenant` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'HRM 沙箱（演示/培训）数据登记表';

-- 校验：应返回 1 行
-- SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hrm_sandbox_record';
