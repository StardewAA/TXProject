-- ============================================================
-- 标书智能体（A：后台知识库管理 + 智能体管理）
-- 日期：2026-09-26 ｜ 域：bidding_bid_* ｜ 菜单段：6790~6799
-- 规范依据：《招投标模块开发规范》§1.2（文件摆放）、§4.4（菜单/权限码与 sql 同步）、
--           §4.7（错误码段位先登记再用）、§4.8（SQL：幂等、无破坏性语句）
-- 业务目标：给招投标专员一个"管资料 → 验召回 → 调智能体"的调优台，
--           并把前台（标书写作/标书审查）要用的接口在 A 侧冻结，供其对接。
-- 幂等：结构 IF NOT EXISTS、数据 INSERT IGNORE；本文件不含 DROP / TRUNCATE / DELETE / ALTER。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 材料台账：一页材料 = 一条可管理、可入库、可溯源的知识条目
--    doc_type：TENDER 招标文件 / FACT 企业资料 / TEMPLATE 技术母本
--    绑定口径（用户 2026-09-15 确认）：TENDER 必须绑标段；FACT/TEMPLATE 可跨标段复用（可空）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 2. 片段表：切片是召回质量的第一现场，必须可查看、可编辑、可重建
--    编辑结果通过「重建索引」生效（底座按自身窗口重切，见接口契约 §4）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 3. 智能体配置：后台调优前台体验的唯一入口
--    task_type 对应五类任务；REVIEW 提示词由招标侧审查编排直接读取并即时生效；
--    PARSE/OUTLINE/DRAFT/FILL 由 Agent 侧内置生效（editable=0，后台只读可比对），避免两处提示词打架
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_agent` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `task_type` varchar(16) NOT NULL COMMENT '任务类型：PARSE/OUTLINE/DRAFT/FILL/REVIEW',
    `name` varchar(64) NOT NULL COMMENT '智能体名称（专员可见）',
    `description` varchar(255) DEFAULT NULL COMMENT '一句话说明：这个智能体负责什么',
    `system_prompt` text COMMENT '系统提示词',
    `model` varchar(64) DEFAULT NULL COMMENT '模型名（空=用平台默认）',
    `temperature` decimal(3,2) DEFAULT NULL COMMENT '温度（空=用平台默认）',
    `knowledge_roles` varchar(64) DEFAULT NULL COMMENT '默认挂载的知识库角色，逗号分隔，如 TENDER,FACT',
    `editable` tinyint NOT NULL DEFAULT '1' COMMENT '是否后台可编辑：1 可编辑 0 只读（Agent 侧内置生效）',
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

-- ------------------------------------------------------------
-- 4. 精准度测试用例（golden set 的存储；标注数据由指标负责人提供，A 负责存储与跑批）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 5. 菜单与权限（段 6790~6799；父菜单 6735「招投标」）
--    授权口径：不写 system_role_menu —— 后台两页仅「招投标专员」可见，由管理员在
--    角色界面勾选（super_admin 自动可见）。这样普通用户不会被误授权看到后台页。
-- ------------------------------------------------------------
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
    (6790, '标书智能体', '', 1, 40, 6735, 'bid-agent', 'ep:magic-stick', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6791, '知识库管理', 'bidding:bid-agent:query', 2, 1, 6790, 'knowledge', 'ep:collection', 'bidding/bidAgent/knowledge/index', 'BiddingBidKnowledge', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6792, '智能体管理', 'bidding:bid-agent:query', 2, 2, 6790, 'agent', 'ep:cpu', 'bidding/bidAgent/agent/index', 'BiddingBidAgentConfig', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6793, '标书智能体查询', 'bidding:bid-agent:query', 3, 1, 6791, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6794, '材料登记', 'bidding:bid-agent:create', 3, 2, 6791, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6795, '材料与智能体维护', 'bidding:bid-agent:update', 3, 3, 6791, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6796, '材料删除', 'bidding:bid-agent:delete', 3, 4, 6791, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6797, '召回验证', 'bidding:bid-agent:retrieve', 3, 5, 6791, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6798, '精准度测试', 'bidding:bid-agent:eval', 3, 6, 6791, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (6799, '智能体试跑', 'bidding:bid-agent:preview', 3, 7, 6792, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ------------------------------------------------------------
-- 6. 预置五类智能体（提示词基线）
--    REVIEW 是后台可编辑并即时生效项；其余四类保留基线文本供专员比对（Agent 侧内置生效）
-- ------------------------------------------------------------
INSERT IGNORE INTO `bidding_bid_agent`
    (`task_type`, `name`, `description`, `system_prompt`, `knowledge_roles`, `editable`, `sort`, `status`, `remark`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    ('PARSE', '招标文件解析', '读招标文件，抽出废标条款、资格要求、评分点与全局事实',
     '你是招投标业务专家。只依据给定材料文本抽取，不允许推断或补全；抽不到的字段留空并标注"未找到"。输出必须是严格 JSON。',
     'TENDER', 0, 10, 0, '提示词由 Agent 侧内置生效，后台可查看比对', 0, '1', NOW(), '1', NOW(), b'0'),
    ('OUTLINE', '标书大纲', '按评分点与废标条款生成章节树，逐条承接',
     '你是标书编制专家。章节树必须逐条承接招标文件的评分点与废标条款，每条都要标明承接来源。',
     'TENDER', 0, 20, 0, '提示词由 Agent 侧内置生效，后台可查看比对', 0, '1', NOW(), '1', NOW(), b'0'),
    ('DRAFT', '标书撰写', '按章节撰写正文，每段附证据引用，数字不得编造',
     '你是标书撰写专家。只写有证据支撑的内容；每个数字都必须能在证据或项目事实卡中找到来源，找不到就写"待补充"。',
     'TENDER,FACT,TEMPLATE', 0, 30, 0, '提示词由 Agent 侧内置生效，后台可查看比对', 0, '1', NOW(), '1', NOW(), b'0'),
    ('FILL', '模板回填', '把事实类字段确定性回填到招标方模板',
     '你只处理必须组织语言的字段；证书编号、金额、日期等事实字段一律不由你输出。',
     'FACT,TEMPLATE', 0, 40, 0, '提示词由 Agent 侧内置生效，后台可查看比对', 0, '1', NOW(), '1', NOW(), b'0'),
    ('REVIEW', '标书审查', '逐条比对招标文件要求与投标文件，输出带定位的风险清单',
     '你是投标文件合规审查专家。逐条判断投标文件是否满足招标文件的强制要求（废标条款、资格要求、必检项）。\n'
     '判定口径：\n'
     '1) 投标文件中有明确、可直接对应的响应内容 → PASS（符合）；\n'
     '2) 有相关表述但口径不一致、不完整或需人工确认 → DOUBT（存疑）；\n'
     '3) 投标文件明确不满足或与要求冲突 → FAIL（不符）；\n'
     '4) 投标文件中找不到任何对应内容 → MISS（未覆盖）。\n'
     '严格要求：只依据提供的投标文件证据判定，不得推测投标文件里没有的内容；证据必须逐字摘自原文。输出必须是严格 JSON。',
     'TENDER', 1, 50, 0, '后台可编辑，审查编排即时读取生效', 0, '1', NOW(), '1', NOW(), b'0');
