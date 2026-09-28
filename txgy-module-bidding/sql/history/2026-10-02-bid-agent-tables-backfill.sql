-- ============================================================
-- 2026-10-02 标书智能体域·纯建表回填（本库漏执行补跑）
-- 来源：master-bidding-sync.sql + history/2026-09-16/09-27 建表段 + 09-18~09-28 安全增量
-- 背景：菜单冲突已由 2026-10-02-bid-agent-menu-local-ids.sql 解决并执行（7000~7023 在库）；
--       建表段因 09-16 脚本开头 ALTER 不存在的 bidding_bid_agent 报错中止从未跑到。
-- 本文件 **不含任何 system_menu 语句**（菜单段全部跳过：09-18-model-menu / 09-22 两个 restore/embed）。
-- 幂等：CREATE TABLE IF NOT EXISTS / INSERT IGNORE / information_schema 条件 DDL / 幂等 DELETE+UPDATE。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- A. 建表 15 张（master 4 + 09-16 六 + 09-27 五） ----------
-- 表: bidding_bid_material（master-bidding-sync.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_material` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `doc_type` varchar(16) NOT NULL COMMENT '材料角色：TENDER/FACT/TEMPLATE',
    `name` varchar(255) NOT NULL COMMENT '材料名称（专员可见）',
    `bid_section_code` varchar(64) DEFAULT NULL COMMENT '所属标段编号（TENDER 必填，FACT/TEMPLATE 可空）',
    `file_id` bigint NOT NULL COMMENT 'infra 文件编号（全局唯一，落库唯一依据）',
    `file_url` varchar(1000) DEFAULT NULL COMMENT '文件访问地址（仅用于取内容入库与即时预览，不作为一致性依据）',
    `file_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
    `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
    `index_status` tinyint NOT NULL DEFAULT '0' COMMENT '入库状态：0 待入库 1 已入库 2 失败',
    `segment_count` int NOT NULL DEFAULT '0' COMMENT '片段数',
    `index_message` varchar(500) DEFAULT NULL COMMENT '入库结果或失败原因（失败必须写明）',
    `valid_until` date DEFAULT NULL COMMENT '有效期（FACT 类资质用于过期提醒）',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_type_section` (`tenant_id`, `doc_type`, `bid_section_code`),
    KEY `idx_tenant_file` (`tenant_id`, `file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标书智能体-材料台账';
-- 表: bidding_bid_segment（master-bidding-sync.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_segment` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `material_id` bigint NOT NULL COMMENT '材料编号',
    `chunk_no` int NOT NULL DEFAULT '0' COMMENT '片段序号（从 1 起）',
    `content` text COMMENT '片段正文',
    `source_block_ids` varchar(255) DEFAULT NULL COMMENT '溯源块编号（材料名#序号）',
    `page_no` int DEFAULT NULL COMMENT '页码',
    `edited` tinyint NOT NULL DEFAULT '0' COMMENT '是否被人工编辑过：0 否 1 是',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_material_chunk` (`tenant_id`, `material_id`, `chunk_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标书智能体-检索片段';
-- 表: bidding_bid_agent（master-bidding-sync.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_agent` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `task_type` varchar(16) NOT NULL COMMENT '任务类型：PARSE/OUTLINE/DRAFT/FILL/REVIEW',
    `name` varchar(64) NOT NULL COMMENT '智能体名称（专员可见）',
    `description` varchar(255) DEFAULT NULL COMMENT '一句话说明：这个智能体负责什么',
    `system_prompt` text COMMENT '系统提示词',
    `model` varchar(64) DEFAULT NULL COMMENT '模型名（空=用平台默认）',
    `temperature` decimal(3,2) DEFAULT NULL COMMENT '温度（空=用平台默认）',
    `knowledge_roles` varchar(64) DEFAULT NULL COMMENT '默认挂载的知识库角色，逗号分隔，如 TENDER,FACT',
    `editable` tinyint NOT NULL DEFAULT '1' COMMENT '是否后台可编辑：1 可编辑 0 只读（锁机制保留）',
    `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
    `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0 开启 1 停用',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_task_type` (`tenant_id`, `task_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标书智能体-配置';
-- 表: bidding_bid_eval_case（master-bidding-sync.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_eval_case` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `name` varchar(255) NOT NULL COMMENT '用例名称（如：废标条款-安全生产许可证）',
    `query` varchar(500) NOT NULL COMMENT '标准问题（检索词）',
    `doc_type` varchar(16) DEFAULT NULL COMMENT '限定材料角色：TENDER/FACT/TEMPLATE，空=不限',
    `bid_section_code` varchar(64) DEFAULT NULL COMMENT '限定标段，空=不限',
    `top_k` int NOT NULL DEFAULT '10' COMMENT '评测取前 K 条',
    `expect_keywords` varchar(500) DEFAULT NULL COMMENT '期望命中的关键词，半角逗号分隔（命中任一即算该用例命中）',
    `expect_block_ids` varchar(1000) DEFAULT NULL COMMENT '期望命中的溯源块编号，半角逗号分隔（可选，优先级高于关键词）',
    `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1 是 0 否',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_enabled` (`tenant_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标书智能体-精准度测试用例';
-- 表: bidding_bid_agent_model（history/2026-09-16-bid-agent-agent-platform.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_agent_model` (
    `id` bigint NOT NULL COMMENT '主键',
    `name` varchar(64) NOT NULL COMMENT '配置名称（如 默认云端模型）',
    `provider` varchar(32) NOT NULL DEFAULT 'openai-compat' COMMENT '协议：openai-compat',
    `base_url` varchar(255) NOT NULL COMMENT 'OpenAI 兼容 base-url（如 https://dashscope.aliyuncs.com/compatible-mode/v1）',
    `model` varchar(64) NOT NULL COMMENT '模型名（如 qwen-plus）',
    `api_key` varchar(512) DEFAULT NULL COMMENT 'API Key（平台 EncryptTypeHandler 加密存储）',
    `temperature` decimal(3,2) DEFAULT 0.20 COMMENT '温度',
    `max_tokens` int DEFAULT 8192 COMMENT '单条回复最大 token',
    `timeout_seconds` int DEFAULT 180 COMMENT '单次调用超时（秒）',
    `max_retries` int DEFAULT 3 COMMENT '失败重试次数',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用 0 停用',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（0=平台全局）',
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标书智能体-云端模型配置';
-- 表: bidding_bid_agent_skill（history/2026-09-16-bid-agent-agent-platform.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_agent_skill` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `code` varchar(64) NOT NULL COMMENT '技能编码（租户内唯一）',
    `name` varchar(64) NOT NULL COMMENT '技能名称',
    `description` varchar(255) DEFAULT NULL COMMENT '一句话说明',
    `prompt_fragment` text COMMENT '提示词片段（注入 system prompt）',
    `knowledge_roles` varchar(64) DEFAULT NULL COMMENT '建议挂载的知识库角色：TENDER/FACT/TEMPLATE',
    `tool_whitelist` varchar(255) DEFAULT NULL COMMENT '建议/限定的工具白名单（逗号分隔）',
    `applicable_task_types` varchar(64) DEFAULT NULL COMMENT '适用任务类型：PARSE/OUTLINE/DRAFT/FILL',
    `auto_attach` tinyint NOT NULL DEFAULT 1 COMMENT '是否自动挂载到适用智能体：1 是 0 否',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
    `remark` varchar(500) DEFAULT NULL,
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（0=平台预置）',
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标书智能体-技能';
-- 表: bidding_bid_agent_profile_skill（history/2026-09-16-bid-agent-agent-platform.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_agent_profile_skill` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `profile_id` bigint NOT NULL COMMENT '智能体配置编号（bidding_bid_agent.id）',
    `skill_id` bigint NOT NULL COMMENT '技能编号（bidding_bid_agent_skill.id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_skill` (`profile_id`, `skill_id`, `deleted`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标书智能体-智能体与技能关联';
-- 表: bidding_bid_agent_mcp_server（history/2026-09-16-bid-agent-agent-platform.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_agent_mcp_server` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `name` varchar(64) NOT NULL COMMENT 'MCP 服务名（租户内唯一；工具将注入为 mcp__{name}__{tool}）',
    `transport` varchar(16) NOT NULL DEFAULT 'streamable-http' COMMENT '传输：stdio / sse / streamable-http',
    `command` varchar(255) DEFAULT NULL COMMENT 'stdio：启动命令',
    `args` varchar(1000) DEFAULT NULL COMMENT 'stdio：参数（JSON 数组）',
    `env` varchar(2000) DEFAULT NULL COMMENT 'stdio：环境变量（JSON 对象，值加密存储）',
    `url` varchar(500) DEFAULT NULL COMMENT 'http/sse：服务地址',
    `headers` varchar(2000) DEFAULT NULL COMMENT 'http/sse：请求头（JSON 对象，值加密存储）',
    `timeout_seconds` int DEFAULT 60 COMMENT '单次调用超时（秒）',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用 0 停用',
    `tool_snapshot_json` text COMMENT 'tools/list 结果快照（管理页展示与白名单勾选）',
    `last_check_at` datetime DEFAULT NULL COMMENT '最近一次连通性自检时间',
    `last_check_result` varchar(500) DEFAULT NULL COMMENT '最近一次自检结果',
    `remark` varchar(500) DEFAULT NULL,
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标书智能体-MCP 服务';
-- 表: bidding_bid_agent_profile_mcp（history/2026-09-16-bid-agent-agent-platform.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_agent_profile_mcp` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `profile_id` bigint NOT NULL COMMENT '智能体配置编号',
    `mcp_server_id` bigint NOT NULL COMMENT 'MCP 服务编号',
    `tool_names` varchar(2000) DEFAULT NULL COMMENT '允许的工具子集（JSON 数组；空=全部）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_mcp` (`profile_id`, `mcp_server_id`, `deleted`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标书智能体-智能体与 MCP 关联';
-- 表: bidding_bid_agent_task（history/2026-09-16-bid-agent-agent-platform.sql）
CREATE TABLE IF NOT EXISTS `bidding_bid_agent_task` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `task_no` varchar(64) NOT NULL COMMENT '任务号（幂等键）',
    `project_id` bigint NOT NULL COMMENT '项目编号',
    `bid_section_code` varchar(64) NOT NULL COMMENT '标段编号（检索隔离口径）',
    `doc_category` varchar(16) DEFAULT NULL COMMENT '标书类型：technical 技术标书 / business 商务标书（前台工坊选择，透传编排侧）',
    `tender_material_id` bigint DEFAULT NULL COMMENT '招标文件材料编号（bidding_bid_material.id）',
    `template_material_id` bigint DEFAULT NULL COMMENT '模板材料编号（可空；空则跳过模板回填）',
    `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED',
    `running_key` varchar(160) GENERATED ALWAYS AS (IF(`status` IN ('PENDING','RUNNING'), CONCAT(`tenant_id`, ':', `project_id`, ':', `bid_section_code`), NULL)) STORED COMMENT '进行中任务的租户:项目:标段（终态为 NULL，供唯一索引防并发重复）',
    `current_step` varchar(16) DEFAULT NULL COMMENT 'PARSE/OUTLINE/DRAFT/FILL',
    `progress` int NOT NULL DEFAULT 0 COMMENT '进度 0~100',
    `agent_task_id` varchar(64) DEFAULT NULL COMMENT '底座任务编号',
    `step_detail_json` text COMMENT '各阶段耗时/产出摘要（JSON）',
    `open_questions_json` text COMMENT '待人工确认清单（JSON）',
    `artifact_file_id` bigint DEFAULT NULL COMMENT '产物文件编号（infra file_id）',
    `artifact_url` varchar(1000) DEFAULT NULL COMMENT '产物访问地址',
    `error_code` varchar(32) DEFAULT NULL COMMENT '失败错误码',
    `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因',
    `prompt_tokens` int DEFAULT NULL, `completion_tokens` int DEFAULT NULL, `total_tokens` int DEFAULT NULL,
    `started_at` datetime DEFAULT NULL COMMENT '开始时间',
    `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
    `remark` varchar(500) DEFAULT NULL,
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    UNIQUE KEY `uk_running` (`running_key`),
    KEY `idx_tenant_project` (`tenant_id`, `project_id`, `deleted`),
    KEY `idx_agent_task` (`agent_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标书智能体-一键生成任务';
-- 表: bidding_bid_project（history/2026-09-27-bid-agent-runtime.sql）
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
-- 表: bidding_bid_agent_chunk（history/2026-09-27-bid-agent-runtime.sql）
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
-- 表: bidding_bid_chapter_draft（history/2026-09-27-bid-agent-runtime.sql）
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
-- 表: bidding_bid_enterprise_fact（history/2026-09-27-bid-agent-runtime.sql）
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
-- 表: bidding_bid_task_shadow（history/2026-09-27-bid-agent-runtime.sql）
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

-- ---------- B. 09-16 条件 ALTER（tool_whitelist/max_tokens/skill_auto_attach/outline_json） ----------
-- 提取条件 ALTER 4 块
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent' AND column_name = 'tool_whitelist');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent` ADD COLUMN `tool_whitelist` varchar(255) DEFAULT NULL COMMENT ''内置工具白名单（逗号分隔，空=全部允许）'' AFTER `knowledge_roles`', 'SELECT ''tool_whitelist exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent' AND column_name = 'max_tokens');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent` ADD COLUMN `max_tokens` int DEFAULT NULL COMMENT ''覆盖全局的单条回复最大 token（空=用全局）'' AFTER `temperature`', 'SELECT ''max_tokens exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent' AND column_name = 'skill_auto_attach');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent` ADD COLUMN `skill_auto_attach` tinyint NOT NULL DEFAULT 1 COMMENT ''是否自动挂载推荐技能（1 是 0 否）'' AFTER `editable`', 'SELECT ''skill_auto_attach exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_project' AND column_name = 'outline_json');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_project` ADD COLUMN `outline_json` longtext COMMENT ''标书章节树（Node 编排回写，JSON）'' AFTER `tender_parse_json`', 'SELECT ''outline_json exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- C. 09-16 种子（8 条预置技能 + 云端模型占位） ----------
-- 种子段（skill 8 条 + model 占位）
INSERT IGNORE INTO `bidding_bid_agent_skill`
    (`id`, `code`, `name`, `description`, `prompt_fragment`, `knowledge_roles`, `tool_whitelist`,
     `applicable_task_types`, `auto_attach`, `status`, `sort`, `remark`, `tenant_id`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    (1000001, 'TENDER_KEYPOINTS', '招标要点与废标条款提取', '从招标文件中提取废标条款、资格要求与评分点，逐条列明出处',
     '提取时逐条列明"条款原文 + 出处（文件名/章节/页码）"；废标条款与资格要求必须一条不漏，缺失即视为未完成。',
     'TENDER', 'bid-retrieve,bid-docx', 'PARSE', 1, 0, 10, '平台预置', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000002, 'OUTLINE_FROM_SCORING', '评分点承接与章节树编排', '章节树逐条承接评分点与废标条款，每条标出来源',
     '章节树每个节点都要标注它承接的评分点/废标条款编号；不得出现无来源的章节。',
     'TENDER', 'bid-retrieve', 'OUTLINE', 1, 0, 20, '平台预置', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000003, 'TECH_PROPOSAL_WRITING', '技术标正文写作', '技术方案/施工组织类章节的写作口径：先结论后依据',
     '技术标正文采用"先给结论、再给依据"的结构；工艺参数、设备型号、检测标准必须来自证据片段。',
     'TENDER,FACT,TEMPLATE', 'bid-retrieve', 'DRAFT', 1, 0, 30, '平台预置', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000004, 'COMMERCIAL_CONSISTENCY', '商务标与报价口径一致', '报价、工期、付款条件在全文保持同一口径',
     '金额、工期、付款方式、质保期在全文必须完全一致；与项目事实卡冲突时以事实卡为准并在待补充项中标注。',
     'FACT,TEMPLATE', 'bid-retrieve', 'DRAFT,FILL', 1, 0, 40, '平台预置', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000005, 'EVIDENCE_TRACEABILITY', '证据引用与溯源规范', '每条关键结论都要给出溯源块号',
     '每条关键结论必须附 sourceBlockIds 溯源；无法溯源的结论改写为"待补充"，不得留下无出处断言。',
     'TENDER,FACT,TEMPLATE', 'bid-retrieve', 'DRAFT,FILL', 1, 0, 50, '平台预置', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000006, 'NO_FABRICATION', '数据不可编造与待补充标注', '数字/日期/证书编号/业绩/人员一律不得推测',
     '任何数字、日期、金额、证书编号、业绩名称、人员姓名都必须来自证据或企业事实；取不到就写"待补充"并登记到待确认清单。',
     'TENDER,FACT', 'bid-retrieve', 'PARSE,DRAFT,FILL', 1, 0, 60, '平台预置', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000007, 'TEMPLATE_FILL_RULES', '模板字段回填口径', '只回填事实类字段，语言类字段交由生成',
     '模板回填只处理事实类字段（编号/金额/日期/名称）；需要组织语言的字段标记为 GENERATE 交由模型生成，不得硬填。',
     'FACT,TEMPLATE', 'bid-docx', 'FILL', 1, 0, 70, '平台预置', 0, '1', NOW(), '1', NOW(), b'0'),
    (1000008, 'BID_DOC_STYLE', '标书公文语言风格', '用语规范、避免口语与夸张表述',
     '行文使用标书公文风格：陈述句为主，禁用"最佳/第一/绝对"等绝对化用语，避免第一人称与口语化表达。',
     'TENDER,TEMPLATE', NULL, 'DRAFT,FILL', 1, 0, 80, '平台预置', 0, '1', NOW(), '1', NOW(), b'0');

-- ------------------------------------------------------------
-- 8. 全局云端模型配置占位行（默认停用，待管理员在「智能体管理 → 云端模型」填 base-url/model/key 后启用）
-- ------------------------------------------------------------
INSERT IGNORE INTO `bidding_bid_agent_model`
    (`id`, `name`, `provider`, `base_url`, `model`, `api_key`, `temperature`, `max_tokens`, `timeout_seconds`,
     `max_retries`, `enabled`, `remark`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    (1, '默认云端模型', 'openai-compat', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'qwen-plus', NULL,
     0.20, 8192, 180, 3, 0, '请在智能体管理页填入百炼/其它 OpenAI 兼容端点的 base-url、模型名与 API Key 后启用',
     0, '1', NOW(), '1', NOW(), b'0');

-- ---------- D. 安全增量（条件/幂等，零菜单） ----------
-- >>> 增量: history/2026-09-18-bid-agent-knowledge-refine.sql
-- ============================================================
-- 标书智能体：知识库管理模块完善（切片人工治理）
-- 日期：2026-09-18 ｜ 域：bidding_bid_agent_chunk
-- 规范依据：《招投标模块开发规范》§4.8（SQL 幂等、无破坏性语句）
-- 业务目标：
--   切片管理（知识库管理 → 切片管理）支持人工编辑/拆分/合并/删除切片，
--   需要给切片表补「是否人工修改」标记，区分机器切片与人工优化切片，
--   便于质量巡检与「修改后重建向量」的闭环提示。
-- 说明：切片表 bidding_bid_agent_chunk 是检索链（向量 + BM25）共用的切片台账，
--       本脚本只补列，不动现有列/索引，存量切片数据无需重写。
-- 幂等：DDL 走 information_schema 判断 + PREPARE，本文件不含 DROP / TRUNCATE / DELETE。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 切片表补「是否人工修改」列（幂等：已存在则跳过）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bidding_bid_agent_chunk'
      AND column_name = 'edited'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `bidding_bid_agent_chunk` ADD COLUMN `edited` tinyint NOT NULL DEFAULT 0 COMMENT ''是否被人工编辑过：0 否 1 是'' AFTER `content`',
    'SELECT ''edited already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- >>> 增量: history/2026-09-20-bid-agent-task-running-unique.sql
-- ============================================================
-- 标书智能体：进行中任务唯一约束（P1-15 修复：任务重复提交竞态）
-- 日期：2026-09-20 ｜ 域：bidding_bid_agent_task
-- 背景：generate() 先查后插判重（TOCTOU），并发双发可双双通过；@Idempotent 仅覆盖 1 秒同参数窗口。
-- 口径：running_key 生成列——进行中（PENDING/RUNNING）时 = tenant_id:project_id:bid_section_code，
--       终态时 = NULL（唯一索引不约束 NULL），由 DB 最终拦截并发重复任务。
-- 幂等：先查 information_schema 再 ALTER。
-- ============================================================

SET NAMES utf8mb4;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_task' AND column_name = 'running_key');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_task` ADD COLUMN `running_key` varchar(160) GENERATED ALWAYS AS (IF(`status` IN (''PENDING'',''RUNNING''), CONCAT(`tenant_id`, '':'', `project_id`, '':'', `bid_section_code`), NULL)) STORED COMMENT ''进行中任务的租户:项目:标段（终态为 NULL，供唯一索引防并发重复）'' AFTER `status`', 'SELECT ''task.running_key exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_task' AND index_name = 'uk_running');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_task` ADD UNIQUE KEY `uk_running` (`running_key`)', 'SELECT ''uk_running exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- >>> 增量: history/2026-09-20-bid-agent-unique-key-deleted.sql
-- ============================================================
-- 标书智能体：唯一键补 deleted（P0-2 修复）
-- 日期：2026-09-20 ｜ 域：bidding_bid_agent_*
-- 背景：原唯一键不含 deleted，逻辑删除行与重建行互斥 →「取消再勾选/删除后重建」报 DuplicateKeyException。
-- 规范依据：《招投标模块开发规范》§六.1：T 轨 UNIQUE (业务编号, deleted, tenant_id)。
-- 幂等：先按 information_schema 判断索引是否已含 deleted 列再 ALTER；清理语句重复执行无害。
-- 配套代码改动：
--   1) 绑定表删除改物理删除（BidAgentProfileSkillMapper / BidAgentProfileMcpMapper）；
--   2) 技能/MCP/模型新增前物理清理同名已删行（purgeDeletedByCode / purgeDeletedByName），
--      避免「删除→重建→再删除」撞唯一键。
-- ============================================================

SET NAMES utf8mb4;

-- 0. 清理历史逻辑删除重复行（同业务键多条 deleted=1 时保留最新一条），保证新唯一键可创建
DELETE d FROM `bidding_bid_agent_skill` d
JOIN `bidding_bid_agent_skill` k
  ON k.tenant_id = d.tenant_id AND k.code = d.code AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

DELETE d FROM `bidding_bid_agent_mcp_server` d
JOIN `bidding_bid_agent_mcp_server` k
  ON k.tenant_id = d.tenant_id AND k.name = d.name AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

DELETE d FROM `bidding_bid_agent_model` d
JOIN `bidding_bid_agent_model` k
  ON k.tenant_id = d.tenant_id AND k.name = d.name AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

DELETE d FROM `bidding_bid_agent_profile_skill` d
JOIN `bidding_bid_agent_profile_skill` k
  ON k.tenant_id = d.tenant_id AND k.profile_id = d.profile_id AND k.skill_id = d.skill_id AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

DELETE d FROM `bidding_bid_agent_profile_mcp` d
JOIN `bidding_bid_agent_profile_mcp` k
  ON k.tenant_id = d.tenant_id AND k.profile_id = d.profile_id AND k.mcp_server_id = d.mcp_server_id AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

-- 1. 唯一键重建（幂等：仅当索引不含 deleted 列时执行）
SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_model' AND index_name = 'uk_tenant_name' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_model` DROP INDEX `uk_tenant_name`, ADD UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`, `deleted`)', 'SELECT ''model uk_tenant_name has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_skill' AND index_name = 'uk_tenant_code' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_skill` DROP INDEX `uk_tenant_code`, ADD UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`, `deleted`)', 'SELECT ''skill uk_tenant_code has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_mcp_server' AND index_name = 'uk_tenant_name' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_mcp_server` DROP INDEX `uk_tenant_name`, ADD UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`, `deleted`)', 'SELECT ''mcp uk_tenant_name has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_profile_skill' AND index_name = 'uk_profile_skill' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_profile_skill` DROP INDEX `uk_profile_skill`, ADD UNIQUE KEY `uk_profile_skill` (`profile_id`, `skill_id`, `deleted`, `tenant_id`)', 'SELECT ''profile_skill uk has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_profile_mcp' AND index_name = 'uk_profile_mcp' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_profile_mcp` DROP INDEX `uk_profile_mcp`, ADD UNIQUE KEY `uk_profile_mcp` (`profile_id`, `mcp_server_id`, `deleted`, `tenant_id`)', 'SELECT ''profile_mcp uk has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- >>> 增量: history/2026-09-20-bid-agent-chunk-material-id.sql
-- ============================================================
-- 标书智能体：切片表补 material_id（P1-3 修复：同名材料互相误删）
-- 日期：2026-09-20 ｜ 域：bidding_bid_agent_chunk
-- 背景：材料删除/重建按 source_doc_name 软关联清理，同名文件（如两个标段的「招标文件.pdf」）会互相误删切片与向量。
-- 口径：材料侧入库/清理/分页全部改为按 material_id 精确级联；契约端（按文档名入库）切片 material_id 为空。
-- 幂等：列/索引均先查 information_schema 再执行；回填语句重复执行无害（只填 material_id IS NULL 的行）。
-- 回填口径：按 (tenant_id, source_doc_name) 关联未删除的材料；历史上同名多材料无法区分，取最小 id（尽力而为）。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 补列（幂等）
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_chunk' AND column_name = 'material_id');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_chunk` ADD COLUMN `material_id` bigint DEFAULT NULL COMMENT ''材料编号（bidding_bid_material.id；按材料精确级联清理/重建，契约端按名入库时为空）'' AFTER `project_id`', 'SELECT ''chunk.material_id exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 索引（幂等）
SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_chunk' AND index_name = 'idx_material');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_chunk` ADD KEY `idx_material` (`tenant_id`, `material_id`)', 'SELECT ''idx_material exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 回填（按名关联未删除材料，取最小 id；尽力而为，历史同名多材料无法精确区分）
--    两表 name/source_doc_name 列的 collation 可能不一致（unicode_ci vs 0900_ai_ci），显式 COLLATE 到 chunk 侧口径
UPDATE `bidding_bid_agent_chunk` c
JOIN `bidding_bid_material` m
  ON m.tenant_id = c.tenant_id AND m.name = c.source_doc_name COLLATE utf8mb4_0900_ai_ci AND m.deleted = 0
SET c.material_id = m.id
WHERE c.material_id IS NULL;

-- >>> 增量: history/2026-09-20-bid-agent-task-doc-category.sql
-- ============================================================
-- 标书智能体：任务台账补 doc_category 列（P1-6 修复：标书类型选择此前无任何作用）
-- 日期：2026-09-20 ｜ 域：bidding_bid_agent_task
-- 背景：前台工坊「技术标书/商务标书」选择只驱动按钮高亮，未落库、未透传编排侧。
-- 口径：落台账 + 透传底座 payload，编排侧按类型组织章节大纲。
-- 幂等：先查 information_schema 再 ALTER。
-- ============================================================

SET NAMES utf8mb4;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_task' AND column_name = 'doc_category');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_task` ADD COLUMN `doc_category` varchar(16) DEFAULT NULL COMMENT ''标书类型：technical 技术标书 / business 商务标书（前台工坊选择，透传编排侧）'' AFTER `bid_section_code`', 'SELECT ''task.doc_category exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- >>> 增量: history/2026-09-21-bid-agent-task-ghost-cancel.sql
-- 标书智能体：清理「运行态幽灵行」（2026-09-21 线上问题修复）
-- 背景：历史版本删除运行中任务时只做逻辑删除（deleted=1），未先落 CANCELED 终态。
-- 逻辑删除后 PENDING/RUNNING 行仍在 running_key 生成列上占位（生成列只看 status），
-- 导致同租户+项目+标段的一键生成被唯一索引 uk_running 永久拦截；
-- 而分页/详情查询自动过滤 deleted=1，用户既看不到也取消不了这条任务。
-- 修复：把已逻辑删除的运行态任务统一转为 CANCELED（running_key 置 NULL、释放槽位）；
-- 代码侧 BidAgentTaskServiceImpl#delete 已同步修正（先落取消终态再逻辑删除）。
UPDATE `bidding_bid_agent_task`
SET `status` = 'CANCELED',
    `finished_at` = NOW()
WHERE `status` IN ('PENDING', 'RUNNING')
  AND `deleted` = 1;

-- >>> 增量: history/2026-09-28-bid-agent-bm25.sql
-- ============================================================
-- 标书智能体：检索关键词通道（BM25 / MySQL FULLTEXT + ngram）
-- 日期：2026-09-28 ｜ 域：bidding_bid_agent_chunk
-- 规范依据：《招投标模块开发规范》§4.8（SQL 幂等、无破坏性语句）；文档 24 §5 待办 1
-- 业务目标：
--   现有检索只有「向量 + rerank」一条腿，遇到条款编号、证书编号、金额、工期这类
--   精确词/数字查询容易漏召回（embedding 对数字与专有名词不敏感）。
--   本脚本给切片表补一个中文 ngram 全文索引，供 BidRetrievalServiceImpl 的关键词通道
--   用 MATCH ... AGAINST 做粗召回，再与向量候选做 RRF 融合。
-- 说明：
--   1) 只加索引，不改列、不动现有索引，切片数据无需重写；
--   2) 用 ngram 解析器（中文无空格，standard 解析器按单字切分不可用）。
--      实例 ngram_token_size=2（已实测），运行期不可改；如需修改必须重建本索引；
--   3) 顺序依赖：本脚本必须晚于 2026-09-27-bid-agent-runtime.sql（切片表在该脚本创建），
--      故文件日期取 2026-09-28。
-- 幂等：先查 information_schema 判断索引/表是否存在，存在即跳过；本文件不含 DROP / TRUNCATE / DELETE。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 切片表补中文全文索引 ft_chunk_content（幂等：已存在则跳过）
-- ------------------------------------------------------------
SET @table_exists := (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'bidding_bid_agent_chunk'
);
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bidding_bid_agent_chunk'
      AND index_name = 'ft_chunk_content'
);
SET @ddl := IF(@table_exists = 0,
    'SELECT ''bidding_bid_agent_chunk not exists, skip bm25 index'' AS skipped',
    IF(@index_exists > 0,
        'SELECT ''ft_chunk_content already exists'' AS skipped',
        'ALTER TABLE `bidding_bid_agent_chunk` ADD FULLTEXT INDEX `ft_chunk_content` (`content`) WITH PARSER ngram'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- >>> 增量: history/2026-09-28-bid-agent-all-editable.sql
-- ============================================================
-- 标书智能体：PARSE/OUTLINE/DRAFT/FILL 四类生成智能体改为后台可编辑
-- 日期：2026-09-28 ｜ 域：bidding_bid_agent
-- 规范依据：《招投标模块开发规范》§4.8（SQL 幂等、无破坏性语句）
-- 业务目标：提示词统一以后台（bidding_bid_agent）为唯一事实源——五类智能体全部后台可编辑，
--           底座（bid-agent-service）按 task_type 拉取运行配置即生效，内置提示词仅作拉取失败兜底。
-- 背景：原设计 editable=0（Agent 侧内置生效、后台只读比对）是为避免"两处提示词打架"；
--       实际运行链路（BidAgentRuntimeServiceImpl.buildRuntime）本就无条件下发后台 systemPrompt，
--       editable=0 只挡了保存接口与前端只读态，没有保护实际生效的提示词，故放开为后台可编辑。
-- 幂等：UPDATE 天然幂等，可重复执行；本文件不含 DROP / TRUNCATE / DELETE。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 四类生成智能体放开后台编辑（平台默认行 + 已存在的租户自建行一并放开）
--    remark 同步更新为当前口径，避免管理页残留旧文案
-- ------------------------------------------------------------
UPDATE `bidding_bid_agent`
SET `editable` = 1,
    `remark`   = '后台可编辑，保存后前台编排下次读取即生效'
WHERE `task_type` IN ('PARSE', 'OUTLINE', 'DRAFT', 'FILL')
  AND `deleted` = b'0';
