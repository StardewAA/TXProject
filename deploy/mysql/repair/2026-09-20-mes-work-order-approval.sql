-- MES 生产工单接入 BPM 多级审批（流程 key：mes-work-order-approval）
-- 变更内容：
--   1. mes_pro_work_order 增加 BPM 流程实例编号
--   2. 新增工单审批结果任务表 mes_work_order_approval_task
--   3. 工单状态字典补「审批中(12)」「已批准(13)」
-- 幂等：可重复执行。

-- 1. 工单表增加 BPM 流程实例编号（提交审批后回填；审批通过是工单可下发的前置条件）
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_pro_work_order' AND column_name='process_instance_id'),'SELECT 1','ALTER TABLE `mes_pro_work_order` ADD COLUMN `process_instance_id` varchar(64) NULL COMMENT ''BPM 审批流程实例编号''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 工单审批结果任务表（BPM 审批事件 → 异步回写工单状态）
CREATE TABLE IF NOT EXISTS `mes_work_order_approval_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `work_order_id` bigint NOT NULL COMMENT '生产工单编号',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BPM 流程实例编号（幂等键）',
  `bpm_status` tinyint DEFAULT NULL COMMENT 'BPM 流程实例状态（2 审批通过 3 驳回）',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批原因/驳回意见',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '任务状态（0待处理 1成功 2失败 3跳过）',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_instance_id` (`process_instance_id`),
  KEY `idx_status_retry` (`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产工单审批结果任务';

-- 3. 工单状态字典补「审批中(12)」「已批准(13)」
INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, creator, updater, deleted)
SELECT 12, '审批中', '12', 'mes_pro_work_order_status', 0, 'warning', 'seed', 'seed', b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='mes_pro_work_order_status' AND value='12' AND deleted=b'0');

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, creator, updater, deleted)
SELECT 13, '已批准', '13', 'mes_pro_work_order_status', 0, 'success', 'seed', 'seed', b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='mes_pro_work_order_status' AND value='13' AND deleted=b'0');

-- 校验
SELECT sort, value, label FROM system_dict_data WHERE dict_type='mes_pro_work_order_status' AND deleted=b'0' ORDER BY sort;
SELECT column_name, column_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_pro_work_order' AND column_name='process_instance_id';
