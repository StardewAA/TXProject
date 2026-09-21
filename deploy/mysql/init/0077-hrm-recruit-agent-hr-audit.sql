-- HRM 招聘 Agent 管理端与审计补强增量脚本。
--
-- 目标：
-- 1. 任务表补充模型调用量（prompt / completion / total tokens），
--    满足审计要求里的「调用量」一项；
-- 2. 新增简历优化项表，把逐项确认结果（模块、字段路径、原值、建议值、最终值、采纳状态、排序）
--    单独落库，使「用户采纳情况」可查询、可追溯，而不是只体现在新版本内容里。
--
-- 说明：MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，重复执行会报重复列，属预期；
-- 新库由建表脚本直接包含这些列。

ALTER TABLE `hrm_recruit_agent_task`
  ADD COLUMN `prompt_tokens` int DEFAULT NULL COMMENT '提示词 token 数',
  ADD COLUMN `completion_tokens` int DEFAULT NULL COMMENT '输出 token 数',
  ADD COLUMN `total_tokens` int DEFAULT NULL COMMENT '合计 token 数';

CREATE TABLE IF NOT EXISTS `hrm_recruit_agent_optimize_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '优化项编号',
  `task_id` bigint NOT NULL COMMENT '所属简历优化任务编号',
  `user_id` bigint NOT NULL COMMENT '归属用户编号，一律取登录用户',
  `seq` int NOT NULL DEFAULT 0 COMMENT '排序，与建议项顺序一致',
  `module` varchar(32) DEFAULT NULL COMMENT '模块：BASIC_INFO 等',
  `field_path` varchar(64) DEFAULT NULL COMMENT '字段路径',
  `original_value` text COMMENT '原值（源版本中逐字出现的原文片段）',
  `suggested_value` text COMMENT '模型建议值',
  `final_value` text COMMENT '用户最终确认值',
  `decision` varchar(16) NOT NULL COMMENT '采纳状态：ACCEPT 接受、REJECT 拒绝、ADJUST 手工调整',
  `result_version_id` bigint DEFAULT NULL COMMENT '确认后生成的个人简历版本编号',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_recruit_agent_optimize_item_task` (`task_id`, `seq`),
  KEY `idx_recruit_agent_optimize_item_user` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘 Agent 简历优化项表';
