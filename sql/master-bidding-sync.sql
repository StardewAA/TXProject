
-- ========== ① bidding 全部业务表结构（IF NOT EXISTS，幂等） ==========


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `bidding_fetch_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_fetch_job` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `source_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æº codeï¼ˆç©º=å…¨æºï¼‰',
  `trigger_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SCHEDULE' COMMENT 'SCHEDULE/MANUAL/REPAIR',
  `scope` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'èŒƒå›´è¯´æ˜Žï¼ˆå¦‚æŒ‰æ—¶é—´çª—å£ï¼‰',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED',
  `produced` bit(1) NOT NULL DEFAULT b'0' COMMENT '生产端是否已投递完成（async：runNow 投递完置1；消费端收尾条件=produced且无未终态task，防提前收尾）',
  `summary_json` json DEFAULT NULL COMMENT 'æ±‡æ€»ï¼šæ€»æ•°/æˆåŠŸ/å¤±è´¥/è½¬äººå·¥æ•°',
  `finished_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_job_status` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='é‡‡é›†æ‰¹æ¬¡ï¼ˆG è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_fetch_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_fetch_source` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `source_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æº codeï¼ˆå”¯ä¸€ï¼‰',
  `source_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æºåç§°',
  `base_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç«™ç‚¹åŸŸå/å…¥å£',
  `allowed_hosts` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '额外放行域名（半角逗号分隔：附件 CDN/文件服务器等溯源域名）',
  `entry_urls_json` json DEFAULT NULL COMMENT 'å…¥å£/æ ç›® URL åˆ—è¡¨',
  `channel_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ä¸‹è½½é€šé“é€‚é…å™¨ codeï¼ˆé€šç”¨æˆ–å¹³å°ä¸“å±žï¼‰',
  `fetch_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CRAWLER' COMMENT '获取方式：CRAWLER=爬虫(列表HTML) / API=接口直取(JSON)',
  `api_config_json` json DEFAULT NULL COMMENT 'API 源配置：{method,url,headers,params,page,rowsPath,totalPath,fieldMapping,envelope}',
  `parser_code_default` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é»˜è®¤è§£æž/ç­–ç•¥ç ï¼ˆURL æœªå•ç‹¬å®¡æ‰¹æ—¶ç”¨ï¼‰',
  `fetch_interval_seconds` int NOT NULL DEFAULT '3600' COMMENT 'æŠ“å–é—´éš”ï¼ˆç§’ï¼Œé¢‘æŽ§ï¼‰',
  `robots_ok` bit(1) NOT NULL DEFAULT b'0' COMMENT 'æ˜¯å¦å…è®¸æŒ‰ robots æŠ“å–',
  `enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT 'å¯ç”¨å¼€å…³',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å¤‡æ³¨',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_code` (`source_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='é‡‡é›†æºé…ç½®ï¼ˆG è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_fetch_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_fetch_task` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `job_id` bigint DEFAULT NULL COMMENT 'æ‰€å±žæ‰¹æ¬¡ï¼ˆbidding_crawl_job.idï¼‰',
  `source_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æº code',
  `url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å¾…æŠ“ URL',
  `url_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'URL SHA-256ï¼ˆåŽ»é‡ï¼‰',
  `channel_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é€šé“é€‚é…å™¨ code',
  `parser_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'è§£æž/ç­–ç•¥ codeï¼ˆURL å®¡æ‰¹ç»‘å®šæˆ–æºé»˜è®¤ï¼‰',
  `priority` int NOT NULL DEFAULT '0' COMMENT 'ä¼˜å…ˆçº§ï¼ˆå°=é«˜ï¼‰',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
  `attempt` int NOT NULL DEFAULT '0' COMMENT 'å·²å°è¯•æ¬¡æ•°ï¼ˆMQ ä¾§ â‰¤3ï¼‰',
  `next_retry_time` datetime DEFAULT NULL COMMENT 'ä¸‹æ¬¡é‡è¯•æ—¶é—´ï¼ˆæŒ‡æ•°é€€é¿ï¼‰',
  `worker_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ‰§è¡ŒèŠ‚ç‚¹æ ‡è¯†ï¼ˆåˆ†å¸ƒå¼æŠ¢å ï¼‰',
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ç»“æžœ/é”™è¯¯è¯´æ˜Ž',
  `payload_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '列表阶段条目快照 JSON（QYZB 全文 HTML/GX 标题日期；持久队列载体，Redis 消息仅带 taskId）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_job_url` (`job_id`,`url_hash`,`source_code`,`deleted`),
  KEY `idx_task_status_retry` (`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='é‡‡é›†ä»»åŠ¡ï¼ˆG è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_filing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_filing` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `filing_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '备案类型（幂等键①）',
  `project_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编号（幂等键②）',
  `tender_project_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '招标项目编号',
  `unified_deal_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统一交易标识码',
  `tender_mode` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '招标方式（代码）',
  `org_form` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织形式（代码）',
  `supervise_dept_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '监督部门代码',
  `supervise_dept_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '监督部门名称',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `data_timestamp` datetime DEFAULT NULL COMMENT 'data_timestamp（05 交换口径，幂等防重）',
  `extra_json` json DEFAULT NULL COMMENT '扩展/附件快照（如附件 file_id 列表）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED（受理状态由监管回推）',
  `receipt_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备案受理号（服务端生成，唯一）',
  `receipt_time` datetime DEFAULT NULL COMMENT '受理（回推）时间',
  `opinion` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '监管回推意见',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_filing_power` (`filing_type`,`project_code`,`deleted`,`tenant_id`),
  UNIQUE KEY `uk_filing_receipt` (`receipt_no`),
  KEY `idx_filing_status_time` (`status`,`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备案受理记录（T 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_follow_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_follow_item` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ç¼–å·ï¼ˆT è½¨ï¼Œæ‹¦æˆªå™¨ç»´æŠ¤ï¼‰',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ· ID',
  `follow_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PROJECT/TENDER_PROJECT/SECTION',
  `follow_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç»´åº¦ä»£ç ï¼ˆproject_code/tender_project_code/bid_section_codeï¼‰',
  `display_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é¡¹ç›®/æ ‡æ®µåå¿«ç…§',
  `issuer_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å‘å¸ƒå•ä½å¿«ç…§',
  `stage_code` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'UNKNOWN' COMMENT 'è¿›å±•é˜¶æ®µï¼šPENDING/TENDER/OPEN_BID/EVAL/RESULT/CONTRACT/DONE/UNKNOWN',
  `stage_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é˜¶æ®µåç§°å¿«ç…§',
  `base_json` json DEFAULT NULL COMMENT 'åŸºç¡€ä¿¡æ¯å¿«ç…§ï¼ˆå»ºè®¾å•ä½/ç›‘ç®¡/æ‹›æ ‡æ–¹å¼/åœ°ç‚¹ç­‰å…³é”®é¡¹ï¼‰',
  `latest_dynamic_time` datetime DEFAULT NULL COMMENT 'æœ€è¿‘åŠ¨æ€æ—¶é—´',
  `unread_count` int NOT NULL DEFAULT '0' COMMENT 'æœªè¯»åŠ¨æ€æ•°ï¼ˆè¯»åŽæ¸…é›¶ï¼‰',
  `follow_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FOLLOWING',
  `follow_time` datetime DEFAULT NULL,
  `unfollow_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_item_user_key` (`tenant_id`,`user_id`,`follow_type`,`follow_key`,`deleted`),
  KEY `idx_follow_item_stage` (`tenant_id`,`user_id`,`stage_code`),
  KEY `idx_follow_item_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='å…³æ³¨é¡¹ç›®åˆ—è¡¨ä¸»æ¡£ï¼ˆT è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_follow_item_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_follow_item_event` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ç¼–å·ï¼ˆT è½¨ï¼Œæ‹¦æˆªå™¨ç»´æŠ¤ï¼‰',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ· ID',
  `item_id` bigint NOT NULL COMMENT 'æ‰€å±žå…³æ³¨è¡Œï¼ˆbidding_follow_item.idï¼‰',
  `source` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'INTERNAL/EXTERNAL',
  `event_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'NOTICE_PUBLISHED/NOTICE_UPDATED/EXTERNAL_ACTIVE/STAGE_CHANGE',
  `notice_id` bigint DEFAULT NULL COMMENT 'å…³è”å…¬å‘Š IDï¼ˆå†…éƒ¨æˆ–å¤–éƒ¨è¡¨ä¸»æ¡£ idï¼‰',
  `event_key` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å”¯ä¸€ï¼šuser+item+source+notice_id/ç‰ˆæœ¬',
  `title_snapshot` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ ‡é¢˜å¿«ç…§',
  `orig_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'åŽŸç½‘å€ï¼ˆå¤–éƒ¨ï¼‰',
  `occurred_at` datetime NOT NULL COMMENT 'åŠ¨æ€å‘ç”Ÿæ—¶é—´',
  `read_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT 'æœªè¯»=æé†’ï¼ˆè¯»åŽç½® 1ï¼‰',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_key` (`event_key`,`deleted`),
  KEY `idx_event_item` (`tenant_id`,`user_id`,`item_id`,`read_flag`),
  KEY `idx_event_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='å…³æ³¨é¡¹åŠ¨æ€/äº‹ä»¶ï¼ˆT è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_notice` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者（用户 ID 字符串）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
  `notice_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告内部编号（bidding 域内唯一）',
  `notice_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告/公示类型 code（NoticeTypeEnum：TENDER_NOTICE/CHANGE_NOTICE/WIN_CANDIDATE_PUBLICITY/WIN_RESULT_PUBLICITY）',
  `title` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告/公示名称（标题；可超百字长句，宽度放宽经建表评审留注 2026-09-03，见五章二档位说明）',
  `project_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联招标项目编号（可为空=不绑定项目）',
  `project_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '招标项目名称快照（检索/列表展示；长句放宽经建表评审留注 2026-09-03）',
  `publisher_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布单位名称（名称快照列）',
  `publish_time` datetime DEFAULT NULL COMMENT '公告/公示发布时间（列表排序与大厅可见性依据）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/UNPUBLISHED/ARCHIVED',
  `internal_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '1=企业内部公告（仅本租户可见）；0=面向公众（经公开白名单外发）',
  `public_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SOCIAL' COMMENT '公开级别：SOCIAL/GOVERNMENT（对齐 PublicLevelEnum）',
  `fields_json` json DEFAULT NULL COMMENT '权威数据集字段全集快照（key=规范英文名称，缺失补 null）',
  `pdf_file_id` bigint DEFAULT NULL COMMENT '公告 PDF 原文件 file_id（FileApi 对象主键；PDF 原文件必须保存，发布前必填——两种前端展示形态都基于该原件）',
  `pdf_sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '公告 PDF 原文件哈希（SHA-256；红线 8/五章三第5款口径：file_id+sha256(+size) 齐存）',
  `pdf_size` bigint DEFAULT NULL COMMENT '公告 PDF 原文件大小（字节）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_code` (`notice_code`,`deleted`,`tenant_id`),
  KEY `idx_notice_type_time` (`notice_type`,`publish_time`),
  KEY `idx_notice_project` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告/公示主档（T 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_notice_content`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_notice_content` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
  `notice_id` bigint NOT NULL COMMENT '归属公告主档 ID',
  `content_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BODY' COMMENT '内容类型编码（正文主体固定 BODY）',
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '正文全文',
  `sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容哈希（SHA-256，防篡改与去重）',
  `version` int NOT NULL COMMENT '版本号（同公告同类型自增、只追加）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_version` (`notice_id`,`content_type`,`version`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告/公示正文内容（版本化）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_notice_external`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_notice_external` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®ï¼ˆé›ªèŠ± IDï¼‰',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'åˆ›å»ºè€…',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'æ›´æ–°è€…',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'æ˜¯å¦åˆ é™¤ï¼ˆé€»è¾‘åˆ é™¤ï¼‰',
  `source_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¥æºç«™é…ç½® codeï¼ˆbidding_crawl_source.source_codeï¼‰',
  `source_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ¥æºç«™åç§°å¿«ç…§',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ ‡é¢˜ï¼ˆåˆ—è¡¨/æ£€ç´¢æ ¸å¿ƒï¼‰',
  `orig_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'åŽŸç½‘å€ï¼ˆå±•ç¤ºå¿…å¸¦ï¼‰',
  `orig_url_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'URL SHA-256ï¼ˆåŽ»é‡æŒ‡çº¹ï¼‰',
  `content_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ­£æ–‡/æ–‡ä»¶æŒ‡çº¹ï¼ˆå†…å®¹çº§åŽ»é‡/å˜æ›´è¯†åˆ«ï¼‰',
  `content_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å±•ç¤ºå½¢æ€ï¼šHTML/PDF/ATTACHMENT/IMAGE/OTHER',
  `content_html` mediumtext COLLATE utf8mb4_unicode_ci COMMENT 'HTML åŽŸæ–‡ï¼ˆåŽŸæ ·ä¿å­˜ï¼Œä¸è½¬ä¹‰ä¸è£å‰ªï¼‰',
  `pdf_file_id` bigint DEFAULT NULL COMMENT 'PDF åŽŸæ–‡ä»¶ file_idï¼ˆçº¢çº¿8ï¼šfile_id+sha256+sizeï¼‰',
  `pdf_sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PDF åŽŸæ–‡ä»¶å“ˆå¸Œ',
  `pdf_size` bigint DEFAULT NULL COMMENT 'PDF åŽŸæ–‡ä»¶å¤§å°ï¼ˆå­—èŠ‚ï¼‰',
  `pdf_download_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PDF 归档访问地址（平台文件服务 URL）',
  `attachment_ids_json` json DEFAULT NULL COMMENT 'é™„ä»¶è®°å½• id åˆ—è¡¨ï¼ˆbidding_notice_external_attachmentï¼‰',
  `notice_type_raw` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å¤–ç«™è‡ªè¿°ç±»åž‹åŽŸæ–‡ï¼ˆä¸çŒœä¸ä¸¢ï¼‰',
  `notice_type_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å¯æ˜ å°„çš„æƒå¨ç ï¼ˆæ˜ å°„ä¸åˆ°ä¸º NULLï¼‰',
  `legal_flag` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Y=ä¾æ³•å¿…æ‹› / N=éžä¾æ³•ï¼ˆå¤–ç«™è‡ªè¿°ï¼‰/ NULL=æœªçŸ¥',
  `industry_flag` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é˜²è…ä¿æ¸©è¡Œä¸š AI åˆ¤å®šï¼šBELONGS/NOT_BELONGS/UNCERTAIN',
  `industry_confidence` decimal(5,4) DEFAULT NULL COMMENT 'è¡Œä¸šåˆ¤å®šç½®ä¿¡åº¦ 0~1',
  `project_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å…³è”é¡¹ç›®ç¼–å·ï¼ˆè·Ÿè¸ªç»´åº¦ï¼Œå¯ä¸ºç©ºï¼‰',
  `tender_project_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å…³è”æ‹›æ ‡é¡¹ç›®ç¼–å·ï¼ˆè·Ÿè¸ªç»´åº¦ï¼‰',
  `bid_section_codes` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å…³è”æ ‡æ®µç¼–å·ï¼ˆå¤šä¸ªé€—å·åˆ†éš”ï¼Œè·Ÿè¸ªç»´åº¦ï¼‰',
  `project_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é¡¹ç›®åç§°å¿«ç…§ï¼ˆåŸºç¡€ä¿¡æ¯ï¼‰',
  `issuer_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å‘å¸ƒå•ä½ï¼ˆå¿«ç…§ï¼‰',
  `publish_time` datetime DEFAULT NULL COMMENT 'å¤–ç«™å…¬å‘Šæ—¶é—´ï¼ˆè§£æžå¤±è´¥å–æŠ“å–æ—¶é—´ï¼‰',
  `effective_from` datetime DEFAULT NULL COMMENT 'ç”Ÿæ•ˆå¼€å§‹',
  `effective_to` datetime DEFAULT NULL COMMENT 'ç”Ÿæ•ˆæˆªæ­¢ï¼ˆåˆ¤æ–­æ˜¯å¦åœ¨ç”Ÿæ•ˆæœŸï¼‰',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW' COMMENT 'NEWâ†’ACTIVEâ†’INACTIVEâ†’ARCHIVED',
  `audit_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNREVIEWED' COMMENT 'UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN',
  `parser_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å®¡æ‰¹ç»‘å®š/æœ¬æ¬¡å¤„ç†ç”¨çš„ç­–ç•¥ç ï¼ˆAI æ¨¡æ¿/è§„åˆ™ codeï¼‰',
  `public_scope` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SOCIAL' COMMENT 'å…¬å¼€èŒƒå›´ï¼ˆé»˜è®¤ SOCIAL å¯å±•ç¤ºï¼‰',
  `fetch_count` int NOT NULL DEFAULT '0' COMMENT 'æŠ“å–æ¬¡æ•°',
  `last_fetch_time` datetime DEFAULT NULL COMMENT 'æœ€è¿‘æŠ“å–æ—¶é—´',
  `extraction_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT 'PDF æŠ½å–çŠ¶æ€ï¼šNONE/SUCCESS/FAIL_EMPTY/FAILï¼ˆOpenDataLoader ç©ºç»“æžœ=FAIL_EMPTY ç›´æŽ¥å¤±è´¥ï¼‰',
  `extraction_text` longtext COLLATE utf8mb4_unicode_ci COMMENT 'PDF æŠ½å–çš„çº¯æ–‡æœ¬ï¼ˆOpenDataLoader æœ¬åœ°æ¨¡å¼ï¼‰',
  `extraction_tables_json` json DEFAULT NULL COMMENT 'PDF æŠ½å–çš„è¡¨æ ¼ï¼ˆJSON æ•°ç»„ï¼‰',
  `extraction_at` datetime DEFAULT NULL COMMENT 'PDF æŠ½å–æ—¶é—´',
  `ai_extraction_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT 'AI 结构化抽取状态：NONE/SUCCESS/FAILED',
  `ai_meta_json` json DEFAULT NULL COMMENT 'AI 结构化字段集（JSON）',
  `ai_extract_at` datetime DEFAULT NULL COMMENT '最近一次 AI 抽取时间',
  `body_parser_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ­£æ–‡ç»“æž„åŒ–ç­–ç•¥ç ',
  `body_blocks_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT 'æ­£æ–‡ç»“æž„åŒ–çŠ¶æ€',
  `body_blocks_json` json DEFAULT NULL COMMENT 'æ­£æ–‡ç»“æž„åŒ–å—',
  `body_blocks_at` datetime DEFAULT NULL COMMENT 'ç»“æž„åŒ–æ—¶é—´',
  `meta_json` json DEFAULT NULL COMMENT 'åŽŸå§‹é¡µé¢ç»“æž„åŒ–å­—æ®µå…¨é›†ï¼ˆå…œåº•é˜²ä¸¢ï¼‰',
  `extra_raw` longtext COLLATE utf8mb4_unicode_ci COMMENT 'æ•´é¡µå¿«ç…§ï¼ˆå¯è¿½æº¯/å¤å®¡ï¼‰',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_orig_url_hash` (`orig_url_hash`,`deleted`),
  KEY `idx_external_status_time` (`status`,`publish_time`),
  KEY `idx_external_project` (`project_code`),
  KEY `idx_external_type_time` (`content_type`,`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='å¤–éƒ¨å…¬å‘Šä¸»æ¡£ï¼ˆG è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_notice_external_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_notice_external_attachment` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `notice_id` bigint NOT NULL COMMENT 'ä¸»æ¡£ ID',
  `file_id` bigint NOT NULL COMMENT 'FileApi æ–‡ä»¶å¯¹è±¡ä¸»é”®',
  `sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ–‡ä»¶å“ˆå¸Œ',
  `size` bigint DEFAULT NULL COMMENT 'å¤§å°ï¼ˆå­—èŠ‚ï¼‰',
  `name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ–‡ä»¶å',
  `ext` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ‰©å±•å',
  `orig_download_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å¤–ç«™åŽŸå§‹ä¸‹è½½é“¾æŽ¥ï¼ˆæº¯æºï¼‰',
  `virus_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLEAN/INFECTEDï¼ˆç—…æ¯’æ‰«æï¼Œå…ˆä¾‹ï¼šèµ„è´¨åŸŸï¼‰',
  PRIMARY KEY (`id`),
  KEY `idx_att_notice` (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='å¤–éƒ¨å…¬å‘Šé™„ä»¶ï¼ˆG è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_notice_external_meta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_notice_external_meta` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `notice_id` bigint NOT NULL COMMENT 'ä¸»æ¡£ ID',
  `meta_json` json DEFAULT NULL COMMENT 'åŽŸå­—æ®µå…¨é›†ï¼ˆåˆ—ä¸å›ºå®šçš„å…œåº•ï¼‰',
  `extra_raw` longtext COLLATE utf8mb4_unicode_ci COMMENT 'æ•´é¡µå¿«ç…§ï¼ˆä¸»æ¡£ extra_raw å†—ä½™åˆ†è¡¨æ—¶ç”¨ï¼‰',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meta_notice` (`notice_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='å¤–éƒ¨å…¬å‘ŠåŠ¨æ€å­—æ®µå…œåº•ï¼ˆG è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_notice_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_notice_subscription` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ç¼–å·ï¼ˆT è½¨ï¼Œæ‹¦æˆªå™¨ç»´æŠ¤ï¼‰',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ· IDï¼ˆæŠ•æ ‡äººï¼‰',
  `target_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION',
  `target_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç›®æ ‡é”®ï¼šå…¬å‘Š id æˆ– ç»´åº¦ä»£ç ',
  `follow_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FOLLOWING' COMMENT 'FOLLOWING/UNFOLLOWED',
  `follow_time` datetime DEFAULT NULL,
  `unfollow_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_user_target` (`tenant_id`,`user_id`,`target_type`,`target_key`,`deleted`),
  KEY `idx_sub_user` (`tenant_id`,`user_id`),
  KEY `idx_sub_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='å…³æ³¨è®¢é˜…ï¼ˆT è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_notice_subscription_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_notice_subscription_event` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ç¼–å·ï¼ˆT è½¨ï¼Œæ‹¦æˆªå™¨ç»´æŠ¤ï¼‰',
  `user_id` bigint NOT NULL COMMENT 'æ”¶ä»¶ç”¨æˆ·',
  `follow_event_id` bigint DEFAULT NULL COMMENT 'å¯¹åº”å…³æ³¨åŠ¨æ€ï¼ˆbidding_follow_item_event.idï¼‰',
  `notice_id` bigint DEFAULT NULL,
  `channel` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IN_APP/EMAIL',
  `channel_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  `sent_time` datetime DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0' COMMENT 'å‘é€é‡è¯•ï¼ˆâ‰¤3ï¼‰',
  `message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_se_user_status` (`tenant_id`,`user_id`,`channel_status`),
  KEY `idx_se_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='æé†’å‘é€ç•™ç—•ï¼ˆT è½¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_subject_auth`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_subject_auth` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者（平台操作人）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '被授权企业租户编号',
  `tenant_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '企业名称快照（授权时点取值，列表免联查）',
  `subject_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体类型：BIDDER/TENDERER',
  `auth_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT '授权状态：ENABLED/DISABLED（SubjectAuthStatusEnum）',
  `authorize_user_id` bigint DEFAULT NULL COMMENT '授权操作人（AdminUser id）',
  `authorize_time` datetime DEFAULT NULL COMMENT '授权（或最近一次启用）时间',
  `disable_time` datetime DEFAULT NULL COMMENT '停用（撤销授权）时间',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_auth_tenant_type` (`tenant_id`,`subject_type`,`deleted`),
  KEY `idx_subject_auth_status` (`auth_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主体授权记录（投标人/招标人，G 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_subject_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_subject_profile` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号（企业=租户）',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `subject_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体类型：BIDDER 投标人 / TENDERER 招标人（SubjectTypeEnum）',
  `subject_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位名称（表89 LEGAL_NAME，必填）',
  `uscc` char(18) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '统一社会信用代码（表89 LEGAL_CODE；GB 32100-2015 含校验码，保存强校验）',
  `legal_person_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '法定代表人姓名（表89 LEGAL_REPRESENT，必填）',
  `legal_person_type_code` varchar(2) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '法人机构类别代码（表89 LEGAL_TYPE C1，必填）',
  `region_code` char(6) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行政区划代码（表89 REGION_CODE C6，GB/T 2260，必填）',
  `industry_code` varchar(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '行业代码（表89 LEGAL_INDUSTRY C3，GB/T 4754-2011，可选）',
  `reg_capital` decimal(20,6) DEFAULT NULL COMMENT '注册资本（表89 REG_CAPITAL，可选）',
  `reg_cap_currency` char(3) COLLATE utf8mb4_unicode_ci DEFAULT '156' COMMENT '注册资本币种数字码（GB/T 12406-2008，默认 156 人民币）',
  `contact_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '联系人（表89 LEGAL_CONTACT，必填）',
  `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '联系人电话（表89 LEGAL_CONTACT_PHONE，必填）',
  `license_file_id` bigint DEFAULT NULL COMMENT '营业执照影像 file_id（FileApi 对象主键；实名凭据，可选）',
  `license_file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '营业执照影像附件名称',
  `ext_json` json DEFAULT NULL COMMENT '权威扩展字段 JSON（key=规范英文名称，可选采集）',
  `complete_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否信息完善（必填清单=SubjectCompleteness 实时核算）',
  `complete_time` datetime DEFAULT NULL COMMENT '最近一次达到完善的时间（变不完整则清空）',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_profile_type` (`tenant_id`,`subject_type`,`deleted`),
  KEY `idx_subject_profile_uscc` (`uscc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主体信息档案（投标人/招标人，T 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bidding_subject_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bidding_subject_user` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '主体所属企业租户',
  `subject_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体类型：BIDDER/TENDERER',
  `user_id` bigint NOT NULL COMMENT '经办用户 id（system AdminUser）',
  `user_nickname` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户昵称快照',
  `user_role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPERATOR' COMMENT '经办角色：OPERATOR（经办/投标操作人，默认）/CONTACT（联系人）',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '启用开关（停用=解绑保留历史）',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_user_bind` (`tenant_id`,`subject_type`,`user_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主体经办用户绑定（G 轨）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;



-- ========== ② bidding 菜单（system_menu 6735~6765） ==========


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `system_menu` WRITE;
/*!40000 ALTER TABLE `system_menu` DISABLE KEYS */;
INSERT IGNORE INTO `system_menu` VALUES (6735,'招投标','',1,301,0,'/bidding','carbon:integration','','',0,_binary '',_binary '',_binary '','admin','2026-09-06 14:01:47','1','2026-09-07 14:57:58',_binary '\0'),(6736,'外部公告审核管理','bidding:notice-external:update',2,1,6735,'noticeExternal','ep:document-copy','bidding/noticeExternal/index','BiddingNoticeExternalIndex',0,_binary '',_binary '',_binary '','1','2026-09-07 14:52:15','1','2026-09-07 14:57:23',_binary '\0'),(6737,'爬虫管理','',1,2,6735,'crawl','carbon:data-vis','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:15','1','2026-09-07 17:24:15',_binary '\0'),(6738,'爬取任务工作台','bidding:crawl-source:query',2,1,6737,'console','','bidding/crawlExternal/console/index','CrawlExternalConsole',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:16','1','2026-09-08 08:52:36',_binary '\0'),(6739,'采集任务','bidding:crawl-task:query',2,2,6737,'task','','bidding/crawlExternal/task/index','CrawlTaskIndex',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:16','1','2026-09-08 08:52:56',_binary ''),(6740,'采集批次','bidding:crawl-job:query',2,3,6737,'job','','bidding/crawlExternal/job/index','CrawlJobIndex',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:17','1','2026-09-08 08:52:56',_binary ''),(6741,'采集源新增','bidding:crawl-source:create',3,1,6738,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:17','1','2026-09-07 17:24:17',_binary '\0'),(6742,'采集源修改','bidding:crawl-source:update',3,1,6738,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:17','1','2026-09-07 17:24:17',_binary '\0'),(6743,'采集源删除','bidding:crawl-source:delete',3,1,6738,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:17','1','2026-09-07 17:24:17',_binary '\0'),(6744,'采集任务重试','bidding:crawl-task:update',3,1,6739,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:18','1','2026-09-08 08:52:56',_binary ''),(6745,'爬取任务查询','bidding:crawl-task:query',3,1,6739,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 08:53:10','1','2026-09-09 02:25:43',_binary '\0'),(6747,'批次查询','bidding:crawl-job:query',3,3,6740,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 08:53:10','1','2026-09-09 02:25:43',_binary '\0'),(6748,'投标流程跟踪','bidding:notice-external:query',2,3,6735,'processTrack','','bidding/processTrack/index','BiddingProcessTrackIndex',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:30','1','2026-09-08 11:19:39',_binary '\0'),(6749,'新建意向','bidding:process-track:create',3,1,6748,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:31','1','2026-09-08 09:33:31',_binary '\0'),(6750,'报名投标','bidding:process-track:register',3,2,6748,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:31','1','2026-09-08 09:33:31',_binary '\0'),(6751,'审核操作','bidding:process-track:review',3,3,6748,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:31','1','2026-09-08 09:33:31',_binary '\0'),(6752,'上传标书','bidding:process-track:upload',3,4,6748,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:31','1','2026-09-08 09:33:31',_binary '\0'),(6753,'公告/公示管理','',1,1000,6735,'notice','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:03','1','2026-09-08 10:27:32',_binary '\0'),(6754,'公告列表','bidding:notice:query',2,1,6753,'list','','bidding/notice/index','NoticeIndex',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:03','1','2026-09-08 10:07:03',_binary '\0'),(6755,'公告新建','bidding:notice:create',3,2,6754,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:03','1','2026-09-08 10:07:03',_binary '\0'),(6756,'公告修改','bidding:notice:update',3,3,6754,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6757,'公告发布','bidding:notice:publish',3,4,6754,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6758,'公告删除','bidding:notice:delete',3,5,6754,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6759,'主体信息与授权管理','',1,1001,6735,'subject-auth','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:34:31',_binary '\0'),(6760,'主体档案（企业自维护）','bidding:subject:query',2,1,6759,'subjectProfile','','bidding/subjectProfile/index','SubjectProfileIndex',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6761,'主体档案维护','bidding:subject:update',3,2,6760,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6762,'平台授权管理','bidding:subject-auth:query',2,2,6759,'subjectAuth','','bidding/subjectAuth/index','SubjectAuthIndex',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6763,'主体授权/经办用户管理','bidding:subject-auth:update',3,3,6762,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6764,'备案提交','bidding:filing:create',3,5,6748,'','',NULL,NULL,0,_binary '',_binary '',_binary '','1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0'),(6765,'备案结果回推','bidding:filing:update',3,6,6748,'','',NULL,NULL,0,_binary '',_binary '',_binary '','1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0');
/*!40000 ALTER TABLE `system_menu` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;



-- ========== ③ 角色挂载（system_role_menu bidding 段） ==========


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `system_role_menu` WRITE;
/*!40000 ALTER TABLE `system_role_menu` DISABLE KEYS */;
INSERT IGNORE INTO `system_role_menu` VALUES (180926,60346,6735,'admin','2026-09-06 14:01:47','admin','2026-09-06 14:01:47',_binary '\0',2019),(180927,1,6735,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180928,1,6736,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180929,1,6737,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180930,1,6738,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180931,1,6741,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180932,1,6742,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180933,1,6743,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180934,1,6745,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180935,1,6746,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180936,1,6747,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180937,1,6748,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180938,1,6749,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180939,1,6750,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180940,1,6751,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180941,1,6752,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180942,1,6753,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180943,1,6754,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180944,1,6755,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180945,1,6756,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180946,1,6757,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180947,1,6758,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180958,1,6759,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180959,1,6760,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180960,1,6761,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180961,1,6762,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180962,1,6763,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180965,2,6735,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180966,2,6736,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180967,2,6737,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180968,2,6738,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180969,2,6741,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180970,2,6742,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180971,2,6743,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180972,2,6745,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180973,2,6746,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180974,2,6747,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180975,2,6748,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180976,2,6749,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180977,2,6750,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180978,2,6751,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180979,2,6752,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180980,2,6753,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180981,2,6754,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180982,2,6755,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180983,2,6756,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180984,2,6757,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180985,2,6758,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180986,2,6759,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180987,2,6760,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180988,2,6761,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180989,2,6762,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180990,2,6763,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180991,1,6765,'1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0',0),(180992,1,6764,'1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0',0),(180993,2,6765,'1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0',0),(180994,2,6764,'1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0',0),(180995,2,6739,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(180996,2,6740,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(180997,2,6741,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(180998,2,6742,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(180999,2,6743,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181000,2,6744,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181001,2,6745,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181002,2,6747,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181003,2,6749,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181004,2,6750,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181005,2,6751,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181006,2,6752,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181007,2,6748,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181008,2,6735,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181009,2,6737,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181010,60352,6735,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181027,60352,6748,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181028,60346,6748,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181029,60352,6749,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181030,60346,6749,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181031,60352,6750,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181032,60346,6750,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181033,60352,6751,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181034,60346,6751,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181035,60352,6752,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181036,60346,6752,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181037,60352,6753,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181038,60346,6753,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181039,60352,6754,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181040,60346,6754,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181041,60352,6755,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181042,60346,6755,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181043,60352,6756,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181044,60346,6756,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181045,60352,6757,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181046,60346,6757,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181047,60352,6758,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181048,60346,6758,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181049,60352,6759,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181050,60346,6759,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181051,60352,6760,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181052,60346,6760,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181053,60352,6761,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181054,60346,6761,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181059,60352,6765,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181060,60346,6765,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181061,60352,6764,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181062,60346,6764,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181073,60346,6748,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181074,60346,6749,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181075,60346,6750,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181076,60346,6751,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181077,60346,6752,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181078,60346,6753,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181079,60346,6754,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181080,60346,6755,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181081,60346,6756,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181082,60346,6757,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181083,60346,6758,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181084,60346,6759,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181085,60346,6760,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181086,60346,6761,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181087,60346,6764,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181088,60346,6765,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019);
/*!40000 ALTER TABLE `system_role_menu` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;



-- ========== ④ 采集源当前配置（QYZB 六栏目/GX 三栏目/EBIDDING） ==========


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `bidding_fetch_source` WRITE;
/*!40000 ALTER TABLE `bidding_fetch_source` DISABLE KEYS */;
INSERT IGNORE INTO `bidding_fetch_source` VALUES (100000001,'','2026-09-06 09:01:29','','2026-09-09 13:50:32',_binary '\0','EBIDDING','秦源招标电子招标平台','https://www.ebidding.com.cn','ebidding.com.cn',NULL,'GENERIC','CRAWLER',NULL,NULL,360,_binary '\0',_binary '',NULL),(690010001,'1','2026-09-07 14:48:20','1','2026-09-09 15:28:33',_binary '\0','QYZB','秦源电子招标平台','https://qyzb.shccmg.com',NULL,'[{\"name\": \"招标公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"203\", \"lookbackDays\": 3, \"fullLookbackDays\": 60}, {\"name\": \"非招标公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"204\", \"lookbackDays\": 3, \"fullLookbackDays\": 60}, {\"name\": \"变更公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"205\", \"lookbackDays\": 3, \"fullLookbackDays\": 60}, {\"name\": \"中标公示\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"321\", \"lookbackDays\": 1, \"fullLookbackDays\": 60}, {\"name\": \"中标公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"322\", \"lookbackDays\": 1, \"fullLookbackDays\": 60}, {\"name\": \"其他公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"323\", \"lookbackDays\": 1, \"fullLookbackDays\": 60}]','GENERIC','API','{\"url\": \"https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage\", \"method\": \"POST\", \"mapping\": {\"url\": \"url\", \"date\": \"publishDate\", \"title\": \"title\", \"category\": \"categoryName\", \"contentHtml\": \"text\"}, \"rowsPath\": \"res.rows\", \"totalPath\": \"res.total\", \"urlPrefix\": \"https://qyzb.shccmg.com/cms/default/webfile\", \"bodyTemplate\": \"{\\\"pageNo\\\":{page},\\\"pageSize\\\":{size},\\\"dto\\\":{\\\"siteId\\\":\\\"{siteId}\\\",\\\"categoryId\\\":\\\"{categoryId}\\\"}}\"}','QYZB_JSON',300,_binary '\0',_binary '','下载器 v1 冒烟：先单栏目（3ywgg/205）；放开全部见文件头注释'),(690010002,'1','2026-09-07 14:48:20','1','2026-09-09 15:51:05',_binary '\0','GX','国信e采（山西）','https://gx.e-bidding.org',NULL,'[{\"name\": \"依法招标-招标公告\", \"parser\": \"GX_E_BIDDING\", \"listUrl\": \"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=01&tabName=招标公告&page=1\", \"maxPages\": 10, \"lookbackDays\": 120, \"fullLookbackDays\": 120}, {\"name\": \"非依法招标\", \"parser\": \"GX_E_BIDDING\", \"listUrl\": \"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=02&page=1\", \"maxPages\": 10, \"lookbackDays\": 3, \"fullLookbackDays\": 120}, {\"name\": \"非依法采购（采购信息）\", \"parser\": \"GX_E_BIDDING\", \"listUrl\": \"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=00&tabName=采购信息&page=1\", \"maxPages\": 10, \"lookbackDays\": 3, \"fullLookbackDays\": 120}]','GENERIC','CRAWLER',NULL,'GX_E_BIDDING',300,_binary '\0',_binary '','下载器 v1：详情=PDF（fileId 待 FileApi P2）；默认停用，冒烟时人工启用');
/*!40000 ALTER TABLE `bidding_fetch_source` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;



-- ========== ⑤ 主体授权与档案（tenant1 发布门/延河） ==========


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `bidding_subject_auth` WRITE;
/*!40000 ALTER TABLE `bidding_subject_auth` DISABLE KEYS */;
INSERT IGNORE INTO `bidding_subject_auth` VALUES (182143816674784,'admin','2026-09-09 15:03:31','','2026-09-09 15:03:31',_binary '\0',1,'西安鸥鹏互联科技有限公司','TENDERER','ENABLED',1,'2026-09-09 15:03:31',NULL,NULL),(2097502753184260098,'1','2026-09-09 09:50:01','1','2026-09-09 09:50:01',_binary '\0',2019,'延河热力集团有限公司','BIDDER','ENABLED',1,'2026-09-09 09:50:01',NULL,NULL);
/*!40000 ALTER TABLE `bidding_subject_auth` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `bidding_subject_profile` WRITE;
/*!40000 ALTER TABLE `bidding_subject_profile` DISABLE KEYS */;
INSERT IGNORE INTO `bidding_subject_profile` VALUES (182143816674785,1,'admin','2026-09-09 15:03:31','','2026-09-09 15:03:31',_binary '\0','TENDERER','西安鸥鹏互联科技有限公司','91610100MAM94DYHJ0','示例法人（请按营业执照维护）','1','610100',NULL,NULL,'156','OPENLAB','18625372606',NULL,NULL,NULL,_binary '',NULL,NULL);
/*!40000 ALTER TABLE `bidding_subject_profile` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;



-- ========== ⑥ AI 链路行级（角色20/model66/key28） ==========


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `ai_chat_role` WRITE;
/*!40000 ALTER TABLE `ai_chat_role` DISABLE KEYS */;
INSERT IGNORE INTO `ai_chat_role` VALUES (20,1,66,'公告AI解析','',NULL,0,'招投标公告结构化抽取+防腐保温行业判定（deepseek-chat）','你是招投标公告信息抽取与防腐保温行业判定助手。只输出一个 JSON 对象（不要 markdown 代码块、不要解释文字、不要复制正文），字段：title,projectName,issuer,publishTime,industryFlag(BELONGS/NOT_BELONGS/UNCERTAIN),industryConfidence(0~1),industryReason。行业判定：管道/储罐/装置/设备及其附属设施的防腐、保温、保冷、隔热（施工/维修/材料/检测）→BELONGS；无关→NOT_BELONGS；不足→UNCERTAIN。缺失字段给null。',NULL,NULL,NULL,_binary '\0',0,'1','2026-09-09 09:28:19','admin','2026-09-09 11:32:17',_binary '\0',1);
/*!40000 ALTER TABLE `ai_chat_role` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;



-- ========== ⑥ AI 链路行级（续） ==========


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `ai_model` WRITE;
/*!40000 ALTER TABLE `ai_model` DISABLE KEYS */;
INSERT IGNORE INTO `ai_model` VALUES (66,28,'deepseekflash','deepseek-chat','DeepSeek',1,0,0,0.2,8192,10,'1','2026-09-08 08:02:36','1','2026-09-09 11:32:17',_binary '\0',1);
/*!40000 ALTER TABLE `ai_model` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;



-- ========== ⑥ AI 链路行级（续） ==========


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `ai_api_key` WRITE;
/*!40000 ALTER TABLE `ai_api_key` DISABLE KEYS */;
INSERT IGNORE INTO `ai_api_key` VALUES (28,'test','sk-4f6f16cbc98841a28d5b43b283344158','DeepSeek','https://api.deepseek.com',0,'1','2026-09-08 08:01:13','1','2026-09-09 11:22:05',_binary '\0',1);
/*!40000 ALTER TABLE `ai_api_key` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;



-- ========== ⑦ 演示公告 5 条 ACTIVE（大厅可见） ==========


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `bidding_notice_external` WRITE;
/*!40000 ALTER TABLE `bidding_notice_external` DISABLE KEYS */;
INSERT IGNORE INTO `bidding_notice_external` VALUES (2096454639908225025,'1','2026-09-06 12:25:11','1','2026-09-07 09:24:32',_binary '\0','EBIDDING','秦源招标电子招标平台','炼化公司所属各单位2026年装置大修消缺公开招标公告','https://www.ebidding.com.cn/notice/detail?doc=DYZB2025-11-G141','194f300c065ada22aca278222773031feed4ad3df4866cc31ec18a9f90156648','2598dead07093fac4909c299255f6e672d5e64648efec6aac8e030c505fa13d3','HTML','<div class=\"notice-html-demo\"><h1>炼化公司所属各单位2026年装置大修消缺公开招标公告</h1><p>炼化公司所属各单位 2026年装置大修/消缺公开招标招标公告（招标编号：DYZB2025-11-G141）项目所在地区：炼化公司所属各单位一、招标条件本炼化公司所属各单位 2026年装置大修/消缺公开招标已由审批/核准/备案机关批准，项目资金来源为生产经营成本，出资比例 100%，招标人为陕西延长石油（集团）有限责任公司炼化公司，本项目已具备招标条件，现招标方式为公开招标。二、项目概况、招标范围和标段划分项目概况及范围：炼化公司所属各单位 2026年装置大修/消缺（主装置检修、辅助装置检修、电气检修、仪表检修、防腐保温施工、机组检修等）公开招标;共分为 7个标段。一标段：炼化公司 2026年延炼大修生产主装置安装检修项目；二标段：炼化公司 2026年石化厂、永炼、榆炼、油田气消缺检修/大修生产主装置安装检修项目；三标段：炼化公司 2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修辅助装置安装检修项目；四标段：炼化公司 2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修仪表检修项目；五标段：炼化公司 2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修检修电气检修项目；六标段：炼化公司 2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修防腐保温项目；七标段：炼化公司 2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修机组检修项目。三、投标人资格要求1、投标人应为中华人民共和国境内依法注册的法人或其他组织，提供有效的营业执照或事业法人登记证书；2、具备一般纳税人资格，提供税务机关出具的一般纳税人资格证明，或近期开具的增值税专用发票记账联，或税务机关官方网站一般纳税人查询记录截图并附网址；3、一标段（主装置）：投标人需具备石油化工工程施工总承包贰级及以上资质;具有特种设备生产许可证（锅炉安装 A）；具有特种设备生产许可证（工业管道安装 GC1）；具有特种设备生产许可证（压力容器制造 D 级或以上）。拟派项目经理须具备机电工程专业贰级建造师及以上资格，具有有效的安全生产考核合格 B证，且本项目实施期间未担任其他在建工程项目经理；拟派项目技术负责人须具备有效的与本项目相关专业有关的中级及以上职称证书；拟派项目安全负责人须具有有效的安全生产考核合格 C证；以上人员同时需提供与本单位签订的有效劳动合同及本单位为其缴纳的至少连续一年以上社保证明(2024年 6月 1日起至今)；二标段（主装置）：投标人需具备石油化工工程施工总承包贰级及以上资质；具有特种设备生产许可证（锅炉安装 A）；具有特种设备生产许可证（工业管道安装 GC1）；具有特种设备生产许可证（压力容器制造 D 级或以上）。拟派项目经理须具备机电工程专业贰级建造师及以上资格，具有有效的安全生产考核合格 B证，且本项目实施期间未担任其他在建工程项目经理；拟派项目技术负责人须具备有效的与本项目相关专业有关的中级及以上职称证书；拟派项目安全负责人须具有有效的安全生产考核合格 C证；以上人员同时需提供与本单位签订的有效劳动合同及本单位为其缴纳的至少连续一年以上社保证明(2024年 6月 1日起至今)；三标段（辅助装置）：投标人需具备石油化工工程施工总承包贰级以上资质；具有特种设备生产许可证（锅炉安装 A）；具有特种设备生产许可证（工业管道安装 GC1）；具有特种设备生产许可证（压力容器制造 D 级或以上）。拟派项目经理及项目副经理各1人须具备机电工程专业贰级建造师及以上资格，具有有效的安全生产考核合格 B证，且本项目实施期间未担任其他在建工程项目经理；拟派项目技术负责人 2人须具备有效的与本项目相关专业有关的中级及以上职称证书；拟派项目安全负责人 2人须具有有效的安全生产考核合格 C证；以上人员同时需提供与本单位签订的有效劳动合同及本单位为其缴纳的至少连续一年以上社保证明(2024年 6月 1日起至今)；四标段（仪表）：投标人需具备机电工程施工总承包贰级及以上资质或石油化工工程施工总承包贰级及以上资质。拟派项目经理及项目副经理各 1人，须具备机电工程专业贰级建造师及以上资格，具有有效的安全生产考核合格 B证，且本项目实施期间未担任其他在建工程项目经理；拟派项目技术负责人 2人，须具备有效的与本项目相关专业有关的中级及以上职称证书；拟派项目安全负责人 2人，须具有有效的安全生产考核合格 C证；以上人员同时需提供与本单位签订的有效劳动合同及本单位为其缴纳的至少连续一年以上社保证明(2024年 6月 1日起至今)；五标段（电气）：投标人需具备机电工程施工总承包贰级及以上资质或国家承认的三级及以上承装（修、试）电力设施许可证。拟派项目经理及项目副经理各 1人，须具备机电工程专业贰级建造师及以上资格，具有有效的安全生产考核合格 B证，且本项目实施期间未担任其他在建工程项目经理；拟派项目技术负责人 2人，须具备有效的与本项目相关专业有关的中级及以上职称证书；拟派项目安全负责人 2人，须具有有效的安全生产考核合格 C证；以上人员同时需提供与本单位签订的有效劳动合同及本单位为其缴纳的至少连续一年以上社保证明(2024年 6月 1日起至今)；六标段（防腐保温）：投标人需具备防水防腐保温工程专业承包壹级资质。拟派项目经理须具备机电工程专业贰级建造师及以上资格，具有有效的安全生产考核合格 B证，且本项目实施期间未担任其他在建工程项目经理；拟派项目技术负责人须具备有效的与本项目相关专业有关的中级及以上职称证书；拟派项目安全负责人须具有有效的安全生产考核合格 C证；以上人员同时需提供与本单位签订的有效劳动合同及本单位为其缴纳的至少连续一年以上社保证明(2024年 6月 1日起至今)；七标段（机组检修）：投标人需具有石油化工工程施工总承包贰级及以上资质。拟派项目经理及项目副经理各 1人，须具备机电工程专业贰级建造师及以上资格，具有有效的安全生产考核合格 B证，且本项目实施期间未担任其他在建工程项目经理；拟派项目技术负责人 2人，须具备有效的与本项目相关专业有关的中级及以上职称证书；拟派项目安全负责人 2人须具有有效的安全生产考核合格 C证；以上人员同时需提供与本单位签订的有效劳动合同及本单位为其缴纳的至少连续一年以上社保证明(2024年 6月 1日起至今)；4、具有有效的安全生产许可证（1-7标段均需提供）；5、提供 2023年 1月 1日起至今，至少有一项类似施工或检维修合同业绩（一、二、三、四、五、六标段）；七标段所提供的业绩合同内容中应涵括烟气轮机、轴流式压缩机、离心式压缩机、往复压缩机及蒸汽轮机的机组检修业绩（可提供多份业绩证明，但所提供的业绩必须涵盖烟气轮机、轴流式压缩机、离心式压缩机、往复压缩机及蒸汽轮机的机组检修，如每份业绩只能体现以上一项不重复内容的，五份业绩算一份有效业绩）；6、不得列入国家企业信用信息公示系统经营异常名录、严重违法失信名单（黑名单），不得列入信用中国经营异常、严重失信主体名单，不得列入中国执行信息公开网失信被执行人名单；7、本次招标不接受联合体投标。四、招标文件的获取获取时间：从 2025年 11月 26日到 2025年 12月 3日。获取方式：本次招标实行网上获取招标文件。凡有意参加投标者，请于规定时间登录延长石油电子招标采购交易平台（网址：https://zc.ycpc.com）支付平台服务费后下载招标文件，平台服务费为 500.00元/标段，支付后不退。具体操作步骤如下：①登录延长石油电子招标采购交易平台（网址：https://zc.ycpc.com）按照提示信息进行投标人注册。②注册成功后，登录延长石油电子招标采购交易平台，查看“公告信息”，点击“参与项目”，并进入项目支付平台服务费，缴费成功后，在平台下载招标文件。③本项目采用电子招标方式，潜在投标人须使用 CA才能完成投标工作，潜在投标人办理 CA具体操作请参考延长石油电子招标采购交易平台的网站首页“帮助中心”的操作指引，在帮助中心界面下载 CA客户端、数字签名服务、投标编制工具。CA办理咨询电话：029-87302528或 029-81292135。④关于平台注册、操作、下载招标文件等有关交易平台、客户端的操作问题请咨询平台客服，咨询电话:张工 029-88445418。⑤关于财务及发票开票问题请联系 029-88444381（财务电话请核实），关于招标文件问题请联系 029-88443211。五、投标文件的递交递交截止时间：2025年 12月 22日 9时 00分递交方式：通过延长石油电子招标投标交易平台在线递交电子投标文件,逾期送达的投标文件，电子招标投标交易平台将予以拒收。六、开标时间及地点开标时间：2025年 12月 22日 9时 00分开标地点：延长石油电子招标投标交易平台在线开标，所有投标人可准时进入延长石油电子招标投标交易平台开标大厅参加在线开标仪式。七、发布公告的媒介本次招标公告同时在《陕西采购与招标网（www.sntba.com）》、《陕西省工业和信息化领域招标投标监管服务系统（https://61.185.253.156:8085/ztbView/newindex.html）》和《陕西延长石油招标采购交易平台（https://zc.ycpc.com）》上发布。八、监督部门本招标项目的监督部门为陕西省工业和信息化厅。九、联系方式招标人：陕西延长石油（集团）有限责任公司炼化公司地  址：陕西省延安市洛川县联系人：张勇电  话：13772880589</p><p>招标代理机构：陕西德源招标有限责任公司地址：陕西省西安市雁塔区科技二路 75号延长石油大厦联系人：马强电话：029-88443024电子邮件：703270390@qq.com</p></div>',NULL,NULL,NULL,NULL,NULL,'招标公告',NULL,'Y','BELONGS',0.9500,'DYZB2025-11-G141',NULL,NULL,'炼化公司所属各单位 2026年装置大修/消缺公开招标','陕西延长石油（集团）有限责任公司炼化公司','2026-09-06 12:25:11',NULL,NULL,'ACTIVE','APPROVED',NULL,'SOCIAL',2,'2026-09-06 16:46:30','NONE',NULL,NULL,NULL,'SUCCESS','{\"title\": \"炼化公司所属各单位2026年装置大修消缺公开招标公告\", \"issuer\": \"陕西延长石油（集团）有限责任公司炼化公司\", \"tenderNo\": \"DYZB2025-11-G141\", \"bidOpenTime\": \"2025-12-22 09:00:00\", \"projectName\": \"炼化公司所属各单位2026年装置大修/消缺公开招标\", \"budgetAmount\": null, \"contactPhone\": \"13772880589\", \"industryFlag\": \"BELONGS\", \"contactPerson\": \"张勇\", \"industryReason\": \"招标范围明确包含防腐保温施工标段，且涉及装置检修中的防腐保温工作，属于管道防腐保温行业。\", \"industryConfidence\": 0.95, \"industryHitKeywords\": \"[\\\"防腐保温\\\",\\\"装置检修\\\",\\\"管道\\\"]\", \"qualificationSummary\": \"投标人须为境内依法注册的法人或其他组织，具备一般纳税人资格；各标段需具备相应资质（如石油化工工程施工总承包贰级及以上、防水防腐保温工程专业承包壹级等），项目经理、技术负责人、安全负责人需满足相应资格并提供社保证明；具有有效的安全生产许可证；提供类似业绩；不得列入失信名单；不接受联合体投标。\"}','2026-09-06 14:56:19','AI_FALLBACK','SUCCESS','[{\"text\": \"炼化公司所属各单位2026年装置大修消缺公开招标公告\", \"type\": \"heading\", \"level\": 1}, {\"text\": \"发布日期：2025年11月26日\", \"type\": \"paragraph\"}, {\"text\": \"招标编号：DYZB2025-11-G141\", \"type\": \"paragraph\"}, {\"text\": \"项目所在地区：陕西省延安市洛川县\", \"type\": \"paragraph\"}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"一、招标条件\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"本炼化公司所属各单位2026年装置大修/消缺公开招标已由审批/核准/备案机关批准，项目资金来源为生产经营成本，出资比例100%，招标人为陕西延长石油（集团）有限责任公司炼化公司。本项目已具备招标条件，现招标方式为公开招标。\", \"type\": \"paragraph\"}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"二、项目概况、招标范围和标段划分\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"项目概况及范围： 炼化公司所属各单位2026年装置大修/消缺（主装置检修、辅助装置检修、电气检修、仪表检修、防腐保温施工、机组检修等）公开招标，共分为7个标段。\", \"type\": \"paragraph\"}, {\"rows\": [[\"一标段\", \"2026年延炼大修生产主装置安装检修项目\"], [\"二标段\", \"2026年石化厂、永炼、榆炼、油田气消缺检修/大修生产主装置安装检修项目\"], [\"三标段\", \"2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修辅助装置安装检修项目\"], [\"四标段\", \"2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修仪表检修项目\"], [\"五标段\", \"2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修电气检修项目\"], [\"六标段\", \"2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修防腐保温项目\"], [\"七标段\", \"2026年延炼、永炼、榆炼、石化厂、油田气消缺检修/大修机组检修项目\"]], \"type\": \"table\", \"headers\": [\"标段\", \"项目内容\"]}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"三、投标人资格要求\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"通用资格要求（1-7标段均需满足）\", \"type\": \"heading\", \"level\": 3}, {\"type\": \"list\", \"items\": [\"投标人应为中华人民共和国境内依法注册的法人或其他组织，提供有效的营业执照或事业法人登记证书；\"], \"ordered\": true}, {\"type\": \"list\", \"items\": [\"具备一般纳税人资格，提供税务机关出具的一般纳税人资格证明，或近期开具的增值税专用发票记账联，或税务机关官方网站一般纳税人查询记录截图并附网址；\"], \"ordered\": true}, {\"type\": \"list\", \"items\": [\"具有有效的安全生产许可证；\"], \"ordered\": true}, {\"type\": \"list\", \"items\": [\"不得列入国家企业信用信息公示系统经营异常名录、严重违法失信名单（黑名单），不得列入信用中国经营异常、严重失信主体名单，不得列入中国执行信息公开网失信被执行人名单；\"], \"ordered\": true}, {\"type\": \"list\", \"items\": [\"本次招标不接受联合体投标。\"], \"ordered\": true}, {\"text\": \"各标段专项资格要求\", \"type\": \"heading\", \"level\": 3}, {\"text\": \"一标段（延炼主装置）\", \"type\": \"heading\", \"level\": 4}, {\"type\": \"list\", \"items\": [\"具备石油化工工程施工总承包贰级及以上资质；\", \"具有特种设备生产许可证（锅炉安装A）；\", \"具有特种设备生产许可证（工业管道安装GC1）；\", \"具有特种设备生产许可证（压力容器制造D级或以上）；\", \"拟派项目经理：机电工程专业贰级建造师及以上资格，有效的安全生产考核合格B证，且本项目实施期间未担任其他在建工程项目经理；\", \"拟派项目技术负责人：与本项目相关专业中级及以上职称证书；\", \"拟派项目安全负责人：有效的安全生产考核合格C证；\", \"以上人员需提供与本单位签订的有效劳动合同及本单位为其缴纳的至少连续一年以上社保证明（2024年6月1日起至今）。\"], \"ordered\": false}, {\"text\": \"二标段（石化厂、永炼、榆炼、油田气主装置）\", \"type\": \"heading\", \"level\": 4}, {\"type\": \"list\", \"items\": [\"资质要求同一标段；\", \"人员要求同一标段。\"], \"ordered\": false}, {\"text\": \"三标段（辅助装置）\", \"type\": \"heading\", \"level\": 4}, {\"type\": \"list\", \"items\": [\"具备石油化工工程施工总承包贰级及以上资质；\", \"特种设备生产许可证要求同一标段；\", \"拟派项目经理及项目副经理各1人：机电工程专业贰级建造师及以上资格，有效的安全生产考核合格B证；\", \"拟派项目技术负责人2人：相关专业中级及以上职称证书；\", \"拟派项目安全负责人2人：有效的安全生产考核合格C证；\", \"人员社保要求同一标段。\"], \"ordered\": false}, {\"text\": \"四标段（仪表检修）\", \"type\": \"heading\", \"level\": 4}, {\"type\": \"list\", \"items\": [\"具备机电工程施工总承包贰级及以上资质或石油化工工程施工总承包贰级及以上资质；\", \"拟派项目经理及项目副经理各1人：机电工程专业贰级建造师及以上资格，有效的安全生产考核合格B证；\", \"拟派项目技术负责人2人：相关专业中级及以上职称证书；\", \"拟派项目安全负责人2人：有效的安全生产考核合格C证；\", \"人员社保要求同一标段。\"], \"ordered\": false}, {\"text\": \"五标段（电气检修）\", \"type\": \"heading\", \"level\": 4}, {\"type\": \"list\", \"items\": [\"具备机电工程施工总承包贰级及以上资质或国家承认的三级及以上承装（修、试）电力设施许可证；\", \"拟派项目经理及项目副经理各1人：机电工程专业贰级建造师及以上资格，有效的安全生产考核合格B证；\", \"拟派项目技术负责人2人：相关专业中级及以上职称证书；\", \"拟派项目安全负责人2人：有效的安全生产考核合格C证；\", \"人员社保要求同一标段。\"], \"ordered\": false}, {\"text\": \"六标段（防腐保温）\", \"type\": \"heading\", \"level\": 4}, {\"type\": \"list\", \"items\": [\"具备防水防腐保温工程专业承包壹级资质；\", \"拟派项目经理：机电工程专业贰级建造师及以上资格，有效的安全生产考核合格B证；\", \"拟派项目技术负责人：相关专业中级及以上职称证书；\", \"拟派项目安全负责人：有效的安全生产考核合格C证；\", \"人员社保要求同一标段。\"], \"ordered\": false}, {\"text\": \"七标段（机组检修）\", \"type\": \"heading\", \"level\": 4}, {\"type\": \"list\", \"items\": [\"具备石油化工工程施工总承包贰级及以上资质；\", \"拟派项目经理及项目副经理各1人：机电工程专业贰级建造师及以上资格，有效的安全生产考核合格B证；\", \"拟派项目技术负责人2人：相关专业中级及以上职称证书；\", \"拟派项目安全负责人2人：有效的安全生产考核合格C证；\", \"人员社保要求同一标段。\"], \"ordered\": false}, {\"text\": \"业绩要求\", \"type\": \"heading\", \"level\": 3}, {\"rows\": [[\"一至六标段\", \"2023年1月1日起至今，至少有一项类似施工或检维修合同业绩\"], [\"七标段\", \"2023年1月1日起至今，业绩合同内容中应涵括烟气轮机、轴流式压缩机、离心式压缩机、往复压缩机及蒸汽轮机的机组检修业绩（可提供多份业绩证明，但必须涵盖以上全部内容；如每份业绩只能体现以上一项不重复内容的，五份业绩算一份有效业绩）\"]], \"type\": \"table\", \"headers\": [\"标段\", \"业绩要求\"]}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"四、招标文件的获取\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"获取时间： 2025年11月26日至2025年12月3日\", \"type\": \"paragraph\"}, {\"text\": \"获取方式： 本次招标实行网上获取招标文件。\", \"type\": \"paragraph\"}, {\"type\": \"list\", \"items\": [\"登录延长石油电子招标采购交易平台（网址：https://zc.ycpc.com）按照提示信息进行投标人注册；\"], \"ordered\": true}, {\"type\": \"list\", \"items\": [\"注册成功后，登录平台查看“公告信息”，点击“参与项目”，并进入项目支付平台服务费，缴费成功后，在平台下载招标文件；\"], \"ordered\": true}, {\"type\": \"list\", \"items\": [\"平台服务费为500.00元/标段，支付后不退；\"], \"ordered\": true}, {\"type\": \"list\", \"items\": [\"本项目采用电子招标方式，潜在投标人须使用CA才能完成投标工作。CA办理具体操作请参考平台网站首页“帮助中心”的操作指引，在帮助中心界面下载CA客户端、数字签名服务、投标编制工具。\"], \"ordered\": true}, {\"text\": \"咨询电话：\", \"type\": \"paragraph\"}, {\"type\": \"list\", \"items\": [\"CA办理咨询：029-87302528 或 029-81292135\", \"平台注册、操作、下载招标文件等：张工 029-88445418\", \"财务及发票开票：029-88444381\", \"招标文件问题：029-88443211\"], \"ordered\": false}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"五、投标文件的递交\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"递交截止时间： 2025年12月22日9时00分\", \"type\": \"paragraph\"}, {\"text\": \"递交方式： 通过延长石油电子招标投标交易平台在线递交电子投标文件，逾期送达的投标文件，电子招标投标交易平台将予以拒收。\", \"type\": \"paragraph\"}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"六、开标时间及地点\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"开标时间： 2025年12月22日9时00分\", \"type\": \"paragraph\"}, {\"text\": \"开标地点： 延长石油电子招标投标交易平台在线开标，所有投标人可准时进入平台开标大厅参加在线开标仪式。\", \"type\": \"paragraph\"}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"七、发布公告的媒介\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"本次招标公告同时在以下平台发布：\", \"type\": \"paragraph\"}, {\"type\": \"list\", \"items\": [\"《陕西采购与招标网》（www.sntba.com）\", \"《陕西省工业和信息化领域招标投标监管服务系统》（https://61.185.253.156:8085/ztbView/newindex.html）\", \"《陕西延长石油招标采购交易平台》（https://zc.ycpc.com）\"], \"ordered\": true}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"八、监督部门\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"本招标项目的监督部门为陕西省工业和信息化厅。\", \"type\": \"paragraph\"}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"九、联系方式\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"招标人： 陕西延长石油（集团）有限责任公司炼化公司 地址： 陕西省延安市洛川县 联系人： 张勇 电话： 13772880589\", \"type\": \"paragraph\"}, {\"text\": \"招标代理机构： 陕西德源招标有限责任公司 地址： 陕西省西安市雁塔区科技二路75号延长石油大厦 联系人： 马强 电话： 029-88443024 电子邮件： 703270390@qq.com\", \"type\": \"paragraph\"}, {\"text\": \"---\", \"type\": \"paragraph\"}, {\"text\": \"> 温馨提示： 请各潜在投标人合理安排时间，提前完成平台注册及CA办理，以免影响投标文件获取及递交。\", \"type\": \"paragraph\"}]','2026-09-06 18:15:45',NULL,NULL),(2096454684942467073,'1','2026-09-06 12:25:22','1','2026-09-07 09:24:32',_binary '\0','EBIDDING','秦源招标电子招标平台','炼化公司所属各单位2026年装置大修消缺公开招标中标候选人公示','https://www.ebidding.com.cn/notice/detail?doc=DYZB2025-11-G141-H','796e58949908a96df608af380a347f1acbe5d83628201c83e94fd6d0b5504d5d','7f462d3bd0c6ed5bee6fe6a44c9a5f9c9a38dc39f4f3a7e12c38b22aaf42b653','HTML','<div class=\"notice-html-demo\"><h1>炼化公司所属各单位2026年装置大修消缺公开招标中标候选人公示</h1><p>炼化公司所属各单位 2026 年装置大修/消缺公开招标（招标编号：DYZB2025-11-G141）评标工作已于 2025 年 12 月 22 日完成，现将评标结果予以公示，公示三日。</p><h2>一、中标候选人基本情况</h2><h3>一标段</h3><table><thead><tr><th>排序</th><th>中标候选人名称</th><th>投标下浮率（%）</th><th>工期</th><th>项目经理</th><th>注册建造师编号</th></tr></thead><tbody><tr><td>1</td><td>陕西化建工程有限责任公司</td><td>1.80</td><td>秦成立</td><td>陕261131443522</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>2</td><td>山东华显安装建设有限公司</td><td>1.50</td><td>赵钦建</td><td>鲁2372011202104294</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>3</td><td>中国化学工程第四建设有限公司</td><td>0.65</td><td>李长虎</td><td>湘1432017201771964</td><td>合同签订之日起至2027年3月31日</td></tr></tbody></table><h3>二标段</h3><table><thead><tr><th>排序</th><th>中标候选人名称</th><th>投标下浮率（%）</th><th>工期</th><th>项目经理</th><th>注册建造师编号</th></tr></thead><tbody><tr><td>1</td><td>陕西化建工程有限责任公司</td><td>1.70</td><td>高宇</td><td>陕261192023721</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>2</td><td>中国化学工程第四建设有限公司</td><td>0.90</td><td>李长虎</td><td>湘1432017201771964</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>3</td><td>山东华显安装建设有限公司</td><td>1.00</td><td>王军</td><td>鲁2372021202108056</td><td>合同签订之日起至2027年3月31日</td></tr></tbody></table><h3>三标段</h3><table><thead><tr><th>排序</th><th>中标候选人名称</th><th>投标下浮率（%）</th><th>工期</th><th>项目经理</th><th>注册建造师编号</th></tr></thead><tbody><tr><td>1</td><td>陕西化建工程有限责任公司</td><td>2.40</td><td>符海彤</td><td>陕261121340162</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>2</td><td>甘肃一安建设科技集团有限公司</td><td>2.60</td><td>刘加生</td><td>甘262161727943</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>3</td><td>山东华显安装建设有限公司</td><td>1.00</td><td>赵钦建</td><td>鲁2372011202104294</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>4</td><td>中国化学工程第四建设有限公司</td><td>0.50</td><td>张军峰</td><td>湘1432017201771772</td><td>合同签订之日起至2027年3月31日</td></tr></tbody></table><h3>四标段</h3><table><thead><tr><th>排序</th><th>中标候选人名称</th><th>投标下浮率（%）</th><th>工期</th><th>项目经理</th><th>注册建造师编号</th></tr></thead><tbody><tr><td>1</td><td>陕西化建工程有限责任公司</td><td>2.80</td><td>严海超</td><td>陕261141557331</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>2</td><td>陕西永卓建设工程有限公司</td><td>3.00</td><td>王世涛</td><td>陕261242407356</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>3</td><td>信邦建设集团有限公司</td><td>1.05</td><td>李海涛</td><td>鲁1372021202205990</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>4</td><td>天人建设安装有限公司</td><td>0.80</td><td>汤颖</td><td>湘1432019202000447</td><td>合同签订之日起至2027年3月31日</td></tr></tbody></table><h3>五标段</h3><table><thead><tr><th>排序</th><th>中标候选人名称</th><th>投标下浮率（%）</th><th>工期</th><th>项目经理</th><th>注册建造师编号</th></tr></thead><tbody><tr><td>1</td><td>陕西化建工程有限责任公司</td><td>3.20</td><td>高万福</td><td>陕261101118738</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>2</td><td>甘肃省庆阳市设备安装有限责任公司</td><td>3.10</td><td>罗黎</td><td>甘262080905379</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>3</td><td>山东长庆建工有限公司</td><td>1.50</td><td>高亮</td><td>鲁1372023202404907</td><td>合同签订之日起至2027年3月31日</td></tr></tbody></table><h3>六标段</h3><table><thead><tr><th>排序</th><th>中标候选人名称</th><th>投标下浮率（%）</th><th>工期</th><th>项目经理</th><th>注册建造师编号</th></tr></thead><tbody><tr><td>1</td><td>陕西建工第八建设集团有限公司</td><td>27.00</td><td>汪玮</td><td>陕261141555427</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>2</td><td>陕西化建工程有限责任公司</td><td>26.00</td><td>邓宝军</td><td>陕261151800227</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>3</td><td>陕西建工安装集团有限公司</td><td>26.00</td><td>李卫安</td><td>陕261192019021</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>4</td><td>豪发建设有限公司</td><td>26.80</td><td>裴晓东</td><td>豫241202193644</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>5</td><td>河南广川建设有限公司</td><td>26.00</td><td>程广强</td><td>豫1412010201008389</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>6</td><td>陕西民和建工有限公司</td><td>26.18</td><td>周涛</td><td>陕1612020202101766</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>7</td><td>陕西利鑫源建设工程有限公司</td><td>25.90</td><td>冯祖翔</td><td>陕261192016495</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>8</td><td>中豫远通建设有限公司</td><td>26.50</td><td>武玉坤</td><td>豫2412023202406485</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>9</td><td>东昊建设集团有限公司</td><td>25.80</td><td>尚兵辉</td><td>豫241171719570</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>10</td><td>卓高建设集团有限公司</td><td>25.50</td><td>戴玲</td><td>豫241131341914</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>11</td><td>凯德利建设集团有限公司</td><td>25.90</td><td>王随根</td><td>豫241122050481</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>12</td><td>安徽陇海建设集团有限公司</td><td>25.70</td><td>彭广亚</td><td>皖234061895461</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>13</td><td>瀚景建设集团有限公司</td><td>29.00</td><td>吴鹏</td><td>皖1422016201724659</td><td>合同签订之日起至2027年3月31日</td></tr><tr><td>14</td><td>美华建安工程集团有限公司</td><td>26.90</td><td>张兵</td><td>豫1412019202000823</td><td>合同签订之日起至2027年3月31日</td></tr></tbody></table><p>注：各标段中标候选人工程质量均符合国家、行业相关规定和标准要求及招标文件要求。</p><h2>二、中标候选人响应招标文件要求的资格能力条件</h2><p>以上中标候选人的投标文件资格能力条件、质量均响应招标文件要求。</p><h2>三、提出异议的渠道和方式</h2><p>有关单位对此结果如有异议，请在公示期内向招标人或招标代理机构提出，并提交书面材料。书面材料应当包括下列内容：（一）单位名称、地址及有效联系方式；（二）所提异议的内容；（三）相关请求及主张；（四）有效线索和相关证明材料。备注：书面材料必须由其法定代表人或者授权代表签字并加盖单位公章。对招标人或招标代理机构答复不满意的，可以自知道或者应当知道之日起 10 日内向监管部门投诉。</p><h2>四、联系方式</h2><p>招标人：陕西延长石油（集团）有限责任公司炼化公司　地址：陕西省延安市洛川县　联系人：张勇　联系电话：0911-3818167<br/>招标代理机构：陕西德源招标有限责任公司　地址：陕西省西安市科技二路 75 号延长石油办公大厦　联系人：马强　联系电话：029-88443024</p></div>',NULL,NULL,NULL,NULL,NULL,'中标候选人公示',NULL,'Y','BELONGS',0.5000,'DYZB2025-11-G141',NULL,NULL,'炼化公司所属各单位 2026 年装置大修/消缺公开招标','陕西延长石油（集团）有限责任公司炼化公司','2026-09-06 12:25:22',NULL,NULL,'ACTIVE','APPROVED',NULL,'SOCIAL',2,'2026-09-06 16:46:33','NONE',NULL,NULL,NULL,'SUCCESS','{\"title\": \"炼化公司所属各单位2026年装置大修消缺公开招标中标候选人公示\", \"issuer\": \"陕西延长石油（集团）有限责任公司炼化公司\", \"tenderNo\": \"DYZB2025-11-G141\", \"bidOpenTime\": null, \"projectName\": \"炼化公司所属各单位2026年装置大修/消缺公开招标\", \"budgetAmount\": null, \"contactPhone\": \"09113818167\", \"industryFlag\": \"UNCERTAIN\", \"contactPerson\": \"张勇\", \"industryReason\": \"公告标题和正文提及装置大修消缺，但未明确涉及防腐保温等具体内容，无法判断是否属于管道防腐保温行业。\", \"industryConfidence\": 0.5, \"industryHitKeywords\": \"[]\", \"qualificationSummary\": \"中标候选人的投标文件资格能力条件、质量均响应招标文件要求。\"}','2026-09-06 14:56:16','HTML_STRUCTURE','SUCCESS','[{\"text\": \"炼化公司所属各单位2026年装置大修消缺公开招标中标候选人公示\", \"type\": \"heading\", \"level\": 1}, {\"text\": \"炼化公司所属各单位 2026 年装置大修/消缺公开招标（招标编号：DYZB2025-11-G141）评标工作已于 2025 年 12 月 22 日完成，现将评标结果予以公示，公示三日。\", \"type\": \"paragraph\"}, {\"text\": \"一、中标候选人基本情况\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"一标段\", \"type\": \"heading\", \"level\": 3}, {\"rows\": [[\"1\", \"陕西化建工程有限责任公司\", \"1.80\", \"秦成立\", \"陕261131443522\", \"合同签订之日起至2027年3月31日\"], [\"2\", \"山东华显安装建设有限公司\", \"1.50\", \"赵钦建\", \"鲁2372011202104294\", \"合同签订之日起至2027年3月31日\"], [\"3\", \"中国化学工程第四建设有限公司\", \"0.65\", \"李长虎\", \"湘1432017201771964\", \"合同签订之日起至2027年3月31日\"]], \"type\": \"table\", \"headers\": [\"排序\", \"中标候选人名称\", \"投标下浮率（%）\", \"工期\", \"项目经理\", \"注册建造师编号\"]}, {\"text\": \"二标段\", \"type\": \"heading\", \"level\": 3}, {\"rows\": [[\"1\", \"陕西化建工程有限责任公司\", \"1.70\", \"高宇\", \"陕261192023721\", \"合同签订之日起至2027年3月31日\"], [\"2\", \"中国化学工程第四建设有限公司\", \"0.90\", \"李长虎\", \"湘1432017201771964\", \"合同签订之日起至2027年3月31日\"], [\"3\", \"山东华显安装建设有限公司\", \"1.00\", \"王军\", \"鲁2372021202108056\", \"合同签订之日起至2027年3月31日\"]], \"type\": \"table\", \"headers\": [\"排序\", \"中标候选人名称\", \"投标下浮率（%）\", \"工期\", \"项目经理\", \"注册建造师编号\"]}, {\"text\": \"三标段\", \"type\": \"heading\", \"level\": 3}, {\"rows\": [[\"1\", \"陕西化建工程有限责任公司\", \"2.40\", \"符海彤\", \"陕261121340162\", \"合同签订之日起至2027年3月31日\"], [\"2\", \"甘肃一安建设科技集团有限公司\", \"2.60\", \"刘加生\", \"甘262161727943\", \"合同签订之日起至2027年3月31日\"], [\"3\", \"山东华显安装建设有限公司\", \"1.00\", \"赵钦建\", \"鲁2372011202104294\", \"合同签订之日起至2027年3月31日\"], [\"4\", \"中国化学工程第四建设有限公司\", \"0.50\", \"张军峰\", \"湘1432017201771772\", \"合同签订之日起至2027年3月31日\"]], \"type\": \"table\", \"headers\": [\"排序\", \"中标候选人名称\", \"投标下浮率（%）\", \"工期\", \"项目经理\", \"注册建造师编号\"]}, {\"text\": \"四标段\", \"type\": \"heading\", \"level\": 3}, {\"rows\": [[\"1\", \"陕西化建工程有限责任公司\", \"2.80\", \"严海超\", \"陕261141557331\", \"合同签订之日起至2027年3月31日\"], [\"2\", \"陕西永卓建设工程有限公司\", \"3.00\", \"王世涛\", \"陕261242407356\", \"合同签订之日起至2027年3月31日\"], [\"3\", \"信邦建设集团有限公司\", \"1.05\", \"李海涛\", \"鲁1372021202205990\", \"合同签订之日起至2027年3月31日\"], [\"4\", \"天人建设安装有限公司\", \"0.80\", \"汤颖\", \"湘1432019202000447\", \"合同签订之日起至2027年3月31日\"]], \"type\": \"table\", \"headers\": [\"排序\", \"中标候选人名称\", \"投标下浮率（%）\", \"工期\", \"项目经理\", \"注册建造师编号\"]}, {\"text\": \"五标段\", \"type\": \"heading\", \"level\": 3}, {\"rows\": [[\"1\", \"陕西化建工程有限责任公司\", \"3.20\", \"高万福\", \"陕261101118738\", \"合同签订之日起至2027年3月31日\"], [\"2\", \"甘肃省庆阳市设备安装有限责任公司\", \"3.10\", \"罗黎\", \"甘262080905379\", \"合同签订之日起至2027年3月31日\"], [\"3\", \"山东长庆建工有限公司\", \"1.50\", \"高亮\", \"鲁1372023202404907\", \"合同签订之日起至2027年3月31日\"]], \"type\": \"table\", \"headers\": [\"排序\", \"中标候选人名称\", \"投标下浮率（%）\", \"工期\", \"项目经理\", \"注册建造师编号\"]}, {\"text\": \"六标段\", \"type\": \"heading\", \"level\": 3}, {\"rows\": [[\"1\", \"陕西建工第八建设集团有限公司\", \"27.00\", \"汪玮\", \"陕261141555427\", \"合同签订之日起至2027年3月31日\"], [\"2\", \"陕西化建工程有限责任公司\", \"26.00\", \"邓宝军\", \"陕261151800227\", \"合同签订之日起至2027年3月31日\"], [\"3\", \"陕西建工安装集团有限公司\", \"26.00\", \"李卫安\", \"陕261192019021\", \"合同签订之日起至2027年3月31日\"], [\"4\", \"豪发建设有限公司\", \"26.80\", \"裴晓东\", \"豫241202193644\", \"合同签订之日起至2027年3月31日\"], [\"5\", \"河南广川建设有限公司\", \"26.00\", \"程广强\", \"豫1412010201008389\", \"合同签订之日起至2027年3月31日\"], [\"6\", \"陕西民和建工有限公司\", \"26.18\", \"周涛\", \"陕1612020202101766\", \"合同签订之日起至2027年3月31日\"], [\"7\", \"陕西利鑫源建设工程有限公司\", \"25.90\", \"冯祖翔\", \"陕261192016495\", \"合同签订之日起至2027年3月31日\"], [\"8\", \"中豫远通建设有限公司\", \"26.50\", \"武玉坤\", \"豫2412023202406485\", \"合同签订之日起至2027年3月31日\"], [\"9\", \"东昊建设集团有限公司\", \"25.80\", \"尚兵辉\", \"豫241171719570\", \"合同签订之日起至2027年3月31日\"], [\"10\", \"卓高建设集团有限公司\", \"25.50\", \"戴玲\", \"豫241131341914\", \"合同签订之日起至2027年3月31日\"], [\"11\", \"凯德利建设集团有限公司\", \"25.90\", \"王随根\", \"豫241122050481\", \"合同签订之日起至2027年3月31日\"], [\"12\", \"安徽陇海建设集团有限公司\", \"25.70\", \"彭广亚\", \"皖234061895461\", \"合同签订之日起至2027年3月31日\"], [\"13\", \"瀚景建设集团有限公司\", \"29.00\", \"吴鹏\", \"皖1422016201724659\", \"合同签订之日起至2027年3月31日\"], [\"14\", \"美华建安工程集团有限公司\", \"26.90\", \"张兵\", \"豫1412019202000823\", \"合同签订之日起至2027年3月31日\"]], \"type\": \"table\", \"headers\": [\"排序\", \"中标候选人名称\", \"投标下浮率（%）\", \"工期\", \"项目经理\", \"注册建造师编号\"]}, {\"text\": \"注：各标段中标候选人工程质量均符合国家、行业相关规定和标准要求及招标文件要求。\", \"type\": \"paragraph\"}, {\"text\": \"二、中标候选人响应招标文件要求的资格能力条件\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"以上中标候选人的投标文件资格能力条件、质量均响应招标文件要求。\", \"type\": \"paragraph\"}, {\"text\": \"三、提出异议的渠道和方式\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"有关单位对此结果如有异议，请在公示期内向招标人或招标代理机构提出，并提交书面材料。书面材料应当包括下列内容：（一）单位名称、地址及有效联系方式；（二）所提异议的内容；（三）相关请求及主张；（四）有效线索和相关证明材料。备注：书面材料必须由其法定代表人或者授权代表签字并加盖单位公章。对招标人或招标代理机构答复不满意的，可以自知道或者应当知道之日起 10 日内向监管部门投诉。\", \"type\": \"paragraph\"}, {\"text\": \"四、联系方式\", \"type\": \"heading\", \"level\": 2}, {\"text\": \"招标人：陕西延长石油（集团）有限责任公司炼化公司　地址：陕西省延安市洛川县　联系人：张勇　联系电话：0911-3818167 招标代理机构：陕西德源招标有限责任公司　地址：陕西省西安市科技二路 75 号延长石油办公大厦　联系人：马强　联系电话：029-88443024\", \"type\": \"paragraph\"}]','2026-09-06 17:39:56',NULL,NULL),(2096454687958171649,'1','2026-09-06 12:25:23','1','2026-09-09 11:32:46',_binary '\0','EBIDDING','秦源招标电子招标平台','陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目候选人公示','https://www.ebidding.com.cn/notice/detail?doc=CG202616295-X','59b668ef7ba6d03ad50dccc2532b12c835d6d0437987a22d2b4103bc22cda4bf','8a14fcfd2221ef4df667164c85c601fc05cd113f09896f086935d2dc914194c4','HTML','<div class=\"notice-html-demo\"><h1>陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目候选人公示</h1><p>陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目（项目编号：CG202616295）采购评审工作已完成，现将采购评审结果予以公示。公示时间自 2026 年 06 月 25 日 20 时 04 分开始，至 2026 年 06 月 29 日 17 时 00 分结束。</p><h2>一、中选候选人情况</h2><p>采购包 包1（CG202616295）：</p><table><thead><tr><th>排名</th><th>候选供应商名称</th><th>得分</th></tr></thead><tbody><tr><td>第1名</td><td>河南省宇祯建设有限公司</td><td>92.00</td></tr><tr><td>第2名</td><td>鸿川建筑产业集团有限公司</td><td>90.45</td></tr><tr><td>第3名</td><td>天信管业科技集团有限公司</td><td>89.45</td></tr></tbody></table><h2>二、中标候选人响应招标文件要求的资格能力条件</h2><p>以上中选候选人的投标文件资格能力条件、质量均符合采购文件要求。</p><h2>三、提出异议的渠道和方式</h2><p>有关单位对此结果如有异议，请在公示期内向采购人或采购代理机构提出，并提交电子材料。电子材料应当包括下列内容：（一）单位名称、地址及有效联系方式；（二）所提异议的内容；（三）相关请求及主张；（四）有效线索和相关证明材料。备注：电子材料必须由其法定代表人或者授权代表签字并加盖单位公章后，在延长石油非招标管理系统进行递交。对采购人或采购代理机构答复不满意的，可以自知道或者应当知道之日起 10 日内向监管部门投诉。</p><h2>四、联系方式</h2><p>采购单位：陕西液化天然气投资发展有限公司　地址：陕西省杨凌示范区渭惠路 35 号　联系人：李成　联系电话：029-87039550<br/>代理机构：国信国际工程咨询集团股份有限公司　地址：西安市南二环西段 58 号成长大厦 20 层　联系人：王文、张望、张海飞、刘洋　联系电话：18992749267</p></div>',NULL,NULL,NULL,NULL,NULL,'候选人公示',NULL,'Y','BELONGS',1.0000,'CG202616295',NULL,NULL,'陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目','陕西液化天然气投资发展有限公司','2026-09-06 12:25:23',NULL,NULL,'ACTIVE','APPROVED',NULL,'SOCIAL',2,'2026-09-06 16:46:34','NONE','陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目候选人公示 候选人公示陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目 （项目编号：CG202616295 ）采购工作工作已完成，现将采购评审结果予以公示。公示时间自 2026年06月25日20时04分 开始， 2026年06月29日 17时00分 结束。一、中选候选人情况采购包包1(CG202616295)：排名 候选供应商名称 得分第1名 河南省宇祯建设有限公司 92.00第2名 鸿川建筑产业集团有限公司 90.45第3名 天信管业科技集团有限公司 89.45二、中标候选人响应招标文件要求的资格能力条件以上 中选候选人的投标文件资格能力条件、质量均符合采购文件要求。三、提出异议的渠道和方式有关单位对此结果如有异议，请在公示期内向招标人或招标代理机构提出，并提交电子材料。电子材料应当包括下列内容：（一）单位名称、地址及有效联系方式；（二）所提异议的内容；（三）相关请求及主张；（四）有效线索和相关证明材料。备注：电子材料必须由其法定代表人或者授权代表签字并加盖单位公章后，在延长石油非招标管理系统进行递交。对招标人或招标代理机构答复不满意的，可以自知道或者应当知道之日起10日内向监管部门投诉。四、联系方式采购单位： 陕西液化天然气投资发展有限公司采购单位地址： 陕西省杨凌示范区渭惠路35号采购联系人： 李成联系电话： 029-87039550国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司代理机构：国信国际工程咨询集团股份有限公司代理机构地址：西安市南二环西段58号成长大厦20层代理机构联系人：王文、张望、张海飞、刘洋联系电话：18992749267国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司国信国际工程咨询集团股份有限公司',NULL,'2026-09-06 14:56:13','SUCCESS','{\"title\": \"陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目候选人公示\", \"issuer\": \"陕西液化天然气投资发展有限公司\", \"projectName\": \"陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目\", \"publishTime\": \"2026-06-25T20:04:00\", \"industryFlag\": \"BELONGS\", \"industryReason\": \"项目名称和正文明确包含‘防腐、保温、保冷维修’，属于设备设施的防腐保温保冷施工，符合行业判定标准。\", \"industryConfidence\": 1.0}','2026-09-09 11:32:46',NULL,'NONE',NULL,NULL,NULL,NULL),(2096454689849802754,'1','2026-09-06 12:25:23','1','2026-09-07 09:24:32',_binary '\0','EBIDDING','秦源招标电子招标平台','陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目成交结果公告','https://www.ebidding.com.cn/notice/detail?doc=CG202616295-CJ','ce8968593a60d72236d8235af93437905b0cbef0ef30e82a157753068c24e76a','516f291b4450e42c039a198c121868dd65f6df2da29c0826c3fa1ec900675643','HTML','<div class=\"notice-html-demo\"><h1>陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目成交结果公告</h1><p>陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目（项目编号：CG202616295）成交结果公示如下：</p><h2>一、中选人信息</h2><p>采购包 包1（CG202616295）：</p><table><thead><tr><th>序号</th><th>候选供应商名称</th><th>中选金额（元）</th></tr></thead><tbody><tr><td>1</td><td>河南省宇祯建设有限公司</td><td>2,297,150.00</td></tr></tbody></table><h2>二、其他说明</h2><p>本项目评审办法为综合评分法，中选人的投标文件资格能力条件、质量均符合采购文件要求。</p><h2>三、联系方式</h2><p>采购单位：陕西液化天然气投资发展有限公司　地址：陕西省杨凌示范区渭惠路 35 号　联系人：李成　联系电话：15029981404<br/>代理机构：国信国际工程咨询集团股份有限公司　地址：西安市南二环西段 58 号成长大厦 20 层　联系人：张望　联系电话：18182414030</p></div>',NULL,NULL,NULL,NULL,NULL,'成交结果公告',NULL,'Y','BELONGS',1.0000,'CG202616295',NULL,NULL,'陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目','陕西液化天然气投资发展有限公司','2026-09-06 12:25:23',NULL,NULL,'ACTIVE','APPROVED',NULL,'SOCIAL',2,'2026-09-06 16:46:35','NONE',NULL,NULL,NULL,'SUCCESS','{\"title\": \"陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目成交结果公告\", \"issuer\": \"陕西液化天然气投资发展有限公司\", \"tenderNo\": \"CG202616295\", \"bidOpenTime\": null, \"projectName\": \"陕西液化天然气投资发展有限公司杨凌LNG工厂年度防腐、保温、保冷维修项目\", \"budgetAmount\": null, \"contactPhone\": \"15029981404\", \"industryFlag\": \"BELONGS\", \"contactPerson\": \"李成\", \"industryReason\": \"项目名称及内容明确涉及LNG工厂的防腐、保温、保冷维修，属于管道及设备防腐保温行业。\", \"industryConfidence\": 1.0, \"industryHitKeywords\": \"[\\\"防腐\\\",\\\"保温\\\",\\\"保冷\\\",\\\"维修\\\"]\", \"qualificationSummary\": \"\"}','2026-09-06 14:56:05',NULL,'NONE',NULL,NULL,NULL,NULL),(2097523389751267329,NULL,'2026-09-09 11:12:01','1','2026-09-09 12:47:06',_binary '\0','QYZB','秦源电子招标平台','陕西涌鑫矿业有限责任公司安山煤矿、沙梁煤矿采空区采洞封堵工程询比采购公告','https://qyzb.shccmg.com/cms/default/webfile/2ywgg/20260909/1282283182586068992.html','727dcda611197805073a02922913d7a74cf57b61ec73b3b770d6a39c5feca4c4','04ee8154d378d8cf8afbc0a47d82c3c415f761f92d10e2a0bfc2c2632b1772f0','HTML','<h1 style=\"text-align: center;\"><strong><span style=\"font-family: 宋体; font-size: 16pt;\">陕西涌鑫矿业有限责任公司安山煤矿、沙梁煤矿采空区采洞封堵工程</span></strong></h1>\n<h1 style=\"text-align: center;\"><strong><span style=\"font-family: 宋体; font-size: 16pt;\">询比采购公告</span></strong></h1>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">陕西涌鑫矿业有限责任公司安山煤矿、沙梁煤矿采空区采洞封堵工程，采购人为陕西涌鑫矿业有限责任公司，项目资金来自企业自筹，出资比例为</span><span style=\"font-family: 宋体;\">100%。项目已具备采购条件，陕西秦源招标有限责任公司受陕西涌鑫矿业有限责任公司委托，现公开邀请供应商参加询比采购活动，相关事项告知如下：</span></span></p>\n<h2><a name=\"_Toc186043182\"></a><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\"><a name=\"_Toc7450\"></a><a name=\"_Toc14583\"></a><span style=\"font-family: 宋体;\">一</span></span></strong><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">、采购项目简介</span></strong></h2>\n<p class=\"17\"><a name=\"_Toc24169\"></a><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">1.1 采购项目名称：陕西涌鑫矿业有限责任公司安山煤矿、沙梁煤矿采空区采洞封堵工程（采购编号：0866-26E1SXQY0885）</span><span style=\"font-family: 宋体;\">。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">1.2 采购人：陕西涌鑫矿业有限责任公司</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">1.3 采购机构：陕西秦源招标有限责任公司</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">1.4 采购项目资金落实情况：企业自筹，出资率100%</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">1.5 采购项目概况：详见第</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">七</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">章</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">技术标准和要求</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">&nbsp;</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">1.6 成交供应商数量及成交份额：一家，成交份额100%</span></p>\n<h2><a name=\"_Toc12250\"></a><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\"><a name=\"_Toc186043183\"></a><a name=\"_Toc25491\"></a><span style=\"font-family: 宋体;\">二</span></span></strong><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">、采购范围及相关要求</span></strong></h2>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">2.1 </span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">项目概况</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">：</span></p>\n<p class=\"17\"><span class=\"15\" style=\"mso-spacerun: \'yes\'; font-family: \'Times New Roman\'; mso-ascii-font-family: 宋体; mso-hansi-font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">根据府谷县人民政府办公室关于全县煤炭</span><span style=\"font-family: 宋体;\">&ldquo;黑口子&rdquo;大排查工作的通知（府政办发[2026]4号）文件要求对安山煤矿、沙梁煤矿井田范围内排查出的&ldquo;黑口子&rdquo;进行封堵，确保矿井开采安全。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">2.</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">2</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"> <span style=\"font-family: 宋体;\">采购范围：</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（一）</span>245202工作面小煤窑封堵</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">1.首先对12处小煤窑口处淤泥进行整体开挖，开挖面积231m</span><sup><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt; vertical-align: super;\">2</span></sup><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">，厚度：</span>1.5米，深度约2m，挖出的淤泥放置于附近空地，封堵完成后可用于回填；对12处小煤窑口施工密闭墙对其进行封闭，密闭墙用c30混凝土进行整体浇筑，底部须掏槽0.5m，保证不渗水；密闭墙宽5.5m，高3.5m，厚1m；</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">2.7号坑整体回填分别面积为：57.5㎡：表土层C30混凝土0.5米+黄土防渗层（&gt;1.5m）+碎石衬垫层（&gt;1m）；</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">3.过沟段治理区：机械清理河槽1.85km，平均宽度6.7米，平均厚度0.5米；山体两侧人工配合机械修筑土坝宽度0.6米，高度1米；铺设防水布长度48米，宽度9米，上层铺设3:7灰土，厚度0.5米。</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">4.临时便道修复：挖机修复临时便道长度860米，宽度：3.5米,开挖土方935m&sup3;；</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（二）根据府谷县政府办及自然资源和规划局相关部门勘察两矿井田内需封堵的</span><span style=\"font-family: 宋体;\">&ldquo;黑口子&rdquo;共计33处，其中安山煤矿22处，沙梁煤矿11处。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">封堵做法对</span>33处小煤窑口处淤泥进行整体开挖，开挖面积480.5m</span><sup><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt; vertical-align: super;\">2</span></sup><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">，深度约</span>2m，挖出的淤泥放置于附近空地，封堵完成后可用于回填；对33处小煤窑口施工密闭墙对其进行封闭，密闭墙用c30混凝土进行整体浇筑，底部须掏槽0.5m，保证不渗水；安山煤矿28处及沙梁煤矿密闭墙平均宽3.5m，高3m，厚1m，安山煤矿5处平均宽度：4.5米，高度3.6米，厚1.35米。</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">机械修临时便道长度共计：</span>3.2km，恢复耕地：1450</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">m</span><sup><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt; vertical-align: super;\"><span style=\"font-family: 宋体;\">2</span></span></sup><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">；所有材料进行二次倒运：平均距离：</span>450米。</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">2.</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">3</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"> <span style=\"font-family: 宋体;\">工期：</span></span><span class=\"15\" style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">合同生效后，</span><span style=\"font-family: 宋体;\">20日历天内完成所有施工内容</span></span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">。</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">2.</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">4</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"> <span style=\"font-family: 宋体;\">质保期：</span></span><span class=\"15\" style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">工程竣工验收合格后，质保</span><span style=\"font-family: 宋体;\">1年</span></span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">。</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">2.</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">5</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"> <span style=\"font-family: 宋体;\">质量要求：</span></span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">满足国家相关工程质量规范标准，建设单位验收并符合要求</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">。</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">2.</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">6</span> <span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">施工地点</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">：陕西涌鑫矿业有限责任公司安山、</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">沙梁煤矿</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">。</span></p>\n<h2><a name=\"_Toc28891\"></a><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\"><a name=\"_Toc186043184\"></a><a name=\"_Toc19720\"></a><span style=\"font-family: 宋体;\">三</span></span></strong><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">、供应商资格要求</span></strong></h2>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">3.1 供应商应满足如下要求：</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">1）依法设立：供应商须是中国境内合法注册的独立法人或其他组织。企业法人应提供有效的营业执照，事业法人应提供事业单位法人证书，其他组织应提供合法登记证明文件。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">2）资质要求：供应商必须具有建筑工程施工总承包三级（含）以上资质，并具备有效的安全生产许可证。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">（</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">3</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">）业绩要求：供应商须提供</span><span style=\"font-family: 宋体;\">2023年</span></span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">5</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">月</span><span style=\"font-family: 宋体;\">1日至询比截止时间类似项目业绩至少1项（证明材料以合同为准，至少包含合同首页、签订日期、合同内容及双方盖章页，有效业绩时间以合同签订日期为准）。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">4）信誉要求：具有良好的社会信誉，未被国家企业信用信息公示系统（http://www.gsxt.gov.cn/index.html）列入严重违法失信名单（黑名单）；未被列入&ldquo;信用中国&rdquo;（http://www.creditchina.gov.cn/）严重失信主体名单（以上条款提供承诺书或相关证明材料）。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">5）承担本项目的主要人员要求：拟派项目负责人须具备建筑工程二级（含）以上注册建造师执业资格证书，并具有有效的B类《安全生产考核合格证书》。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">6）其他要求：</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">①单位负责人为同一人或者存在控股、管理关系的不同单位，不得参加同一项目；</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">②提供授权委托代理人近5个月其中连续3个月的社保证明或个人所得税缴纳记录（法定代表人参加可不提供）；</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">③供应商</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">及其项目负责人基本信息应在</span><span style=\"font-family: 宋体;\">&ldquo;陕西省（或全国）建筑市场监管公共服务平台&rdquo;可查询，提供查询截图。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">3.2 供应商不得存在下列情形之一（提供承诺书或相关证明材料）：</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">处于被责令停产停业、暂扣或者吊销执照、暂扣或者吊销许可证、吊销资质证书状态；进入清算程序，或被宣告破产，或其他丧失履约能力的情形。</span></p>\n<p class=\"17\"><span style=\"font-family: 宋体; font-size: 10.5pt;\">3.3 本次采购</span><span style=\"font-family: 宋体; font-size: 10.5pt;\">不</span><span style=\"font-family: 宋体; font-size: 10.5pt;\">接受联合体。</span></p>\n<h2><a name=\"_Toc186043185\"></a><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\"><a name=\"_Toc22864\"></a><a name=\"_Toc11898\"></a><span style=\"font-family: 宋体;\">四</span></span></strong><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">、采购文件的获取</span></strong></h2>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">4.1凡有意参加询比者请登录&ldquo;秦源招标电子招标平台&rdquo;</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">网址为：</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">http://qyzb.shccmg.com/）下载询比采购文件，具体操作步骤如下：</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">1）登录&ldquo;秦源招标电子招标平台&rdquo;，按照提示信息进行询比人注册；</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">2）注册成功后，登录系统，在【我要参与】列表中找到所要参加的项目，点击【立即参与】-【确定】，获取</span></span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">询比</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">资格。完成后在【我的项目】列表中找到已参与的项目，上传授权委托书及</span><span style=\"font-family: 宋体;\">&ldquo;三、</span></span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">供应商</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">资格要求</span><span style=\"font-family: 宋体;\">&rdquo;所有资料的扫描件，点击【提交】。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">3）有关注册、下载询比采购文件、上传电子文件等有关问题可咨询&ldquo;秦源招标电子招标平台&rdquo;客服，客服电话：400-685-1179，邮箱：qyebidding@163.com。咨询日期：法定工作日，咨询时间：08:30-12:00，13：30-17:30。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">秦源招标二期电子招标系统的供应商操作手册及开标注意事项：</span><a href=\"../cms/default/webfile/cywj/20241202/1048174459543879680.html%E3%80%82\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">https://qyzb.shccmg.com/cms/default/webfile/cywj/20241202/1048174459543879680.html。</span></a></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">4.2询比采购文件费用：500元，通过秦源招标电子招标平台支付，售后不退。</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">4.3购买询比采购文件的发票，待采购机构开出后供应商可在平台打印（购买询比采购文件发票仅提供电子增值税普通发票）。</span></p>\n<p class=\"17\"><a name=\"_Toc6936\"></a><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">4.4询比采购文件购买时间：202</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">6</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">年</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">9月9日</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">至</span><span style=\"font-family: 宋体;\">202</span></span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">6</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">年</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">9</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">月</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">14</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">日，每日上午</span> <span style=\"font-family: 宋体;\">8</span></span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">:</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">00时至12</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">:</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">00时，下午14</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">:</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">00时至18</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">:</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">00时（北京时间）。</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">4.5采购文件获取不足三家时，本次采购活动终止，</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">采购人重新组织采购</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">。</span></p>\n<h2><a name=\"_Toc186043187\"></a><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\"><a name=\"_Toc29163\"></a><a name=\"_Toc25509\"></a><span style=\"font-family: 宋体;\">五</span></span></strong><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">、</span></strong><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">响应文件的递交</span></strong></h2>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">5.1递交截止时间：202</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">6</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">年</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">9</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">月</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">19</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">日上午</span><span style=\"font-family: 宋体;\">9:00（北京时间）。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">5.2递交方式：将电子文件上传递交至&ldquo;秦源招标电子招标平台&rdquo;，未在询比开始时间前成功递交的响应文件将被拒绝接收。</span></p>\n<h2><a name=\"_Toc1668\"></a><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\"><a name=\"_Toc6678\"></a><a name=\"_Toc24274\"></a><span style=\"font-family: 宋体;\">六</span></span></strong><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">、询比时间和地点</span></strong></h2>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">6.1询比开始时间：202</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">6</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">年</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">9</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">月</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">19</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">日上午</span><span style=\"font-family: 宋体;\">9:00（北京时间）。</span></span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">6.2询比地点：</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">秦源招标电子招标平台。</span></p>\n<h2><a name=\"_Toc26541\"></a><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">七</span></strong><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">、发布公告媒介</span></strong></h2>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">本次询比采购公告在</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">中国招标投标公共服务平台</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">（</span><span style=\"font-family: 宋体;\">http://www.cebpubservice.com/）</span></span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">、</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">陕西采购与招标网（</span><span style=\"font-family: 宋体;\">http://www.sntba.com）及秦源招标电子招标平台（http://qyzb.shccmg.com）上同时发布。</span></span></p>\n<h2><a name=\"_Toc16042\"></a><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">八、项目监督</span></strong></h2>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">监督部门：</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">陕西涌鑫矿业有限责任公司企业管理部</span><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">；</span></p>\n<p class=\"17\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">监督邮箱：</span><a href=\"mailto:yxkyaudit@yeah.net\"><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt; mso-font-kerning: 1.0000pt;\">yxkyaudit@yeah.net</span></a><span style=\"mso-spacerun: \'yes\'; font-family: 宋体; font-size: 10.5000pt;\">。</span></p>\n<h2><a name=\"_Toc759\"></a><strong><span style=\"font-family: 宋体; font-size: 10.5pt;\">九、联系方式</span></strong></h2>\n<table class=\"MsoNormalTable\" style=\"border-collapse: collapse; margin-left: -11.0000pt; mso-table-layout-alt: fixed; border: none; mso-padding-alt: 0.0000pt 5.4000pt 0.0000pt 5.4000pt;\" border=\"0\" cellspacing=\"0\">\n<tbody>\n<tr style=\"height: 105.9500pt;\">\n<td style=\"width: 224.0500pt; padding: 0.0000pt 5.4000pt 0.0000pt 5.4000pt; border: none;\" valign=\"top\" width=\"298\">\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\">采购人：陕西涌鑫矿业有限责任公司</span></p>\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\">地址：</span><span style=\"font-family: 宋体; font-size: 10.5000pt;\">陕西省榆林市府谷县庙沟门镇</span></p>\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\">联系人：</span><span style=\"font-family: 宋体; font-size: 10.5000pt;\">马先生</span></p>\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\">电话：</span><span style=\"font-family: 宋体; font-size: 10.5000pt;\">0912-8825030</span></p>\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\">&nbsp;</span></p>\n</td>\n<td style=\"width: 234.3000pt; padding: 0.0000pt 5.4000pt 0.0000pt 5.4000pt; border: none;\" valign=\"top\" width=\"312\">\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\">采购机构：陕西秦源招标有限责任公司</span></p>\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">地址：陕西省神木市陕煤集团杏花园办公基地</span><span style=\"font-family: 宋体;\">1号楼</span></span><span style=\"font-family: 宋体; font-size: 10.5000pt;\">1102室</span></p>\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\">联系人：招标五部</span> <span style=\"font-family: 宋体; font-size: 10.5000pt;\">刘泽</span></p>\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\">电话：</span><span style=\"font-family: 宋体; font-size: 10.5000pt;\">18192595569</span><span style=\"font-family: 宋体; font-size: 10.5000pt;\">&nbsp;</span></p>\n<p class=\"17\" style=\"text-indent: 0.0000pt; mso-char-indent-count: 0.0000; text-autospace: ideograph-numeric; mso-pagination: widow-orphan;\"><span style=\"font-family: 宋体; font-size: 10.5000pt;\"><span style=\"font-family: 宋体;\">邮箱：</span><span style=\"font-family: 宋体;\">1003363966@qq.com</span></span></p>\n</td>\n</tr>\n</tbody>\n</table>',NULL,NULL,NULL,NULL,NULL,'非招标公告',NULL,NULL,NULL,NULL,'0866-26E1SXQY0885',NULL,NULL,'1.1 采购项目名称：陕西涌鑫矿业有限责任公司安山煤矿、沙梁煤矿采空区采洞封堵工程','陕西涌鑫矿业有限责任公司','2026-09-09 00:00:00',NULL,NULL,'ACTIVE','APPROVED','QYZB_JSON','SOCIAL',1,'2026-09-09 11:12:01','NONE',NULL,NULL,NULL,'NONE',NULL,NULL,NULL,'NONE',NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `bidding_notice_external` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- ========== ⑦ AI 链路启用状态（目标态，幂等） ==========
UPDATE ai_chat_role SET status = 0, model_id = 66 WHERE id = 20;
UPDATE ai_model SET status = 0 WHERE id = 66;
UPDATE ai_api_key SET status = 0 WHERE id = 28;

-- ========== 校验（应返回：菜单 30 / 源 3 / AI 角色启用） ==========
SELECT COUNT(*) AS bidding_menus FROM system_menu WHERE id BETWEEN 6735 AND 6765;
SELECT source_code, source_name FROM bidding_fetch_source;
SELECT id, name, status FROM ai_chat_role WHERE id = 20;
