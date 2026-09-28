-- ============================================================
-- 标书智能体：检索链上云（百炼 embedding/rerank + Qdrant 容器）结构变更
-- 日期：2026-09-16 ｜ 域：bidding_bid_agent_chunk / bidding_bid_agent
-- 规范依据：《招投标模块开发规范》§4.8（SQL 幂等、无破坏性语句）
-- 业务目标：
--   1) 切片表补 bid_section_code —— TENDER 材料必绑标段，检索需要按标段隔离（本期真正生效）；
--   2) 智能体配置补齐 5 行种子（PARSE/OUTLINE/DRAFT/FILL/REVIEW）——
--      2026-09-26 脚本执行时库内只落了 PARSE 一行，导致「智能体管理」只有一行且不可编辑、试跑入口不可达；
--      这里用 INSERT IGNORE 幂等补录（唯一键 uk_tenant_task_type 保证不重复）。
-- 说明：向量库集合 bidding_bid_agent_chunk 由应用启动时按
--      spring.ai.vectorstore.qdrant.initialize-schema=true 自动创建，维度取 embedding.dimensions（默认 1024），
--      本脚本不涉及 Qdrant（Qdrant 非关系库，且集合为空时无需迁移）。
-- 幂等：DDL 走 information_schema 判断 + PREPARE，数据走 INSERT IGNORE；本文件不含 DROP / TRUNCATE / DELETE。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 切片表补「标段编号」列（幂等：已存在则跳过）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bidding_bid_agent_chunk'
      AND column_name = 'bid_section_code'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `bidding_bid_agent_chunk` ADD COLUMN `bid_section_code` varchar(64) DEFAULT NULL COMMENT ''标段编号：TENDER 必填，检索按标段隔离'' AFTER `doc_type`',
    'SELECT ''bid_section_code already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 标段 + 租户联合索引（幂等：已存在则跳过）
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bidding_bid_agent_chunk'
      AND index_name = 'idx_tenant_section'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `bidding_bid_agent_chunk` ADD KEY `idx_tenant_section` (`tenant_id`, `bid_section_code`)',
    'SELECT ''idx_tenant_section already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 智能体配置 5 行种子补录（与 2026-09-26-bid-agent.sql 同源，仅 REVIEW 后台可编辑）
--    注意：本表用的是雪花主键（App 侧 @TableId(ASSIGN_ID) 生成），不会走数据库自增，
--    因此裸 SQL 必须显式给 id，否则报 "Field 'id' doesn't have a default value"。
--    这里用稳定的种子 id 段（1000001~1000005）；若某 task_type 已存在，由唯一键 uk_tenant_task_type 忽略。
-- ------------------------------------------------------------
INSERT IGNORE INTO `bidding_bid_agent`
    (`id`, `task_type`, `name`, `description`, `system_prompt`, `knowledge_roles`, `editable`, `sort`, `status`,
     `remark`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    (1000001, 'PARSE', '招标文件解析', '读招标文件，抽出废标条款、资格要求、评分点与全局事实',
     '你是招投标业务专家。只依据给定材料文本抽取，不允许推断或补全；抽不到的字段留空并标注"未找到"。输出必须是严格 JSON。',
     'TENDER', 0, 10, 0, '提示词由 Agent 侧内置生效，后台可查看比对', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000002, 'OUTLINE', '标书大纲', '按评分点与废标条款生成章节树，逐条承接',
     '你是标书编制专家。章节树必须逐条承接招标文件的评分点与废标条款，每条都要标明承接来源。',
     'TENDER', 0, 20, 0, '提示词由 Agent 侧内置生效，后台可查看比对', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000003, 'DRAFT', '标书撰写', '按章节撰写正文，每段附证据引用，数字不得编造',
     '你是标书撰写专家。只写有证据支撑的内容；每个数字都必须能在证据或项目事实卡中找到来源，找不到就写"待补充"。',
     'TENDER,FACT,TEMPLATE', 0, 30, 0, '提示词由 Agent 侧内置生效，后台可查看比对', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000004, 'FILL', '模板回填', '把事实类字段确定性回填到招标方模板',
     '你只处理必须组织语言的字段；证书编号、金额、日期等事实字段一律不由你输出。',
     'FACT,TEMPLATE', 0, 40, 0, '提示词由 Agent 侧内置生效，后台可查看比对', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000005, 'REVIEW', '标书审查', '逐条比对招标文件要求与投标文件，输出带定位的风险清单',
     '你是投标文件合规审查专家。逐条判断投标文件是否满足招标文件的强制要求（废标条款、资格要求、必检项）。\n'
     '判定口径：\n'
     '1) 投标文件中有明确、可直接对应的响应内容 → PASS（符合）；\n'
     '2) 有相关表述但口径不一致、不完整或需人工确认 → DOUBT（存疑）；\n'
     '3) 投标文件明确不满足或与要求冲突 → FAIL（不符）；\n'
     '4) 投标文件中找不到任何对应内容 → MISS（未覆盖）。\n'
     '严格要求：只依据提供的投标文件证据判定，不得推测投标文件里没有的内容；证据必须逐字摘自原文。输出必须是严格 JSON。',
     'TENDER', 1, 50, 0, '后台可编辑，审查编排即时读取生效', 0, '1', NOW(), '1', NOW(), b'0');
