-- ============================================================
-- 标书智能体运行时表（自 txgy-module-bid 并入 bidding 模块，表名前缀统一 bidding_）
-- 日期：2026-09-27 ｜ 域：bidding_bid_*（运行时 5 张）
-- 规范依据：《招投标模块开发规范》§4.8（SQL 幂等、无破坏性语句）
-- 业务目标：承载「Agent → Java」契约端（/admin-api/bidding/bid-agent/contract/**）的运行时落库：
--           项目上下文、检索切片、章节草稿、企业事实、任务影子状态。
-- 结构口径（重要）：本文件**逐列对齐旧侧结构**，仅改表名，不改列/索引/唯一键——
--   bid_project → bidding_bid_project、bid_segment → bidding_bid_agent_chunk、
--   bid_chapter_draft → bidding_bid_chapter_draft、bid_enterprise_fact → bidding_bid_enterprise_fact、
--   bid_task_shadow → bidding_bid_task_shadow。
--   对齐依据：deploy/mysql/init/0070-source-bid-2026-09-07.sql.sql、0071-source-bid-segment-2026-09-07.sql.sql。
--   原因：搬入的 DO（dal/dataobject/bidagent/*）字段映射未变，若 DDL 与 DO 不一致，接口运行期会报
--         Unknown column（例如 facts/context 用 type/name/value 与 facts_json/tender_parse_json）。
-- 幂等：CREATE TABLE IF NOT EXISTS；本文件不含 DROP / TRUNCATE / DELETE。
-- 数据：旧侧数据不迁移（迁移结论：切片可再生成、其余为运行态），故表建成后为空。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 项目表（对应 BidProjectDO：name / facts_json / tender_parse_json / status / remark）
--    /contract/context/{projectId} 与 /contract/retrieve 的项目事实卡来源
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_project` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` varchar(255) DEFAULT NULL COMMENT '项目名称',
    `facts_json` text COMMENT '项目事实卡（JSON：工期/质量目标/报价策略/关键约束）',
    `tender_parse_json` text COMMENT '招标文件解析结果（JSON）',
    `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0 启用 1 禁用）',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`, `deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='标书项目（运行时）';

-- ------------------------------------------------------------
-- 2. 检索切片表（对应 BidAgentChunkDO；旧表名 bid_segment）
--    /contract/segment/index、/contract/segment/page、/contract/segment/delete 的落库表；
--    向量库点 id 存 vector_id，正文与溯源存 content/source_block_ids
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_agent_chunk` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `vector_id` varchar(64) DEFAULT NULL COMMENT '向量库中的向量 ID',
    `project_id` bigint DEFAULT NULL COMMENT '项目编号',
    `material_id` bigint DEFAULT NULL COMMENT '材料编号（bidding_bid_material.id；按材料精确级联清理/重建，契约端按名入库时为空）',
    `doc_type` varchar(16) DEFAULT NULL COMMENT '文档类型：TENDER/FACT/TEMPLATE',
    `source_doc_name` varchar(255) DEFAULT NULL COMMENT '来源文档名',
    `chapter_path` varchar(255) DEFAULT NULL COMMENT '章节路径',
    `page_no` int DEFAULT NULL COMMENT '页码',
    `source_block_ids` varchar(1000) DEFAULT NULL COMMENT '溯源块编号（逗号分隔）',
    `content` text COMMENT '片段内容',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_doc` (`tenant_id`, `source_doc_name`),
    KEY `idx_material` (`tenant_id`, `material_id`),
    KEY `idx_proj_doctype` (`project_id`, `doc_type`, `deleted`),
    KEY `idx_vector` (`vector_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='标书检索切片（运行时）';

-- ------------------------------------------------------------
-- 3. 章节草稿表（对应 BidChapterDraftDO）
--    /contract/draft/chapter 按 project_id + node_id + version 幂等回写，故保留唯一键
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_chapter_draft` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `project_id` bigint NOT NULL COMMENT '项目编号',
    `node_id` varchar(64) NOT NULL COMMENT '章节节点编号',
    `content` longtext COMMENT '章节正文',
    `refs` varchar(2000) DEFAULT NULL COMMENT '溯源引用块编号（逗号分隔）',
    `status` varchar(16) DEFAULT 'draft' COMMENT '状态：draft/review/confirmed',
    `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_node_version` (`project_id`, `node_id`, `version`),
    KEY `idx_proj_node` (`project_id`, `node_id`, `deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='标书章节草稿（运行时）';

-- ------------------------------------------------------------
-- 4. 企业事实表（对应 BidEnterpriseFactDO；COPY_FACT 通道唯一数据源）
--    注意：列是 type/name/value/unit/valid_until，不是 fact_key/fact_value
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_enterprise_fact` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `type` varchar(32) DEFAULT NULL COMMENT '类型：LICENSE/QUAL/PERFORMANCE/PERSON/FINANCE/PLAN/PRICE',
    `name` varchar(255) DEFAULT NULL COMMENT '事实名称',
    `value` varchar(1000) DEFAULT NULL COMMENT '事实值',
    `unit` varchar(32) DEFAULT NULL COMMENT '单位',
    `valid_until` date DEFAULT NULL COMMENT '有效期',
    `source_doc_name` varchar(255) DEFAULT NULL COMMENT '来源文档名',
    `source_block_ids` varchar(1000) DEFAULT NULL COMMENT '溯源块编号（逗号分隔）',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_type` (`tenant_id`, `type`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='企业事实（运行时）';

-- ------------------------------------------------------------
-- 5. Agent 任务影子状态表（对应 BidTaskShadowDO）
--    /contract/task-status 按 task_id 幂等上报，故保留唯一键；
--    progress 为 0~1 小数（decimal(5,4)，上限 9.9999，即只接受比例值）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_task_shadow` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `task_id` varchar(64) NOT NULL COMMENT 'Agent 任务编号',
    `task_type` varchar(16) DEFAULT NULL COMMENT '任务类型：parse/outline/draft/fill',
    `project_id` bigint DEFAULT NULL COMMENT '项目编号',
    `status` varchar(32) DEFAULT NULL COMMENT '任务状态',
    `phase` varchar(32) DEFAULT NULL COMMENT '当前阶段',
    `progress` decimal(5,4) DEFAULT NULL COMMENT '进度 0~1',
    `message` varchar(1000) DEFAULT NULL COMMENT '结果摘要或错误信息',
    `agent_updated_at` varchar(32) DEFAULT NULL COMMENT 'Agent 侧更新时间（ISO 8601）',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_task` (`task_id`, `deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='智能体任务影子状态（运行时）';
