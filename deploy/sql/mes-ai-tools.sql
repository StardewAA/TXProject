-- ============================================================
--  MES 数据查询工具注册（Function Calling）
--  租户：2010（华翰）
--  说明：name 必须与 Spring Bean 名完全一致
-- ============================================================

DELETE FROM ai_tool
WHERE tenant_id = 2010
  AND name IN ('mes_work_order_query', 'mes_task_query',
               'mes_feedback_query', 'mes_qc_ipqc_query');

INSERT INTO ai_tool
    (name, description, status, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
    ('mes_work_order_query', '查询 MES 生产工单：支持按工单编号、工单编码、工单名称、工单状态查询，可分页，返回工单编码、产品名称、数量、状态等',
     0, '1', NOW(), '1', NOW(), 0, 2010),
    ('mes_task_query', '查询 MES 生产任务：支持按任务编号、任务编码、任务名称、所属工单、任务状态、创建时间范围查询，可分页，返回任务编码、产品、工作站、工序、数量等',
     0, '1', NOW(), '1', NOW(), 0, 2010),
    ('mes_feedback_query', '查询 MES 生产报工记录：支持按报工单编号、所属工单、报工状态、报工时间范围查询，可分页，返回报工数量、合格品数量、不良品数量、报工人、审核状态等',
     0, '1', NOW(), '1', NOW(), 0, 2010),
    ('mes_qc_ipqc_query', '查询 MES 过程检验单（IPQC）：支持按检验单编号、所属工单、检测结果、状态查询，可分页，返回检验数量、合格/不合格数量、缺陷统计、检测人等',
     0, '1', NOW(), '1', NOW(), 0, 2010);

SELECT id, name, status, tenant_id FROM ai_tool WHERE tenant_id = 2010;
