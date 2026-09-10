-- ======================================================================
-- 云培训学院 - 模块四：学员学习中心（核心-难）基础框架建表脚本
-- 数据库：ruoyi-vue-pro（与部署 MySQL 一致）
-- 说明：
--   1. 依据《模块四 学员学习中心（核心-难）详细搭建方案》3.1~3.4/4.x 设计；
--   2. 工程为 Java yudao 微服务，DO 字段 camelCase 自动映射为 snake_case；
--   3. 学员会话/任务按租户隔离，索引遵循 yudao tenant + 逻辑删除约定。
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_learner_learning.sql
-- ======================================================================

-- ----------------------------------------------------------------------
-- 3.1 学习任务表 training_learner_task（管理员指派 / 学员自主选学）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_learner_task` (
  `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `user_id`          bigint        NOT NULL COMMENT '学员用户ID',
  `course_id`        bigint        NOT NULL COMMENT '课程ID（当前生效版本）',
  `source_type`      varchar(16)   NOT NULL DEFAULT 'assign' COMMENT '来源：assign指派 / self自主选学',
  `status`           tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0待学习 1学习中 2已完成',
  `progress_percent` decimal(8,2)  NOT NULL DEFAULT 0.00 COMMENT '学习进度(%)',
  `credit_total`     decimal(5,2)  NOT NULL DEFAULT 0.00 COMMENT '应获学时（小时）',
  `credit_earned`    decimal(5,2)  NOT NULL DEFAULT 0.00 COMMENT '已获学时（小时）',
  `started_at`       datetime      DEFAULT NULL COMMENT '首次学习开始时间',
  `completed_at`     datetime      DEFAULT NULL COMMENT '完成时间',
  `remark`           varchar(500)  DEFAULT NULL COMMENT '备注',
  `creator`          varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`          varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`        bigint        NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tlt_user_course` (`tenant_id`, `user_id`, `course_id`),
  KEY `idx_tlt_course` (`course_id`),
  KEY `idx_tlt_status` (`status`),
  KEY `idx_tlt_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员学习任务表';

-- ----------------------------------------------------------------------
-- 3.2 学习会话表 training_learner_session（防挂机计时主表）
--     心跳规则：delta∈[1,max_delta]累计有效秒；delta>timeout 自动结束(timeout)；
--     过快心跳(clock)、验证失败(cheat) 均判无效。
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_learner_session` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id`       bigint       NOT NULL COMMENT '学员用户ID',
  `course_id`     bigint       DEFAULT NULL COMMENT '课程ID（直播/回放为NULL）',
  `task_id`       bigint       DEFAULT NULL COMMENT '学习任务ID（直播/回放为NULL）',
  `media_type`    varchar(16)  NOT NULL COMMENT '媒资类型：video/document/live/replay',
  `media_id`      bigint       NOT NULL COMMENT '媒资ID（course_media.id 或 live_room.id）',
  `start_at`      datetime     NOT NULL COMMENT '会话开始时间',
  `end_at`        datetime     DEFAULT NULL COMMENT '会话结束时间',
  `valid_seconds` int          NOT NULL DEFAULT 0 COMMENT '有效学习秒数',
  `status`        varchar(16)  NOT NULL DEFAULT 'active' COMMENT '状态：active/ended/invalid',
  `end_reason`    varchar(32)  DEFAULT NULL COMMENT '结束原因：normal/timeout/cheat/clock',
  `creator`       varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`       varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`     bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tls_user_course` (`user_id`, `course_id`, `status`),
  KEY `idx_tls_task` (`task_id`),
  KEY `idx_tls_media` (`media_id`),
  KEY `idx_tls_user_type` (`user_id`, `media_type`),
  KEY `idx_tls_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习会话表';

-- ----------------------------------------------------------------------
-- 3.3 防挂机验证表 training_verify_challenge
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_verify_challenge` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '挑战ID',
  `session_id`  bigint       NOT NULL COMMENT '学习会话ID',
  `question`    varchar(500) NOT NULL COMMENT '验证问题（数学算式）',
  `answer_hash` varchar(64)  NOT NULL COMMENT '正确答案SHA-256摘要（小写hex）',
  `issued_at`   datetime     NOT NULL COMMENT '下发时间',
  `answered_at` datetime     DEFAULT NULL COMMENT '作答时间',
  `passed`      bit(1)       DEFAULT NULL COMMENT '是否通过',
  `attempts`    int          NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tvc_session` (`session_id`),
  KEY `idx_tvc_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='防挂机人机验证挑战表';

-- ----------------------------------------------------------------------
-- 3.4 文档阅读明细表 training_doc_read_log
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_doc_read_log` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `session_id`    bigint       NOT NULL COMMENT '学习会话ID',
  `user_id`       bigint       NOT NULL COMMENT '学员用户ID',
  `course_id`     bigint       DEFAULT NULL COMMENT '课程ID',
  `media_id`      bigint       NOT NULL COMMENT '文档媒资ID',
  `page_no`       int          NOT NULL COMMENT '页号（从1开始）',
  `total_pages`   int          DEFAULT NULL COMMENT '文档总页数（会话内最后一次上报）',
  `stay_seconds`  int          NOT NULL DEFAULT 0 COMMENT '本页累计停留秒数',
  `report_at`     datetime     NOT NULL COMMENT '最近上报时间',
  `creator`       varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`       varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`     bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tdrl_session` (`session_id`),
  KEY `idx_tdrl_user_media` (`user_id`, `course_id`, `media_id`),
  KEY `idx_tdrl_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档阅读明细表';

