-- ======================================================================
-- 云培训学院 - 模块三：培训管理（培训班/分班管控）基础框架建表脚本
-- 数据库：ruoyi-vue-pro（与部署 MySQL 一致）
-- 说明：
--   1. 依据《模块三 培训管理（核心-难）详细搭建方案》设计；
--   2. 工程为 Java yudao 微服务，表名前缀 training_；
--   3. 覆盖功能：培训班（四定）、学员批量添加/移除并自动生成学习任务、
--      班级学习进度看板、未完成督促（站内信）、上岗资格核查；
--   4. 站内信模板（system_notify_template）缺省自动补插，脚本幂等可重复执行。
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_class.sql
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 培训班表 training_class（核心主表）
--    培训类型 type：1岗前培训 2复工培训 3工人技能培训 4质检专项 5安全管理
--                  6特种作业持证 7学徒进阶培训班 8复合型人才专项培训班
--    状态 status：0草稿（未开班） 1进行中 2已完成 3已取消
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_class` (
  `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '培训班ID',
  `code`             varchar(64)   DEFAULT NULL COMMENT '培训班编码（开班后自动生成，如 TB{id}）',
  `name`             varchar(200)  NOT NULL COMMENT '培训班名称',
  `type`             tinyint       NOT NULL DEFAULT 1 COMMENT '培训类型：1岗前培训 2复工培训 3工人技能培训 4质检专项 5安全管理 6特种作业持证 7学徒进阶培训班 8复合型人才专项培训班',
  `project_id`       varchar(64)   DEFAULT NULL COMMENT '关联工程项目编号（岗前/复工培训建议必填，供上岗资格核查）',
  `project_name`     varchar(200)  DEFAULT NULL COMMENT '关联工程项目名称',
  `target_condition` varchar(1000) DEFAULT NULL COMMENT '参训对象筛选条件（JSON，如岗位/工种/部门/班组/成长等级）',
  `target_posts`     varchar(500)  DEFAULT NULL COMMENT '定岗（结业后对应岗位，JSON数组，四定-定岗）',
  `start_time`       datetime      DEFAULT NULL COMMENT '开班时间',
  `deadline`         datetime      DEFAULT NULL COMMENT '培训截止时间',
  `course_count`     int           NOT NULL DEFAULT 0 COMMENT '绑定课程数（冗余）',
  `student_count`    int           NOT NULL DEFAULT 0 COMMENT '在班学员数（冗余）',
  `status`           tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1进行中 2已完成 3已取消',
  `remark`           varchar(500)  DEFAULT NULL COMMENT '备注',
  `creator`          varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`          varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`        bigint        NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tc_status_type` (`status`, `type`),
  KEY `idx_tc_project` (`project_id`),
  KEY `idx_tc_deadline` (`deadline`),
  KEY `idx_tc_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训班表';

-- ----------------------------------------------------------------------
-- 2. 培训班课程绑定表 training_class_course（四定-定课，含课程信息快照）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_class_course` (
  `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
  `class_id`         bigint       NOT NULL COMMENT '培训班ID',
  `course_id`        bigint       NOT NULL COMMENT '课程ID（引用 training_course 上架课程）',
  `course_code`      varchar(64)  DEFAULT NULL COMMENT '课程编码快照',
  `course_title`     varchar(200) NOT NULL COMMENT '课程标题快照',
  `duration_minutes` int          DEFAULT NULL COMMENT '课程总时长快照（分钟）',
  `sort`             int          NOT NULL DEFAULT 0 COMMENT '排序',
  `creator`          varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`          varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`        bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tcc_class_course` (`class_id`, `course_id`, `deleted`),
  KEY `idx_tcc_class` (`class_id`),
  KEY `idx_tcc_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训班课程绑定表';

