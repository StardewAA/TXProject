-- =====================================================================
-- 报工审批 BPM 接入：报工表新增流程实例字段 + 审批结果任务表
-- 依据：《报工审批 BPM 接入设计方案》（人员4 · 平台横切）
-- 幂等：可重复执行（information_schema 判断 + PREPARE / CREATE IF NOT EXISTS）
-- =====================================================================

-- 1. 报工表新增 BPM 流程实例编号（提交报工发起流程后回填） --------------------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mes_pro_feedback'
      AND COLUMN_NAME = 'process_instance_id') = 0,
  'ALTER TABLE mes_pro_feedback ADD COLUMN process_instance_id varchar(64) NULL COMMENT ''BPM 流程实例编号'' AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 报工审批结果任务表（BPM 事件先落表，定时任务异步消费） ------------------
-- 沿用 mes_pro_project_sync_task 的成熟模式：规避 BPM 与 MES 事务提交的时序差
CREATE TABLE IF NOT EXISTS `mes_feedback_approval_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `feedback_id` bigint NOT NULL COMMENT '报工单编号',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'BPM 流程实例编号（幂等键）',
  `bpm_status` tinyint NULL DEFAULT NULL COMMENT 'BPM 流程实例状态：2 审批通过、3 驳回',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审批原因/驳回意见',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '任务状态：0 待处理、1 成功、2 失败、3 跳过',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_time` datetime NULL DEFAULT NULL COMMENT '下次重试时间',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败原因',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_fb_task_feedback_id` (`feedback_id`) USING BTREE,
  KEY `idx_fb_task_status_retry` (`status`, `next_retry_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 报工审批结果任务';
