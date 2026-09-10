-- ============================================================
--  修复并校验：「MES 生产助手」角色的 AI 工具绑定（租户 2010）
--  用途：当 AI 助手提示"无法查询/没有可用工具"时，在数据库执行一次即可。
--  可重复执行。
--
--  背景：历史上工具注册脚本用 DELETE + INSERT 重建 ai_tool，导致工具 id
--        反复漂移；而 ai_chat_role.tool_ids 曾硬编码为 '22,23,24,25'，
--        一旦与实际 id 不一致，角色就绑不到任何工具，模型自然"看不到"数据工具。
--
--  效果：
--   1) 幂等注册 5 个核心工具（MES 工单/任务/报工/IPQC + 用户档案），保留现有 id；
--   2) 将「MES 生产助手」的 tool_ids 重置为这 5 个工具的当前 id；
--   3) 输出校验结果。
--
--  注意：会覆盖该角色在控制台已勾选的其他工具；如需额外工具，执行后再到
--        控制台【AI 大模型 → 角色管理】中补充勾选即可。
-- ============================================================

-- ---------- 1. 幂等注册工具（不存在才插入，id 保持稳定） ----------
INSERT INTO ai_tool
    (name, description, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'mes_work_order_query', '查询 MES 生产工单：支持按工单编号、工单编码、工单名称、工单状态查询，可分页，返回工单编码、产品名称、数量、状态等',
    0, '1', NOW(), '1', NOW(), 0, 2010
WHERE NOT EXISTS (SELECT 1 FROM ai_tool WHERE tenant_id = 2010 AND name = 'mes_work_order_query');

INSERT INTO ai_tool
    (name, description, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'mes_task_query', '查询 MES 生产任务：支持按任务编号、任务编码、任务名称、所属工单、任务状态、创建时间范围查询，可分页，返回任务编码、产品、工作站、工序、数量等',
    0, '1', NOW(), '1', NOW(), 0, 2010
WHERE NOT EXISTS (SELECT 1 FROM ai_tool WHERE tenant_id = 2010 AND name = 'mes_task_query');

INSERT INTO ai_tool
    (name, description, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'mes_feedback_query', '查询 MES 生产报工记录：支持按报工单编号、所属工单、报工状态、报工时间范围查询，可分页，返回报工数量、合格品数量、不良品数量、报工人、审核状态等',
    0, '1', NOW(), '1', NOW(), 0, 2010
WHERE NOT EXISTS (SELECT 1 FROM ai_tool WHERE tenant_id = 2010 AND name = 'mes_feedback_query');

INSERT INTO ai_tool
    (name, description, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'mes_qc_ipqc_query', '查询 MES 过程检验单（IPQC）：支持按检验单编号、所属工单、检测结果、状态查询，可分页，返回检验数量、合格/不合格数量、缺陷统计、检测人等',
    0, '1', NOW(), '1', NOW(), 0, 2010
WHERE NOT EXISTS (SELECT 1 FROM ai_tool WHERE tenant_id = 2010 AND name = 'mes_qc_ipqc_query');

INSERT INTO ai_tool
    (name, description, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'user_profile_query', '查询用户档案信息：昵称、部门、岗位、手机号、邮箱等；id 为空表示查询当前登录用户',
    0, '1', NOW(), '1', NOW(), 0, 2010
WHERE NOT EXISTS (SELECT 1 FROM ai_tool WHERE tenant_id = 2010 AND name = 'user_profile_query');

-- ---------- 2. 将「MES 生产助手」的 tool_ids 重置为上述 5 个工具 ----------
SET @bindToolIds = (
    SELECT GROUP_CONCAT(id ORDER BY id)
    FROM ai_tool
    WHERE tenant_id = 2010 AND deleted = 0
      AND name IN ('mes_work_order_query', 'mes_task_query', 'mes_feedback_query',
                   'mes_qc_ipqc_query', 'user_profile_query')
);

UPDATE ai_chat_role
SET tool_ids = @bindToolIds, update_time = NOW()
WHERE tenant_id = 2010 AND name = 'MES 生产助手';

-- ---------- 3. 校验输出 ----------
SELECT '角色绑定结果（若为空说明角色未创建，请先执行 create-mes-role.sql）' AS step;
SELECT id, name, tool_ids, update_time
FROM ai_chat_role
WHERE tenant_id = 2010 AND name = 'MES 生产助手';

SELECT '工具清单（id 应与角色 tool_ids 一一对应）' AS step;
SELECT id, name, status
FROM ai_tool
WHERE tenant_id = 2010 AND deleted = 0
  AND name IN ('mes_work_order_query', 'mes_task_query', 'mes_feedback_query',
               'mes_qc_ipqc_query', 'user_profile_query')
ORDER BY id;