-- ----------------------------------------------------------------------
-- 3. 培训班学员表 training_class_student（四定-定人）
--    status：0在班 1已移除（保留历史，便于结业追溯）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_class_student` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '学员绑定ID',
  `class_id`    bigint      NOT NULL COMMENT '培训班ID',
  `user_id`     bigint      NOT NULL COMMENT '学员用户ID（引用 system_users，登录用户即工人/员工）',
  `user_name`   varchar(64) DEFAULT NULL COMMENT '学员姓名快照',
  `dept_name`   varchar(100) DEFAULT NULL COMMENT '部门/班组快照',
  `source`      tinyint     NOT NULL DEFAULT 1 COMMENT '加入来源：1手动添加 2Excel批量导入 3按参与对象筛选 5导入并指定班级',
  `status`      tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0在班 1已移除',
  `join_time`   datetime    DEFAULT NULL COMMENT '入班时间',
  `removed_time` datetime   DEFAULT NULL COMMENT '移除时间',
  `removed_reason` varchar(255) DEFAULT NULL COMMENT '移除原因',
  `creator`     varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint      NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tcs_class_user` (`class_id`, `user_id`, `deleted`),
  KEY `idx_tcs_class_status` (`class_id`, `status`),
  KEY `idx_tcs_user` (`user_id`),
  KEY `idx_tcs_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训班学员表';

-- ----------------------------------------------------------------------
-- 4. 学员学习任务表 training_student_task
--    学员入班时按“学员 × 绑定课程”自动生成一条学习任务，支撑班级学习进度看板。
--    状态语义：
--      study_status  学时完成状态：0未完成 1已完成
--      course_status 课程完成状态：0未开始 1进行中 2已完成
--      exam_status   考试状态：0未参加 1已通过 2未通过
--      cert_status   证书获取状态：0未获取 1已获取
--      status        任务总状态（看板聚合用）：0未开始 1进行中 2已完成
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_student_task` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `class_id`      bigint       NOT NULL COMMENT '培训班ID',
  `user_id`       bigint       NOT NULL COMMENT '学员用户ID',
  `course_id`     bigint       NOT NULL COMMENT '课程ID',
  `course_title`  varchar(200) NOT NULL COMMENT '课程标题快照',
  `study_status`  tinyint      NOT NULL DEFAULT 0 COMMENT '学时完成状态：0未完成 1已完成',
  `course_status` tinyint      NOT NULL DEFAULT 0 COMMENT '课程完成状态：0未开始 1进行中 2已完成',
  `exam_status`   tinyint      NOT NULL DEFAULT 0 COMMENT '考试状态：0未参加 1已通过 2未通过',
  `cert_status`   tinyint      NOT NULL DEFAULT 0 COMMENT '证书获取状态：0未获取 1已获取',
  `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '任务总状态：0未开始 1进行中 2已完成',
  `finish_time`   datetime     DEFAULT NULL COMMENT '任务完成时间',
  `creator`       varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`       varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`     bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tst_class_user_course` (`class_id`, `user_id`, `course_id`, `deleted`),
  KEY `idx_tst_class_user` (`class_id`, `user_id`),
  KEY `idx_tst_user` (`user_id`),
  KEY `idx_tst_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员学习任务表';

-- ----------------------------------------------------------------------
-- 5. 督促提醒记录表 training_class_remind
--     每次“一键督促”留痕：向学员发送站内信并记录结果
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_class_remind` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `class_id`    bigint       NOT NULL COMMENT '培训班ID',
  `class_name`  varchar(200) DEFAULT NULL COMMENT '培训班名称快照',
  `user_id`     bigint       NOT NULL COMMENT '学员用户ID',
  `user_name`   varchar(64)  DEFAULT NULL COMMENT '学员姓名快照',
  `remind_type` tinyint      NOT NULL DEFAULT 1 COMMENT '督促类型：1学习进度督促（综合）',
  `message_id`  bigint       DEFAULT NULL COMMENT '站内信消息ID（system_notify_message.id，发送成功回填）',
  `content`     varchar(1024) DEFAULT NULL COMMENT '站内信内容快照',
  `status`      tinyint      NOT NULL DEFAULT 0 COMMENT '发送状态：0成功 1失败（模板缺失/发送异常）',
  `error_msg`   varchar(500) DEFAULT NULL COMMENT '失败原因',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tcr_class` (`class_id`),
  KEY `idx_tcr_user` (`user_id`),
  KEY `idx_tcr_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='督促提醒记录表';

-- ----------------------------------------------------------------------
-- 6. 站内信模板兜底（缺省自动补插，幂等）
--    用途：班级“一键督促”发送站内信提醒；模板不存在时不允许发送，
--    故在此兜底创建，保证督促功能可用。
--    变量：{userName} {className} {deadline} {postName}（与 params 一一对应）
-- ----------------------------------------------------------------------
INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '培训任务未完成提醒', 'TRAIN_CLASS_STUDY_REMIND', '云培训学院',
       '您好，{userName}，您参加的培训班【{className}】有学习任务尚未完成（截止时间：{deadline}）。请尽快登录云培训学院完成课程学习，逾期将影响上岗资格核查与结业取证。',
       2, '["userName","className","deadline"]', 0, '模块三：培训班未完成学习任务督促提醒',
       'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'TRAIN_CLASS_STUDY_REMIND' AND `deleted` = b'0');

-- ----------------------------------------------------------------------
-- 7. 增量：学员加入来源新增 5「导入并指定班级」（批量导入学员后手动指定要加入的培训班）
--    幂等：直接 MODIFY 列注释，可重复执行
-- ----------------------------------------------------------------------
ALTER TABLE `training_class_student`
  MODIFY COLUMN `source` tinyint NOT NULL DEFAULT 1 COMMENT '加入来源：1手动添加 2Excel批量导入 3按参与对象筛选 5导入并指定班级';
