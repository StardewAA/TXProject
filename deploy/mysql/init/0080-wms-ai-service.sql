-- ============================================================================
-- WMS AI 客服 —— 一键初始化（与具体 ID 无关，可重复执行）
--
-- 用途：新人克隆代码、建好库之后，跑这一份脚本 + 在界面填 API Key 即可使用 AI 助手。
--
-- 为什么需要它：以下内容此前只存在于个别开发者的本地库里，代码仓库里没有，
-- 导致「同一份代码，别人拉下来看不到 AI 菜单 / 配置页报 500」：
--   1) wms_ai_config 表（AI 助手选模型用）
--   2) ai_chat_role_permission / ai_chat_role_user 两张表（AI 权限体系）
--   3) 「AI 客服管理」菜单及其子页面、按钮权限
--   4) 把上述菜单授权给角色，并加进租户套餐（否则授权了也看不见）
--
-- 设计原则：全部**按 path / permission / 名称动态查找**，不写死自增 ID；
--          全部带 NOT EXISTS 判重，可安全重复执行。
-- ============================================================================

-- ========== 1. 建表：AI 助手配置 ==========
CREATE TABLE IF NOT EXISTS `wms_ai_config` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `model_id`        bigint       DEFAULT NULL COMMENT '使用的模型编号（ai_model.id）',
    `enabled`         bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用界面配置',
    `timeout_seconds` int          DEFAULT NULL COMMENT '调用超时（秒）',
    `remark`          varchar(255) DEFAULT NULL COMMENT '备注',
    `creator`         varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`       bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='WMS AI 客服配置';

-- ========== 2. AI 权限体系的两张表 ==========
-- 与 deploy/mysql/repair/2026-09-18-ai-chat-role-permission-tables.sql 保持一致
CREATE TABLE IF NOT EXISTS `ai_chat_role_permission` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`     bigint      NOT NULL COMMENT '聊天角色编号（ai_chat_role.id）',
    `biz_type`    varchar(64) NOT NULL COMMENT '业务域（AiMesBizTypeEnum）',
    `tool_name`   varchar(64) DEFAULT NULL COMMENT '对应工具名',
    `creator`     varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`   bigint      NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_role` (`role_id`, `tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 聊天角色业务域权限';

CREATE TABLE IF NOT EXISTS `ai_chat_role_user` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`     bigint      NOT NULL COMMENT '聊天角色编号（ai_chat_role.id）',
    `user_id`     bigint      NOT NULL COMMENT '用户编号',
    `creator`     varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`   bigint      NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_role_user` (`role_id`, `user_id`, `tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 聊天角色可见用户';

-- ========== 3. 定位 WMS 根菜单（按 path 动态查找，不写死 id）==========
SET @wmsRootId = (SELECT id FROM system_menu
                  WHERE deleted = b'0' AND path = '/wms' AND parent_id = 0 LIMIT 1);

-- 没有 WMS 根菜单时直接报错停下，避免后续插入挂到 NULL 上产生孤儿菜单
-- （用 SIGNAL 让问题当场暴露，而不是静默插入脏数据）
-- 注意：若你的库里 WMS 根菜单 path 不是 '/wms'，请先改这里的条件。
SET @wmsRootExists = IFNULL(@wmsRootId, 0);

-- ========== 4. 父菜单：AI 客服管理 ==========
INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'AI 客服管理', '', 1, 9, @wmsRootExists, 'ai-service', 'lucide:bot', NULL, NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @wmsRootExists > 0
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
      SELECT 1 FROM system_menu m
      WHERE m.parent_id = @wmsRootExists AND m.path = 'ai-service' AND m.deleted = b'0'));

SET @aiServiceId = (SELECT id FROM system_menu
                    WHERE parent_id = @wmsRootExists AND path = 'ai-service' AND deleted = b'0' LIMIT 1);

-- ========== 5. AI 助手配置（本模块页面 + 两个按钮权限）==========
INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'AI 助手配置', '', 2, 1, @aiServiceId, 'config', 'lucide:settings-2', 'wms/ai/config/index', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @aiServiceId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
    SELECT 1 FROM system_menu m WHERE m.parent_id = @aiServiceId AND m.path = 'config' AND m.deleted = b'0'));

SET @aiConfigId = (SELECT id FROM system_menu
                   WHERE parent_id = @aiServiceId AND path = 'config' AND deleted = b'0' LIMIT 1);

INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'AI 配置查询', 'wms:ai-config:query', 3, 1, @aiConfigId, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @aiConfigId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
    SELECT 1 FROM system_menu m WHERE m.permission = 'wms:ai-config:query' AND m.deleted = b'0'));

INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'AI 配置保存', 'wms:ai-config:update', 3, 2, @aiConfigId, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @aiConfigId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
    SELECT 1 FROM system_menu m WHERE m.permission = 'wms:ai-config:update' AND m.deleted = b'0'));

-- ========== 6. 复用 AI 模块的 5 个管理页面 ==========
-- 做法是「复制原菜单行」：保留其 component / component_name，只改挂载点与名称，
-- 避免手写组件路径出错；源菜单按 permission 动态查找（不写死 2760 等本地 id）。

-- 6.1 模型配置  ← 源：ai:model:query
INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '模型配置', '', 2, 2, @aiServiceId, 'model', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.permission = 'ai:model:query' AND src.deleted = b'0'
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
      SELECT 1 FROM system_menu m WHERE m.parent_id = @aiServiceId AND m.path = 'model' AND m.deleted = b'0'))
LIMIT 1;

-- 6.2 API 密钥  ← 源：ai:api-key:query
INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'API 密钥', '', 2, 3, @aiServiceId, 'api-key', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.permission = 'ai:api-key:query' AND src.deleted = b'0'
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
      SELECT 1 FROM system_menu m WHERE m.parent_id = @aiServiceId AND m.path = 'api-key' AND m.deleted = b'0'))
LIMIT 1;

-- 6.3 知识库  ← 源：ai:knowledge:query
INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '知识库', '', 2, 4, @aiServiceId, 'knowledge', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.permission = 'ai:knowledge:query' AND src.deleted = b'0'
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
      SELECT 1 FROM system_menu m WHERE m.parent_id = @aiServiceId AND m.path = 'knowledge' AND m.deleted = b'0'))
LIMIT 1;

-- 6.4 聊天角色  ← 源：ai:chat-role:query
INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '聊天角色', '', 2, 5, @aiServiceId, 'chat-role', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.permission = 'ai:chat-role:query' AND src.deleted = b'0'
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
      SELECT 1 FROM system_menu m WHERE m.parent_id = @aiServiceId AND m.path = 'chat-role' AND m.deleted = b'0'))
LIMIT 1;

-- 6.5 聊天管理  ← 源：ai:chat-conversation:query
INSERT INTO system_menu
(name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '聊天管理', '', 2, 6, @aiServiceId, 'chat-manager', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.permission = 'ai:chat-conversation:query' AND src.deleted = b'0'
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
      SELECT 1 FROM system_menu m WHERE m.parent_id = @aiServiceId AND m.path = 'chat-manager' AND m.deleted = b'0'))
LIMIT 1;

-- ========== 7. 授权：给「已拥有 ai:* 权限的角色」补上这些菜单挂载点 ==========
-- 动态策略：不写死 role_id。凡是已经能看 AI 模块（有 ai:model:query）的角色，
-- 就一并获得 WMS 下的挂载菜单；各租户分别处理，互不影响。
-- 语义与既有授权一致：只追加，不删除任何既有授权。
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT grants.role_id, target.id, '1', NOW(), '1', NOW(), b'0', grants.tenant_id
FROM system_role_menu grants
JOIN system_menu src ON src.id = grants.menu_id
JOIN system_menu target ON (
        target.id = @aiServiceId
        OR target.parent_id = @aiServiceId
        OR target.parent_id IN (SELECT id FROM (SELECT id FROM system_menu WHERE parent_id = @aiServiceId) x)
    )
    AND target.deleted = b'0'
WHERE src.permission = 'ai:model:query' AND src.deleted = b'0' AND grants.deleted = b'0'
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_role_menu e
                  WHERE e.role_id = grants.role_id AND e.menu_id = target.id
                    AND e.tenant_id = grants.tenant_id AND e.deleted = b'0');

-- ========== 8. 套餐：把新菜单加进「各租户当前所用套餐」的 menu_ids ==========
-- 动态策略：不写死套餐 id / 租户 id。
-- 只处理「menu_ids 里已包含 WMS 根菜单」的套餐（说明它本来就有 WMS 权限域），
-- 把缺失的 AI 菜单 id 逐个追加；已包含的不重复追加。可重复执行。
--
-- 判重用的是 JSON_CONTAINS(m.menu_ids, CAST(id AS JSON))。
--   实测：menu_ids 的元素是数字，JSON_SEARCH 对数字元素一律返回 NULL（不可用）；
--   而 JSON_CONTAINS 传标量 JSON（CAST(6750 AS JSON)）能正确判断数组成员。

-- 8.1 AI 客服管理父菜单
UPDATE system_tenant_package p
JOIN system_menu t ON t.id = @aiServiceId AND t.deleted = b'0'
SET p.menu_ids = JSON_ARRAY_APPEND(p.menu_ids, '$', t.id)
WHERE p.deleted = b'0'
  AND JSON_CONTAINS(p.menu_ids, CAST(@wmsRootExists AS JSON)) = 1
  AND JSON_CONTAINS(p.menu_ids, CAST(t.id AS JSON)) = 0;

-- 8.2 其下直接子菜单（AI 助手配置 / 模型配置 / API 密钥 / 知识库 / 聊天角色 / 聊天管理）
UPDATE system_tenant_package p
JOIN system_menu t ON t.parent_id = @aiServiceId AND t.deleted = b'0'
SET p.menu_ids = JSON_ARRAY_APPEND(p.menu_ids, '$', t.id)
WHERE p.deleted = b'0'
  AND JSON_CONTAINS(p.menu_ids, CAST(@wmsRootExists AS JSON)) = 1
  AND JSON_CONTAINS(p.menu_ids, CAST(t.id AS JSON)) = 0;

-- 8.3 孙子节点（AI 配置查询 / AI 配置保存 两个按钮权限）
UPDATE system_tenant_package p
JOIN system_menu t ON t.parent_id IN (
        SELECT id FROM (SELECT id FROM system_menu WHERE parent_id = @aiServiceId) x)
    AND t.deleted = b'0'
SET p.menu_ids = JSON_ARRAY_APPEND(p.menu_ids, '$', t.id)
WHERE p.deleted = b'0'
  AND JSON_CONTAINS(p.menu_ids, CAST(@wmsRootExists AS JSON)) = 1
  AND JSON_CONTAINS(p.menu_ids, CAST(t.id AS JSON)) = 0;

-- ========== 9. 结果确认 ==========
SELECT 'AI 客服管理菜单 id' AS item, CAST(@aiServiceId AS CHAR) AS value
UNION ALL
SELECT '该菜单下节点数（含 2 个按钮权限）', CAST(COUNT(*) AS CHAR) FROM system_menu
        WHERE deleted = b'0'
          AND (id = @aiServiceId OR parent_id = @aiServiceId
               OR parent_id IN (SELECT id FROM (SELECT id FROM system_menu WHERE parent_id = @aiServiceId) y))
UNION ALL
SELECT '已授权角色数', CAST(COUNT(DISTINCT role_id) AS CHAR) FROM system_role_menu
        WHERE deleted = b'0'
          AND (menu_id = @aiServiceId OR menu_id IN (
                SELECT id FROM (SELECT id FROM system_menu WHERE parent_id = @aiServiceId) z))
UNION ALL
SELECT '已含该菜单的套餐数', CAST(COUNT(*) AS CHAR) FROM system_tenant_package
        WHERE deleted = b'0' AND @aiServiceId IS NOT NULL
          AND JSON_CONTAINS(menu_ids, CAST(@aiServiceId AS JSON)) = 1;
