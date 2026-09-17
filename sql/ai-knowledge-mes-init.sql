-- ============================================================
-- MES 生产数据知识库初始化/重建脚本
-- 内容严格来源于平台数据库（ruoyi-vue-pro 库 mes_* 业务表真实记录）
-- 知识库归属：租户 2010（华瀚节能建材有限公司）
--
-- 配套代码变更（txgy-module-ai）：
--   1. LocalHashingEmbeddingModel：本地哈希向量模型（无需外部 Embedding API）
--   2. AiPlatformEnum 新增 Local 平台
--   3. /admin-api/ai/knowledge/segment/reindex 重建索引接口
--   4. 聊天 Prompt 模板：知识库优先 + 来源标注（知识库 / 联网搜索）
--
-- 使用步骤：
--   1. 执行本脚本（可重复执行，自动清理重建）
--   2. AI 服务重建/重启后，调用一次重建索引接口（SimpleVectorStore 为内存向量，
--      容器重建后需重新向量化）：
--      PUT /admin-api/ai/knowledge/segment/reindex?knowledgeId={knowledge_id}
--      Header: Authorization: Bearer {token}, tenant-id: 2010
--   3. 若业务数据更新，重新执行本脚本 + 重建索引即可刷新知识库
-- ============================================================

-- ---------- 0. 清理旧知识库（幂等） ----------
SET @oldKid = (SELECT id FROM ai_knowledge WHERE name = 'MES 生产数据知识库' AND tenant_id = 2010 LIMIT 1);
DELETE s FROM ai_knowledge_segment s JOIN ai_knowledge_document d ON s.document_id = d.id
WHERE d.knowledge_id = @oldKid;
DELETE FROM ai_knowledge_document WHERE knowledge_id = @oldKid;
DELETE FROM ai_knowledge WHERE id = @oldKid;

-- ---------- 1. 本地向量模型（无需外部 Embedding API） ----------
SET @embKeyId = (SELECT id FROM ai_api_key WHERE name = '本地向量模型（内置）' AND tenant_id = 2010 LIMIT 1);
INSERT INTO ai_api_key (name, api_key, platform, url, status, creator, tenant_id)
SELECT '本地向量模型（内置）', 'local-embedding', 'Local', NULL, 0, '80370', 2010
FROM DUAL WHERE @embKeyId IS NULL;
SET @embKeyId = (SELECT id FROM ai_api_key WHERE name = '本地向量模型（内置）' AND tenant_id = 2010 LIMIT 1);

SET @embId = (SELECT id FROM ai_model WHERE model = 'local-hashing-embedding' AND tenant_id = 2010 LIMIT 1);
INSERT INTO ai_model (name, model, platform, type, key_id, sort, status, temperature, max_tokens, max_contexts, creator, updater, tenant_id)
SELECT '本地哈希向量模型', 'local-hashing-embedding', 'Local', 5, @embKeyId, 1, 0, 0.0, 4096, 8, '80370', '80370', 2010
FROM DUAL WHERE @embId IS NULL;
SET @embId = (SELECT id FROM ai_model WHERE model = 'local-hashing-embedding' AND tenant_id = 2010 LIMIT 1);

-- ---------- 2. 知识库 ----------
INSERT INTO ai_knowledge (name, description, embedding_model_id, embedding_model, top_k, similarity_threshold,
                          status, creator, updater, deleted, tenant_id)
VALUES ('MES 生产数据知识库',
        '天信管业 MES 平台业务数据知识库，内容严格来源于平台数据库真实记录：生产工单、生产任务、生产报工、物料产品、车间工位、客户供应商、质量检验（IQC/IPQC/OQC）、设备台账与点检维修、排班班组、仓库与库存事务。',
        @embId, 'local-hashing-embedding', 5, 0.25, 0, '80370', '80370', 0, 2010);
SET @kid = LAST_INSERT_ID();

-- ---------- 3. 文档 + 分段 ----------

-- 3.1 知识库说明
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '知识库说明', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, @doc,
'本知识库为天信管业 MES 平台生产数据知识库，全部内容直接来源于平台数据库（MySQL ruoyi-vue-pro 库）的真实业务记录，覆盖 11 个业务域：生产工单、生产任务、生产报工、工艺路线、物料产品、车间产线、客户供应商、质量检验、设备管理、排班班组、仓储库存。用户询问平台内业务数据时，请严格依据本知识库内容回答，并注明「数据来源：平台知识库」。', 0, '', 0, 0, '80370', '80370', 0, 2010);

