-- HRM 招聘沟通会话。求职者与岗位 HR 按职位一对一沟通，支持跨租户联系。

CREATE TABLE IF NOT EXISTS `hrm_recruit_chat_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话编号',
  `post_id` bigint NOT NULL COMMENT '招聘职位编号',
  `hr_user_id` bigint NOT NULL COMMENT '岗位招聘负责人用户编号',
  `candidate_user_id` bigint NOT NULL COMMENT '求职者用户编号',
  `candidate_id` bigint DEFAULT NULL COMMENT '正式投递后绑定的候选人编号',
  `last_content` varchar(500) DEFAULT NULL COMMENT '最后一条消息摘要',
  `last_sender_user_id` bigint DEFAULT NULL COMMENT '最后一条消息发送人',
  `last_time` datetime DEFAULT NULL COMMENT '最后一条消息时间',
  `candidate_unread` int NOT NULL DEFAULT 0 COMMENT '求职者未读数',
  `hr_unread` int NOT NULL DEFAULT 0 COMMENT 'HR 未读数',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '会话状态：0 待发起，1 等待回复，2 招聘方已回复，3 沟通失败',
  `application_status` varchar(32) DEFAULT NULL COMMENT '投递状态：READY_TO_SUBMIT 待投递，SUBMITTED 已投递',
  `application_id` bigint DEFAULT NULL COMMENT '正式应聘记录编号',
  `hr_reply_time` datetime DEFAULT NULL COMMENT '招聘方首次回复时间',
  `end_time` datetime DEFAULT NULL COMMENT '沟通结束时间',
  `end_reason` varchar(255) DEFAULT NULL COMMENT '沟通结束原因',
  `active_status` tinyint GENERATED ALWAYS AS (IF(`status` = 3, NULL, 1)) STORED COMMENT '未结束会话唯一键标记',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '职位所属租户',
  PRIMARY KEY (`id`),
  KEY `idx_recruit_chat_candidate_time` (`candidate_user_id`, `last_time`),
  KEY `idx_recruit_chat_hr_time` (`hr_user_id`, `last_time`),
  KEY `idx_recruit_chat_post_users` (`post_id`, `candidate_user_id`, `hr_user_id`),
  UNIQUE KEY `uk_recruit_chat_active` (`post_id`, `candidate_user_id`, `hr_user_id`, `active_status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘沟通会话';

CREATE TABLE IF NOT EXISTS `hrm_recruit_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息编号',
  `conversation_id` bigint NOT NULL COMMENT '会话编号',
  `sender_user_id` bigint NOT NULL COMMENT '发送人用户编号',
  `content_type` tinyint NOT NULL DEFAULT 1 COMMENT '内容类型：1 文本，2 简历',
  `content` varchar(2000) NOT NULL COMMENT '消息内容',
  `send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '职位所属租户',
  PRIMARY KEY (`id`),
  KEY `idx_recruit_chat_message_conv` (`conversation_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘沟通消息';
