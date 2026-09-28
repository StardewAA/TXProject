-- =====================================================================
-- 智慧标书审查（前台智慧标书审查）建表 + 字典 + 菜单权限
-- 方案依据：docs/模块设计与规范/智慧标书审查-设计方案.md（V2.0 · DeepSeek 单通道）
-- 规范依据：《招投标模块开发规范》§4.5（幂等/乐观锁）、§4.7（错误码段 1-060-110）、
--           §4.8（密级/脱敏）、§5（建表：T 轨 TenantBaseDO 列、文件只存 file_id、
--           内容型文本版本化、索引命名 uk_/idx_）
-- 设计说明：
--   1) 7 张表：task（主表）/ rule_set / rule_item / issue / report / content / ai_log
--   2) 全部 T 轨（含 tenant_id），由 TenantDatabaseInterceptor 自动隔离
--   3) 文件不入库：只落 file_id + sha256 + size（红线 8）
--   4) 标书正文 / AI 原始返回 / 报告 Markdown 落 content 表，追加式版本化，不覆盖历史
--   5) ai_log 为只追加流水，保留 6 个月（归档任务见方案 §6.4）
--   6) 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，本文件可重复执行
-- 执行库：ruoyi-vue-pro
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1) 审查任务主表
--    一次上传 = 一个任务；承载状态机、进度、结论与归属人
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_task` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `task_no` varchar(64) NOT NULL COMMENT '任务编号（业务唯一键，形如 BR20260916000001）',
    `task_name` varchar(128) NOT NULL COMMENT '任务名称（默认取标书文件名）',
    `source_scene` varchar(16) NOT NULL COMMENT '来源场景：ADMIN_TASK 管理端建单 / PORTAL_SELF 个人端自助',
    `owner_user_id` bigint NOT NULL COMMENT '归属人（个人端即经办人本人；后端强制本人可见）',
    `owner_name` varchar(64) DEFAULT '' COMMENT '归属人名称快照',
    `project_id` bigint DEFAULT NULL COMMENT '关联投标项目 id（可空）',
    `tender_project_id` bigint DEFAULT NULL COMMENT '关联招标项目 id（可空）',
    `rule_set_id` bigint DEFAULT NULL COMMENT '采用的规则集 id',
    `tender_file_id` bigint DEFAULT NULL COMMENT '招标文件 file_id（可空：不传则只用平台规则库）',
    `tender_file_url` varchar(512) DEFAULT '' COMMENT '招标文件访问地址快照（仅作解析取流定位；一致性以 file_id + sha256 为准）',
    `tender_file_name` varchar(255) DEFAULT '' COMMENT '招标文件名快照',
    `tender_file_sha256` varchar(64) DEFAULT '' COMMENT '招标文件内容哈希（规则集复用依据）',
    `bid_file_id` bigint NOT NULL COMMENT '投标文件（标书）file_id',
    `bid_file_url` varchar(512) DEFAULT '' COMMENT '标书访问地址快照（仅作解析取流定位；一致性以 file_id + sha256 为准）',
    `bid_file_name` varchar(255) DEFAULT '' COMMENT '标书文件名快照',
    `bid_file_sha256` varchar(64) DEFAULT '' COMMENT '标书内容哈希（相同文件复用审查结果）',
    `parse_mode` varchar(16) DEFAULT '' COMMENT '解析方式：TEXT 文本层 / EMPTY 无文字层',
    `status` varchar(16) NOT NULL COMMENT '状态：QUEUED/RUNNING/SUCCESS/FAILED/CANCELED',
    `current_step` varchar(24) DEFAULT '' COMMENT '当前步骤：PARSE/RULE/GENERATE/REPORT',
    `progress` int NOT NULL DEFAULT 0 COMMENT '进度 0~100',
    `verdict` varchar(16) DEFAULT NULL COMMENT '结论：PASS/RISK/FAIL（仅 SUCCESS 时有值）',
    `score_total` decimal(6,2) DEFAULT NULL COMMENT '自评总分（AI 预估，报告需标注）',
    `score_json` json DEFAULT NULL COMMENT '分项得分（技术/商务/价格等）',
    `rule_cnt` int NOT NULL DEFAULT 0 COMMENT '参与比对的规则条数',
    `issue_cnt` int NOT NULL DEFAULT 0 COMMENT '问题条数',
    `high_issue_cnt` int NOT NULL DEFAULT 0 COMMENT '高危问题条数',
    `ai_called` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否发生过模型调用（扫描件失败时应为 0）',
    `error_code` varchar(32) DEFAULT '' COMMENT '失败错误码（1-060-110-xxx 对应常量名）',
    `error_msg` varchar(255) DEFAULT '' COMMENT '失败原因（面向用户的短文案）',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
    `started_time` datetime DEFAULT NULL COMMENT '开始执行时间',
    `finished_time` datetime DEFAULT NULL COMMENT '完成时间',
    `cost_ms` bigint DEFAULT NULL COMMENT '总耗时（毫秒）',
    `remark` varchar(255) DEFAULT '' COMMENT '备注（个人端可填，管理端可见）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（规范 §4.5）',
    `idempotent_key` varchar(64) DEFAULT '' COMMENT '幂等键（前端一次点击一个键）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（@TableLogic）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`, `deleted`, `tenant_id`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`, `deleted`, `tenant_id`),
    KEY `idx_owner_status` (`owner_user_id`, `status`, `deleted`, `tenant_id`),
    KEY `idx_tenant_create` (`tenant_id`, `create_time`),
    KEY `idx_bid_sha` (`bid_file_sha256`, `status`)
) COMMENT='智慧标书审查任务';

-- ---------------------------------------------------------------------
-- 2) 规则集（平台规则库模板 / 招标文件解析结果）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_rule_set` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `rule_set_code` varchar(64) NOT NULL COMMENT '规则集编码（业务唯一键）',
    `rule_set_name` varchar(128) NOT NULL COMMENT '规则集名称',
    `source` varchar(16) NOT NULL COMMENT '来源：PLATFORM 平台规则库 / TENDER_DOC 招标文件解析 / MIXED 混合',
    `tender_file_id` bigint DEFAULT NULL COMMENT '招标文件 file_id（source=TENDER_DOC 时必填）',
    `tender_file_sha256` varchar(64) DEFAULT '' COMMENT '招标文件哈希（同文件不重复解析）',
    `parse_status` varchar(16) DEFAULT 'SUCCESS' COMMENT '解析状态：SUCCESS/FAILED/PENDING',
    `rule_cnt` int NOT NULL DEFAULT 0 COMMENT '规则条数',
    `enable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `default_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否平台默认规则集（个人端未传招标文件时使用）',
    `remark` varchar(255) DEFAULT '' COMMENT '说明',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_set_code` (`rule_set_code`, `deleted`, `tenant_id`),
    KEY `idx_source_enable` (`source`, `enable`, `deleted`)
) COMMENT='智慧标书审查规则集';

-- ---------------------------------------------------------------------
-- 3) 规则项（废标项 / 格式要求 / 评分项）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_rule_item` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `rule_set_id` bigint NOT NULL COMMENT '规则集 id',
    `rule_code` varchar(64) NOT NULL COMMENT '规则编码（AI 输出必须命中白名单才入库）',
    `rule_name` varchar(128) NOT NULL COMMENT '规则名称（如"投标函是否盖章签字"）',
    `category` varchar(32) NOT NULL COMMENT '类别 code：FORM 形式评审 / QUALIFICATION 资格条件 / RESPONSE 响应性 / BUSINESS 商务 / TECH 技术 / PRICE 价格',
    `rule_type` varchar(16) NOT NULL COMMENT '类型 code：DISQUALIFY 废标项 / SCORING 评分项 / FORMAT 格式要求 / QUALIFICATION 资格要求',
    `check_mode` varchar(16) NOT NULL DEFAULT 'AI' COMMENT '检查方式 code：RULE 规则匹配 / AI 语义判断',
    `severity` varchar(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '严重级别 code：HIGH/MEDIUM/LOW',
    `score_max` decimal(6,2) DEFAULT NULL COMMENT '满分（rule_type=SCORING 时有值）',
    `requirement` text COMMENT '要求原文（来自招标文件或平台规则库）',
    `evidence_hint` varchar(500) DEFAULT '' COMMENT '检查点提示（告诉模型看哪里）',
    `source_ref` varchar(255) DEFAULT '' COMMENT '出处（招标文件条款号/页码）',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
    `enable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_code` (`rule_set_id`, `rule_code`, `deleted`, `tenant_id`),
    KEY `idx_set_enable` (`rule_set_id`, `enable`, `deleted`)
) COMMENT='智慧标书审查规则项';

