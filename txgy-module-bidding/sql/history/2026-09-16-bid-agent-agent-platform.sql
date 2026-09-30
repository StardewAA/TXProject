-- ============================================================
-- 标书智能体：自有云端模型 + 技能/MCP/工具 + 一键生成标书（表/菜单/种子）
-- 日期：2026-09-16 ｜ 域：bidding_bid_agent_*
-- 规范依据：《招投标模块开发规范》§4.4（菜单/权限码与 sql 同步）、§4.7（错误码段位先登记）、§4.8（SQL 幂等）
-- 业务目标：
--   1) 智能体管理脱离平台 AI 模块：本模块自持一套云端大模型配置（全局一套 + 每智能体可覆盖）；
--   2) 智能体可挂载「技能（提示词增强包）/ MCP 服务 / 内置工具白名单」；
--   3) 一键生成标书：任务台账（前台/后台都可发起与查看）。
-- 幂等：DDL 走 information_schema 判断 + PREPARE，数据走 INSERT IGNORE；本文件不含 DROP / TRUNCATE / DELETE。
-- 说明：雪花主键表（智能体域）不走自增，种子数据必须显式给 id。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 智能体配置扩展：工具白名单 / 每智能体 max-tokens / 技能自动挂载开关
-- ------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent' AND column_name = 'tool_whitelist');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent` ADD COLUMN `tool_whitelist` varchar(255) DEFAULT NULL COMMENT ''内置工具白名单（逗号分隔，空=全部允许）'' AFTER `knowledge_roles`', 'SELECT ''tool_whitelist exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent' AND column_name = 'max_tokens');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent` ADD COLUMN `max_tokens` int DEFAULT NULL COMMENT ''覆盖全局的单条回复最大 token（空=用全局）'' AFTER `temperature`', 'SELECT ''max_tokens exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent' AND column_name = 'skill_auto_attach');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent` ADD COLUMN `skill_auto_attach` tinyint NOT NULL DEFAULT 1 COMMENT ''是否自动挂载推荐技能（1 是 0 否）'' AFTER `editable`', 'SELECT ''skill_auto_attach exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 项目表补章节树列（一键生成的 outline 回写）
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_project' AND column_name = 'outline_json');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_project` ADD COLUMN `outline_json` longtext COMMENT ''标书章节树（Node 编排回写，JSON）'' AFTER `tender_parse_json`', 'SELECT ''outline_json exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 全局云端模型配置（全局一套；每智能体可覆盖 model/temperature/max_tokens）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 3. 技能（提示词增强包，可自动挂载）+ 智能体关联
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 4. MCP 服务 + 智能体关联
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 5. 一键生成标书任务台账（前台/后台共用）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 6. 菜单与权限码（占号 6800~6814；super_admin 走旁路，普通角色由管理员在角色界面勾选）
-- ------------------------------------------------------------
INSERT IGNORE INTO `system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    (6800, '标书生成', 'bidding:bid-agent:generate:query', 2, 41, 6735, 'bidGenerate', 'ep:magic-stick',
     'bidding/bidAgent/generate/index', 'BiddingBidGenerate', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6801, '技能管理', 'bidding:bid-agent:skill:query', 2, 3, 6790, 'skills', 'ep:notebook',
     'bidding/bidAgent/skill/index', 'BiddingBidSkill', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6802, 'MCP 服务', 'bidding:bid-agent:mcp:query', 2, 4, 6790, 'mcp', 'ep:connection',
     'bidding/bidAgent/mcp/index', 'BiddingBidMcp', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6803, '生成任务查询', 'bidding:bid-agent:generate:query', 3, 1, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6804, '发起一键生成', 'bidding:bid-agent:generate:create', 3, 2, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6805, '取消生成任务', 'bidding:bid-agent:generate:cancel', 3, 3, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6806, '技能查询', 'bidding:bid-agent:skill:query', 3, 1, 6801, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6807, '技能新增', 'bidding:bid-agent:skill:create', 3, 2, 6801, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6808, '技能修改', 'bidding:bid-agent:skill:update', 3, 3, 6801, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6809, '技能删除', 'bidding:bid-agent:skill:delete', 3, 4, 6801, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6810, 'MCP 查询', 'bidding:bid-agent:mcp:query', 3, 1, 6802, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6811, 'MCP 新增', 'bidding:bid-agent:mcp:create', 3, 2, 6802, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6812, 'MCP 修改', 'bidding:bid-agent:mcp:update', 3, 3, 6802, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6813, 'MCP 删除', 'bidding:bid-agent:mcp:delete', 3, 4, 6802, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6814, 'MCP 自检与试调', 'bidding:bid-agent:mcp:test', 3, 5, 6802, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ------------------------------------------------------------
-- 7. 平台预置技能（8 条，auto_attach=1；显式雪花段 id）
-- ------------------------------------------------------------
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
