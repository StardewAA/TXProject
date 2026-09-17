-- ============================================================
-- MES AI 助手 - 权限控制相关表结构
-- 说明：在现有 AI 模块（ai_chat_role / ai_model / ai_api_key / ai_tool）
--       基础上，新增「角色权限配置」与「用户-角色绑定」两张表，
--       用于实现 AI 对话时的 MES 业务数据权限控制（后端强校验）。
-- 执行方式：mysql -uroot -proot ruoyi-vue-pro < ai_mes_permission.sql
-- ============================================================

-- 1. AI 聊天角色权限配置表
--    一个角色可配置多条权限，每条对应一个 MES 业务域
CREATE TABLE IF NOT EXISTS `ai_chat_role_permission` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `role_id`      bigint       NOT NULL COMMENT 'AI 聊天角色编号，关联 ai_chat_role.id',
    `biz_type`     varchar(64)  NOT NULL COMMENT 'MES 业务域枚举，见 AiMesBizTypeEnum，如 PRO_WORK_ORDER',
    `biz_name`     varchar(128) DEFAULT NULL COMMENT '业务域名称（冗余展示用，如：生产工单）',
    `can_query`    bit(1)       DEFAULT b'1' COMMENT '是否允许查询该业务域数据',
    `can_sensitive` bit(1)      DEFAULT b'0' COMMENT '是否允许访问敏感生产数据（成本/售价/客户/工艺配方参数等）',
    `bill_types`   varchar(1024) DEFAULT NULL COMMENT '可查询的单据类型列表，JSON 数组字符串，为空表示不限',
    `data_scope`   varchar(32)  DEFAULT 'ALL' COMMENT '数据范围：ALL=全部 / DEPT=本部门 / SELF=仅本人相关',
    `remark`       varchar(512) DEFAULT NULL COMMENT '备注',
    `creator`      varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`    bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_biz` (`role_id`, `biz_type`, `deleted`, `tenant_id`),
    KEY `idx_tenant_role` (`tenant_id`, `role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI 聊天角色 - MES 业务权限配置表';

-- 2. AI 聊天角色与用户关联表
--    决定「哪个用户可以使用哪个 AI 角色、从而继承该角色的数据权限」
CREATE TABLE IF NOT EXISTS `ai_chat_role_user` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '编号',
    `role_id`     bigint      NOT NULL COMMENT 'AI 聊天角色编号，关联 ai_chat_role.id',
    `user_id`     bigint      NOT NULL COMMENT '用户编号，关联 system_users.id',
    `creator`     varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`   bigint      NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_user` (`role_id`, `user_id`, `deleted`, `tenant_id`),
    KEY `idx_tenant_user` (`tenant_id`, `user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI 聊天角色 - 用户关联表';

-- 3. 初始化：为「MES」分类下的角色预置常用业务域权限
--    说明：仅当角色存在且尚未配置过权限时插入，重复执行不会产生脏数据
INSERT INTO `ai_chat_role_permission`
    (`role_id`, `biz_type`, `biz_name`, `can_query`, `can_sensitive`, `data_scope`, `creator`, `updater`, `tenant_id`)
SELECT r.`id`, t.biz_type, t.biz_name, b'1', b'0', 'ALL', 'system', 'system', r.`tenant_id`
FROM `ai_chat_role` r
         JOIN (
    SELECT 'PRO_WORK_ORDER' AS biz_type, '生产工单' AS biz_name
    UNION ALL SELECT 'PRO_TASK', '生产任务'
    UNION ALL SELECT 'PRO_FEEDBACK', '生产报工'
    UNION ALL SELECT 'PRO_PROCESS', '工艺工序'
    UNION ALL SELECT 'MD_ITEM', '物料产品'
    UNION ALL SELECT 'WM_STOCK', '仓储库存'
    UNION ALL SELECT 'QC_INSPECT', '质量检验'
    UNION ALL SELECT 'DV_MACHINERY', '设备台账'
) t
WHERE r.`category` = 'MES'
  AND r.`deleted` = b'0'
  AND NOT EXISTS (
        SELECT 1 FROM `ai_chat_role_permission` p
        WHERE p.`role_id` = r.`id` AND p.`biz_type` = t.biz_type AND p.`deleted` = b'0'
    );
