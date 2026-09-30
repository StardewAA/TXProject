-- MES 委外发料单接入 BPM 审批（复用采购入库打样模板）

ALTER TABLE `mes_wm_outsource_issue`
    ADD COLUMN `process_instance_id` varchar(64) NULL COMMENT 'BPM 流程实例编号' AFTER `status`;

CREATE TABLE `mes_wm_outsource_issue_approval_task` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `issue_id` bigint NOT NULL COMMENT '委外发料单编号',
    `process_instance_id` varchar(64) DEFAULT NULL COMMENT 'BPM 流程实例编号（幂等键）',
    `bpm_status` tinyint DEFAULT NULL COMMENT 'BPM 流程实例状态：2 审批通过 3 驳回',
    `reason` varchar(500) DEFAULT NULL COMMENT '审批原因/驳回意见',
    `status` tinyint NOT NULL DEFAULT '0' COMMENT '任务状态：0 待处理 1 成功 2 失败 3 跳过',
    `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
    `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
    `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_oi_task_issue_id` (`issue_id`) USING BTREE,
    KEY `idx_oi_task_status_retry` (`status`, `next_retry_time`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 委外发料审批结果任务';
