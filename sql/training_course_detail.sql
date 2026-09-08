-- ======================================================================
-- 云培训学院 - 模块二：课程管理 二级子模块细化开发（21~26）增量脚本
-- 数据库：ruoyi-vue-pro（与部署 MySQL 一致）
-- 覆盖：
--   21 课程创建与维护：课程分类表 + 13 类行业种子；课程表扩展业务属性字段
--   22 媒资绑定：媒资表增加外部资源引用 ID（资源中心/VR 项目编号，占位）
--   24 课程上线审核：课程表增加审核人/审核时间留痕字段
--   26 课程评论管理：评论表软删留痕（status + deleted_by + deleted_at + delete_reason）
--   25 生命周期-删除保护：学习引用占位表 training_course_learning_ref
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_course_detail.sql
-- ======================================================================

-- ----------------------------------------------------------------------
-- 0.1 课程表字段扩展（版本链字段 root_id/version/is_current 已由 training_course.sql 提供）
-- ----------------------------------------------------------------------
ALTER TABLE `training_course`
  ADD COLUMN `course_level` varchar(20) DEFAULT NULL COMMENT '成长等级标记：APPRENTICE学徒 JUNIOR初级工 INTERMEDIATE中级工 SENIOR高级工 TECHNICIAN技师 SENIOR_TECHNICIAN高级技师（对应 training_growth_level 六级字典）' AFTER `level`,
  ADD COLUMN `applicable_posts` varchar(500) DEFAULT NULL COMMENT '适用岗位/工种（JSON数组）' AFTER `industry`,
  ADD COLUMN `owner_flag` varchar(64) DEFAULT NULL COMMENT '资源归属标识（对外部资源中心 owner_flag 的快照，占位）' AFTER `applicable_posts`,
  ADD COLUMN `is_composite` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否复合课程' AFTER `is_required`,
  ADD COLUMN `composite_tags` varchar(500) DEFAULT NULL COMMENT '复合课程子模块标签（JSON数组，如["管道安装","防腐保温"]）' AFTER `is_composite`,
  ADD COLUMN `reviewer_id` bigint DEFAULT NULL COMMENT '最近审核完成人ID' AFTER `submitter_id`,
  ADD COLUMN `reviewed_at` datetime DEFAULT NULL COMMENT '最近审核完成时间' AFTER `reviewer_id`;

-- ----------------------------------------------------------------------
-- 0.2 课程媒资表字段扩展（外部资源引用）
-- ----------------------------------------------------------------------
ALTER TABLE `training_course_media`
  ADD COLUMN `media_ref_id` varchar(255) DEFAULT NULL COMMENT '外部资源引用ID（对接资源中心媒资ID或VR项目编号；当前自含媒资填URL时为NULL，占位）' AFTER `url`;

-- VR 类型媒资无访问地址，允许 url 为空
ALTER TABLE `training_course_media`
  MODIFY COLUMN `url` varchar(500) DEFAULT NULL COMMENT '访问地址（VR/3D仿真类型可空）';

-- ----------------------------------------------------------------------
-- 0.3 课程评论表软删留痕（治理删除后保留原文，学员端/管理端可区分状态展示）
-- ----------------------------------------------------------------------
ALTER TABLE `training_course_comment`
  ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1正常 2已删除（软删留痕）' AFTER `user_name`,
  ADD COLUMN `deleted_by` bigint DEFAULT NULL COMMENT '删除人ID' AFTER `status`,
  ADD COLUMN `deleted_at` datetime DEFAULT NULL COMMENT '删除时间' AFTER `deleted_by`,
  ADD COLUMN `delete_reason` varchar(200) DEFAULT NULL COMMENT '删除原因（管理端治理占位，如"违规内容"）' AFTER `deleted_at`;

-- ----------------------------------------------------------------------
-- 1. 课程分类表 training_course_category（21 课程创建与维护）
--    说明：资源中心 categories(type=course) 在当前工程内以本表作为占位实现，
--          后续对接资源中心时可替换数据来源。
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_course_category` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name`        varchar(50)  NOT NULL COMMENT '分类名称',
  `code`        varchar(50)  DEFAULT NULL COMMENT '分类编码（唯一标识，对接资源中心时使用）',
  `sort`        int          NOT NULL DEFAULT 0 COMMENT '排序',
  `remark`      varchar(200) DEFAULT NULL COMMENT '备注',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tcc_name` (`name`),
  KEY `idx_tcc_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程分类表（13类行业种子，资源中心 categories 占位）';

-- 13 类行业分类种子（对接资源中心前以本表为准）
INSERT INTO `training_course_category` (`name`, `code`, `sort`, `remark`, `creator`) VALUES
  ('防腐保温', 'anticorrosion-insulation', 1, '防腐、绝热保温类工艺课程', 'seed'),
  ('管道安装', 'pipeline-install', 2, '管道预制、安装、试压类课程', 'seed'),
  ('焊接工艺', 'welding-tech', 3, '手工焊、氩弧焊、埋弧焊等工艺课程', 'seed'),
  ('电气仪表', 'electric-instrument', 4, '电气设备、仪表与自动化控制课程', 'seed'),
  ('化工工艺', 'chemical-process', 5, '化工单元操作与工艺安全课程', 'seed'),
  ('机械维修', 'mechanical-maint', 6, '机械设备维护、检修课程', 'seed'),
  ('起重吊装', 'hoisting', 7, '起重机械与吊装作业课程', 'seed'),
  ('受限空间', 'confined-space', 8, '受限空间作业安全课程', 'seed'),
  ('动火作业', 'hot-work', 9, '动火作业许可与安全课程', 'seed'),
  ('安全生产', 'safe-production', 10, '安全生产法规与通用安全课程', 'seed'),
  ('质量管理', 'quality-mgmt', 11, '质量管理体系与检验检测课程', 'seed'),
  ('环保技术', 'env-tech', 12, '环保设施运行与污染防治课程', 'seed'),
  ('通用技能', 'general-skill', 13, '新员工通用素养与办公技能课程', 'seed');

-- ----------------------------------------------------------------------
-- 2. 学习引用占位表 training_course_learning_ref（25 课程生命周期管理）
--    说明：用于“已被学员学习/归档/考核引用的课程禁止物理删除(409)”的占位实现；
--          学员学习记录由学员端（模块五）登记，本表先承接演示数据。
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_course_learning_ref` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '引用ID',
  `course_id`   bigint       NOT NULL COMMENT '课程ID',
  `user_id`     bigint       DEFAULT NULL COMMENT '学员用户ID',
  `user_name`   varchar(64)  DEFAULT NULL COMMENT '学员姓名快照',
  `ref_type`    varchar(30)  NOT NULL DEFAULT 'LEARN' COMMENT '引用类型：LEARN学习 RECORD档案 ASSESS考核（预留）',
  `remark`      varchar(200) DEFAULT NULL COMMENT '备注',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tclr_course` (`course_id`),
  KEY `idx_tclr_user` (`user_id`),
  KEY `idx_tclr_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程学习引用占位表（删除保护）';
