-- WMS 新增「AI 客服管理」菜单
--
-- 目标：AI 助手（客服）留在仓储首页，本菜单用来「管理 AI 助手」——
--   1) AI 助手配置（本模块新建页面）：选助手用哪个模型
--   2) 模型配置 / API 密钥：复用 AI 模块已有页面，用来接其他厂商、维护模型与 Key
--   3) 知识库 / 聊天角色 / 聊天管理：复用 AI 模块已有页面，管理助手挂的知识库、Agent 与历史会话
--
-- 复用的页面与新页面的 component 均已在代码中存在，菜单只做挂载，不重复开发。
-- 幂等：按 permission / path 判重，可重复执行。

-- ========== 1. 父菜单：AI 客服管理 ==========
INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'AI 客服管理', '', 1, 9, 6400, 'ai-service', 'lucide:bot', NULL, NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
        SELECT 1 FROM system_menu m
        WHERE m.parent_id = 6400 AND m.path = 'ai-service' AND m.deleted = b'0'));

SET @aiServiceId = (SELECT id FROM system_menu
                    WHERE parent_id = 6400 AND path = 'ai-service' AND deleted = b'0' LIMIT 1);

-- ========== 2. AI 助手配置（本模块页面）==========
INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'AI 助手配置', '', 2, 1, @aiServiceId, 'config', 'lucide:settings-2', 'wms/ai/config/index', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @aiServiceId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
        SELECT 1 FROM system_menu m
        WHERE m.parent_id = @aiServiceId AND m.path = 'config' AND m.deleted = b'0'));

SET @aiConfigId = (SELECT id FROM system_menu
                   WHERE parent_id = @aiServiceId AND path = 'config' AND deleted = b'0' LIMIT 1);

-- AI 助手配置的两个按钮权限
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

-- ========== 3. 复用 AI 模块的 5 个管理页面 ==========
-- 用「复制菜单行」的方式：保留原 component，只改挂载点与名称，避免手写路径出错。
INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '模型配置', '', 2, 2, @aiServiceId, 'model', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.id = 2767
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
        SELECT 1 FROM system_menu m
        WHERE m.parent_id = @aiServiceId AND m.path = 'model' AND m.deleted = b'0'));

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'API 密钥', '', 2, 3, @aiServiceId, 'api-key', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.id = 2761
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
        SELECT 1 FROM system_menu m
        WHERE m.parent_id = @aiServiceId AND m.path = 'api-key' AND m.deleted = b'0'));

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '知识库', '', 2, 4, @aiServiceId, 'knowledge', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.id = 2915
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
        SELECT 1 FROM system_menu m
        WHERE m.parent_id = @aiServiceId AND m.path = 'knowledge' AND m.deleted = b'0'));

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '聊天角色', '', 2, 5, @aiServiceId, 'chat-role', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.id = 2773
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
        SELECT 1 FROM system_menu m
        WHERE m.parent_id = @aiServiceId AND m.path = 'chat-role' AND m.deleted = b'0'));

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '聊天管理', '', 2, 6, @aiServiceId, 'chat-conversation', src.icon, src.component, src.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM system_menu src
WHERE src.id = 2778
  AND @aiServiceId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
        SELECT 1 FROM system_menu m
        WHERE m.parent_id = @aiServiceId AND m.path = 'chat-conversation' AND m.deleted = b'0'));

-- ========== 4. 结果确认 ==========
SELECT id, name, parent_id, path, component, permission, sort
FROM system_menu
WHERE deleted = b'0' AND (id = @aiServiceId OR parent_id = @aiServiceId)
ORDER BY sort, id;
