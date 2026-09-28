-- HRM 招聘 Agent 任务增量脚本。
--
-- 记录每一次 Agent 调用的场景、业务对象、幂等键、状态、模型与提示词版本，
-- 用于：幂等去重、失败可追溯、以及「模型/提示词版本/调用状态」的审计要求。
--
-- 使用 IF NOT EXISTS，允许在已有数据库上重复执行。

CREATE TABLE IF NOT EXISTS `hrm_recruit_agent_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务编号',
  `user_id` bigint NOT NULL COMMENT '发起用户编号（个人端为本人，管理端为 HR）',
  `scene` varchar(32) NOT NULL COMMENT '场景：RESUME_ANALYZE 等',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '业务对象类型：PERSONAL_RESUME_VERSION、APPLICATION 等',
  `biz_id` bigint DEFAULT NULL COMMENT '业务对象编号',
  `resume_version_id` bigint DEFAULT NULL COMMENT '输入的个人简历版本编号',
  `post_id` bigint DEFAULT NULL COMMENT '输入或关联的职位编号',
  `post_fingerprint` varchar(64) DEFAULT NULL COMMENT '任务发起时的职位内容指纹',
  `idempotency_key` varchar(64) NOT NULL COMMENT '幂等键，同一用户同一场景重复提交返回同一任务',
  `status` varchar(16) NOT NULL COMMENT '状态：PENDING、RUNNING、SUCCEEDED、FAILED',
  `model` varchar(64) DEFAULT NULL COMMENT '实际使用的模型标识',
  `prompt_version` varchar(32) DEFAULT NULL COMMENT '提示词版本',
  `start_time` datetime DEFAULT NULL COMMENT '开始执行时间',
  `finish_time` datetime DEFAULT NULL COMMENT '执行结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
  `error_code` varchar(64) DEFAULT NULL COMMENT '失败错误码，用于页面区分可重试与不可重试',
  `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因（不得包含简历原文）',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recruit_agent_task_idem` (`user_id`, `scene`, `idempotency_key`, `deleted`),
  KEY `idx_recruit_agent_task_biz` (`biz_type`, `biz_id`),
  KEY `idx_recruit_agent_task_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘 Agent 任务表';

-- 简历分析结果（诊断与匹配共用）。
CREATE TABLE IF NOT EXISTS `hrm_recruit_agent_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分析结果编号',
  `task_id` bigint NOT NULL COMMENT '所属任务编号',
  `scene` varchar(32) NOT NULL COMMENT '场景：RESUME_ANALYZE、JOB_MATCH 等',
  `resume_version_id` bigint DEFAULT NULL COMMENT '输入的个人简历版本编号',
  `post_id` bigint DEFAULT NULL COMMENT '关联职位编号',
  `post_fingerprint` varchar(64) DEFAULT NULL COMMENT '分析时的职位内容指纹，职位变化后可判定结论过期',
  `overall_score` int DEFAULT NULL COMMENT '综合得分，无评分场景为空',
  `result_json` text COMMENT '结构化分析结果（分项、依据、优势、缺口、待确认项）',
  `summary` varchar(1000) DEFAULT NULL COMMENT '结论摘要',
  `prompt_version` varchar(32) DEFAULT NULL COMMENT '提示词版本',
  `model` varchar(64) DEFAULT NULL COMMENT '生成该结论的模型',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_recruit_agent_analysis_task` (`task_id`),
  KEY `idx_recruit_agent_analysis_version` (`resume_version_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘 Agent 分析结果表';
