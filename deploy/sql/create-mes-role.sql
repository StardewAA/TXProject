-- ============================================================
--  创建「MES 生产助手」聊天角色（租户 2010）
--  绑定：4 个 MES 查询工具 + 豆包对话模型(id=65)
-- ============================================================

-- 查看参考：已有角色的头像格式
SELECT id, name, avatar, public_status, status FROM ai_chat_role WHERE deleted = 0 LIMIT 3;

DELETE FROM ai_chat_role
WHERE tenant_id = 2010 AND name = 'MES 生产助手';

INSERT INTO ai_chat_role
    (user_id, model_id, name, avatar, category, sort, description, system_message,
     knowledge_ids, tool_ids, mcp_client_names, public_status, status,
     creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
    (NULL, 65, 'MES 生产助手',
     'https://static.iocoder.cn/images/ai/role/ai-chat-role-1.png',
     'MES', 1,
     '面向 MES 生产场景的智能助手，可查询生产工单、生产任务、报工记录、过程检验单等数据，用中文结构化作答',
     CONCAT('你是【MES 生产助手】，服务于天信管业防腐保温智慧平台的 MES 生产场景。请严格遵守：',
            '1) 当用户询问工单、任务、报工、检验等生产数据时，必须调用对应工具查询真实数据后再回答，严禁凭空编造；',
            '2) 回答用中文，条理清晰，数量类问题尽量用列表或表格呈现，并给出工单编码、产品名称、状态等关键字段；',
            '3) 工具未查询到数据时，明确告知未查到，并建议用户确认筛选条件；',
            '4) 你只做数据查询与统计，绝不执行任何新增、修改、删除等写操作；',
            '5) 数值保留合理精度，日期统一按 YYYY-MM-DD 展示；',
            '6) 回答简洁，控制在 200 字以内，除非用户要求详细展开。'),
     NULL, '22,23,24,25', NULL,
     b'1', 0,
     '1', NOW(), '1', NOW(), 0, 2010);

SELECT id, name, model_id, tool_ids, public_status, status, tenant_id
FROM ai_chat_role WHERE tenant_id = 2010;
