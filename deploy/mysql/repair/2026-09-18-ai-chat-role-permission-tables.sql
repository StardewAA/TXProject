-- AI 角色权限体系缺失的两张关联表
-- 背景：AiPermissionGuard / AiChatRolePermissionServiceImpl / AiChatRoleUserMapper 在运行时
-- 依赖这两张表，但仓库 deploy/mysql 下的建表脚本从未包含它们，
-- 导致 AI 权限功能一调用就报 Table 'ai_chat_role_permission' doesn't exist。
-- 表结构依据 DO 定义：AiChatRolePermissionDO / AiChatRoleUserDO（含 BaseDO 公共字段）。

CREATE TABLE IF NOT EXISTS `ai_chat_role_user` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `role_id`     bigint       NOT NULL COMMENT 'AI 聊天角色编号',
  `user_id`     bigint       NOT NULL COMMENT '用户编号',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天角色与用户的绑定';

CREATE TABLE IF NOT EXISTS `ai_chat_role_permission` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `role_id`        bigint       NOT NULL COMMENT 'AI 聊天角色编号',
  `biz_type`       varchar(64)  NOT NULL COMMENT '业务域编码',
  `biz_name`       varchar(128) DEFAULT '' COMMENT '业务域名称',
  `can_query`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否允许查询',
  `can_sensitive`  bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否允许访问敏感数据',
  `bill_types`     varchar(512) DEFAULT '' COMMENT '允许的单据类型（逗号分隔，可空表示全部）',
  `data_scope`     varchar(32)  NOT NULL DEFAULT 'ALL' COMMENT '数据范围：ALL=全部，SELF=仅本人',
  `remark`         varchar(255) DEFAULT '' COMMENT '备注',
  `creator`        varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_biz_type` (`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天角色的业务域权限配置';
