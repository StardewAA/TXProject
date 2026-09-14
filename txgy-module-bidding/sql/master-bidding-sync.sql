-- ============================================================================
-- bidding 模块「初始状态」SQL 包（表结构 + 最小启动数据；幂等，可重复执行）
-- 生成时间：见 git 提交（表结构取自包内既有 DDL；菜单/采集源按开发库当前目标态导出）
-- 用途：新环境/新库一键初始化 —— 建表 → 菜单可见 → 爬虫可启动，即可投入使用。
--
-- 包含（**顺序有讲究**：爬虫必需项排在菜单之前）：
--   ① 业务表结构 21 张（CREATE TABLE IF NOT EXISTS；字段语义见模块设计文档）
--   ② 采集源配置（EBIDDING / QYZB / GX —— 爬虫启动所需的最小数据）
--   ③ 原文下载限速闸位 GLOBAL 单行（合规频控 2 分钟/次；缺行时程序亦会自愈补建）
--   ④ 菜单 6735~6776（招投标及其子菜单/按钮权限，属 system 服务库）
--   ⑤ 角色菜单授权（system_role_menu，同上）
--
-- 为什么这个顺序（2026-09-11 事故复盘）：mysql 客户端**遇到第一个错误就中止**执行。旧版把限速闸/审计表
--   排在菜单段之后，目标库若没有 system_menu（菜单属 system 服务库），菜单段报错即中止 → 闸位表没建 →
--   爬虫每个任务都在闸位处抛错、退避后进死信、下一轮再造新任务 = 「无限重试」。现在：全部表结构集中在 ①，
--   爬虫必需的两段（②③）排在菜单（④⑤）之前——即使菜单段因库不同而失败，爬虫仍然可用。
--   库表缺失时应用启动会打 ERROR 并**自动停掉爬虫调度**（BiddingSchemaHealthCheck），不再空转重试。
--
-- 不包含（用户口径 2026-09-11「不要数据…不需要任务和公告的数据」）：
--   采集任务/批次、公告/附件、主体授权与档案、AI 渠道与密钥、演示公告等业务数据与凭据；
--   原包中相关种子已归档到 sql/history/2026-09-11-master-sync-removed-seeds.sql（不在本包内执行）。
--
-- 执行：mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < master-bidding-sync.sql
--   若目标库没有 system_menu（菜单在 system 服务库）：本包会在 ④ 处报错中止，但前 ①②③ 已生效（爬虫可用），
--   随后把 ④⑤ 在 system 库单独执行即可；也可用 mysql --force 继续执行到结束。
-- 幂等：结构 IF NOT EXISTS、数据 INSERT IGNORE；包内无 DROP / TRUNCATE / DELETE，
--   重复执行不改变既有行，也不清空任何业务数据。
-- ============================================================================
SET NAMES utf8mb4;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;

-- ========== ① 业务表结构（21 张，全部 IF NOT EXISTS） ==========

