-- 修复：报工审批 BPM 联动的库表缺失（报工分页 500 / 定时任务每分钟报错）
-- 1. mes_pro_feedback 缺 process_instance_id 列（BPM 流程实例）
ALTER TABLE `mes_pro_feedback`
    ADD COLUMN `process_instance_id` varchar(64) DEFAULT NULL COMMENT 'BPM 流程实例编号' AFTER `status`;

-- 2. mes_feedback_approval_task 表缺失（报工审批结果异步消费任务）
CREATE TABLE `mes_feedback_approval_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `feedback_id` bigint NOT NULL COMMENT '报工单编号',
  `process_instance_id` varchar(64) NOT NULL COMMENT 'BPM 流程实例编号（幂等键）',
  `bpm_status` tinyint DEFAULT NULL COMMENT 'BPM 流程实例状态（2 审批通过 3 驳回）',
  `reason` varchar(500) DEFAULT NULL COMMENT '审批原因/驳回意见',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '任务状态（0待处理 1成功 2失败 3跳过）',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_instance_id` (`process_instance_id`),
  KEY `idx_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 报工审批结果任务';
