-- HRM 个人用户简历增量脚本。
-- 个人简历按 user_id 隔离，支持附件简历和在线填写简历。

CREATE TABLE IF NOT EXISTS `hrm_personal_resume` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '个人简历编号',
  `user_id` bigint NOT NULL COMMENT '登录用户编号',
  `title` varchar(100) NOT NULL COMMENT '简历标题',
  `resume_type` tinyint NOT NULL COMMENT '简历类型：1 附件，2 在线',
  `file_name` varchar(255) DEFAULT NULL COMMENT '附件名称',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '附件地址',
  `content` text COMMENT '在线简历内容占位',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_personal_resume_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 个人用户简历表';
