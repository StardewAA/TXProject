-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- ============================================================================
-- bidding 模块业务表 DDL（从开发库 ruoyi-vue-pro 导出，仅结构，不含数据/不加 DROP）
-- 来源：Docker MySQL txgy-mysql-1 / ruoyi-vue-pro；导出：2026-09-08
-- 范围：16 张 bidding_* 表；列注释已还原为中文（开发库内原注释存在 cp1252 双重编码，
--       本文件已做反转修复；如需同步修复库内注释可用下方“修复库注释”说明）。
-- 幂等提示：本文件只含 CREATE TABLE（不带 IF NOT EXISTS / DROP）。
--       全新环境直接执行；对已存在的库重复执行会报“表已存在”，需先手动 DROP。
-- ============================================================================
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_crawl_job` (
  `id` bigint NOT NULL COMMENT '主键',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `source_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '源 code（空=全源）',
  `trigger_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SCHEDULE' COMMENT 'SCHEDULE/MANUAL/REPAIR',
  `scope` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '范围说明（如按时间窗口）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED',
  `summary_json` json DEFAULT NULL COMMENT '汇总：总数/成功/失败/转人工数',
  `finished_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_job_status` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集批次（G 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_crawl_source` (
  `id` bigint NOT NULL COMMENT '主键',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `source_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '源 code（唯一）',
  `source_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '源名称',
  `base_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '站点域名/入口',
  `allowed_hosts` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????(??????:?? CDN/??????????)',
  `entry_urls_json` json DEFAULT NULL COMMENT '入口/栏目 URL 列表',
  `channel_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '下载通道适配器 code（通用或平台专属）',
  `parser_code_default` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认解析/策略码（URL 未单独审批时用）',
  `fetch_interval_seconds` int NOT NULL DEFAULT '3600' COMMENT '抓取间隔（秒，频控）',
  `robots_ok` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否允许按 robots 抓取',
  `enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '启用开关',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_code` (`source_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集源配置（G 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_crawl_task` (
  `id` bigint NOT NULL COMMENT '主键',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `job_id` bigint DEFAULT NULL COMMENT '所属批次（bidding_crawl_job.id）',
  `source_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '源 code',
  `url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '待抓 URL',
  `url_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'URL SHA-256（去重）',
  `channel_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '通道适配器 code',
  `parser_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '解析/策略 code（URL 审批绑定或源默认）',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级（小=高）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
  `attempt` int NOT NULL DEFAULT '0' COMMENT '已尝试次数（MQ 侧 ≤3）',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间（指数退避）',
  `worker_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行节点标识（分布式抢占）',
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结果/错误说明',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_job_url` (`job_id`,`url_hash`,`source_code`,`deleted`),
  KEY `idx_task_status_retry` (`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集任务（G 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_filing` (
  `id` bigint NOT NULL COMMENT '??(?? ID)',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '????',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '????',
  `filing_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????(????)',
  `project_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????(????)',
  `tender_project_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????',
  `unified_deal_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '???????',
  `tender_mode` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '????(??)',
  `org_form` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '????(??)',
  `supervise_dept_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????',
  `supervise_dept_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????',
  `submit_time` datetime DEFAULT NULL COMMENT '????',
  `data_timestamp` datetime DEFAULT NULL COMMENT 'data_timestamp(05 ????,????)',
  `extra_json` json DEFAULT NULL COMMENT '??/????(??? file_id ??)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED(?????????)',
  `receipt_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '?????(?????,??)',
  `receipt_time` datetime DEFAULT NULL COMMENT '??(??)??',
  `opinion` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_filing_power` (`filing_type`,`project_code`,`deleted`,`tenant_id`),
  UNIQUE KEY `uk_filing_receipt` (`receipt_no`),
  KEY `idx_filing_status_time` (`status`,`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='??????(T ?)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_follow_item` (
  `id` bigint NOT NULL COMMENT '主键',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号（T 轨，拦截器维护）',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `follow_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PROJECT/TENDER_PROJECT/SECTION',
  `follow_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维度代码（project_code/tender_project_code/bid_section_code）',
  `display_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目/标段名快照',
  `issuer_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布单位快照',
  `stage_code` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'UNKNOWN' COMMENT '进展阶段：PENDING/TENDER/OPEN_BID/EVAL/RESULT/CONTRACT/DONE/UNKNOWN',
  `stage_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '阶段名称快照',
  `base_json` json DEFAULT NULL COMMENT '基础信息快照（建设单位/监管/招标方式/地点等关键项）',
  `latest_dynamic_time` datetime DEFAULT NULL COMMENT '最近动态时间',
  `unread_count` int NOT NULL DEFAULT '0' COMMENT '未读动态数（读后清零）',
  `follow_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FOLLOWING',
  `follow_time` datetime DEFAULT NULL,
  `unfollow_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_item_user_key` (`tenant_id`,`user_id`,`follow_type`,`follow_key`,`deleted`),
  KEY `idx_follow_item_stage` (`tenant_id`,`user_id`,`stage_code`),
  KEY `idx_follow_item_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注项目列表主档（T 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_follow_item_event` (
  `id` bigint NOT NULL COMMENT '主键',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号（T 轨，拦截器维护）',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `item_id` bigint NOT NULL COMMENT '所属关注行（bidding_follow_item.id）',
  `source` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'INTERNAL/EXTERNAL',
  `event_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'NOTICE_PUBLISHED/NOTICE_UPDATED/EXTERNAL_ACTIVE/STAGE_CHANGE',
  `notice_id` bigint DEFAULT NULL COMMENT '关联公告 ID（内部或外部表主档 id）',
  `event_key` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '唯一：user+item+source+notice_id/版本',
  `title_snapshot` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题快照',
  `orig_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原网址（外部）',
  `occurred_at` datetime NOT NULL COMMENT '动态发生时间',
  `read_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '未读=提醒（读后置 1）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_key` (`event_key`,`deleted`),
  KEY `idx_event_item` (`tenant_id`,`user_id`,`item_id`,`read_flag`),
  KEY `idx_event_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注项动态/事件（T 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_notice` (
  `id` bigint NOT NULL COMMENT '??(?? ID)',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '????',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???(?? ID ???)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '????(????)',
  `notice_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??????(bidding ????)',
  `notice_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??/???? code(NoticeTypeEnum:TENDER_NOTICE/CHANGE_NOTICE/WIN_CANDIDATE_PUBLICITY/WIN_RESULT_PUBLICITY)',
  `title` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??/????(??;??????,??????????? 2026-09-03,????????)',
  `project_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '????????(???=?????)',
  `project_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '????????(??/????;??????????? 2026-09-03)',
  `publisher_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????(?????)',
  `publish_time` datetime DEFAULT NULL COMMENT '??/??????(????????????)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '??:DRAFT/PUBLISHED/UNPUBLISHED/ARCHIVED',
  `internal_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '1=??????(??????);0=????(????????)',
  `public_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SOCIAL' COMMENT '????:SOCIAL/GOVERNMENT(?? PublicLevelEnum)',
  `fields_json` json DEFAULT NULL COMMENT '???????????(key=??????,??? null)',
  `pdf_file_id` bigint DEFAULT NULL COMMENT '?? PDF ??? file_id(FileApi ????;PDF ???????,?????��??????????????)',
  `pdf_sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '?? PDF ?????(SHA-256;?? 8/????5???:file_id+sha256(+size) ??)',
  `pdf_size` bigint DEFAULT NULL COMMENT '?? PDF ?????(??)',
  `version` int NOT NULL DEFAULT '0' COMMENT '?????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_code` (`notice_code`,`deleted`,`tenant_id`),
  KEY `idx_notice_type_time` (`notice_type`,`publish_time`),
  KEY `idx_notice_project` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='??/????(T ?)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_notice_content` (
  `id` bigint NOT NULL COMMENT '??(?? ID)',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '????',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '????(????)',
  `notice_id` bigint NOT NULL COMMENT '?????? ID',
  `content_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BODY' COMMENT '??????(?????? BODY)',
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????',
  `sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????(SHA-256,??????)',
  `version` int NOT NULL COMMENT '???(????????????)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_version` (`notice_id`,`content_type`,`version`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='??/??????(???)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_notice_external` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
  `source_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源站配置 code（bidding_crawl_source.source_code）',
  `source_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源站名称快照',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题（列表/检索核心）',
  `orig_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原网址（展示必带）',
  `orig_url_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'URL SHA-256（去重指纹）',
  `content_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '正文/文件指纹（内容级去重/变更识别）',
  `content_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER',
  `content_html` mediumtext COLLATE utf8mb4_unicode_ci COMMENT 'HTML 原文（原样保存，不转义不裁剪）',
  `pdf_file_id` bigint DEFAULT NULL COMMENT 'PDF 原文件 file_id（红线8：file_id+sha256+size）',
  `pdf_sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PDF 原文件哈希',
  `pdf_size` bigint DEFAULT NULL COMMENT 'PDF 原文件大小（字节）',
  `pdf_download_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PDF ??????(?????? URL)',
  `attachment_ids_json` json DEFAULT NULL COMMENT '附件记录 id 列表（bidding_notice_external_attachment）',
  `notice_type_raw` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外站自述类型原文（不猜不丢）',
  `notice_type_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '可映射的权威码（映射不到为 NULL）',
  `legal_flag` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Y=依法必招 / N=非依法（外站自述）/ NULL=未知',
  `industry_flag` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '防腐保温行业 AI 判定：BELONGS/NOT_BELONGS/UNCERTAIN',
  `industry_confidence` decimal(5,4) DEFAULT NULL COMMENT '行业判定置信度 0~1',
  `project_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联项目编号（跟踪维度，可为空）',
  `tender_project_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联招标项目编号（跟踪维度）',
  `bid_section_codes` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联标段编号（多个逗号分隔，跟踪维度）',
  `project_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目名称快照（基础信息）',
  `issuer_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布单位（快照）',
  `publish_time` datetime DEFAULT NULL COMMENT '外站公告时间（解析失败取抓取时间）',
  `effective_from` datetime DEFAULT NULL COMMENT '生效开始',
  `effective_to` datetime DEFAULT NULL COMMENT '生效截止（判断是否在生效期）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW' COMMENT 'NEW→ACTIVE→INACTIVE→ARCHIVED',
  `audit_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNREVIEWED' COMMENT 'UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN',
  `parser_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批绑定/本次处理用的策略码（AI 模板/规则 code）',
  `public_scope` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SOCIAL' COMMENT '公开范围（默认 SOCIAL 可展示）',
  `fetch_count` int NOT NULL DEFAULT '0' COMMENT '抓取次数',
  `last_fetch_time` datetime DEFAULT NULL COMMENT '最近抓取时间',
  `extraction_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT 'PDF 抽取状态：NONE/SUCCESS/FAIL_EMPTY/FAIL（OpenDataLoader 空结果=FAIL_EMPTY 直接失败）',
  `extraction_text` longtext COLLATE utf8mb4_unicode_ci COMMENT 'PDF 抽取的纯文本（OpenDataLoader 本地模式）',
  `extraction_tables_json` json DEFAULT NULL COMMENT 'PDF 抽取的表格（JSON 数组）',
  `extraction_at` datetime DEFAULT NULL COMMENT 'PDF 抽取时间',
  `ai_extraction_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT 'AI ???????:NONE/SUCCESS/FAILED',
  `ai_meta_json` json DEFAULT NULL COMMENT 'AI ??????(JSON)',
  `ai_extract_at` datetime DEFAULT NULL COMMENT '???? AI ????',
  `body_parser_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '正文结构化策略码',
  `body_blocks_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT '正文结构化状态',
  `body_blocks_json` json DEFAULT NULL COMMENT '正文结构化块',
  `body_blocks_at` datetime DEFAULT NULL COMMENT '结构化时间',
  `meta_json` json DEFAULT NULL COMMENT '原始页面结构化字段全集（兜底防丢）',
  `extra_raw` longtext COLLATE utf8mb4_unicode_ci COMMENT '整页快照（可追溯/复审）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_orig_url_hash` (`orig_url_hash`,`deleted`),
  KEY `idx_external_status_time` (`status`,`publish_time`),
  KEY `idx_external_project` (`project_code`),
  KEY `idx_external_type_time` (`content_type`,`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部公告主档（G 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_notice_external_attachment` (
  `id` bigint NOT NULL COMMENT '主键',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `notice_id` bigint NOT NULL COMMENT '主档 ID',
  `file_id` bigint NOT NULL COMMENT 'FileApi 文件对象主键',
  `sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件哈希',
  `size` bigint DEFAULT NULL COMMENT '大小（字节）',
  `name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件名',
  `ext` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '扩展名',
  `orig_download_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外站原始下载链接（溯源）',
  `virus_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLEAN/INFECTED（病毒扫描，先例：资质域）',
  PRIMARY KEY (`id`),
  KEY `idx_att_notice` (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部公告附件（G 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_notice_external_meta` (
  `id` bigint NOT NULL COMMENT '主键',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `notice_id` bigint NOT NULL COMMENT '主档 ID',
  `meta_json` json DEFAULT NULL COMMENT '原字段全集（列不固定的兜底）',
  `extra_raw` longtext COLLATE utf8mb4_unicode_ci COMMENT '整页快照（主档 extra_raw 冗余分表时用）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meta_notice` (`notice_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部公告动态字段兜底（G 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_notice_subscription` (
  `id` bigint NOT NULL COMMENT '主键',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号（T 轨，拦截器维护）',
  `user_id` bigint NOT NULL COMMENT '用户 ID（投标人）',
  `target_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION',
  `target_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标键：公告 id 或 维度代码',
  `follow_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FOLLOWING' COMMENT 'FOLLOWING/UNFOLLOWED',
  `follow_time` datetime DEFAULT NULL,
  `unfollow_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_user_target` (`tenant_id`,`user_id`,`target_type`,`target_key`,`deleted`),
  KEY `idx_sub_user` (`tenant_id`,`user_id`),
  KEY `idx_sub_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注订阅（T 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_notice_subscription_event` (
  `id` bigint NOT NULL COMMENT '主键',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号（T 轨，拦截器维护）',
  `user_id` bigint NOT NULL COMMENT '收件用户',
  `follow_event_id` bigint DEFAULT NULL COMMENT '对应关注动态（bidding_follow_item_event.id）',
  `notice_id` bigint DEFAULT NULL,
  `channel` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IN_APP/EMAIL',
  `channel_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  `sent_time` datetime DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '发送重试（≤3）',
  `message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_se_user_status` (`tenant_id`,`user_id`,`channel_status`),
  KEY `idx_se_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒发送留痕（T 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_subject_auth` (
  `id` bigint NOT NULL COMMENT '??(?? ID)',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???(?????)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '????',
  `tenant_id` bigint NOT NULL COMMENT '?????????',
  `tenant_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????(??????,?????)',
  `subject_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????:BIDDER/TENDERER',
  `auth_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT '????:ENABLED/DISABLED(SubjectAuthStatusEnum)',
  `authorize_user_id` bigint DEFAULT NULL COMMENT '?????(AdminUser id)',
  `authorize_time` datetime DEFAULT NULL COMMENT '??(???????)??',
  `disable_time` datetime DEFAULT NULL COMMENT '??(????)??',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_auth_tenant_type` (`tenant_id`,`subject_type`,`deleted`),
  KEY `idx_subject_auth_status` (`auth_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='??????(???/???,G ?)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_subject_profile` (
  `id` bigint NOT NULL COMMENT '??(?? ID)',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '????(??=??)',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '????',
  `subject_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????:BIDDER ??? / TENDERER ???(SubjectTypeEnum)',
  `subject_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????(?89 LEGAL_NAME,??)',
  `uscc` char(18) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????????(?89 LEGAL_CODE;GB 32100-2015 ????,?????)',
  `legal_person_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???????(?89 LEGAL_REPRESENT,??)',
  `legal_person_type_code` varchar(2) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????????(?89 LEGAL_TYPE C1,??)',
  `region_code` char(6) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??????(?89 REGION_CODE C6,GB/T 2260,??)',
  `industry_code` varchar(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '????(?89 LEGAL_INDUSTRY C3,GB/T 4754-2011,??)',
  `reg_capital` decimal(20,6) DEFAULT NULL COMMENT '????(?89 REG_CAPITAL,??)',
  `reg_cap_currency` char(3) COLLATE utf8mb4_unicode_ci DEFAULT '156' COMMENT '?????????(GB/T 12406-2008,?? 156 ???)',
  `contact_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???(?89 LEGAL_CONTACT,??)',
  `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '?????(?89 LEGAL_CONTACT_PHONE,??)',
  `license_file_id` bigint DEFAULT NULL COMMENT '?????? file_id(FileApi ????;????,??)',
  `license_file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????????',
  `ext_json` json DEFAULT NULL COMMENT '?????? JSON(key=??????,????)',
  `complete_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '??????(????=SubjectCompleteness ????)',
  `complete_time` datetime DEFAULT NULL COMMENT '???????????(???????)',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_profile_type` (`tenant_id`,`subject_type`,`deleted`),
  KEY `idx_subject_profile_uscc` (`uscc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='??????(???/???,T ?)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bidding_subject_user` (
  `id` bigint NOT NULL COMMENT '??(?? ID)',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '???',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '????',
  `tenant_id` bigint NOT NULL COMMENT '????????',
  `subject_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????:BIDDER/TENDERER',
  `user_id` bigint NOT NULL COMMENT '???? id(system AdminUser)',
  `user_nickname` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????',
  `user_role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPERATOR' COMMENT '????:OPERATOR(??/?????,??)/CONTACT(???)',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '????(??=??????)',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_user_bind` (`tenant_id`,`subject_type`,`user_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='????????(G ?)';
/*!40101 SET character_set_client = @saved_cs_client */;