-- ---------------------------------------------------------------------
-- 4) 问题项（核心出参；evidence_text 为原文片段，供人工复核定位）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_issue` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `task_id` bigint NOT NULL COMMENT '审查任务 id',
    `rule_item_id` bigint DEFAULT NULL COMMENT '命中规则项 id（平台规则库来源时有值）',
    `rule_code` varchar(64) DEFAULT '' COMMENT '规则编码',
    `category` varchar(32) NOT NULL COMMENT '类别 code（同 rule_item.category）',
    `severity` varchar(16) NOT NULL COMMENT '严重级别 code：HIGH/MEDIUM/LOW',
    `issue_title` varchar(255) NOT NULL COMMENT '问题标题',
    `issue_desc` text COMMENT '问题说明',
    `hit_location` varchar(128) DEFAULT '' COMMENT '命中位置（页码/章节/段落）',
    `evidence_text` text COMMENT '标书原文片段（证据；AI 无法定位时为空并降级为"证据不足"）',
    `evidence_lack` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否证据不足（true 时不计入高危）',
    `suggestion` text COMMENT '整改建议',
    `source` varchar(16) NOT NULL COMMENT '来源 code：AI 语义判断 / RULE 规则匹配 / MANUAL 人工补录',
    `ai_confidence` decimal(4,3) DEFAULT NULL COMMENT 'AI 置信度 0~1',
    `status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT '复核状态 code：OPEN 待复核 / CONFIRMED 已确认 / IGNORED 已忽略 / FIXED 已整改',
    `reviewer_id` bigint DEFAULT NULL COMMENT '复核人',
    `review_remark` varchar(500) DEFAULT '' COMMENT '复核意见',
    `review_time` datetime DEFAULT NULL COMMENT '复核时间',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_task_severity` (`task_id`, `severity`, `deleted`),
    KEY `idx_task_status` (`task_id`, `status`, `deleted`)
) COMMENT='智慧标书审查问题项';

-- ---------------------------------------------------------------------
-- 5) 审查报告
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_report` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `task_id` bigint NOT NULL COMMENT '审查任务 id',
    `report_no` varchar(64) NOT NULL COMMENT '报告编号（业务唯一键）',
    `verdict` varchar(16) NOT NULL COMMENT '结论 code：PASS/RISK/FAIL',
    `score_total` decimal(6,2) DEFAULT NULL COMMENT '自评总分（AI 预估）',
    `score_json` json DEFAULT NULL COMMENT '分项得分',
    `issue_summary_json` json DEFAULT NULL COMMENT '问题摘要（按类别/级别计数 + 高频问题 TOP）',
    `conclusion` text COMMENT '总体结论与建议（Markdown）',
    `html_content` mediumtext COMMENT '报告正文（HTML，供页面渲染与打印）',
    `pdf_file_id` bigint DEFAULT NULL COMMENT '报告 PDF file_id（导出后回填）',
    `docx_file_id` bigint DEFAULT NULL COMMENT '报告 Word file_id（导出后回填）',
    `docx_file_url` varchar(512) DEFAULT '' COMMENT '报告 Word 访问地址（生成时快照，便于直接下载/预览）',
    `template_code` varchar(32) DEFAULT 'DEFAULT' COMMENT '报告模板 code',
    `ai_generated` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否 AI 生成（页脚需标注"AI 生成，需人工复核"）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_no` (`report_no`, `deleted`, `tenant_id`),
    KEY `idx_task` (`task_id`, `deleted`)
) COMMENT='智慧标书审查报告';

-- ---------------------------------------------------------------------
-- 6) 版本化内容表（标书正文 / 结构化章节 / AI 原始返回 / 报告 Markdown）
--    规范 §5.3 第 2 款：内容每次变更追加新版本，旧版本只读
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_content` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；等同 content_id）',
    `biz_id` bigint NOT NULL COMMENT '业务记录 id（本域即 task_id）',
    `content_type` varchar(24) NOT NULL COMMENT '内容类型：BID_TEXT 标书正文 / BID_STRUCT 结构化章节 / TENDER_TEXT 招标文件正文 / AI_RAW AI 原始返回 / REPORT_MD 报告 Markdown',
    `content` mediumtext COMMENT '正文内容',
    `sha256` varchar(64) DEFAULT '' COMMENT '内容哈希（防篡改与去重）',
    `version` int NOT NULL DEFAULT 1 COMMENT '版本号（自增、只追加）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_content_version` (`biz_id`, `content_type`, `version`, `deleted`),
    KEY `idx_biz_type` (`biz_id`, `content_type`)
) COMMENT='智慧标书审查版本化内容';

