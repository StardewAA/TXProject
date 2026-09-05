-- =====================================================================
-- 外部公告采集/跟踪/提醒 DDL 草稿（v0.6，未执行）
-- 依据：docs/外部公告采集与展示设计方案.md v0.6（§十五 决策修订）；《招投标模块开发规范》第五章
-- 说明：本文件为【草稿，未在任何库执行】。归属划分（以 v0.6 为准）：
--       G 轨（BaseDO，无租户语义）：bidding_notice_external(+_meta/_attachment)、bidding_crawl_source/_task/_job；
--       T 轨（TenantBaseDO，tenant_id 由拦截器自动注入/过滤，业务不手写租户校验）：bidding_notice_subscription、
--       bidding_follow_item、bidding_follow_item_event、bidding_notice_subscription_event。
--       PDF 抽取：本地 OpenDataLoader（本地模式）只抽文本+表格；扫描件抽空(空结果)→ FAIL_EMPTY 直接报错失败。
-- =====================================================================
-- 安全评审备注（2026-09，v0.6 草稿）：
-- 1) 唯一键含 deleted（如 uk_external_orig_url_hash(orig_url_hash, deleted)）：
--    冲突仅发生在“对同一 URL 逻辑删除后再次逻辑删除同一条”时（0→1 撞既有 (hash,1)）。
--    本域当前无删除入口（采集产出幂等复用、无 delete API），不触发；后续若引入删除：
--    方案 A 物理删除；方案 B 同 URL 复用旧行恢复 deleted=0；方案 C 唯一键改 (hash, deleted_at 时间戳列)。
-- 2) HTML/JSON/URL 均按“原文留档+展示侧净化/校验”策略：入库不裁剪，展示走 jsoup 白名单与 http(s) 域名白名单（应用层实现）。
-- =====================================================================

