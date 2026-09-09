-- HRM 招聘两组合并增量脚本。
-- 不删除历史职位、候选人、简历或面试数据；可重复执行。

CREATE TABLE IF NOT EXISTS `hrm_recruit_application` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '应聘记录编号',
  `applicant_user_id` bigint NULL DEFAULT NULL COMMENT '门户投递用户编号，管理端历史记录为空',
  `candidate_id` bigint NOT NULL COMMENT '候选人编号',
  `post_id` bigint NOT NULL COMMENT '职位编号',
  `resume_id` bigint NOT NULL COMMENT '简历版本编号',
  `channel_id` bigint NULL DEFAULT NULL COMMENT '招聘渠道编号',
  `status` varchar(32) NOT NULL COMMENT '应聘状态',
  `status_update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '状态更新时间',
  `eliminate_reason` varchar(500) NULL DEFAULT NULL COMMENT '淘汰原因',
  `withdraw_reason` varchar(500) NULL DEFAULT NULL COMMENT '撤回原因',
  `source_remark` varchar(500) NULL DEFAULT NULL COMMENT '来源说明',
  `employee_id` bigint NULL DEFAULT NULL COMMENT '录用生成的员工编号',
  `onboard_time` datetime NULL DEFAULT NULL COMMENT '入职时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_application_applicant` (`applicant_user_id`, `create_time`),
  KEY `idx_application_candidate_post_status` (`candidate_id`, `post_id`, `status`),
  KEY `idx_application_post_status` (`post_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘应聘记录表';

CREATE TABLE IF NOT EXISTS `hrm_recruit_application_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '应聘流程日志编号',
  `application_id` bigint NOT NULL,
  `from_status` varchar(32) NULL DEFAULT NULL,
  `to_status` varchar(32) NOT NULL,
  `action` varchar(64) NOT NULL,
  `reason` varchar(500) NULL DEFAULT NULL,
  `operator_id` bigint NULL DEFAULT NULL,
  `process_instance_id` varchar(64) NULL DEFAULT NULL,
  `request_id` varchar(64) NULL DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_application_log_application` (`application_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘应聘流程日志表';

CREATE TABLE IF NOT EXISTS `hrm_recruit_offer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Offer 编号',
  `application_id` bigint NOT NULL,
  `offer_no` varchar(64) NOT NULL,
  `approval_status` varchar(32) NOT NULL,
  `salary_amount` decimal(12,2) NULL DEFAULT NULL,
  `salary_unit` tinyint NULL DEFAULT NULL,
  `post_id` bigint NOT NULL,
  `entry_post_id` bigint NULL DEFAULT NULL,
  `planned_onboard_date` date NULL DEFAULT NULL,
  `valid_until` date NULL DEFAULT NULL,
  `attachment_ids` varchar(2000) NULL DEFAULT NULL,
  `process_instance_id` varchar(64) NULL DEFAULT NULL,
  `approval_version` int NOT NULL DEFAULT 0,
  `employee_id` bigint NULL DEFAULT NULL,
  `sent_time` datetime NULL DEFAULT NULL,
  `confirm_time` datetime NULL DEFAULT NULL,
  `remark` varchar(500) NULL DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_offer_application` (`application_id`),
  KEY `idx_offer_status` (`approval_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘 Offer 表';

-- 面试表在第一组已有，给第二组补充应聘主键和应聘内轮次。
SET @schema_name = DATABASE();
SET @has_application_id = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'hrm_recruit_interview'
    AND column_name = 'application_id'
);
SET @sql = IF(@has_application_id = 0,
  'ALTER TABLE `hrm_recruit_interview` ADD COLUMN `application_id` bigint NULL DEFAULT NULL COMMENT ''应聘记录编号'' AFTER `id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_round_no = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'hrm_recruit_interview'
    AND column_name = 'round_no'
);
SET @sql = IF(@has_round_no = 0,
  'ALTER TABLE `hrm_recruit_interview` ADD COLUMN `round_no` int NULL DEFAULT NULL COMMENT ''应聘内面试轮次'' AFTER `stage_number`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_application_index = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @schema_name AND table_name = 'hrm_recruit_interview'
    AND index_name = 'idx_recruit_interview_application'
);
SET @sql = IF(@has_application_index = 0,
  'ALTER TABLE `hrm_recruit_interview` ADD KEY `idx_recruit_interview_application` (`application_id`, `round_no`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 门户招聘入口及统一流程查询权限。权限复用已有 portal 查询权限。
INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (1645, '招聘应聘', 'hrm:portal:query', 2, 5, 1614, 'recruit', 'ep:briefcase', 'hrm/portal/recruit/index', 'HrmPortalRecruit', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`), `component` = VALUES(`component`), `component_name` = VALUES(`component_name`), `deleted` = b'0';