-- ----------------------------------------------------------------------
-- 学习打卡记录表 training_checkin_record
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_checkin_record` (
  `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `session_id`           bigint       NOT NULL COMMENT '学习会话ID',
  `user_id`              bigint       NOT NULL COMMENT '学员用户ID',
  `course_id`            bigint       DEFAULT NULL COMMENT '课程ID',
  `media_id`             bigint       DEFAULT NULL COMMENT '媒资ID',
  `media_type`           varchar(16)  DEFAULT NULL COMMENT '媒资类型',
  `valid_seconds_before` int          NOT NULL DEFAULT 0 COMMENT '打卡前会话累计有效秒数',
  `checkin_at`           datetime     NOT NULL COMMENT '打卡时间',
  `creator`              varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`              varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`            bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tcr_session` (`session_id`),
  KEY `idx_tcr_user_course` (`user_id`, `course_id`),
  KEY `idx_tcr_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习打卡记录表';

-- ----------------------------------------------------------------------
-- 直播房间表 training_live_room
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_live_room` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '房间ID',
  `title`           varchar(200) NOT NULL COMMENT '直播标题',
  `cover_url`       varchar(500) DEFAULT NULL COMMENT '封面图URL',
  `lecturer_name`   varchar(64)  DEFAULT NULL COMMENT '讲师姓名',
  `stream_url`      varchar(500) DEFAULT NULL COMMENT '直播流地址',
  `status`          tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0未开始 1直播中 2已结束',
  `plan_start_at`   datetime     DEFAULT NULL COMMENT '计划开始时间',
  `plan_end_at`     datetime     DEFAULT NULL COMMENT '计划结束时间',
  `replay_video_url` varchar(500) DEFAULT NULL COMMENT '回放视频地址',
  `credit_hours`    decimal(5,2) DEFAULT NULL COMMENT '讲师课时（小时）',
  `sort`            int          NOT NULL DEFAULT 0 COMMENT '排序',
  `remark`          varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`         varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`       bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tlr_status` (`status`),
  KEY `idx_tlr_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播房间表';

-- ----------------------------------------------------------------------
-- 直播弹幕表 training_live_danmaku
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_live_danmaku` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '弹幕ID',
  `room_id`     bigint       NOT NULL COMMENT '直播房间ID',
  `user_id`     bigint       NOT NULL COMMENT '发送人用户ID',
  `user_name`   varchar(64)  DEFAULT '' COMMENT '发送人昵称快照',
  `content`     varchar(500) NOT NULL COMMENT '弹幕内容',
  `create_at`   datetime     NOT NULL COMMENT '发送时间',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tld_room_id` (`room_id`, `id`),
  KEY `idx_tld_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播弹幕表';

-- ----------------------------------------------------------------------
-- 学习全局配置表 training_global_config（防挂机/弹幕/打卡参数）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_global_config` (
  `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key`   varchar(64)   NOT NULL COMMENT '配置键（唯一）',
  `config_value` varchar(2000) NOT NULL COMMENT '配置值',
  `remark`       varchar(500)  DEFAULT NULL COMMENT '备注',
  `creator`      varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`      varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`    bigint        NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tgc_key_tenant` (`config_key`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习全局配置表';

-- ======================================================================
-- 种子数据
-- ======================================================================

-- 4.x 学习全局配置（默认值，可在管理端修改）
INSERT INTO `training_global_config` (`config_key`, `config_value`, `remark`, `tenant_id`) VALUES
('danmaku_enabled',          'true',  '直播弹幕开关', 1),
('verify_interval_min',      '1',     '人机验证下发间隔（分钟）', 1),
('heartbeat_interval_seconds', '5',  '前端心跳间隔（秒）', 1),
('max_delta_seconds',        '15',    '单次心跳合法 delta 上限（秒），超过判异常', 1),
('session_timeout_seconds',  '30',    '心跳超时秒数，超过自动结束会话(timeout)', 1),
('doc_min_stay_seconds',     '5',     '文档单页最短停留秒数（计入已覆盖）', 1),
('video_complete_ratio',     '0.9',   '视频完成判定：有效观看/总时长比例', 1),
('doc_complete_ratio',       '0.8',   '文档完成判定：覆盖页数/总页数比例', 1),
('min_valid_seconds_checkin','15',    '单会话可打卡的最低有效秒数', 1)
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

-- 直播房间演示数据（1 直播中 / 0 未开始 / 2 已结束含回放）
INSERT INTO `training_live_room`
(`title`, `cover_url`, `lecturer_name`, `stream_url`, `status`, `plan_start_at`, `plan_end_at`, `replay_video_url`, `credit_hours`, `sort`, `remark`, `tenant_id`) VALUES
('现场安全管理规范解读（直播）', 'https://file.example.com/live/cover-live.png', '王讲师', 'http://media.local/video/live-demo.mp4', 1,
 DATE_SUB(NOW(), INTERVAL 20 MINUTE), DATE_ADD(NOW(), INTERVAL 40 MINUTE), NULL, 1.00, 1, '模块四联调直播房间', 1),
('高处作业要点（未开始）', 'https://file.example.com/live/cover-upcoming.png', '李讲师', NULL, 0,
 DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), NULL, 1.50, 2, '预告房间', 1),
('管道防腐保温直播回放', 'https://file.example.com/live/cover-replay.png', '张讲师', NULL, 2,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 1 HOUR,
 'http://media.local/video/replay-demo.mp4', 2.00, 3, '已结束可回放', 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);
