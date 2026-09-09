-- ======================================================================
-- 云培训学院 - 模块一：资源中心（易）建表脚本
-- 数据库：ruoyi-vue-pro（与部署 MySQL 一致）
-- 说明：
--   1. 依据《模块一 资源中心（易）详细搭建方案》第四章 4.1~4.8 设计；
--   2. 工程为 Java yudao 微服务，表名前缀 training_；
--   3. 分类表三类（文档/视频/试题）共用一张表，type 区分；
--   4. 资源删除一律逻辑删除（deleted=1），被引用资源由 resource_reference 校验拦截。
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_resource_center.sql
-- ======================================================================

-- ----------------------------------------------------------------------
-- 4.1 分类表 training_category（doc/video/question 共用，parent_id 二级层级）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_category` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `type`        varchar(20)  NOT NULL COMMENT '分类类型：doc文档 video视频 question试题 course课程(预留)',
  `name`        varchar(100) NOT NULL COMMENT '分类名称',
  `parent_id`   bigint       NOT NULL DEFAULT 0 COMMENT '父分类ID（0=根，仅支持二级）',
  `sort`        int          NOT NULL DEFAULT 0 COMMENT '排序',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tcat_type_parent` (`type`, `parent_id`),
  KEY `idx_tcat_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源中心分类表（文档/视频/试题共用）';

-- ----------------------------------------------------------------------
-- 4.2 文档资源表 training_document_resource
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_document_resource` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '文档资源ID',
  `title`         varchar(200)  NOT NULL COMMENT '文档标题',
  `category_id`   bigint        DEFAULT NULL COMMENT '所属分类ID',
  `file_name`     varchar(255)  NOT NULL COMMENT '原始文件名',
  `file_path`     varchar(500)  NOT NULL COMMENT '文件相对存储路径（uploads/resource 下）',
  `file_type`     varchar(20)   NOT NULL COMMENT '文件类型：pdf/jpg/png',
  `file_size`     bigint        DEFAULT NULL COMMENT '文件大小（字节）',
  `biz_category`  varchar(50)   DEFAULT NULL COMMENT '归属业务分类（行业新规/国标解读/防腐工艺等）',
  `tags`          varchar(255)  DEFAULT NULL COMMENT '标签（逗号分隔，轻量实现）',
  `est_minutes`   int           DEFAULT NULL COMMENT '预估阅读分钟（学时换算基数，预留）',
  `status`        tinyint       NOT NULL DEFAULT 2 COMMENT '状态：1草稿 2上线 3下架 4归档',
  `view_count`    int           NOT NULL DEFAULT 0 COMMENT '预览次数（阅读上报累加）',
  `read_seconds`  int           NOT NULL DEFAULT 0 COMMENT '累计阅读停留秒数',
  `uploader_id`   bigint        DEFAULT NULL COMMENT '上传人ID',
  `uploader_name` varchar(64)   DEFAULT NULL COMMENT '上传人姓名',
  `creator`       varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`       varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`     bigint        NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tdr_category` (`category_id`),
  KEY `idx_tdr_status` (`status`),
  KEY `idx_tdr_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档资源表';

