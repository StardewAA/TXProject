-- =====================================================================
-- 资质/证书域（certificate）建表脚本 —— txgy-module-bidding 小模块 · M1 证照台账域
-- 依据：
--   1) 《招投标模块开发规范》第五章（表命名 cert_ 例外已登记 §5.1.4；T 轨 TenantBaseDO；
--      审计列 BaseDO 四-1；文件 FileApi 三-5；代码列三-7；UK 含 deleted+tenant_id 六-1；
--      只追加表归档六-3）；§4.5（乐观锁 version 列）；§4.8（密级列）
--   2) 《电子证件全流程管理_数据库设计.md》v3.3 及 §18 v3.4 对齐说明（certificate 分支）
--   3) GB/T 36903—2018（证照元数据 5.2/5.4）、GB/T 36904—2018（证照标识）、
--      GB/T 36905—2018（OFD 文件）、建办市〔2024〕51 号（建设工程企业资质证书 17 信息项/ZZZT）
-- 表前缀例外登记：开发规范 §5 一-4（cert_/qualification_ 已登记，不套 bidding_）
-- 本文件为 M1 首批：certificate_info（表1）、cert_file_versions（表2）、cert_change_logs（表13）
-- 其余表（审查/自查/续期/变更/吊销/预警/企业资质等）随 M 里程碑分批落 sql/
-- =====================================================================

