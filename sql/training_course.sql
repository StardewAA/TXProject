-- ======================================================================
-- 云培训学院 - 模块二：课程管理（核心-难）基础框架建表脚本
-- 数据库：ruoyi-vue-pro（与部署 MySQL 一致）
-- 说明：
--   1. 依据《模块二 课程管理（核心-难）详细搭建方案》4.1~4.4 设计；
--   2. 工程为 Java yudao 微服务，表名前缀 training_（与原方案 SQLite 表名对应）；
--   3. 课程版本链使用 root_id + version + is_current 设计（同族版本保留）。
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_course.sql
-- ======================================================================

-- ----------------------------------------------------------------------
-- 4.1 课程表 training_course（含版本链字段）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_course` (
  `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `root_id`          bigint        DEFAULT NULL COMMENT '版本链根课程ID（同族根，新课程=自身id）',
  `version`          int           NOT NULL DEFAULT 1 COMMENT '版本号（从1递增）',
  `is_current`       bit(1)        NOT NULL DEFAULT b'1' COMMENT '是否当前生效版本',
  `category_id`      bigint        DEFAULT NULL COMMENT '课程分类ID（复用资源中心 categories，type=course，预留）',
  `code`             varchar(64)   DEFAULT NULL COMMENT '课程编码（审核通过后锁定）',
  `title`            varchar(200)  NOT NULL COMMENT '课程标题',
  `subtitle`         varchar(300)  DEFAULT NULL COMMENT '课程副标题',
  `summary`          text          COMMENT '课程简介',
  `cover_url`        varchar(500)  DEFAULT NULL COMMENT '封面图URL',
  `level`            tinyint       DEFAULT NULL COMMENT '课程级别：1初级 2中级 3高级',
  `credit`           decimal(5,2)  DEFAULT NULL COMMENT '课程学分',
  `duration_minutes` int           DEFAULT NULL COMMENT '课程总时长（分钟）',
  `industry`         varchar(100)  DEFAULT NULL COMMENT '面向行业',
  `biz_tags`         varchar(500)  DEFAULT NULL COMMENT '业务标签（JSON数组，如["岗前培训","复工强制"]）',
  `is_required`      bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否必修',
  `exam_id`          bigint        DEFAULT NULL COMMENT '关联结课考试试卷ID（模块三预留）',
  `lecturer`         varchar(64)   DEFAULT NULL COMMENT '主讲教师',
  `lecturer_brief`   varchar(500)  DEFAULT NULL COMMENT '讲师简介',
  `media_type`       tinyint       NOT NULL DEFAULT 0 COMMENT '内容类型：0未绑定 1文档 2视频 3图文（由媒资汇总）',
  `media_count`      int           NOT NULL DEFAULT 0 COMMENT '媒资数量（冗余）',
  `comment_count`    int           NOT NULL DEFAULT 0 COMMENT '评论数（冗余）',
  `audit_count`      int           NOT NULL DEFAULT 0 COMMENT '审核次数（冗余）',
  `status`           tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1待审核 2审核通过(待上架) 3已上架 4已下架',
  `submitter_id`     bigint        DEFAULT NULL COMMENT '最近提交人ID',
  `submitted_at`     datetime      DEFAULT NULL COMMENT '最近提交时间',
  `reject_reason`    varchar(500)  DEFAULT NULL COMMENT '最近驳回原因（驳回必填）',
  `last_audit_time`  datetime      DEFAULT NULL COMMENT '最近审核时间',
  `sort`             int           NOT NULL DEFAULT 0 COMMENT '排序',
  `remark`           varchar(500)  DEFAULT NULL COMMENT '备注',
  `creator`          varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`          varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`        bigint        NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_training_course_root` (`root_id`, `is_current`),
  KEY `idx_training_course_status` (`status`),
  KEY `idx_training_course_category` (`category_id`),
  KEY `idx_training_course_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表（含版本链）';

-- ----------------------------------------------------------------------
-- 4.2 课程媒资表 training_course_media（文档/视频/图片绑定）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_course_media` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '媒资ID',
  `course_id`    bigint       NOT NULL COMMENT '课程ID',
  `media_type`   tinyint      NOT NULL COMMENT '媒资类型：1文档 2视频 3图片',
  `name`         varchar(200) NOT NULL COMMENT '媒资名称',
  `url`          varchar(500) NOT NULL COMMENT '访问地址',
  `size_bytes`   bigint       DEFAULT NULL COMMENT '文件大小（字节）',
  `duration_seconds` int      DEFAULT NULL COMMENT '视频时长（秒，视频类型）',
  `sort`         int          NOT NULL DEFAULT 0 COMMENT '排序',
  `creator`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`    bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tcm_course` (`course_id`),
  KEY `idx_tcm_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程媒资绑定表';

-- ----------------------------------------------------------------------
-- 4.3 课程审核日志表 training_course_audit_log（永久留痕）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_course_audit_log` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `course_id`    bigint       NOT NULL COMMENT '课程ID',
  `action`       varchar(30)  NOT NULL COMMENT '动作：SUBMIT提交审核 WITHDRAW撤审 APPROVE通过 REJECT驳回 ONLINE上架 OFFLINE下架 PUBLISH发布新版本',
  `from_status`  tinyint      DEFAULT NULL COMMENT '操作前状态',
  `to_status`    tinyint      DEFAULT NULL COMMENT '操作后状态',
  `operator_id`  bigint       DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名快照',
  `reason`       varchar(500) DEFAULT NULL COMMENT '备注/驳回原因',
  `creator`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`    bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tcal_course` (`course_id`),
  KEY `idx_tcal_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程审核日志表';

-- ----------------------------------------------------------------------
-- 4.4 课程评论表 training_course_comment（学员课程详情页评论）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_course_comment` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `course_id`    bigint       NOT NULL COMMENT '课程ID',
  `parent_id`    bigint       NOT NULL DEFAULT 0 COMMENT '父评论ID（0=一级评论，预留回复能力）',
  `content`      varchar(500) NOT NULL COMMENT '评论内容（≤500字）',
  `user_id`      bigint       DEFAULT NULL COMMENT '评论用户ID',
  `user_name`    varchar(64)  DEFAULT NULL COMMENT '评论用户姓名快照',
  `creator`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`    bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tcc_course` (`course_id`),
  KEY `idx_tcc_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程评论表';

-- ----------------------------------------------------------------------
-- 种子数据（演示用一条示例课程，便于列表页/工作台联调）
-- ----------------------------------------------------------------------
INSERT INTO `training_course`
  (`root_id`, `version`, `is_current`, `category_id`, `code`, `title`, `subtitle`, `summary`,
   `level`, `credit`, `duration_minutes`, `industry`, `biz_tags`, `is_required`, `exam_id`,
   `lecturer`, `lecturer_brief`, `media_type`, `media_count`, `status`, `sort`, `remark`, `creator`)
VALUES
  (NULL, 1, b'1', NULL, 'TC-DEMO-001', '示例课程：管道防腐保温工艺入门', '演示数据，用于课程管理框架联调',
   '本课程介绍管道防腐保温基础工艺与安全规范，为初步开发联调用例。', 1, 2.00, 45, '防腐保温',
   '["示例标签"]', b'0', NULL, '张工', '资深防腐工艺工程师', 0, 0, 0, 0, 'seed', 'admin');
UPDATE `training_course` SET `root_id` = `id` WHERE `root_id` IS NULL;