-- 1) 外部公告主档（G 轨聚合；HTML 原样 / PDF / 附件 / 原网址）
CREATE TABLE IF NOT EXISTS `bidding_notice_external` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
    `source_code` varchar(32) NOT NULL COMMENT '来源站配置 code（bidding_crawl_source.source_code）',
    `source_name` varchar(128) DEFAULT NULL COMMENT '来源站名称快照',
    `title` varchar(512) NOT NULL COMMENT '标题（列表/检索核心）',
    `orig_url` varchar(1024) NOT NULL COMMENT '原网址（展示必带）',
    `orig_url_hash` char(64) NOT NULL COMMENT 'URL SHA-256（去重指纹）',
    `content_hash` char(64) DEFAULT NULL COMMENT '正文/文件指纹（内容级去重/变更识别）',
    `content_type` varchar(16) NOT NULL COMMENT '展示形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER',
    `content_html` mediumtext DEFAULT NULL COMMENT 'HTML 原文（原样保存，不转义不裁剪）',
    `pdf_file_id` bigint DEFAULT NULL COMMENT 'PDF 原文件 file_id（红线8：file_id+sha256+size）',
    `pdf_sha256` char(64) DEFAULT NULL COMMENT 'PDF 原文件哈希',
    `pdf_size` bigint DEFAULT NULL COMMENT 'PDF 原文件大小（字节）',
    `attachment_ids_json` json DEFAULT NULL COMMENT '附件记录 id 列表（bidding_notice_external_attachment）',
    `notice_type_raw` varchar(128) DEFAULT NULL COMMENT '外站自述类型原文（不猜不丢）',
    `notice_type_code` varchar(64) DEFAULT NULL COMMENT '可映射的权威码（映射不到为 NULL）',
    `legal_flag` varchar(8) DEFAULT NULL COMMENT 'Y=依法必招 / N=非依法（外站自述）/ NULL=未知',
    `industry_flag` varchar(16) DEFAULT NULL COMMENT '防腐保温行业 AI 判定：BELONGS/NOT_BELONGS/UNCERTAIN',
    `industry_confidence` decimal(5,4) DEFAULT NULL COMMENT '行业判定置信度 0~1',
    `project_code` varchar(64) DEFAULT NULL COMMENT '关联项目编号（跟踪维度，可为空）',
    `tender_project_code` varchar(64) DEFAULT NULL COMMENT '关联招标项目编号（跟踪维度）',
    `bid_section_codes` varchar(512) DEFAULT NULL COMMENT '关联标段编号（多个逗号分隔，跟踪维度）',
    `project_name` varchar(256) DEFAULT NULL COMMENT '项目名称快照（基础信息）',
    `issuer_name` varchar(128) DEFAULT NULL COMMENT '发布单位（快照）',
    `publish_time` datetime DEFAULT NULL COMMENT '外站公告时间（解析失败取抓取时间）',
    `effective_from` datetime DEFAULT NULL COMMENT '生效开始',
    `effective_to` datetime DEFAULT NULL COMMENT '生效截止（判断是否在生效期）',
    `status` varchar(16) NOT NULL DEFAULT 'NEW' COMMENT 'NEW→ACTIVE→INACTIVE→ARCHIVED',
    `audit_status` varchar(16) NOT NULL DEFAULT 'UNREVIEWED' COMMENT 'UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN',
    `parser_code` varchar(64) DEFAULT NULL COMMENT '审批绑定/本次处理用的策略码（AI 模板/规则 code）',
    `public_scope` varchar(16) NOT NULL DEFAULT 'SOCIAL' COMMENT '公开范围（默认 SOCIAL 可展示）',
    `fetch_count` int NOT NULL DEFAULT 0 COMMENT '抓取次数',
    `last_fetch_time` datetime DEFAULT NULL COMMENT '最近抓取时间',
    `extraction_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'PDF 抽取状态：NONE/SUCCESS/FAIL_EMPTY/FAIL（OpenDataLoader 空结果=FAIL_EMPTY 直接失败）',
    `extraction_text` longtext DEFAULT NULL COMMENT 'PDF 抽取的纯文本（OpenDataLoader 本地模式）',
    `extraction_tables_json` json DEFAULT NULL COMMENT 'PDF 抽取的表格（JSON 数组）',
    `extraction_at` datetime DEFAULT NULL COMMENT 'PDF 抽取时间',
    `meta_json` json DEFAULT NULL COMMENT '原始页面结构化字段全集（兜底防丢）',
    `extra_raw` longtext DEFAULT NULL COMMENT '整页快照（可追溯/复审）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_external_orig_url_hash` (`orig_url_hash`, `deleted`),
    KEY `idx_external_status_time` (`status`, `publish_time`),
    KEY `idx_external_project` (`project_code`),
    KEY `idx_external_type_time` (`content_type`, `publish_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '外部公告主档（G 轨）';

-- 2) 动态字段/元数据兜底（与主档 1:1 可选分离；v0.5 已并入主档 meta_json，本表预留拆分场景）
CREATE TABLE IF NOT EXISTS `bidding_notice_external_meta` (
    `id` bigint NOT NULL COMMENT '主键',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `notice_id` bigint NOT NULL COMMENT '主档 ID',
    `meta_json` json DEFAULT NULL COMMENT '原字段全集（列不固定的兜底）',
    `extra_raw` longtext DEFAULT NULL COMMENT '整页快照（主档 extra_raw 冗余分表时用）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_meta_notice` (`notice_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '外部公告动态字段兜底（G 轨）';

-- 3) 外部公告附件（下载地址=FileApi 签名，不落第三方裸链）
CREATE TABLE IF NOT EXISTS `bidding_notice_external_attachment` (
    `id` bigint NOT NULL COMMENT '主键',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `notice_id` bigint NOT NULL COMMENT '主档 ID',
    `file_id` bigint NOT NULL COMMENT 'FileApi 文件对象主键',
    `sha256` char(64) DEFAULT NULL COMMENT '文件哈希',
    `size` bigint DEFAULT NULL COMMENT '大小（字节）',
    `name` varchar(256) DEFAULT NULL COMMENT '文件名',
    `ext` varchar(32) DEFAULT NULL COMMENT '扩展名',
    `orig_download_url` varchar(1024) DEFAULT NULL COMMENT '外站原始下载链接（溯源）',
    `virus_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLEAN/INFECTED（病毒扫描，先例：资质域）',
    PRIMARY KEY (`id`),
    KEY `idx_att_notice` (`notice_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '外部公告附件（G 轨）';

-- 4) 采集源配置（白名单源：URL/频控/通道/策略）
CREATE TABLE IF NOT EXISTS `bidding_crawl_source` (
    `id` bigint NOT NULL COMMENT '主键',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `source_code` varchar(32) NOT NULL COMMENT '源 code（唯一）',
    `source_name` varchar(128) NOT NULL COMMENT '源名称',
    `base_url` varchar(1024) NOT NULL COMMENT '站点域名/入口',
    `entry_urls_json` json DEFAULT NULL COMMENT '入口/栏目 URL 列表',
    `channel_code` varchar(64) NOT NULL COMMENT '下载通道适配器 code（通用或平台专属）',
    `parser_code_default` varchar(64) DEFAULT NULL COMMENT '默认解析/策略码（URL 未单独审批时用）',
    `fetch_interval_seconds` int NOT NULL DEFAULT 3600 COMMENT '抓取间隔（秒，频控）',
    `robots_ok` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否允许按 robots 抓取',
    `enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '启用开关',
    `remark` varchar(512) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_code` (`source_code`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采集源配置（G 轨）';

-- 5) 采集任务（分布式：PENDING→RUNNING→SUCCESS/FAILED/SKIPPED，断点/重试/worker 归属）
CREATE TABLE IF NOT EXISTS `bidding_crawl_task` (
    `id` bigint NOT NULL COMMENT '主键',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `job_id` bigint DEFAULT NULL COMMENT '所属批次（bidding_crawl_job.id）',
    `source_code` varchar(32) NOT NULL COMMENT '源 code',
    `url` varchar(1024) NOT NULL COMMENT '待抓 URL',
    `url_hash` char(64) NOT NULL COMMENT 'URL SHA-256（去重）',
    `channel_code` varchar(64) DEFAULT NULL COMMENT '通道适配器 code',
    `parser_code` varchar(64) DEFAULT NULL COMMENT '解析/策略 code（URL 审批绑定或源默认）',
    `priority` int NOT NULL DEFAULT 0 COMMENT '优先级（小=高）',
    `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
    `attempt` int NOT NULL DEFAULT 0 COMMENT '已尝试次数（MQ 侧 ≤3）',
    `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间（指数退避）',
    `worker_id` varchar(64) DEFAULT NULL COMMENT '执行节点标识（分布式抢占）',
    `started_at` datetime DEFAULT NULL,
    `finished_at` datetime DEFAULT NULL,
    `message` varchar(1024) DEFAULT NULL COMMENT '结果/错误说明',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_url_hash` (`url_hash`, `source_code`, `deleted`),
    KEY `idx_task_status_retry` (`status`, `next_retry_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采集任务（G 轨）';

-- 6) 采集批次（调度/人工跑批汇总）
CREATE TABLE IF NOT EXISTS `bidding_crawl_job` (
    `id` bigint NOT NULL COMMENT '主键',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `source_code` varchar(32) DEFAULT NULL COMMENT '源 code（空=全源）',
    `trigger_type` varchar(16) NOT NULL DEFAULT 'SCHEDULE' COMMENT 'SCHEDULE/MANUAL/REPAIR',
    `scope` varchar(64) DEFAULT NULL COMMENT '范围说明（如按时间窗口）',
    `status` varchar(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED',
    `summary_json` json DEFAULT NULL COMMENT '汇总：总数/成功/失败/转人工数',
    `finished_at` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_job_status` (`status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采集批次（G 轨）';

-- 7) 关注（公告/项目/招标项目/标段）——T 轨（TenantBaseDO，tenant_id 拦截器自动注入；user_id 为普通业务列）
CREATE TABLE IF NOT EXISTS `bidding_notice_subscription` (
    `id` bigint NOT NULL COMMENT '主键',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨，拦截器维护）',
    `user_id` bigint NOT NULL COMMENT '用户 ID（投标人）',
    `target_type` varchar(16) NOT NULL COMMENT 'ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION',
    `target_key` varchar(128) NOT NULL COMMENT '目标键：公告 id 或 维度代码',
    `follow_status` varchar(16) NOT NULL DEFAULT 'FOLLOWING' COMMENT 'FOLLOWING/UNFOLLOWED',
    `follow_time` datetime DEFAULT NULL,
    `unfollow_time` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sub_user_target` (`tenant_id`, `user_id`, `target_type`, `target_key`, `deleted`),
    KEY `idx_sub_user` (`tenant_id`, `user_id`),
    KEY `idx_sub_tenant_id` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '关注订阅（T 轨）';

-- 8) 关注项目列表主档（界面“关注的项目列表”行）——T 轨（TenantBaseDO）
CREATE TABLE IF NOT EXISTS `bidding_follow_item` (
    `id` bigint NOT NULL COMMENT '主键',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨，拦截器维护）',
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `follow_type` varchar(16) NOT NULL COMMENT 'PROJECT/TENDER_PROJECT/SECTION',
    `follow_key` varchar(128) NOT NULL COMMENT '维度代码（project_code/tender_project_code/bid_section_code）',
    `display_name` varchar(256) DEFAULT NULL COMMENT '项目/标段名快照',
    `issuer_name` varchar(128) DEFAULT NULL COMMENT '发布单位快照',
    `stage_code` varchar(16) DEFAULT 'UNKNOWN' COMMENT '进展阶段：PENDING/TENDER/OPEN_BID/EVAL/RESULT/CONTRACT/DONE/UNKNOWN',
    `stage_name` varchar(64) DEFAULT NULL COMMENT '阶段名称快照',
    `base_json` json DEFAULT NULL COMMENT '基础信息快照（建设单位/监管/招标方式/地点等关键项）',
    `latest_dynamic_time` datetime DEFAULT NULL COMMENT '最近动态时间',
    `unread_count` int NOT NULL DEFAULT 0 COMMENT '未读动态数（读后清零）',
    `follow_status` varchar(16) NOT NULL DEFAULT 'FOLLOWING',
    `follow_time` datetime DEFAULT NULL,
    `unfollow_time` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follow_item_user_key` (`tenant_id`, `user_id`, `follow_type`, `follow_key`, `deleted`),
    KEY `idx_follow_item_stage` (`tenant_id`, `user_id`, `stage_code`),
    KEY `idx_follow_item_tenant_id` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '关注项目列表主档（T 轨）';

-- 9) 关注项动态/事件（“有动态才提醒”、未读、时间线数据源）——T 轨（TenantBaseDO）
CREATE TABLE IF NOT EXISTS `bidding_follow_item_event` (
    `id` bigint NOT NULL COMMENT '主键',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨，拦截器维护）',
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `item_id` bigint NOT NULL COMMENT '所属关注行（bidding_follow_item.id）',
    `source` varchar(16) NOT NULL COMMENT 'INTERNAL/EXTERNAL',
    `event_type` varchar(24) NOT NULL COMMENT 'NOTICE_PUBLISHED/NOTICE_UPDATED/EXTERNAL_ACTIVE/STAGE_CHANGE',
    `notice_id` bigint DEFAULT NULL COMMENT '关联公告 ID（内部或外部表主档 id）',
    `event_key` varchar(160) NOT NULL COMMENT '唯一：user+item+source+notice_id/版本',
    `title_snapshot` varchar(512) DEFAULT NULL COMMENT '标题快照',
    `orig_url` varchar(1024) DEFAULT NULL COMMENT '原网址（外部）',
    `occurred_at` datetime NOT NULL COMMENT '动态发生时间',
    `read_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '未读=提醒（读后置 1）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_key` (`event_key`, `deleted`),
    KEY `idx_event_item` (`tenant_id`, `user_id`, `item_id`, `read_flag`),
    KEY `idx_event_tenant_id` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '关注项动态/事件（T 轨）';

-- 10) 提醒发送留痕（站内信必发 + EMAIL 可选；真实渠道 API 占位后替换）——T 轨（TenantBaseDO）
CREATE TABLE IF NOT EXISTS `bidding_notice_subscription_event` (
    `id` bigint NOT NULL COMMENT '主键',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨，拦截器维护）',
    `user_id` bigint NOT NULL COMMENT '收件用户',
    `follow_event_id` bigint DEFAULT NULL COMMENT '对应关注动态（bidding_follow_item_event.id）',
    `notice_id` bigint DEFAULT NULL,
    `channel` varchar(16) NOT NULL COMMENT 'IN_APP/EMAIL',
    `channel_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    `sent_time` datetime DEFAULT NULL,
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '发送重试（≤3）',
    `message` varchar(512) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_se_user_status` (`tenant_id`, `user_id`, `channel_status`),
    KEY `idx_se_tenant_id` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '提醒发送留痕（T 轨）';