CREATE TABLE IF NOT EXISTS `bidding_fetch_job` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `source_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SCHEDULE',
  `scope` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RUNNING',
  `produced` bit(1) NOT NULL DEFAULT b'0',
  `summary_json` json DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_job_status` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_fetch_source` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `source_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `allowed_hosts` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entry_urls_json` json DEFAULT NULL,
  `channel_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fetch_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CRAWLER',
  `api_config_json` json DEFAULT NULL,
  `parser_code_default` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fetch_interval_seconds` int NOT NULL DEFAULT '3600',
  `robots_ok` bit(1) NOT NULL DEFAULT b'0',
  `enabled` bit(1) NOT NULL DEFAULT b'0',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_code` (`source_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_fetch_task` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `job_id` bigint DEFAULT NULL,
  `source_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `url_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parser_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority` int NOT NULL DEFAULT '0',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `attempt` int NOT NULL DEFAULT '0',
  `next_retry_time` datetime DEFAULT NULL,
  `worker_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `message` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_job_url` (`job_id`,`url_hash`,`source_code`,`deleted`),
  KEY `idx_task_status_retry` (`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_filing` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `filing_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `project_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `tender_project_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `unified_deal_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tender_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `org_form` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `supervise_dept_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `supervise_dept_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `submit_time` datetime DEFAULT NULL,
  `data_timestamp` datetime DEFAULT NULL,
  `extra_json` json DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `receipt_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `receipt_time` datetime DEFAULT NULL,
  `opinion` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_filing_power` (`filing_type`,`project_code`,`deleted`,`tenant_id`),
  UNIQUE KEY `uk_filing_receipt` (`receipt_no`),
  KEY `idx_filing_status_time` (`status`,`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_follow_item` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `user_id` bigint NOT NULL,
  `follow_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `follow_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `issuer_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stage_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'UNKNOWN',
  `stage_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `base_json` json DEFAULT NULL,
  `latest_dynamic_time` datetime DEFAULT NULL,
  `unread_count` int NOT NULL DEFAULT '0',
  `follow_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FOLLOWING',
  `follow_time` datetime DEFAULT NULL,
  `unfollow_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_item_user_key` (`tenant_id`,`user_id`,`follow_type`,`follow_key`,`deleted`),
  KEY `idx_follow_item_stage` (`tenant_id`,`user_id`,`stage_code`),
  KEY `idx_follow_item_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_follow_item_event` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `user_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `notice_id` bigint DEFAULT NULL,
  `event_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title_snapshot` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `orig_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `occurred_at` datetime NOT NULL,
  `read_flag` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_key` (`event_key`,`deleted`),
  KEY `idx_event_item` (`tenant_id`,`user_id`,`item_id`,`read_flag`),
  KEY `idx_event_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_notice` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `notice_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `notice_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `project_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `project_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publisher_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publish_time` datetime DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `internal_flag` bit(1) NOT NULL DEFAULT b'0',
  `public_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SOCIAL',
  `fields_json` json DEFAULT NULL,
  `pdf_file_id` bigint DEFAULT NULL,
  `pdf_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pdf_size` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_code` (`notice_code`,`deleted`,`tenant_id`),
  KEY `idx_notice_type_time` (`notice_type`,`publish_time`),
  KEY `idx_notice_project` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_notice_content` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `notice_id` bigint NOT NULL,
  `content_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BODY',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `version` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_version` (`notice_id`,`content_type`,`version`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_notice_external` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `source_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `orig_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `orig_url_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_html` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `pdf_file_id` bigint DEFAULT NULL,
  `pdf_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pdf_size` bigint DEFAULT NULL,
  `pdf_download_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_ids_json` json DEFAULT NULL,
  `notice_type_raw` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notice_type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `legal_flag` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `industry_flag` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `industry_confidence` decimal(5,4) DEFAULT NULL,
  `project_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tender_project_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bid_section_codes` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `project_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `issuer_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publish_time` datetime DEFAULT NULL,
  `effective_from` datetime DEFAULT NULL,
  `effective_to` datetime DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW',
  `audit_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNREVIEWED',
  `parser_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `public_scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SOCIAL',
  `fetch_count` int NOT NULL DEFAULT '0',
  `last_fetch_time` datetime DEFAULT NULL,
  `extraction_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `extraction_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `extraction_tables_json` json DEFAULT NULL,
  `extraction_at` datetime DEFAULT NULL,
  `ai_extraction_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `ai_meta_json` json DEFAULT NULL,
  `ai_extract_at` datetime DEFAULT NULL,
  `body_parser_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body_blocks_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `body_blocks_json` json DEFAULT NULL,
  `body_blocks_at` datetime DEFAULT NULL,
  `meta_json` json DEFAULT NULL,
  `extra_raw` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_orig_url_hash` (`orig_url_hash`,`deleted`),
  KEY `idx_external_status_time` (`status`,`publish_time`),
  KEY `idx_external_project` (`project_code`),
  KEY `idx_external_type_time` (`content_type`,`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_notice_external_attachment` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `notice_id` bigint NOT NULL,
  `file_id` bigint NOT NULL,
  `sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `size` bigint DEFAULT NULL,
  `name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ext` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `orig_download_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `virus_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  PRIMARY KEY (`id`),
  KEY `idx_att_notice` (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_notice_external_meta` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `notice_id` bigint NOT NULL,
  `meta_json` json DEFAULT NULL,
  `extra_raw` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meta_notice` (`notice_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_notice_subscription` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `user_id` bigint NOT NULL,
  `target_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `follow_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FOLLOWING',
  `follow_time` datetime DEFAULT NULL,
  `unfollow_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_user_target` (`tenant_id`,`user_id`,`target_type`,`target_key`,`deleted`),
  KEY `idx_sub_user` (`tenant_id`,`user_id`),
  KEY `idx_sub_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_notice_subscription_event` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `user_id` bigint NOT NULL,
  `follow_event_id` bigint DEFAULT NULL,
  `notice_id` bigint DEFAULT NULL,
  `channel` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `sent_time` datetime DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_se_user_status` (`tenant_id`,`user_id`,`channel_status`),
  KEY `idx_se_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_subject_auth` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  `tenant_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `subject_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `auth_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `authorize_user_id` bigint DEFAULT NULL,
  `authorize_time` datetime DEFAULT NULL,
  `disable_time` datetime DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_auth_tenant_type` (`tenant_id`,`subject_type`,`deleted`),
  KEY `idx_subject_auth_status` (`auth_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_subject_profile` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `subject_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `uscc` char(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `legal_person_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `legal_person_type_code` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `region_code` char(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `industry_code` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reg_capital` decimal(20,6) DEFAULT NULL,
  `reg_cap_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '156',
  `contact_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `license_file_id` bigint DEFAULT NULL,
  `license_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ext_json` json DEFAULT NULL,
  `complete_flag` bit(1) NOT NULL DEFAULT b'0',
  `complete_time` datetime DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_profile_type` (`tenant_id`,`subject_type`,`deleted`),
  KEY `idx_subject_profile_uscc` (`uscc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bidding_subject_user` (
  `id` bigint NOT NULL,
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  `subject_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `user_nickname` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPERATOR',
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_user_bind` (`tenant_id`,`subject_type`,`user_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bidding_project (
  id                    bigint       NOT NULL COMMENT '主键（雪花）',
  tenant_id             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨，拦截器自动维护）',
  project_code          varchar(64)  NOT NULL COMMENT '项目编号（数据集 ProjectVO.PROJECT_CODE，租户内唯一）',
  project_name          varchar(512) NOT NULL COMMENT '项目名称（PROJECT_NAME）',
  region_code           varchar(16)  DEFAULT NULL COMMENT '行政区划代码（REGION_CODE，GB/T 2260）',
  invest_project_code   varchar(64)  DEFAULT NULL COMMENT '投资项目统一代码（INVEST_PROJECT_CODE）',
  relate_code           varchar(64)  DEFAULT NULL COMMENT '关联项目编号（RELATE_CODE）',
  address               varchar(512) DEFAULT NULL COMMENT '建设地点（ADDRESS）',
  legal_person          varchar(128) DEFAULT NULL COMMENT '法定代表人（LEGAL_PERSON）',
  industries_type       varchar(32)  DEFAULT NULL COMMENT '行业类别（INDUSTRIES_TYPE，GB/T 4754）',
  fund_source           varchar(64)  DEFAULT NULL COMMENT '资金来源（FUND_SOURCE）',
  contribution_scale    varchar(64)  DEFAULT NULL COMMENT '出资比例（CONTRIBUTION_SCALE）',
  project_scale         varchar(512) DEFAULT NULL COMMENT '项目规模（PROJECT_SCALE）',
  approval_dept_name    varchar(128) DEFAULT NULL COMMENT '审批部门（APPROVAL_DEPT_NAME）',
  approval_dept_code    varchar(32)  DEFAULT NULL COMMENT '审批部门代码（APPROVAL_DEPT_CODE）',
  approval_doc_no       varchar(128) DEFAULT NULL COMMENT '审批文号（APPROVAL_DOC_NO）',
  state                 varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '内部管理态：ACTIVE=进行中 / CLOSED=已结束（数据集无状态字段，为本模块管理需要自建）',
  fields_json           json         DEFAULT NULL COMMENT 'ProjectVO 数据集全量字段快照（key=规范英文名，缺失补 null）',
  remark                varchar(512) DEFAULT NULL COMMENT '备注',
  creator               varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater               varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_code (tenant_id, project_code, deleted),
  KEY idx_project_name (project_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目主档（数据集 ProjectVO）';

CREATE TABLE IF NOT EXISTS bidding_tender_project (
  id                       bigint       NOT NULL COMMENT '主键（雪花）',
  tenant_id                bigint       NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
  tender_project_code      varchar(64)  NOT NULL COMMENT '招标项目编号（TenderProjectVO.TENDER_PROJECT_CODE，租户内唯一，全链路追溯主键）',
  project_code             varchar(64)  DEFAULT NULL COMMENT '所属项目编号（PROJECT_CODE，关联 bidding_project.project_code）',
  tender_project_name      varchar(512) NOT NULL COMMENT '招标项目名称（TENDER_PROJECT_NAME）',
  tender_project_type      varchar(32)  DEFAULT NULL COMMENT '招标项目类型（TENDER_PROJECT_TYPE）',
  region_code              varchar(16)  DEFAULT NULL COMMENT '行政区划（REGION_CODE）',
  tender_content           text         COMMENT '招标内容（TENDER_CONTENT）',
  tenderer_name            varchar(256) DEFAULT NULL COMMENT '招标人名称（TENDERER_NAME）',
  tenderer_code_type       varchar(8)   DEFAULT NULL COMMENT '招标人代码类型（TENDERER_CODE_TYPE）',
  tenderer_code            varchar(64)  DEFAULT NULL COMMENT '招标人代码（TENDERER_CODE）',
  tender_agency_name       varchar(256) DEFAULT NULL COMMENT '招标代理机构名称（TENDER_AGENCY_NAME）',
  tender_agency_code_type  varchar(8)   DEFAULT NULL COMMENT '代理机构代码类型（TENDER_AGENCY_CODE_TYPE）',
  tender_agency_code       varchar(64)  DEFAULT NULL COMMENT '代理机构代码（TENDER_AGENCY_CODE）',
  tender_mode              varchar(16)  DEFAULT NULL COMMENT '招标方式（TENDER_MODE）',
  qulification_check_mode  varchar(16)  DEFAULT NULL COMMENT '资格审查方式（QULIFICATION_CHECK_MODE，规范原文拼写照录）',
  tender_organize_form     varchar(16)  DEFAULT NULL COMMENT '招标组织形式（TENDER_ORGANIZE_FORM）',
  supervise_dept_name      varchar(256) DEFAULT NULL COMMENT '监督部门名称（SUPERVISE_DEPT_NAME）',
  supervise_dept_code      varchar(32)  DEFAULT NULL COMMENT '监督部门代码（SUPERVISE_DEPT_CODE）',
  approve_dept_name        varchar(256) DEFAULT NULL COMMENT '审批部门名称（APPROVE_DEPT_NAME）',
  approve_dept_code        varchar(32)  DEFAULT NULL COMMENT '审批部门代码（APPROVE_DEPT_CODE）',
  state                    varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '内部管理态：ACTIVE/CLOSED（同项目主档口径）',
  fields_json              json         DEFAULT NULL COMMENT 'TenderProjectVO 全量字段快照',
  remark                   varchar(512) DEFAULT NULL COMMENT '备注',
  creator                  varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater                  varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted                  bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tender_project_code (tenant_id, tender_project_code, deleted),
  KEY idx_tp_project (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='招标项目主档（数据集 TenderProjectVO）';

CREATE TABLE IF NOT EXISTS bidding_bid_section (
  id                    bigint       NOT NULL COMMENT '主键（雪花）',
  tenant_id             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
  bid_section_code      varchar(64)  NOT NULL COMMENT '标段（包）编号（BidSectionVO.BID_SECTION_CODE，租户内唯一）',
  tender_project_code   varchar(64)  NOT NULL COMMENT '所属招标项目编号（TENDER_PROJECT_CODE）',
  bid_section_name      varchar(512) NOT NULL COMMENT '标段（包）名称（BID_SECTION_NAME）',
  unified_deal_code     varchar(64)  DEFAULT NULL COMMENT '统一交易标识码（UNIFIED_DEAL_CODE）',
  bid_ection_num        varchar(32)  DEFAULT NULL COMMENT '标段（包）序号（BID_ECTION_NUM，规范原文拼写照录）',
  bid_tender_mode       varchar(16)  DEFAULT NULL COMMENT '招标方式（BID_TENDER_MODE）',
  region_code           varchar(16)  DEFAULT NULL COMMENT '行政区划（REGION_CODE）',
  tra_place_code        varchar(32)  DEFAULT NULL COMMENT '交易场地代码（TRA_PLACE_CODE）',
  bid_section_content   text         COMMENT '标段（包）内容（BID_SECTION_CONTENT）',
  bid_section_type      varchar(32)  DEFAULT NULL COMMENT '标段（包）类型（BID_SECTION_TYPE）',
  contract_reckon_price varchar(32)  DEFAULT NULL COMMENT '合同估算价（CONTRACT_RECKON_PRICE，按数据集保真为字符串）',
  currency_code         varchar(8)   DEFAULT NULL COMMENT '币种（CURRENCY_CODE）',
  price_unit            varchar(16)  DEFAULT NULL COMMENT '价格单位（PRICE_UNIT）',
  time_limit            varchar(64)  DEFAULT NULL COMMENT '工期/服务期（TIME_LIMIT）',
  bid_qualification     text         COMMENT '投标人资格条件（BID_QUALIFICATION）',
  is_web_bidding        varchar(8)   DEFAULT NULL COMMENT '是否电子招投标（IS_WEB_BIDDING）',
  is_nomeet_bidding     varchar(8)   DEFAULT NULL COMMENT '是否不见面开标（IS_NOMEET_BIDDING）',
  state                 varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '内部管理态：ACTIVE/CLOSED',
  fields_json           json         DEFAULT NULL COMMENT 'BidSectionVO 全量字段快照',
  remark                varchar(512) DEFAULT NULL COMMENT '备注',
  creator               varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater               varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_bid_section_code (tenant_id, bid_section_code, deleted),
  KEY idx_bs_tender_project (tender_project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标段（包）主档（数据集 BidSectionVO）';

CREATE TABLE IF NOT EXISTS bidding_crawl_download_gate (
  id               bigint       NOT NULL COMMENT '固定主键（GLOBAL 闸位=1）',
  scope_key        varchar(64)  NOT NULL COMMENT '闸位作用域（GLOBAL=全局单闸）',
  last_download_at  datetime    DEFAULT NULL COMMENT '上次发起原文下载时刻；NULL=从未下载',
  last_task_id     bigint       DEFAULT NULL COMMENT '上次占位对应任务 id（审计）',
  last_url         varchar(1024) DEFAULT NULL COMMENT '上次下载地址（审计）',
  creator          varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater          varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted          bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gate_scope (scope_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原文下载限速闸（原子占位；合规频控唯一真相）';

CREATE TABLE IF NOT EXISTS bidding_crawl_download_audit (
  id                 bigint       NOT NULL COMMENT '主键（雪花）',
  task_id            bigint       DEFAULT NULL COMMENT '采集任务 id',
  source_code        varchar(32)  DEFAULT NULL COMMENT '采集源 code',
  url                varchar(1024) NOT NULL COMMENT '下载地址',
  download_kind      varchar(24)  NOT NULL COMMENT 'DETAIL_HTML/PDF/ARCHIVE_HTML/ATTACHMENT/CHANNEL',
  http_status        int          DEFAULT NULL COMMENT 'HTTP 状态码',
  byte_size          bigint       DEFAULT NULL COMMENT '下载字节数',
  duration_ms        bigint       DEFAULT NULL COMMENT '耗时（毫秒）',
  speed_bps          bigint       DEFAULT NULL COMMENT '下载速率（字节/秒）',
  sha256             char(64)     DEFAULT NULL COMMENT '内容 SHA-256',
  integrity_verdict  varchar(24)  DEFAULT NULL COMMENT '完整性结论 PASS/FAIL_*',
  storage_status     varchar(16)  DEFAULT NULL COMMENT '存储状态 NONE/REGISTERED/FAILED',
  file_id            bigint       DEFAULT NULL COMMENT '归档后的平台文件 id',
  message            varchar(512) DEFAULT NULL COMMENT '备注/失败原因',
  creator            varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater            varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted            bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  KEY idx_dl_audit_task (task_id),
  KEY idx_dl_audit_time (create_time),
  KEY idx_dl_audit_verdict (integrity_verdict),
  KEY idx_dl_audit_source (source_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原文下载审计（下载量/速率/摘要/完整性/存储状态）';

-- ========== ② 采集源配置（爬虫启动的最小数据；ENABLED/栏目模板见 JSON 配置列） ==========

INSERT  IGNORE INTO `bidding_fetch_source` (`id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `source_code`, `source_name`, `base_url`, `allowed_hosts`, `entry_urls_json`, `channel_code`, `fetch_type`, `api_config_json`, `parser_code_default`, `fetch_interval_seconds`, `robots_ok`, `enabled`, `remark`) VALUES (100000001,'','2026-09-06 09:01:29','','2026-09-09 13:50:32',_binary '\0','EBIDDING','秦源招标电子招标平台','https://www.ebidding.com.cn','ebidding.com.cn',NULL,'GENERIC','CRAWLER',NULL,NULL,360,_binary '\0',_binary '',NULL),(690010001,'1','2026-09-07 14:48:20','1','2026-09-09 15:28:33',_binary '\0','QYZB','秦源电子招标平台','https://qyzb.shccmg.com',NULL,'[{\"name\": \"招标公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"203\", \"lookbackDays\": 3, \"fullLookbackDays\": 60}, {\"name\": \"非招标公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"204\", \"lookbackDays\": 3, \"fullLookbackDays\": 60}, {\"name\": \"变更公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"205\", \"lookbackDays\": 3, \"fullLookbackDays\": 60}, {\"name\": \"中标公示\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"321\", \"lookbackDays\": 1, \"fullLookbackDays\": 60}, {\"name\": \"中标公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"322\", \"lookbackDays\": 1, \"fullLookbackDays\": 60}, {\"name\": \"其他公告\", \"parser\": \"QYZB_JSON\", \"siteId\": \"725\", \"maxPages\": 10, \"pageSize\": 10, \"categoryId\": \"323\", \"lookbackDays\": 1, \"fullLookbackDays\": 60}]','GENERIC','API','{\"url\": \"https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage\", \"method\": \"POST\", \"mapping\": {\"url\": \"url\", \"date\": \"publishDate\", \"title\": \"title\", \"category\": \"categoryName\", \"contentHtml\": \"text\"}, \"rowsPath\": \"res.rows\", \"totalPath\": \"res.total\", \"urlPrefix\": \"https://qyzb.shccmg.com/cms/default/webfile\", \"bodyTemplate\": \"{\\\"pageNo\\\":{page},\\\"pageSize\\\":{size},\\\"dto\\\":{\\\"siteId\\\":\\\"{siteId}\\\",\\\"categoryId\\\":\\\"{categoryId}\\\"}}\"}','QYZB_JSON',300,_binary '\0',_binary '','下载器 v1 冒烟：先单栏目（3ywgg/205）；放开全部见文件头注释'),(690010002,'1','2026-09-07 14:48:20','1','2026-09-09 15:51:05',_binary '\0','GX','国信e采（山西）','https://gx.e-bidding.org',NULL,'[{\"name\": \"依法招标-招标公告\", \"parser\": \"GX_E_BIDDING\", \"listUrl\": \"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=01&tabName=招标公告&page=1\", \"maxPages\": 10, \"lookbackDays\": 120, \"fullLookbackDays\": 120}, {\"name\": \"非依法招标\", \"parser\": \"GX_E_BIDDING\", \"listUrl\": \"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=02&page=1\", \"maxPages\": 10, \"lookbackDays\": 3, \"fullLookbackDays\": 120}, {\"name\": \"非依法采购（采购信息）\", \"parser\": \"GX_E_BIDDING\", \"listUrl\": \"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=00&tabName=采购信息&page=1\", \"maxPages\": 10, \"lookbackDays\": 3, \"fullLookbackDays\": 120}]','GENERIC','CRAWLER',NULL,'GX_E_BIDDING',300,_binary '\0',_binary '','下载器 v1：详情=PDF（fileId 待 FileApi P2）；默认停用，冒烟时人工启用');

-- ========== ③ 原文下载限速闸位（GLOBAL 单行，合规频控 2 分钟/次） ==========

INSERT  IGNORE INTO `bidding_crawl_download_gate` (`id`, `scope_key`, `last_download_at`, `last_task_id`, `last_url`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (1,'GLOBAL','2026-09-11 16:28:18',2097890010101645313,'https://qyzb.shccmg.com/cms/default/webfile/zbgg/20260806/1270076914609946624.html','system','2026-09-11 08:51:33','download-gate','2026-09-11 16:28:18',_binary '\0');

-- ========== ④ 菜单（招投标 6735 及子菜单/按钮；INSERT IGNORE = 已存在则保持现状） ==========
-- 注意：system_menu 属 system 服务库；若本库没有该表，本段会报错中止（前面 ①②③ 已生效）。

INSERT  IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (6735,'招投标','',1,301,0,'/bidding','carbon:integration','','',0,_binary '',_binary '',_binary '','admin','2026-09-06 14:01:47','1','2026-09-07 14:57:58',_binary '\0'),(6736,'外部公告审核管理','bidding:notice-external:update',2,1,6735,'noticeExternal','ep:document-copy','bidding/noticeExternal/index','BiddingNoticeExternalIndex',0,_binary '',_binary '',_binary '','1','2026-09-07 14:52:15','1','2026-09-07 14:57:23',_binary '\0'),(6737,'信息聚合任务管理','',1,2,6735,'crawl','carbon:data-vis','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:15','1','2026-09-11 08:51:33',_binary '\0'),(6738,'爬取任务工作台','bidding:crawl-source:query',2,1,6737,'console','','bidding/crawlExternal/console/index','CrawlExternalConsole',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:16','1','2026-09-08 08:52:36',_binary '\0'),(6739,'任务查询','bidding:crawl-task:query',3,4,6738,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:16','1','2026-09-08 08:52:56',_binary ''),(6740,'采集批次','bidding:crawl-job:query',2,3,6737,'job','','bidding/crawlExternal/job/index','CrawlJobIndex',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:17','1','2026-09-08 08:52:56',_binary ''),(6741,'采集源新增','bidding:crawl-source:create',3,1,6738,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:17','1','2026-09-07 17:24:17',_binary '\0'),(6742,'采集源修改','bidding:crawl-source:update',3,1,6738,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:17','1','2026-09-07 17:24:17',_binary '\0'),(6743,'采集源删除','bidding:crawl-source:delete',3,1,6738,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:17','1','2026-09-07 17:24:17',_binary '\0'),(6744,'采集任务重试','bidding:crawl-task:update',3,5,6738,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-07 17:24:18','1','2026-09-08 08:52:56',_binary ''),(6745,'爬取任务查询','bidding:crawl-task:query',3,1,6739,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 08:53:10','1','2026-09-09 02:25:43',_binary '\1'),(6747,'批次查询','bidding:crawl-job:query',3,3,6740,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 08:53:10','1','2026-09-09 02:25:43',_binary '\0'),(6748,'投标流程跟踪','bidding:notice-external:query',2,3,6735,'processTrack','','bidding/processTrack/index','BiddingProcessTrackIndex',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:30','1','2026-09-08 11:19:39',_binary '\0'),(6749,'新建意向','bidding:process-track:create',3,1,6748,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:31','1','2026-09-08 09:33:31',_binary '\0'),(6750,'报名投标','bidding:process-track:register',3,2,6748,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:31','1','2026-09-08 09:33:31',_binary '\0'),(6751,'审核操作','bidding:process-track:review',3,3,6748,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:31','1','2026-09-08 09:33:31',_binary '\0'),(6752,'上传标书','bidding:process-track:upload',3,4,6748,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 09:33:31','1','2026-09-08 09:33:31',_binary '\0'),(6753,'公告/公示管理','',1,1000,6735,'notice','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:03','1','2026-09-08 10:27:32',_binary '\0'),(6754,'公告列表','bidding:notice:query',2,1,6753,'list','','bidding/notice/index','NoticeIndex',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:03','1','2026-09-08 10:07:03',_binary '\0'),(6755,'公告新建','bidding:notice:create',3,2,6754,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:03','1','2026-09-08 10:07:03',_binary '\0'),(6756,'公告修改','bidding:notice:update',3,3,6754,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6757,'公告发布','bidding:notice:publish',3,4,6754,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6758,'公告删除','bidding:notice:delete',3,5,6754,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6759,'主体信息与授权管理','',1,1001,6735,'subject-auth','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:34:31',_binary '\0'),(6760,'主体档案（企业自维护）','bidding:subject:query',2,1,6759,'subjectProfile','','bidding/subjectProfile/index','SubjectProfileIndex',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6761,'主体档案维护','bidding:subject:update',3,2,6760,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6762,'平台授权管理','bidding:subject-auth:query',2,2,6759,'subjectAuth','','bidding/subjectAuth/index','SubjectAuthIndex',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6763,'主体授权/经办用户管理','bidding:subject-auth:update',3,3,6762,'','','','',0,_binary '',_binary '',_binary '','1','2026-09-08 10:07:04','1','2026-09-08 10:07:04',_binary '\0'),(6764,'备案提交','bidding:filing:create',3,5,6748,'','',NULL,NULL,0,_binary '',_binary '',_binary '','1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0'),(6765,'备案结果回推','bidding:filing:update',3,6,6748,'','',NULL,NULL,0,_binary '',_binary '',_binary '','1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0'),(6770,'项目与标段','',1,30,6735,'project','ep:collection',NULL,NULL,0,_binary '',_binary '',_binary '','1','2026-09-10 10:10:57','1','2026-09-10 10:10:57',_binary '\0'),(6771,'项目台账','bidding:project:query',2,1,6770,'project','ep:document','bidding/project/index','BiddingProject',0,_binary '',_binary '',_binary '','1','2026-09-10 10:10:57','1','2026-09-10 10:10:57',_binary '\0'),(6772,'标段台账','bidding:bid-section:query',2,2,6770,'bid-section','ep:grid','bidding/bidSection/index','BiddingBidSection',0,_binary '',_binary '',_binary '','1','2026-09-10 10:10:57','1','2026-09-10 10:10:57',_binary '\0'),(6773,'项目新建','bidding:project:create',3,1,6771,'','',NULL,NULL,0,_binary '',_binary '',_binary '','1','2026-09-10 10:10:57','1','2026-09-10 10:10:57',_binary '\0'),(6774,'项目修改','bidding:project:update',3,2,6771,'','',NULL,NULL,0,_binary '',_binary '',_binary '','1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0'),(6775,'标段新建','bidding:bid-section:create',3,1,6772,'','',NULL,NULL,0,_binary '',_binary '',_binary '','1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0'),(6776,'标段修改','bidding:bid-section:update',3,2,6772,'','',NULL,NULL,0,_binary '',_binary '',_binary '','1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0');

-- ========== ⑤ 角色菜单授权（system_role_menu） ==========

INSERT  IGNORE INTO `system_role_menu` (`id`, `role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES (180926,60346,6735,'admin','2026-09-06 14:01:47','admin','2026-09-06 14:01:47',_binary '\0',2019),(180927,1,6735,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180928,1,6736,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180929,1,6737,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180930,1,6738,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180931,1,6741,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180932,1,6742,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180933,1,6743,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180935,1,6746,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180936,1,6747,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180937,1,6748,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180938,1,6749,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180939,1,6750,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180940,1,6751,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180941,1,6752,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180942,1,6753,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180943,1,6754,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180944,1,6755,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180945,1,6756,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180946,1,6757,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180947,1,6758,'admin','2026-09-08 10:28:27','admin','2026-09-08 10:28:27',_binary '\0',0),(180958,1,6759,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180959,1,6760,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180960,1,6761,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180961,1,6762,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180962,1,6763,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180965,2,6735,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180966,2,6736,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180967,2,6737,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180968,2,6738,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180969,2,6741,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180970,2,6742,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180971,2,6743,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180973,2,6746,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180974,2,6747,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180975,2,6748,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180976,2,6749,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180977,2,6750,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180978,2,6751,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180979,2,6752,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180980,2,6753,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180981,2,6754,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180982,2,6755,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180983,2,6756,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180984,2,6757,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180985,2,6758,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180986,2,6759,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180987,2,6760,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180988,2,6761,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180989,2,6762,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180990,2,6763,'admin','2026-09-08 10:34:30','admin','2026-09-08 10:34:30',_binary '\0',0),(180991,1,6765,'1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0',0),(180992,1,6764,'1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0',0),(180993,2,6765,'1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0',0),(180994,2,6764,'1','2026-09-09 02:25:43','1','2026-09-09 02:25:43',_binary '\0',0),(180995,2,6739,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(180996,2,6740,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(180997,2,6741,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(180998,2,6742,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(180999,2,6743,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181000,2,6744,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181002,2,6747,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181003,2,6749,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181004,2,6750,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181005,2,6751,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181006,2,6752,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181007,2,6748,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181008,2,6735,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181009,2,6737,'1','2026-09-09 08:43:52','1','2026-09-09 08:43:52',_binary '\0',0),(181010,60352,6735,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181027,60352,6748,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181028,60346,6748,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181029,60352,6749,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181030,60346,6749,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181031,60352,6750,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181032,60346,6750,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181033,60352,6751,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181034,60346,6751,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181035,60352,6752,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181036,60346,6752,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181037,60352,6753,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181038,60346,6753,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181039,60352,6754,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181040,60346,6754,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181041,60352,6755,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181042,60346,6755,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181043,60352,6756,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181044,60346,6756,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181045,60352,6757,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181046,60346,6757,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181047,60352,6758,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181048,60346,6758,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181049,60352,6759,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181050,60346,6759,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181051,60352,6760,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181052,60346,6760,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181053,60352,6761,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181054,60346,6761,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181059,60352,6765,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181060,60346,6765,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181061,60352,6764,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181062,60346,6764,'1','2026-09-09 10:12:28','1','2026-09-09 10:12:28',_binary '\0',0),(181073,60346,6748,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181074,60346,6749,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181075,60346,6750,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181076,60346,6751,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181077,60346,6752,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181078,60346,6753,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181079,60346,6754,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181080,60346,6755,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181081,60346,6756,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181082,60346,6757,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181083,60346,6758,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181084,60346,6759,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181085,60346,6760,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181086,60346,6761,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181087,60346,6764,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181088,60346,6765,'1','2026-09-09 10:51:39','1','2026-09-09 10:51:39',_binary '\0',2019),(181102,2,6770,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181103,1,6770,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181104,2,6771,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181105,1,6771,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181106,2,6772,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181107,1,6772,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181108,2,6773,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181109,1,6773,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181110,2,6774,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181111,1,6774,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181112,2,6775,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181113,1,6775,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181114,2,6776,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0),(181115,1,6776,'1','2026-09-10 10:10:58','1','2026-09-10 10:10:58',_binary '\0',0);

/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;

-- ========== 校验（期望：结构 21 / 采集源 3 / 闸位 1 / 菜单 37 / 授权 135） ==========
SELECT COUNT(*) AS tables_ok FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('bidding_fetch_job', 'bidding_fetch_source', 'bidding_fetch_task', 'bidding_filing', 'bidding_follow_item', 'bidding_follow_item_event', 'bidding_notice', 'bidding_notice_content', 'bidding_notice_external', 'bidding_notice_external_attachment', 'bidding_notice_external_meta', 'bidding_notice_subscription', 'bidding_notice_subscription_event', 'bidding_subject_auth', 'bidding_subject_profile', 'bidding_subject_user', 'bidding_project', 'bidding_tender_project', 'bidding_bid_section', 'bidding_crawl_download_gate', 'bidding_crawl_download_audit');
SELECT COUNT(*) AS sources_ok FROM bidding_fetch_source;
SELECT COUNT(*) AS gate_ok FROM bidding_crawl_download_gate;
SELECT COUNT(*) AS menus_ok FROM system_menu WHERE id BETWEEN 6735 AND 6799;
SELECT COUNT(*) AS role_menus_ok FROM system_role_menu WHERE menu_id BETWEEN 6735 AND 6799;
