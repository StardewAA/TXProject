-- ============================================================
--  注册立即可用的工具（运行中的 ai-server 已内置这些 Bean）
--  租户：2010
-- ============================================================

DELETE FROM ai_tool
WHERE tenant_id = 2010 AND name IN ('user_profile_query', 'weather_query');

INSERT INTO ai_tool
    (name, description, status, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
    ('user_profile_query', '查询当前登录用户的信息：昵称、手机号、部门、岗位等',
     0, '1', NOW(), '1', NOW(), 0, 2010),
    ('weather_query', '查询指定城市的实时天气信息',
     0, '1', NOW(), '1', NOW(), 0, 2010);

-- 把新工具动态追加到「MES 生产助手」角色（保留 MES 数据工具 22-25）
UPDATE ai_chat_role
SET tool_ids = CONCAT(tool_ids, ',',
        (SELECT GROUP_CONCAT(id) FROM (
             SELECT id FROM ai_tool
             WHERE tenant_id = 2010
               AND name IN ('user_profile_query', 'weather_query')
         ) t)),
    update_time = NOW()
WHERE tenant_id = 2010 AND name = 'MES 生产助手';

SELECT id, name, description FROM ai_tool WHERE tenant_id = 2010;
SELECT id, name, tool_ids FROM ai_chat_role WHERE tenant_id = 2010;