-- 3.2 生产工单（mes_pro_work_order）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '生产工单', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('汇总统计：平台数据库当前共有 ', COUNT(*), ' 张生产工单；其中已确认 ',
       SUM(status = 1), ' 张、已完成 ', SUM(status = 2), ' 张、已取消 ', SUM(status = 3), ' 张、草稿 ', SUM(status = 0), ' 张。'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_pro_work_order WHERE tenant_id = 1 AND deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('生产工单【', wo.code, '】', wo.name,
       '：产品【', IFNULL(it.name, '未知'), '】，计划数量 ', wo.quantity + 0,
       '，已生产 ', wo.quantity_produced + 0,
       '，客户【', IFNULL(cl.name, '无'), '】，要求交期 ', DATE(wo.request_date),
       '，状态【', CASE wo.status WHEN 0 THEN '草稿' WHEN 1 THEN '已确认' WHEN 2 THEN '已完成' WHEN 3 THEN '已取消' ELSE '未知' END, '】',
       IF(wo.finish_date IS NULL, '', CONCAT('，完成时间 ', wo.finish_date))),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_pro_work_order wo
LEFT JOIN mes_md_item it ON it.id = wo.product_id
LEFT JOIN mes_md_client cl ON cl.id = wo.client_id
WHERE wo.tenant_id = 1 AND wo.deleted = 0;

-- 3.3 生产任务（mes_pro_task）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '生产任务', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('汇总统计：平台数据库当前共有 ', COUNT(*), ' 个生产任务；其中草稿 ', SUM(status = 0),
       ' 个、已完成 ', SUM(status = 4), ' 个、已取消 ', SUM(status = 5), ' 个。'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_pro_task WHERE tenant_id = 1 AND deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('生产任务【', t.code, '】', t.name,
       '：所属生产工单【', IFNULL(wo.code, '未知'), '】，物料【', IFNULL(it.name, '未知'), '】，工位【', IFNULL(ws.name, '未分配'), '】',
       '，计划数量 ', t.quantity + 0, '，已生产 ', t.produced_quantity + 0,
       '，合格 ', t.qualify_quantity + 0, '，不合格 ', t.unqualify_quantity + 0,
       IFNULL(CONCAT('，开工时间 ', t.start_time), '，开工时间 未开始'),
       IFNULL(CONCAT('，完工时间 ', t.end_time), ''),
       IFNULL(CONCAT('，计划完成日期 ', DATE(t.finish_date)), ''),
       '，状态【', CASE t.status WHEN 0 THEN '草稿' WHEN 4 THEN '已完成' WHEN 5 THEN '已取消' ELSE CONCAT('状态码', t.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_pro_task t
LEFT JOIN mes_pro_work_order wo ON wo.id = t.work_order_id
LEFT JOIN mes_md_item it ON it.id = t.item_id
LEFT JOIN mes_md_workstation ws ON ws.id = t.workstation_id
WHERE t.tenant_id = 1 AND t.deleted = 0;

-- 3.4 生产报工（mes_pro_feedback）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '生产报工', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('生产报工单【', f.code, '】',
       '：类型【', CASE f.type WHEN 1 THEN '自行报工' WHEN 2 THEN '统一报工' ELSE CONCAT('类型码', f.type) END, '】',
       '，渠道 ', IFNULL(f.channel, '-'),
       '，所属工单【', IFNULL(wo.code, '未知'), '】，任务【', IFNULL(t.code, '无'), '】，物料【', IFNULL(it.name, '未知'), '】',
       '，报工数量 ', f.feedback_quantity + 0, '，合格 ', f.qualified_quantity + 0, '，不合格 ', f.unqualified_quantity + 0,
       IFNULL(CONCAT('，报工时间 ', f.feedback_time), ''),
       '，状态【', CASE f.status WHEN 0 THEN '草稿' WHEN 2 THEN '审批中' WHEN 3 THEN '待检验' WHEN 4 THEN '已完成' ELSE CONCAT('状态码', f.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_pro_feedback f
LEFT JOIN mes_pro_work_order wo ON wo.id = f.work_order_id
LEFT JOIN mes_pro_task t ON t.id = f.task_id
LEFT JOIN mes_md_item it ON it.id = f.item_id
WHERE f.tenant_id = 1 AND f.deleted = 0;

-- 3.5 物料产品（mes_md_item + 类型 + 计量单位）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '物料产品', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('汇总统计：平台数据库当前共有 ', COUNT(*), ' 条物料/产品主数据。'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_md_item WHERE tenant_id = 1 AND deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('物料【', i.code, '】', i.name,
       IFNULL(CONCAT('，规格：', i.specification), ''),
       '，类型【', IFNULL(t.name, '未分类'), '】，单位【', IFNULL(u.name, '未知'), '】',
       '，安全库存：最小 ', i.min_stock + 0, ' / 最大 ', i.max_stock + 0,
       '，状态【', CASE i.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE CONCAT('状态码', i.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_md_item i
LEFT JOIN mes_md_item_type t ON t.id = i.item_type_id
LEFT JOIN mes_md_unit_measure u ON u.id = i.unit_measure_id
WHERE i.tenant_id = 1 AND i.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('物料类型【', t.code, '】', t.name,
       '：类别 ', CASE t.item_or_product WHEN 'ITEM' THEN '物料' WHEN 'PRODUCT' THEN '产品' ELSE t.item_or_product END,
       IF(t.parent_id > 0, CONCAT('，上级类型【', IFNULL(pt.name, '') , '】'), '')),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_md_item_type t LEFT JOIN mes_md_item_type pt ON pt.id = t.parent_id
WHERE t.tenant_id = 1 AND t.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('平台计量单位清单：', GROUP_CONCAT(u.name ORDER BY u.id SEPARATOR '、')), 0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_md_unit_measure u WHERE u.tenant_id = 1 AND u.deleted = 0;

-- 3.6 车间与工位（mes_md_workshop / mes_md_workstation）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '车间产线', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('车间【', w.code, '】', w.name,
       IFNULL(CONCAT('，面积 ', w.area + 0, ' 平方米'), ''),
       '，状态【', CASE w.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE CONCAT('状态码', w.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_md_workshop w WHERE w.tenant_id = 1 AND w.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('工位【', s.code, '】', s.name,
       '，所属车间【', IFNULL(w.name, '未分配'), '】',
       IFNULL(CONCAT('，地址：', s.address), ''),
       '，状态【', CASE s.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE CONCAT('状态码', s.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_md_workstation s LEFT JOIN mes_md_workshop w ON w.id = s.workshop_id
WHERE s.tenant_id = 1 AND s.deleted = 0;

-- 3.7 客户与供应商（mes_md_client / mes_md_vendor）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '客户供应商', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('客户【', c.code, '】', c.name,
       '：类型 ', CASE c.type WHEN 1 THEN '企业客户' WHEN 2 THEN '个人' ELSE CONCAT('类型码', c.type) END,
       IFNULL(CONCAT('，联系人 ', c.contact1_name), ''),
       IFNULL(CONCAT('，电话 ', c.contact1_telephone), ''),
       IFNULL(CONCAT('，地址 ', c.address), '')),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_md_client c WHERE c.tenant_id = 1 AND c.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('供应商【', v.code, '】', v.name,
       IFNULL(CONCAT('，等级 ', v.level), ''),
       IFNULL(CONCAT('，联系人 ', v.contact1_name), ''),
       IFNULL(CONCAT('，电话 ', v.contact1_telephone), ''),
       IFNULL(CONCAT('，地址 ', v.address), '')),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_md_vendor v WHERE v.tenant_id = 1 AND v.deleted = 0;

-- 3.8 质量检验（mes_qc_iqc / mes_qc_ipqc / mes_qc_oqc）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '质量检验', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('IQC 来料检验单【', q.code, '】', q.name,
       '：来源单据 ', IFNULL(q.source_doc_code, '-'),
       '，供应商【', IFNULL(v.name, '未知'), '】，物料【', IFNULL(it.name, '未知'), '】',
       '，来料数量 ', q.received_quantity + 0, '，检验数量 ', IFNULL(q.check_quantity + 0, 0),
       '，合格 ', IFNULL(q.qualified_quantity + 0, 0), '，不合格 ', IFNULL(q.unqualified_quantity + 0, 0),
       '，结论【', CASE q.check_result WHEN 1 THEN '校验通过' WHEN 2 THEN '校验不通过' ELSE '未检' END, '】',
       '，状态【', CASE q.status WHEN 0 THEN '草稿' WHEN 4 THEN '已完成' WHEN 5 THEN '已取消' ELSE CONCAT('状态码', q.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_qc_iqc q
LEFT JOIN mes_md_vendor v ON v.id = q.vendor_id
LEFT JOIN mes_md_item it ON it.id = q.item_id
WHERE q.tenant_id = 1 AND q.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('IPQC 过程检验单【', q.code, '】', q.name,
       '：类型 ', CASE q.type WHEN 1 THEN '自制工序检验' WHEN 2 THEN '首检' WHEN 3 THEN '巡检' WHEN 4 THEN '自检' WHEN 5 THEN '成品检验' ELSE CONCAT('类型码', q.type) END,
       '，工单【', IFNULL(wo.code, '未知'), '】，物料【', IFNULL(it.name, '未知'), '】',
       '，检验数量 ', IFNULL(q.check_quantity + 0, 0),
       '，合格 ', IFNULL(q.qualified_quantity + 0, 0), '，不合格 ', IFNULL(q.unqualified_quantity + 0, 0),
       '，结论【', CASE q.check_result WHEN 1 THEN '校验通过' WHEN 2 THEN '校验不通过' ELSE '未检' END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_qc_ipqc q
LEFT JOIN mes_pro_work_order wo ON wo.id = q.work_order_id
LEFT JOIN mes_md_item it ON it.id = q.item_id
WHERE q.tenant_id = 1 AND q.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('OQC 出货检验单【', q.code, '】', q.name,
       '：客户【', IFNULL(c.name, '未知'), '】，物料【', IFNULL(it.name, '未知'), '】',
       '，出货数量 ', q.out_quantity + 0, '，检验数量 ', IFNULL(q.check_quantity + 0, 0),
       '，合格 ', IFNULL(q.qualified_quantity + 0, 0), '，不合格 ', IFNULL(q.unqualified_quantity + 0, 0),
       '，结论【', CASE q.check_result WHEN 1 THEN '校验通过' WHEN 2 THEN '校验不通过' ELSE '未检' END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_qc_oqc q
LEFT JOIN mes_md_client c ON c.id = q.client_id
LEFT JOIN mes_md_item it ON it.id = q.item_id
WHERE q.tenant_id = 1 AND q.deleted = 0;

-- 3.9 设备管理（mes_dv_machinery / 点检 / 维修）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '设备管理', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('设备【', m.code, '】', m.name,
       IFNULL(CONCAT('，品牌 ', m.brand), ''),
       IFNULL(CONCAT('，规格 ', m.specification), ''),
       '，类型【', IFNULL(mt.name, '未分类'), '】，所在车间【', IFNULL(w.name, '未分配'), '】',
       '，状态【', CASE m.status WHEN 1 THEN '停机' WHEN 2 THEN '生产中' WHEN 3 THEN '维护中' ELSE CONCAT('状态码', m.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_dv_machinery m
LEFT JOIN mes_dv_machinery_type mt ON mt.id = m.machinery_type_id
LEFT JOIN mes_md_workshop w ON w.id = m.workshop_id
WHERE m.tenant_id = 1 AND m.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('设备点检记录 #', r.id,
       '：设备【', IFNULL(m.name, '未知'), '】，点检时间 ', r.check_time,
       '，状态【', CASE r.status WHEN 10 THEN '待点检' WHEN 20 THEN '已完成' ELSE CONCAT('状态码', r.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_dv_check_record r LEFT JOIN mes_dv_machinery m ON m.id = r.machinery_id
WHERE r.tenant_id = 1 AND r.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('设备维修单【', r.code, '】', IFNULL(r.name, ''),
       '：设备【', IFNULL(m.name, '未知'), '】',
       IFNULL(CONCAT('，要求完成日期 ', DATE(r.require_date)), ''),
       IFNULL(CONCAT('，实际完成 ', DATE(r.finish_date)), ''),
       IFNULL(CONCAT('，维修结果【', CASE r.result WHEN 1 THEN '修复成功' WHEN 2 THEN '报废' ELSE '未定' END, '】'), ''),
       '，状态【', CASE r.status WHEN 0 THEN '草稿' WHEN 1 THEN '维修中' WHEN 2 THEN '待验收' WHEN 4 THEN '已确认' ELSE CONCAT('状态码', r.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_dv_repair r LEFT JOIN mes_dv_machinery m ON m.id = r.machinery_id
WHERE r.tenant_id = 1 AND r.deleted = 0;

-- 3.10 排班班组（mes_cal_team / mes_cal_plan）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '排班班组', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('生产班组【', t.code, '】', t.name), 0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_cal_team t WHERE t.tenant_id = 1 AND t.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('排班计划【', p.code, '】', p.name,
       '：排班周期 ', DATE(p.start_date), ' 至 ', DATE(p.end_date),
       '，班制【', CASE p.shift_type WHEN 1 THEN '单白班' WHEN 2 THEN '两班倒' WHEN 3 THEN '三班倒' ELSE IFNULL(CONCAT('类型码', p.shift_type), '未设') END, '】',
       '，轮换方式【', CASE p.shift_method WHEN 1 THEN '按季度' WHEN 2 THEN '按月' WHEN 3 THEN '按周' WHEN 4 THEN '按天' ELSE IFNULL(CONCAT('类型码', p.shift_method), '未设') END, '】',
       '，状态【', CASE p.status WHEN 0 THEN '草稿' WHEN 1 THEN '已确认' ELSE CONCAT('状态码', p.status) END, '】'),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_cal_plan p WHERE p.tenant_id = 1 AND p.deleted = 0;

-- 3.11 仓储库存（mes_wm_warehouse / mes_wm_transaction）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens, segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '仓储库存', '', '', 0, 0, 8000, 0, '80370', '80370', 0, 2010);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('仓库【', w.code, '】', w.name,
       IFNULL(CONCAT('，地址 ', w.address), ''),
       IF(w.frozen = 1, '，已冻结', '，未冻结')),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_wm_warehouse w WHERE w.tenant_id = 1 AND w.deleted = 0;
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id, tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc,
CONCAT('库存事务 #', tx.id,
       '：物料【', IFNULL(it.name, '未知'), '】数量 ', tx.quantity + 0,
       '，仓库【', IFNULL(w.name, '未知'), '】',
       IFNULL(CONCAT('，批次 ', tx.batch_code), ''),
       IFNULL(CONCAT('，关联单据 ', tx.biz_code), ''),
       IFNULL(CONCAT('，时间 ', tx.transaction_time), '')),
0, '', 0, 0, '80370', '80370', 0, 2010
FROM mes_wm_transaction tx
LEFT JOIN mes_md_item it ON it.id = tx.item_id
LEFT JOIN mes_wm_warehouse w ON w.id = tx.warehouse_id
WHERE tx.tenant_id = 1 AND tx.deleted = 0;

-- ---------- 4. 角色绑定（MES 生产助手 → 知识库） + 系统提示词 ----------
UPDATE ai_chat_role
SET knowledge_ids = CONCAT(@kid),
    system_message = '你是天信管业 MES 智能生产助手，负责解答生产工单、生产任务、生产报工、工艺工序、物料产品、车间产线、质量检验、设备、库存、排班、客户供应商等业务问题。回答规则：1) 涉及平台业务数据时，优先依据知识库检索到的内容回答，并在回答末尾注明「数据来源：平台知识库」；2) 知识库中没有的数据不要编造，必须明确说明该回答来自联网搜索或通用知识，并非平台库内数据；3) 回答简洁、专业、准确。',
    updater = '80370'
WHERE id = 20 AND tenant_id = 2010;

-- ---------- 5. 回显 ----------
SELECT @kid AS knowledge_id, @embId AS embedding_model_id;
SELECT COUNT(*) AS document_cnt FROM ai_knowledge_document WHERE knowledge_id = @kid;
SELECT COUNT(*) AS segment_cnt FROM ai_knowledge_segment WHERE knowledge_id = @kid;
SELECT id, name, knowledge_ids FROM ai_chat_role WHERE id = 20 AND tenant_id = 2010;
