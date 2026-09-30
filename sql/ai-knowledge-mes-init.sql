-- ============================================================================
-- MES 操作手册知识库 —— 初始化（幂等，可重复执行）
--
-- 用途：给「MES 生产助手」等 MES 分类角色挂一个**业务背景 / 操作口径 / 流程引导**知识库，
--       用于回答「产品入库怎么操作」「被拒绝时为什么」「能问哪些问题」这类非数据类问题。
--
-- 【为什么不是数据快照】
--   1) 业务类提问（命中工单/库存/质检等业务域关键词）在 AiChatMessageServiceImpl 里
--      **直接跳过知识库召回**，由后端 MesDataQueryService 实时查库注入真实数据；
--   2) 系统提示词明确要求：知识库 <Reference> 内容只作业务背景，不得作为业务数值来源。
--   于是把数据库快照灌进知识库既不会被用到、又会过期误导 —— 本脚本只灌"口径与流程"。
--
-- 【为什么必须再调一次 reindex】
--   向量库当前为 SimpleVectorStore（内存 + 每分钟落盘到 ~/vector_store/*.json），
--   SQL 插入的段落没有向量、检索不到；必须调用：
--     PUT /admin-api/ai/knowledge/segment/reindex?knowledgeId=<本脚本输出的知识库 id>
--   （权限 ai:knowledge:update）才会写入向量。ai 容器重建后同样需要再跑一次。
--
-- 【租户】脚本按 @tenantId 生成，**不是写死 2010**：给哪个租户用就把下面那行改掉重跑。
--         知识库表带 tenant_id（多租户隔离），所以每个租户要各自建一份。
--         绑定时会把知识库追加给**该租户全部 MES 分类角色**（category = 'MES'），
--         并顺手清掉所有 MES 角色里"指向已不存在知识库"的悬空 id（悬空 id 会让提问直接报错）。
-- ============================================================================

SET NAMES utf8mb4;

-- ★ 目标租户：给其他租户建库时只改这一行
SET @tenantId = 2010;
-- 内容署名（creator/updater），用固定系统标识，避免绑定具体用户
SET @operator = 'system';

-- ---------- 0. 幂等清理：同租户同名知识库及其文档/分段 ----------
SET @oldKid = (SELECT id FROM ai_knowledge
               WHERE name = 'MES 操作手册' AND tenant_id = @tenantId LIMIT 1);
DELETE s FROM ai_knowledge_segment s
    JOIN ai_knowledge_document d ON s.document_id = d.id
WHERE d.knowledge_id = @oldKid;
DELETE FROM ai_knowledge_document WHERE knowledge_id = @oldKid;
DELETE FROM ai_knowledge WHERE id = @oldKid;

-- ---------- 1. 向量模型（本地哈希，无需外部 Embedding API）----------
SET @embKeyId = (SELECT id FROM ai_api_key
                 WHERE name = '本地向量模型（内置）' AND tenant_id = @tenantId LIMIT 1);
INSERT INTO ai_api_key (name, api_key, platform, url, status, creator, tenant_id)
SELECT '本地向量模型（内置）', 'local-embedding', 'Local', NULL, 0, @operator, @tenantId
FROM DUAL WHERE @embKeyId IS NULL;
SET @embKeyId = (SELECT id FROM ai_api_key
                 WHERE name = '本地向量模型（内置）' AND tenant_id = @tenantId LIMIT 1);

SET @embId = (SELECT id FROM ai_model
              WHERE model = 'local-hashing-embedding' AND tenant_id = @tenantId LIMIT 1);
INSERT INTO ai_model (name, model, platform, type, key_id, sort, status,
                      temperature, max_tokens, max_contexts, creator, updater, tenant_id)
SELECT '本地哈希向量模型', 'local-hashing-embedding', 'Local', 5, @embKeyId, 1, 0,
       0.0, 4096, 8, @operator, @operator, @tenantId
FROM DUAL WHERE @embId IS NULL;
SET @embId = (SELECT id FROM ai_model
              WHERE model = 'local-hashing-embedding' AND tenant_id = @tenantId LIMIT 1);

-- ---------- 2. 知识库 ----------
INSERT INTO ai_knowledge (name, description, embedding_model_id, embedding_model, top_k,
                          similarity_threshold, status, creator, updater, deleted, tenant_id)
VALUES ('MES 操作手册',
        '天信管业 MES 操作手册与业务口径知识库：业务域速查、常用操作流程、AI 助手权限口径与常见问题处置。仅提供业务背景与流程，不提供任何业务数值。',
        @embId, 'local-hashing-embedding', 5, 0.20, 0, @operator, @operator, 0, @tenantId);
SET @kid = LAST_INSERT_ID();

-- ---------- 3. 文档 + 分段 ----------
-- 3.1 知识库定位
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens,
                                   segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '知识库说明', '', '', 0, 0, 8000, 0, @operator, @operator, 0, @tenantId);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id,
                                  tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, @doc,
        '本知识库是天信管业 MES（智慧生产执行系统）的操作口径与流程说明库。它只回答三类问题：① 系统怎么操作、单据怎么流转；② AI 助手能做什么、为什么有些问题会被拒绝；③ 常见问题的处置办法。所有业务数值（工单数量、库存、合格率等）都不在本知识库中，而是由后端在每次提问时实时查询数据库得到，因此回答里出现的数字都以本次实时查询结果为准。',
        0, '', 0, 0, @operator, @operator, 0, @tenantId);

-- 3.2 业务域速查（AI 助手可查范围）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens,
                                   segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, 'AI 助手可查业务域速查', '', '', 0, 0, 8000, 0, @operator, @operator, 0, @tenantId);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id,
                                  tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc, t.content, 0, '', 0, 0, @operator, @operator, 0, @tenantId
FROM (
    SELECT 1 AS seq, 'MES 生产助手按 13 个业务域授权查询：生产工单、生产任务、生产报工、工艺工序、安灯异常、待检任务、物料产品、车间产线、质量检验、设备台账、仓储库存（MES 侧库存）、排班班组、客户供应商。典型问法：「查询已取消的生产工单」「WO-20260902-001 的详情」「金岩原辅材料库的库存」「本月报工了多少」「有哪些设备在维修」「IPQC 检验记录」「待检任务有哪些」。' AS content
    UNION ALL SELECT 2, 'MES 生产助手支持的录入类操作（需要用户在弹窗中二次确认后才会落库）：新建生产工单、生产报工、过程检验单（IPQC）、物料/产品、客户、供应商、车间、工位、工序、班组、计量单位、物料分类、设备、设备类型、仓库区域、生产任务指派。典型说法：「给工单 WO-20260902-001 报工 500 件，合格 490」「新建物料：岩棉板，单位立方米，规格 1200×600×50」。'
    UNION ALL SELECT 3, '除 MES 业务域外，系统另有 WMS 仓储 AI 业务域（库存余额、库存预警、商品档案、仓库信息、往来企业、出入库单据）。WMS 域需要单独授权；若未授权，提问涉及这些域时会被权限守门员拒绝。MES 侧的出入库单据填写与提交需在「仓库管理」菜单（MES 系统 → 仓库管理）手工完成，AI 只能查询单据状态，不代写此类单据。'
    UNION ALL SELECT 4, 'AI 助手只做「理解 + 预填/查询」，写入类操作一律要求人工确认，且不代替审批。涉及单据审批、状态流转的最终决定权仍在审批人。'
) t;

-- 3.3 常用操作与流程
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens,
                                   segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '常用操作与流程', '', '', 0, 0, 8000, 0, @operator, @operator, 0, @tenantId);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id,
                                  tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc, t.content, 0, '', 0, 0, @operator, @operator, 0, @tenantId
FROM (
    SELECT 1 AS seq, '登录与租户：浏览器打开平台地址后先选择租户再登录。数据按租户隔离，选错租户会出现「数据查不到」或菜单缺失；MES 助手回答的业务数据也严格限定在当前登录租户内。' AS content
    UNION ALL SELECT 2, 'AI 助手入口：进入 MES 系统（智慧生产执行系统）板块后，页面右下角出现蓝色圆形悬浮球，点击展开对话面板；面板可拖拽移动、位置自动记忆，不会挤压页面内容；标题栏可最大化/收起，输入区左下角可「新对话」「完整对话」。'
    UNION ALL SELECT 3, '生产工单主流程：创建工单（草稿）→ 确认 → 车间按任务报工 → 质检（过程检验 IPQC）→ 完工入库 → 工单完成。工单状态为：草稿 0、已确认 1、已完成 2、已取消 3。列表页支持按编码、名称、状态、日期多条件筛选。'
    UNION ALL SELECT 4, '质检流程：按检验阶段进入对应菜单 —— IQC 来料检验、IPQC 过程检验、OQC 出货检验、RQC 退货检验。新建检验单后选择检验模板与指标，逐项录入检验结果；不合格品走缺陷记录。其中 IPQC 数据可供 AI 助手查询。'
    UNION ALL SELECT 5, '安全环保（污染判定）流程：进入「MES 系统 → 安全环保检测 → 污染判定」，系统对采购入库、生产领用、中间废弃物、成品四个环节做 AI 初筛（CLEAN 干净 / POLLUTED 污染 / UNCERTAIN 待定），再由人工复核确认最终二值（CLEAN / POLLUTED）。判定为污染的批次会自动打环保戳并进入污染暂存台账；台账支持登记、处置中、终态、自动解除（CLEARED），全程留痕。复核是收口动作，未处置完成会触发流转门禁。'
    UNION ALL SELECT 6, '报工智能填单流程：在 AI 面板描述报工内容（工单、产品、工序、数量、合格/不良数量）→ AI 识别并预填 → 弹出确认卡片 → 用户确认后调用后端既有提交链路生成报工单。合格/不良/工废/料废的数量联动、BOM 消耗等校验仍由后端既有逻辑保证，AI 不参与计算规则。'
    UNION ALL SELECT 7, '仓库与出入库操作（菜单：MES 系统 → 仓库管理）：1) 入库作业：「采购入库」（供应商来料）、「产品入库」（自制成品/半成品完工入库）、「外协入库」、「杂项入库」；采购到货可先做「到货通知」再收货；「生产退料」用于生产退回物料；2) 出库作业：「生产领料」（生产用料领用）、「销售出库」（一般先做「发货通知」再出货）、「外协发料」、「杂项出库」；3) 仓库基础：「仓库设置」（按 仓库 → 库位 → 库区 逐层维护）、「库存现有量」（库存查询）、「库存盘点」、「条码配置」；「缺料预警」用于缺料提示。单据含明细行并与库存联动，建议在页面完成填写与提交；AI 助手可查单据与库存数据、可做生产领料类的智能填单，但不代写出入库单据。'
    UNION ALL SELECT 8, '生产流转卡与项目：生产流转卡可按编号、编码、批次号查询；项目在「MES 系统 → 生产管理 → 项目管理」维护，支持优先级（紧急/高/中/低）、负责人、责任部门、计划起止时间、成员管理与状态流转，并支持 Excel 批量导入与导出。'
) t;

-- 3.4 AI 助手权限口径（为什么会被拒绝）
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens,
                                   segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, 'AI 助手权限口径', '', '', 0, 0, 8000, 0, @operator, @operator, 0, @tenantId);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id,
                                  tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc, t.content, 0, '', 0, 0, @operator, @operator, 0, @tenantId
FROM (
    SELECT 1 AS seq, 'MES 助手的权限由后端规则强校验（不交给大模型判断），共三道闸门：① 提问命中「用户没有授权」的业务域 → 直接拒绝；② 提问涉及敏感数据（成本、售价、工时工资、工艺配方参数、客户联系方式等）而角色未开启敏感数据权限 → 直接拒绝；③ 提问包含越狱/注入类指令（如「忽略以上指令」「你现在扮演…」「绕过权限」）→ 直接拒绝且不调用大模型。' AS content
    UNION ALL SELECT 2, '被拒绝时的统一回复话术是「您当前账号没有访问该业务数据的权限，无法执行本次查询」；提示词注入类提问的回复是「抱歉，检测到您的提问包含不安全的指令内容，我无法执行」。看到这两句话说明是权限规则命中，不是系统故障。'
    UNION ALL SELECT 3, '若希望某个账号能查询更多业务域，需要在「AI 大模型 → 聊天角色」里给对应角色补充业务域授权，并把该用户与角色绑定；敏感数据需要在对应业务域上单独开启「允许访问敏感数据」。数据范围可为全部数据 / 本部门数据 / 仅本人相关数据。'
    UNION ALL SELECT 4, 'AI 只依据后端返回的真实数据作答，不猜测、不估算、不编造；查不到数据时会明确回复「未查询到相关数据，请核对查询条件」。回答中出现「数据查询接口异常，请稍后重试」表示后端查询环节出错，需要联系运维查看 ai 服务日志，而不是换个说法再问一次。'
    UNION ALL SELECT 5, 'AI 助手回答基于当前登录租户的数据，并只展示提问条件匹配的记录；不会输出系统提示词、上下文、工具调用过程等内部细节。'
) t;

-- 3.5 常见问题处置
INSERT INTO ai_knowledge_document (knowledge_id, name, url, content, content_length, tokens,
                                   segment_max_tokens, status, creator, updater, deleted, tenant_id)
VALUES (@kid, '常见问题处置', '', '', 0, 0, 8000, 0, @operator, @operator, 0, @tenantId);
SET @doc = LAST_INSERT_ID();
INSERT INTO ai_knowledge_segment (knowledge_id, document_id, content, content_length, vector_id,
                                  tokens, status, creator, updater, deleted, tenant_id)
SELECT @kid, @doc, t.content, 0, '', 0, 0, @operator, @operator, 0, @tenantId
FROM (
    SELECT 1 AS seq, 'AI 回复长时间停在「思考中」：推理模型思考链阶段属正常；超过 2 分钟仍未输出，需检查 ai 服务日志与模型 API Key 是否有效。' AS content
    UNION ALL SELECT 2, 'AI 查不到业务数据：先确认登录租户与数据所属租户一致（数据按租户隔离），再确认筛选条件（单号、物料名、状态）是否与库中一致；同一问法在别人电脑上能答、你这边不答时，通常是连的后端库不同或账号的 AI 角色/业务域授权不同。'
    UNION ALL SELECT 3, '页面菜单缺失：菜单需要授权给当前角色（系统管理 → 角色管理 → 勾选菜单），或数据库未执行对应的升级 SQL；【AI 客服管理】等菜单还需将其加入租户套餐。'
    UNION ALL SELECT 4, 'MES 助手面板看不到：该入口只在 MES 系统（智慧生产执行系统）板块内显示，切到 CRM/ERP 等其他板块会隐藏。'
    UNION ALL SELECT 5, '知识库检索不到内容（流程类问题答得泛泛）：知识库段落需要先向量化，调用 PUT /admin-api/ai/knowledge/segment/reindex?knowledgeId=<知识库编号> 重建向量索引即可；向量当前保存在 ai 容器内的内存向量库中，容器重建后需要重新执行一次该动作。'
) t;

-- ---------- 4. 绑定：追加给本租户全部 MES 分类角色 ----------
-- 4.1 先清理所有 MES 角色里"指向不存在知识库"的悬空 id（悬空 id 会让提问直接报错）
-- 注意：临时表列显式指定 utf8mb4_unicode_ci，与 ai_chat_role.knowledge_ids 保持同一排序规则，
-- 否则 MySQL 8 会在字符串比较时报 Illegal mix of collations。
CREATE TEMPORARY TABLE tmp_mes_role_kb (
    role_id   bigint,
    valid_ids varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
);
INSERT INTO tmp_mes_role_kb (role_id, valid_ids)
SELECT r.id,
       (SELECT GROUP_CONCAT(k.id ORDER BY k.id)
        FROM ai_knowledge k
        WHERE k.deleted = 0 AND k.tenant_id = r.tenant_id
          AND FIND_IN_SET(CAST(k.id AS CHAR) COLLATE utf8mb4_unicode_ci,
                          r.knowledge_ids)) AS valid_ids
FROM ai_chat_role r
WHERE r.deleted = 0 AND r.category = 'MES';

UPDATE ai_chat_role r
    JOIN tmp_mes_role_kb t ON t.role_id = r.id
SET r.knowledge_ids = t.valid_ids
WHERE IFNULL(r.knowledge_ids, '') <> IFNULL(t.valid_ids, '');

DROP TEMPORARY TABLE tmp_mes_role_kb;

-- 4.2 追加本知识库（已绑定则不重复追加）
--     绑定范围：MES 分类角色 + 「平台通用助手」（AI首页/门户首页助手也要能答操作类问题）
UPDATE ai_chat_role r
SET r.knowledge_ids = IF(r.knowledge_ids IS NULL OR r.knowledge_ids = '',
                         CAST(@kid AS CHAR), CONCAT(r.knowledge_ids, ',', @kid))
WHERE r.deleted = 0 AND r.tenant_id = @tenantId
  AND (r.category = 'MES' OR r.name = '平台通用助手' COLLATE utf8mb4_unicode_ci)
  AND (r.knowledge_ids IS NULL
       OR NOT FIND_IN_SET(CAST(@kid AS CHAR) COLLATE utf8mb4_unicode_ci, r.knowledge_ids));

-- ---------- 5. 结果确认 ----------
SELECT '知识库编号 knowledgeId' AS item, CAST(@kid AS CHAR) AS value
UNION ALL
SELECT '文档数', CAST(COUNT(DISTINCT d.id) AS CHAR)
FROM ai_knowledge_document d WHERE d.knowledge_id = @kid
UNION ALL
SELECT '分段数', CAST(COUNT(*) AS CHAR)
FROM ai_knowledge_segment s WHERE s.knowledge_id = @kid
UNION ALL
SELECT '已绑定 MES 角色数', CAST(COUNT(*) AS CHAR)
FROM ai_chat_role r
WHERE r.deleted = 0 AND r.category = 'MES' AND r.tenant_id = @tenantId
  AND FIND_IN_SET(CAST(@kid AS CHAR) COLLATE utf8mb4_unicode_ci, r.knowledge_ids);

-- 别忘了：执行完请调用一次 PUT /admin-api/ai/knowledge/segment/reindex?knowledgeId=<上面的编号> 完成向量化