-- ----------------------------------------------------------------------
-- 4.3 视频资源表 training_video_resource
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_video_resource` (
  `id`               bigint         NOT NULL AUTO_INCREMENT COMMENT '视频资源ID',
  `title`            varchar(200)   NOT NULL COMMENT '视频标题',
  `category_id`      bigint         DEFAULT NULL COMMENT '所属分类ID',
  `cover_url`        varchar(500)   DEFAULT NULL COMMENT '封面图URL（可选）',
  `file_name`        varchar(255)   NOT NULL COMMENT '原始文件名',
  `file_path`        varchar(500)   NOT NULL COMMENT '文件相对存储路径（uploads/resource 下）',
  `file_type`        varchar(20)    NOT NULL COMMENT '文件类型：mp4/mov',
  `file_size`        bigint         DEFAULT NULL COMMENT '文件大小（字节）',
  `duration_seconds` int            DEFAULT NULL COMMENT '视频时长（秒，上传时前端提供或后续解析）',
  `transcode_status` tinyint        NOT NULL DEFAULT 0 COMMENT '转码状态：0未转码 1排队 2处理中 3成功 4失败',
  `credit_hours`     decimal(5,2)   DEFAULT NULL COMMENT '学时值（独立配置）',
  `tags`             varchar(255)   DEFAULT NULL COMMENT '标签（逗号分隔）',
  `status`           tinyint        NOT NULL DEFAULT 2 COMMENT '状态：1草稿 2上线 3下架 4归档',
  `view_count`       int            NOT NULL DEFAULT 0 COMMENT '播放次数',
  `uploader_id`      bigint         DEFAULT NULL COMMENT '上传人ID',
  `uploader_name`    varchar(64)    DEFAULT NULL COMMENT '上传人姓名',
  `creator`          varchar(64)    DEFAULT '' COMMENT '创建者',
  `create_time`      datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`          varchar(64)    DEFAULT '' COMMENT '更新者',
  `update_time`      datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bit(1)         NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`        bigint         NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tvr_category` (`category_id`),
  KEY `idx_tvr_status` (`status`),
  KEY `idx_tvr_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频资源表';

-- ----------------------------------------------------------------------
-- 4.4 试题表 training_question
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_question` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '试题ID',
  `qtype`        tinyint      NOT NULL COMMENT '题型：1单选 2多选 3判断 4实操问答 5案例分析',
  `stem`         text         NOT NULL COMMENT '题干',
  `options_json` text         COMMENT '选项JSON：[{"key":"A","text":"...","isCorrect":true}]',
  `answer`       text         COMMENT '正确答案（判断/问答/案例分析存文本）',
  `analysis`     text         COMMENT '解析',
  `difficulty`   tinyint      NOT NULL DEFAULT 2 COMMENT '难度：1易 2中 3难',
  `status`       tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1启用 2停用（被引用禁物理删除可停用）',
  `creator_id`   bigint       DEFAULT NULL COMMENT '创建人ID',
  `creator_name` varchar(64)  DEFAULT NULL COMMENT '创建人姓名',
  `creator`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`    bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tq_qtype` (`qtype`),
  KEY `idx_tq_status` (`status`),
  KEY `idx_tq_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试题表';

-- ----------------------------------------------------------------------
-- 4.5 标签表 training_tag（多维：工种/课程/安全/工艺）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_tag` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name`        varchar(50)  NOT NULL COMMENT '标签名',
  `dimension`   varchar(20)  NOT NULL COMMENT '标签维度：work_type工种/course课程/safety安全/process工艺',
  `sort`        int          NOT NULL DEFAULT 0 COMMENT '排序',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ttag_dim_name` (`dimension`, `name`, `tenant_id`),
  KEY `idx_ttag_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表（试题多维度打标，文档/视频用字符串标签）';

-- ----------------------------------------------------------------------
-- 4.6 试题-标签关联表 training_question_tag
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_question_tag` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `question_id` bigint      NOT NULL COMMENT '试题ID',
  `tag_id`      bigint      NOT NULL COMMENT '标签ID',
  `creator`     varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`     bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint      NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tqt_question_tag` (`question_id`, `tag_id`),
  KEY `idx_tqt_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试题-标签关联表';

-- ----------------------------------------------------------------------
-- 4.7 资源引用表 training_resource_reference（防物理删除依据）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_resource_reference` (
  `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '引用ID',
  `resource_type` varchar(20) NOT NULL COMMENT '资源类型：document/video/question',
  `resource_id`   bigint      NOT NULL COMMENT '被引用资源ID',
  `ref_type`      varchar(20) NOT NULL COMMENT '引用方类型：course/exam/practice',
  `ref_id`        bigint      NOT NULL COMMENT '引用方ID（课程ID/试卷ID等）',
  `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`       varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立引用时间',
  `deleted`       bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`     bigint      NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_trr_res` (`resource_type`, `resource_id`),
  KEY `idx_trr_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源引用表（被引用资源禁止物理删除）';

-- ----------------------------------------------------------------------
-- 4.8 操作日志表 training_operation_log（资源生命周期操作留痕）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_operation_log` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id`     bigint       DEFAULT NULL COMMENT '操作人ID',
  `user_name`   varchar(64)  DEFAULT NULL COMMENT '操作人姓名',
  `action`      varchar(30)  NOT NULL COMMENT '动作：UPLOAD/CREATE/UPDATE/DELETE/ONLINE/OFFLINE/ARCHIVE/CREDIT/TRANSCODE/ENABLE/DISABLE/IMPORT',
  `target_type` varchar(20)  NOT NULL COMMENT '对象类型：document/video/question/category/tag',
  `target_id`   bigint       DEFAULT NULL COMMENT '对象ID',
  `detail`      varchar(500) DEFAULT NULL COMMENT '变更摘要',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tol_target` (`target_type`, `target_id`),
  KEY `idx_tol_action` (`action`),
  KEY `idx_tol_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源中心操作日志表';

-- ======================================================================
-- 种子数据（便于分类树/标签下拉/列表页联调）
-- ======================================================================

-- 文档分类
INSERT INTO `training_category` (`type`, `name`, `parent_id`, `sort`, `creator`) VALUES
  ('doc', '行业新规', 0, 1, 'admin'),
  ('doc', '国标解读', 0, 2, 'admin'),
  ('doc', '防腐工艺', 0, 3, 'admin');
-- 视频分类
INSERT INTO `training_category` (`type`, `name`, `parent_id`, `sort`, `creator`) VALUES
  ('video', '安全培训', 0, 1, 'admin'),
  ('video', '岗位技能', 0, 2, 'admin'),
  ('video', '现场实录', 0, 3, 'admin');
-- 试题分类
INSERT INTO `training_category` (`type`, `name`, `parent_id`, `sort`, `creator`) VALUES
  ('question', '通用安全', 0, 1, 'admin'),
  ('question', '特种作业', 0, 2, 'admin'),
  ('question', '施工工艺', 0, 3, 'admin');
-- 二级分类示例（文档：防腐工艺下的子类）
INSERT INTO `training_category` (`type`, `name`, `parent_id`, `sort`, `creator`) VALUES
  ('doc', '管道防腐', (SELECT id FROM (SELECT id FROM `training_category` WHERE `type`='doc' AND `name`='防腐工艺' AND `deleted`=b'0' LIMIT 1) t), 1, 'admin'),
  ('doc', '保温绝热', (SELECT id FROM (SELECT id FROM `training_category` WHERE `type`='doc' AND `name`='防腐工艺' AND `deleted`=b'0' LIMIT 1) t), 2, 'admin');

-- 标签（四维）
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`) VALUES
  ('焊工', 'work_type', 1, 'admin'),
  ('电工', 'work_type', 2, 'admin'),
  ('管道工', 'work_type', 3, 'admin'),
  ('岗前培训', 'course', 1, 'admin'),
  ('复工强制', 'course', 2, 'admin'),
  ('持证上岗', 'course', 3, 'admin'),
  ('动火作业', 'safety', 1, 'admin'),
  ('受限空间', 'safety', 2, 'admin'),
  ('高处作业', 'safety', 3, 'admin'),
  ('防腐工艺', 'process', 1, 'admin'),
  ('保温工艺', 'process', 2, 'admin');