-- 表1：certificate_info 证照台账主表（T 轨；只存证照事实，审查结论不落本表 —— v3.3 §二）
CREATE TABLE IF NOT EXISTS `certificate_info` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（录入人）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除 @TableLogic；删痕走 cert_change_logs）',
    `org_id` bigint DEFAULT NULL COMMENT '组织归属（v3.3 §二）',
    `cert_name` varchar(256) NOT NULL COMMENT '证照名称（GB/T 36903 5.2.1 ZZMC/certificateName；AI 自查名称匹配）',
    `cert_no` varchar(128) NOT NULL COMMENT '证书编号（建办市51号 ZSBH；随证型/码制浮动按 varchar(128) 落，开发规范 §5.2；租户内唯一）',
    `cert_type` varchar(16) NOT NULL COMMENT '证照大类（台账分类 code：ADMIN_LICENSE/SYSTEM_CERT/PERSON_QUAL/HONOR/OTHER，v3.3 §二）',
    `cert_category_code` varchar(32) DEFAULT NULL COMMENT '证照类型代码（GB/T 36902 5.2.2 ZZLXDM 21 位/行标，如 11100000000013338W002）',
    `cert_level` varchar(64) DEFAULT NULL COMMENT '资质等级（一级/甲级…）',
    `cert_scope` text COMMENT '许可/业务范围（较长文本随表存储，§5 三-1）',
    `holder_name` varchar(256) NOT NULL COMMENT '持证主体（GB/T 36903 5.2.8 CZZT/certificateHolderName；AI 自查一票否决）',
    `holder_type` varchar(16) NOT NULL DEFAULT 'ORG' COMMENT '持证主体类别 code：ORG/PERSON/EXTERNAL（v3.3 §二）',
    `holder_id` bigint DEFAULT NULL COMMENT '关联档案 ID（人员/企业档案）',
    `issuing_authority` varchar(256) DEFAULT NULL COMMENT '证照颁发机构（GB/T 36903 5.2.5 ZZBFJG；L2）；契约 §2.3 标为可选 ⇒ 可空（原 NOT NULL 与契约冲突，端到端实测报 Field issuing_authority doesn not have a default value）',
    `issuing_authority_code` varchar(32) DEFAULT NULL COMMENT '证照颁发机构代码（GB/T 36903 5.2.6 ZZBFJGDM，统一社会信用代码 GB 32100）',
    `issue_date` date DEFAULT NULL COMMENT '证照颁发日期（GB/T 36903 5.2.7 ZZBFRQ；展示用）',
    `valid_from` date DEFAULT NULL COMMENT '证照有效期起始日期（GB/T 36903 5.2.12 ZZYXQQSRQ；自查覆盖判断）',
    `valid_to` date DEFAULT NULL COMMENT '证照有效期截止日期（GB/T 36903 5.2.13 ZZYXQJZRQ；NULL=长期；自查余期+到期预警扫描）',
    `applicable_industries` json DEFAULT NULL COMMENT '适用行业（结构化 JSON）',
    `applicable_regions` json DEFAULT NULL COMMENT '适用地区（结构化 JSON）',
    `annual_review_cycle` smallint DEFAULT NULL COMMENT '年检周期（月），预警用',
    `next_review_date` date DEFAULT NULL COMMENT '下次年检日（自查+年检预警）',
    `is_exclusive_locked` bit(1) NOT NULL DEFAULT b'0' COMMENT '排他锁定（在建项目占用）',
    `lock_info` json DEFAULT NULL COMMENT '锁定明细（结构化 JSON）',
    `e_cert_ident` varchar(96) DEFAULT NULL COMMENT '电子证照标识（GB/T 36904 6 段编码+MOD37,36，≤74 位；51号 DZZZBZ；v3.4 起仅存标识，文件实体见 cert_file_versions）',
    `cert_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '台账状态 code（对齐 51号 ZZZT：ACTIVE/EXPIRING_SOON/EXPIRED/SUSPENDED/REVOKED/CANCELLED；自查只认 ACTIVE）',
    `verification_status` varchar(16) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '验真状态 code：UNVERIFIED/VERIFYING/VERIFIED/INVALID（与 cert_status 独立，自查不认 INVALID）',
    `last_verified_at` datetime DEFAULT NULL COMMENT '最近验真时间',
    `verify_api_source` varchar(128) DEFAULT NULL COMMENT '验真来源',
    `official_verify_url` varchar(512) DEFAULT NULL COMMENT '官方核验链接（展示）',
    `source_channel` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源 code：MANUAL/IMPORT/OCR/API_SYNC',
    `extra_props` json DEFAULT NULL COMMENT '动态扩展（结构化 JSON，如 cmmi_level）',
    `remark` text COMMENT '备注（较长文本随表存储，§5 三-1）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（开发规范 §4.5 乐观锁款；更新走 WHERE id=? AND version=?）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cert_no` (`cert_no`, `deleted`, `tenant_id`),
    KEY `idx_cert_status` (`cert_status`),
    KEY `idx_cert_valid_to` (`valid_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证照台账主表（T 轨 · cert_ 前缀例外）';

-- 表2：cert_file_versions 文件版本表（材料/文件管理 —— v3.3 §三 + v3.4 §18.2）
CREATE TABLE IF NOT EXISTS `cert_file_versions` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（上传人）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（上传时间）',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（is_current 切换等）',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `cert_id` bigint NOT NULL COMMENT '关联证照（冗余父键 certificate_info.id，§5 四-2）',
    `file_category` varchar(16) NOT NULL DEFAULT 'ORIGINAL' COMMENT '文件类别 code：ORIGINAL 原件/COPY 副本/CHANGE_PAGE 变更页/RENEWAL_PAGE 延续页/E_CERT 电子证照/OTHER（自查“有原件/有电子证照”按 ORIGINAL/E_CERT 且 is_current=TRUE 判定，v3.4 §18.1-2）',
    `file_name` varchar(256) NOT NULL COMMENT '文件名',
    `file_id` bigint NOT NULL COMMENT '文件对象主键（FileApi 上传，§5 三-5；URL 仅签名回显不作一致性依据）',
    `file_hash` varchar(64) NOT NULL COMMENT 'SHA-256（防篡改/去重，§5 三-5）',
    `file_format` varchar(16) DEFAULT NULL COMMENT '文件格式：PDF/JPG/PNG/OFD（OFD=GB/T 36905 版式电子证照）',
    `file_size` bigint NOT NULL COMMENT '字节数',
    `copy_creation_time` datetime DEFAULT NULL COMMENT '加注件制作时间（GB/T 36903 5.4.1 JZJZZSJ；仅加注件行填写）',
    `copy_creator` varchar(256) DEFAULT NULL COMMENT '加注件制作者（GB/T 36903 5.4.2 JZJZZZ）',
    `copy_cause` varchar(512) DEFAULT NULL COMMENT '加注件制作事由（GB/T 36903 5.4.3 JZJZZSY，一般同步水印）',
    `copy_expiring_time` datetime DEFAULT NULL COMMENT '加注件有效期截止时间（GB/T 36903 5.4.4 JZJYXQJZSJ）',
    `version` int NOT NULL DEFAULT 1 COMMENT '同类别文件版本号（只追加，旧版本只读保留）',
    `is_current` bit(1) NOT NULL DEFAULT b'1' COMMENT '当前生效版本（自查只取 TRUE 行）',
    `virus_scan_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '病毒扫描状态 code：PENDING/CLEAN/INFECTED（上传须 CLEAN 才可预览/下载）',
    `remark` text COMMENT '备注',
    `voided` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否作废（F2 软删留痕：作废=版本不再有效但行保留；当前版本被作废则该类别失去当前版本）',
    `void_reason` varchar(512) DEFAULT NULL COMMENT '作废原因（留痕展示用）',
    `voided_at` datetime DEFAULT NULL COMMENT '作废时间（留痕用；操作人见 updater）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cert_file_hash` (`cert_id`, `file_hash`, `deleted`, `tenant_id`),
    UNIQUE KEY `uk_cert_cat_version` (`cert_id`, `file_category`, `version`, `deleted`, `tenant_id`),
    KEY `idx_cert_current` (`cert_id`, `is_current`, `deleted`),
    KEY `idx_cert_voided` (`cert_id`, `voided`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证照文件版本表（T 轨）';

-- 表3：cert_change_logs 变更审计日志（必建 · 等保2.0 三级 —— v3.3 §13 + v3.4 §18.3）
CREATE TABLE IF NOT EXISTS `cert_change_logs` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（操作人 ID 字符串）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（操作时间）',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（只追加，业务不置 1）',
    `cert_id` bigint NOT NULL COMMENT '关联证照（冗余父键 certificate_info.id）',
    `field_name` varchar(64) NOT NULL COMMENT '变更字段名（STATUS_CHANGE 时为 cert_status 等）',
    `old_value` text COMMENT '变更前值（CREATE 行为空）',
    `new_value` text COMMENT '变更后值',
    `change_type` varchar(16) NOT NULL COMMENT '变更类型 code：CREATE/UPDATE/STATUS_CHANGE/REVOKE',
    `op_source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '操作来源 code：MANUAL/IMPORT/OCR/API_SYNC/SYSTEM',
    `op_ip` varchar(45) DEFAULT NULL COMMENT '操作 IP（支持 IPv6；审计追溯，日志侧脱敏展示）',
    `batch_id` varchar(64) DEFAULT NULL COMMENT '批次号（整批导入/回滚归组）',
    `record_hash` varchar(64) NOT NULL COMMENT '本行哈希 SHA-256(关键字段拼接)，防篡改',
    `prev_hash` varchar(64) NOT NULL DEFAULT 'ROOT' COMMENT '哈希链前驱 record_hash（链首=ROOT）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_change_prev` (`cert_id`, `prev_hash`, `deleted`, `tenant_id`),
    KEY `idx_change_cert_time` (`cert_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证照变更审计日志（只追加·哈希链；保留≥3年，按月归档任务见 v3.4 §18.3-2）';
