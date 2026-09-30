-- =====================================================================
-- 云培训学院 - 模块九：数据统计与档案归档 - 表结构（框架骨架）
-- 说明：
--   1. 本脚本仅建模块九自有表骨架（字段留全），业务逻辑后续迭代实现；
--   2. 全部表含 tenant_id，配合框架租户插件自动隔离；
--   3. 脚本可重复执行（CREATE TABLE IF NOT EXISTS）。
-- =====================================================================

-- 1. 学习行为日志表（91 学时管控与学习监管）
CREATE TABLE IF NOT EXISTS `training_study_behavior_log` (
  `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id`          bigint       NOT NULL COMMENT '学员用户ID',
  `user_name`        varchar(64)  DEFAULT NULL COMMENT '学员姓名',
  `study_type`       varchar(20)  NOT NULL COMMENT '学习类型：video/document/live/vr',
  `resource_id`      bigint       DEFAULT NULL COMMENT '资源ID',
  `resource_title`   varchar(255) DEFAULT NULL COMMENT '资源名称',
  `course_id`        bigint       DEFAULT NULL COMMENT '关联课程ID',
  `study_seconds`    bigint       DEFAULT 0 COMMENT '学习时长（秒）',
  `valid_seconds`    bigint       DEFAULT 0 COMMENT '有效学时秒数（防刷课后）',
  `valid_flag`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否有效学时（0-异常 1-正常）',
  `abnormal_reason`  varchar(255) DEFAULT NULL COMMENT '刷课异常原因',
  `progress_percent` int          DEFAULT 0 COMMENT '学习进度百分比',
  `study_date`       date         NOT NULL COMMENT '学习日期',
  `creator`          varchar(64)  DEFAULT '' COMMENT '创建者',
  `updater`          varchar(64)  DEFAULT '' COMMENT '更新者',
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`        bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_sbl_user` (`user_id`, `study_date`),
  KEY `idx_sbl_tenant` (`tenant_id`, `study_date`),
  KEY `idx_sbl_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训学习行为日志表';

-- 2. 证书模板表（95 证书模板配置 / 92 个人档案证书展示）
CREATE TABLE IF NOT EXISTS `training_cert_template` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `name`         varchar(128) NOT NULL COMMENT '模板名称',
  `code`         varchar(64)  NOT NULL COMMENT '模板编码',
  `type`         tinyint      NOT NULL DEFAULT 1 COMMENT '证书类型：1-普通电子证书 2-联合认证证书',
  `template_url` varchar(500) DEFAULT NULL COMMENT '模板背景图地址',
  `content_json` text         DEFAULT NULL COMMENT '模板字段配置（JSON）',
  `valid_days`   int          DEFAULT NULL COMMENT '证书有效天数',
  `review_days`  int          DEFAULT NULL COMMENT '到期前复审提醒天数',
  `status`       tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用 1-停用',
  `remark`       varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `updater`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`    bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tct_code` (`code`),
  KEY `idx_tct_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训证书模板表';

-- 3. 外部 API 配置表（95 外部API接口管理 / 96 对接鉴权限流）
CREATE TABLE IF NOT EXISTS `training_external_api_config` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `app_name`       varchar(128) NOT NULL COMMENT '接入方名称',
  `app_key`        varchar(64)  NOT NULL COMMENT '接口鉴权 AppKey',
  `app_secret`     varchar(128) NOT NULL COMMENT '接口鉴权 AppSecret',
  `rate_limit_qps` int          NOT NULL DEFAULT 10 COMMENT '限流 QPS',
  `degrade_switch` bit(1)       NOT NULL DEFAULT b'0' COMMENT '降级开关（0-正常 1-已降级）',
  `blacklist`      varchar(500) DEFAULT NULL COMMENT 'IP 黑名单（逗号分隔）',
  `status`         tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用 1-停用',
  `remark`         varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`        varchar(64)  DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)  DEFAULT '' COMMENT '更新者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teac_app_key` (`app_key`),
  KEY `idx_teac_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部API接口配置表';

-- 4. 证书验真记录表（97 证书公开验真：免登录访问留痕）
CREATE TABLE IF NOT EXISTS `training_cert_verify_record` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `cert_code`     varchar(64)  NOT NULL COMMENT '被验真的证书编号',
  `verify_result` tinyint      NOT NULL DEFAULT 0 COMMENT '验真结果：0-未查询到 1-有效 2-已过期 3-已吊销',
  `revoke_reason` varchar(255) DEFAULT NULL COMMENT '吊销原因',
  `verify_ip`     varchar(64)  DEFAULT NULL COMMENT '验真来源 IP',
  `user_agent`    varchar(500) DEFAULT NULL COMMENT '验真来源 UA',
  `creator`       varchar(64)  DEFAULT '' COMMENT '创建者',
  `updater`       varchar(64)  DEFAULT '' COMMENT '更新者',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tcvr_cert_code` (`cert_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='证书验真记录表';

-- 5. 档案人工变更日志表（95 档案人工变更：留痕审计、不可删除）
CREATE TABLE IF NOT EXISTS `training_archive_manual_log` (
  `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `target_user_id`   bigint       NOT NULL COMMENT '被变更学员用户ID',
  `target_user_name` varchar(64)  DEFAULT NULL COMMENT '被变更学员姓名',
  `field_name`       varchar(64)  NOT NULL COMMENT '变更字段名',
  `before_value`     varchar(500) DEFAULT NULL COMMENT '变更前值',
  `after_value`      varchar(500) DEFAULT NULL COMMENT '变更后值',
  `remark`           varchar(500) DEFAULT NULL COMMENT '变更备注',
  `operator_id`      bigint       NOT NULL COMMENT '操作人ID',
  `operator_name`    varchar(64)  DEFAULT NULL COMMENT '操作人姓名',
  `creator`          varchar(64)  DEFAULT '' COMMENT '创建者',
  `updater`          varchar(64)  DEFAULT '' COMMENT '更新者',
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除（业务上禁止删除，仅框架逻辑删除）',
  `tenant_id`        bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_taml_target_user` (`target_user_id`),
  KEY `idx_taml_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='档案人工变更日志表';
