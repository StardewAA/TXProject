-- HRM 个人简历版本与投递快照增量脚本。
--
-- 目标：
-- 1. 个人简历新增不可变版本，保留父版本、来源职位、创建方式与修改摘要；
-- 2. 企业侧候选人简历新增结构化内容快照字段，投递时固化，个人简历后续变化不影响历史应聘。
--
-- 使用 IF NOT EXISTS，允许在已有数据库上重复执行。

CREATE TABLE IF NOT EXISTS `hrm_personal_resume_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '版本编号',
  `user_id` bigint NOT NULL COMMENT '登录用户编号',
  `parent_version_id` bigint DEFAULT NULL COMMENT '父版本编号',
  `version_no` int NOT NULL COMMENT '用户维度递增的版本号',
  `source_resume_id` bigint DEFAULT NULL COMMENT '来源个人简历编号',
  `source_post_id` bigint DEFAULT NULL COMMENT '来源目标职位编号',
  `create_type` tinyint NOT NULL DEFAULT 1 COMMENT '创建方式：1 手动固化，2 确认优化建议',
  `change_summary` varchar(500) DEFAULT NULL COMMENT '修改摘要',
  `schema_version` varchar(32) NOT NULL DEFAULT 'v1' COMMENT '内容结构版本',
  `content` text COMMENT '结构化简历内容（JSON）',
  `content_hash` varchar(64) NOT NULL COMMENT '内容指纹，用于幂等与追溯',
  `idempotency_key` varchar(64) NOT NULL COMMENT '幂等键，同一用户重复提交不生成多个版本',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 有效，1 已失效',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_personal_version_idem` (`user_id`, `idempotency_key`, `deleted`),
  KEY `idx_personal_version_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 个人简历版本表';

-- 企业侧候选人简历快照字段。
-- 说明：MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，重复执行会报重复列，属预期；新库由建表脚本直接包含这些列。
ALTER TABLE `hrm_recruit_resume`
  ADD COLUMN `content` text NULL COMMENT '简历内容快照（在线结构化简历）',
  ADD COLUMN `content_hash` varchar(64) DEFAULT NULL COMMENT '内容指纹',
  ADD COLUMN `source_personal_version_id` bigint DEFAULT NULL COMMENT '来源个人简历版本编号',
  ADD COLUMN `post_id` bigint DEFAULT NULL COMMENT '投递职位编号',
  ADD COLUMN `post_fingerprint` varchar(64) DEFAULT NULL COMMENT '投递时职位内容指纹';
