-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- =====================================================================
-- 公示公告域（notice）建表脚本 —— txgy-module-bidding 小模块
-- 依据：《招投标模块开发规范》第五章（表结构分轨/字段长度/文本存储/冗余列/索引）
-- 评审：对照第五章第七节建表评审清单（T 轨、审计列、唯一键含 deleted、正文版本化）
-- 说明：权威数据集字段全集不入列，统一存 bidding_notice.fields_json（key=规范英文名称，
--       由 NoticePayloadUtils 按数据集 VO 规整，"该有的字段一个不能少"）。
-- 对应接口数据集：招标公告与资格预审公告 / 变更公告（工程） / 中标候选人公示 / 中标结果公示
-- 已验证：2026-09-03 已在开发库 MySQL 8.4（ruoyi-vue-pro）执行成功；
--       插入/回读、uk_notice_code 与 uk_notice_version 唯一键拦截均通过（见 NoticeDbSmoke）
-- =====================================================================

-- 公告/公示主档（T 轨：继承 TenantBaseDO -> tenant_id bigint）
CREATE TABLE IF NOT EXISTS `bidding_notice` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（用户 ID 字符串）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
    `notice_code` varchar(64) NOT NULL COMMENT '公告内部编号（bidding 域内唯一）',
    `notice_type` varchar(32) NOT NULL COMMENT '公告/公示类型 code（NoticeTypeEnum：TENDER_NOTICE/CHANGE_NOTICE/WIN_CANDIDATE_PUBLICITY/WIN_RESULT_PUBLICITY）',
    `title` varchar(256) NOT NULL COMMENT '公告/公示名称（标题；可超百字长句，宽度放宽经建表评审留注 2026-09-03，见五章二档位说明）',
    `project_code` varchar(64) DEFAULT NULL COMMENT '关联招标项目编号（可为空=不绑定项目）',
    `project_name` varchar(256) DEFAULT NULL COMMENT '招标项目名称快照（检索/列表展示；长句放宽经建表评审留注 2026-09-03）',
    `publisher_name` varchar(128) DEFAULT NULL COMMENT '发布单位名称（名称快照列）',
    `publish_time` datetime DEFAULT NULL COMMENT '公告/公示发布时间（列表排序与大厅可见性依据）',
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/UNPUBLISHED/ARCHIVED',
    `internal_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '1=企业内部公告（仅本租户可见）；0=面向公众（经公开白名单外发）',
    `public_level` varchar(16) NOT NULL DEFAULT 'SOCIAL' COMMENT '公开级别：SOCIAL/GOVERNMENT（对齐 PublicLevelEnum）',
    `fields_json` json DEFAULT NULL COMMENT '权威数据集字段全集快照（key=规范英文名称，缺失补 null）',
    `pdf_file_id` bigint DEFAULT NULL COMMENT '公告 PDF 原文件 file_id（FileApi 对象主键；PDF 原文件必须保存，发布前必填——两种前端展示形态都基于该原件）',
    `pdf_sha256` char(64) DEFAULT NULL COMMENT '公告 PDF 原文件哈希（SHA-256；红线 8/五章三第5款口径：file_id+sha256(+size) 齐存）',
    `pdf_size` bigint DEFAULT NULL COMMENT '公告 PDF 原文件大小（字节）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notice_code` (`notice_code`, `deleted`, `tenant_id`),
    KEY `idx_notice_type_time` (`notice_type`, `publish_time`),
    KEY `idx_notice_project` (`project_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '公告/公示主档（T 轨）';

-- 公告/公示正文（版本化，只追加不更新；同一公告同类型版本唯一）
CREATE TABLE IF NOT EXISTS `bidding_notice_content` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
    `notice_id` bigint NOT NULL COMMENT '归属公告主档 ID',
    `content_type` varchar(16) NOT NULL DEFAULT 'BODY' COMMENT '内容类型编码（正文主体固定 BODY）',
    `content` mediumtext NOT NULL COMMENT '正文全文',
    `sha256` char(64) NOT NULL COMMENT '内容哈希（SHA-256，防篡改与去重）',
    `version` int NOT NULL COMMENT '版本号（同公告同类型自增、只追加）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notice_version` (`notice_id`, `content_type`, `version`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '公告/公示正文内容（版本化）';