-- ---------------------------------------------------------------------
-- 7) AI 调用流水（只追加；prompt 只留摘要哈希，不留全文）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_ai_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `task_id` bigint DEFAULT NULL COMMENT '审查任务 id',
    `step` varchar(24) DEFAULT '' COMMENT '步骤：RULE/GENERATE/REPORT/PROBE',
    `model` varchar(64) DEFAULT '' COMMENT '模型名（如 deepseek-chat）',
    `prompt_digest` varchar(64) DEFAULT '' COMMENT '提示词摘要哈希（不存全文）',
    `request_chars` int DEFAULT 0 COMMENT '请求字符数',
    `response_chars` int DEFAULT 0 COMMENT '响应字符数',
    `cost_ms` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
    `status` varchar(16) DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/DEGRADED/REUSE',
    `error_msg` varchar(255) DEFAULT '' COMMENT '失败原因（脱敏，不含提示词正文）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_task_step` (`task_id`, `step`),
    KEY `idx_tenant_create` (`tenant_id`, `create_time`)
) COMMENT='智慧标书审查 AI 调用流水';

-- ---------------------------------------------------------------------
-- 8) 字典注册（前端一律走 /bidding/meta/dicts 取枚举，禁止硬编码）
--    id 分块：7100000000000060xx ~ 7100000000000069xx（不复用既有区块）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- bid_review_status（任务状态）
(710000000000006001,0,'bid_review_status','QUEUED','排队中','info',b'0',1,b'1','已接收，等待执行','1',NOW(),'1',NOW(),b'0'),
(710000000000006002,0,'bid_review_status','RUNNING','审查中','processing',b'0',2,b'1','流水线执行中（前端按此轮询进度）','1',NOW(),'1',NOW(),b'0'),
(710000000000006003,0,'bid_review_status','SUCCESS','已完成','success',b'1',3,b'1','终态：跑完即终态，结论另看 verdict','1',NOW(),'1',NOW(),b'0'),
(710000000000006004,0,'bid_review_status','FAILED','已失败','error',b'1',4,b'1','终态：可重试（限次）','1',NOW(),'1',NOW(),b'0'),
(710000000000006005,0,'bid_review_status','CANCELED','已取消','default',b'1',5,b'1','终态：用户或管理端取消','1',NOW(),'1',NOW(),b'0'),
-- bid_review_step（流水线步骤）
(710000000000006101,0,'bid_review_step','PARSE','解析文件','info',b'0',1,b'1','抽取标书文本与表格','1',NOW(),'1',NOW(),b'0'),
(710000000000006102,0,'bid_review_step','RULE','准备审查规则','info',b'0',2,b'1','招标文件抽规则或取平台规则库','1',NOW(),'1',NOW(),b'0'),
(710000000000006103,0,'bid_review_step','GENERATE','逐条审查','processing',b'0',3,b'1','规则×标书逐条比对，产出问题清单','1',NOW(),'1',NOW(),b'0'),
(710000000000006104,0,'bid_review_step','REPORT','生成报告','processing',b'0',4,b'1','汇总得分、结论与整改建议','1',NOW(),'1',NOW(),b'0'),
-- bid_review_scene（来源场景）
(710000000000006201,0,'bid_review_scene','PORTAL_SELF','个人端自助','info',b'0',1,b'1','门户上传 + 一键生成','1',NOW(),'1',NOW(),b'0'),
(710000000000006202,0,'bid_review_scene','ADMIN_TASK','管理端建单','default',b'0',2,b'1','管理端指定招标文件与标书','1',NOW(),'1',NOW(),b'0'),
-- bid_review_verdict（审查结论）
(710000000000006301,0,'bid_review_verdict','PASS','通过','success',b'1',1,b'1','未命中问题项','1',NOW(),'1',NOW(),b'0'),
(710000000000006302,0,'bid_review_verdict','RISK','存在风险','warning',b'1',2,b'1','命中中/低风险问题','1',NOW(),'1',NOW(),b'0'),
(710000000000006303,0,'bid_review_verdict','FAIL','不通过','error',b'1',3,b'1','命中高危废标项，需整改后重投','1',NOW(),'1',NOW(),b'0'),
-- bid_review_rule_category（规则类别）
(710000000000006401,0,'bid_review_rule_category','FORM','形式评审','info',b'0',1,b'1','投标函、签章、格式等','1',NOW(),'1',NOW(),b'0'),
(710000000000006402,0,'bid_review_rule_category','QUALIFICATION','资格条件','info',b'0',2,b'1','资质、业绩、人员等门槛','1',NOW(),'1',NOW(),b'0'),
(710000000000006403,0,'bid_review_rule_category','RESPONSE','响应性评审','warning',b'0',3,b'1','是否实质响应招标要求','1',NOW(),'1',NOW(),b'0'),
(710000000000006404,0,'bid_review_rule_category','BUSINESS','商务评审','default',b'0',4,b'1','商务分相关','1',NOW(),'1',NOW(),b'0'),
(710000000000006405,0,'bid_review_rule_category','TECH','技术评审','default',b'0',5,b'1','技术方案等','1',NOW(),'1',NOW(),b'0'),
(710000000000006406,0,'bid_review_rule_category','PRICE','价格评审','default',b'0',6,b'1','报价相关','1',NOW(),'1',NOW(),b'0'),
-- bid_review_rule_type（规则类型）
(710000000000006501,0,'bid_review_rule_type','DISQUALIFY','废标项','error',b'0',1,b'1','命中即为不通过','1',NOW(),'1',NOW(),b'0'),
(710000000000006502,0,'bid_review_rule_type','QUALIFICATION','资格要求','warning',b'0',2,b'1','资格门槛类要求','1',NOW(),'1',NOW(),b'0'),
(710000000000006503,0,'bid_review_rule_type','FORMAT','格式要求','info',b'0',3,b'1','格式与签章要求','1',NOW(),'1',NOW(),b'0'),
(710000000000006504,0,'bid_review_rule_type','SCORING','评分项','success',b'0',4,b'1','影响自评得分','1',NOW(),'1',NOW(),b'0'),
-- bid_review_check_mode（检查方式）
(710000000000006601,0,'bid_review_check_mode','RULE','规则匹配','info',b'0',1,b'1','关键词/正则/字段存在性等确定性检查','1',NOW(),'1',NOW(),b'0'),
(710000000000006602,0,'bid_review_check_mode','AI','AI 语义判断','processing',b'0',2,b'1','语义类要求交给大模型判断','1',NOW(),'1',NOW(),b'0'),
-- bid_review_severity（严重级别）
(710000000000006701,0,'bid_review_severity','HIGH','高危','error',b'0',1,b'1','多为废标项，必须整改','1',NOW(),'1',NOW(),b'0'),
(710000000000006702,0,'bid_review_severity','MEDIUM','中危','warning',b'0',2,b'1','影响得分或存在争议','1',NOW(),'1',NOW(),b'0'),
(710000000000006703,0,'bid_review_severity','LOW','低危','default',b'0',3,b'1','建议优化','1',NOW(),'1',NOW(),b'0'),
-- bid_review_issue_status（问题项复核状态）
(710000000000006801,0,'bid_review_issue_status','OPEN','待复核','info',b'0',1,b'1','AI/规则产出后默认状态','1',NOW(),'1',NOW(),b'0'),
(710000000000006802,0,'bid_review_issue_status','CONFIRMED','已确认','error',b'0',2,b'1','复核人确认是问题','1',NOW(),'1',NOW(),b'0'),
(710000000000006803,0,'bid_review_issue_status','IGNORED','已忽略','default',b'0',3,b'1','复核人判定为误报','1',NOW(),'1',NOW(),b'0'),
(710000000000006804,0,'bid_review_issue_status','FIXED','已整改','success',b'0',4,b'1','标书已修改，等待复核','1',NOW(),'1',NOW(),b'0'),
-- bid_review_issue_source（问题来源）
(710000000000006901,0,'bid_review_issue_source','AI','AI 判断','processing',b'0',1,b'1','语义类问题','1',NOW(),'1',NOW(),b'0'),
(710000000000006902,0,'bid_review_issue_source','RULE','规则匹配','info',b'0',2,b'1','确定性规则命中','1',NOW(),'1',NOW(),b'0'),
(710000000000006903,0,'bid_review_issue_source','MANUAL','人工补录','default',b'0',3,b'1','复核时人工添加','1',NOW(),'1',NOW(),b'0');
