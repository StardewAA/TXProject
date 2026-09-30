-- =====================================================================
-- 资质/证书域（qualification_）建表脚本 —— txgy-module-bidding 小模块 · M5 企业资质域
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 §十五（表 15-1 enterprise_qualification 资质主档 /
--      表 15-7 qualification_apply_record 资质业务申报流（状态机 + 乐观锁））＋全局约定
--      （公共列：tenant_id/审计列/idempotent_key/version/archive_status；T 轨）
--   2) 《招投标模块开发规范》§5 一-4（qualification_ 前缀例外已登记）四-1（审计列）§4.5（乐观锁/状态流转走接口）
--   3) 建办市〔2024〕51 号 ZZZT 状态口径（台账状态）与国标表110/表111（资质等级/类别，随主系统字典）
-- 本文件为 M5：enterprise_qualification（表15-1）、qualification_apply_record（表15-7）
-- 说明：主档与申报流状态机 + 乐观锁；申报办结回写主档 cert_no/valid_to（同事务，表15-7 注释口径）；
--   主档 valid_to 到期预警复用见 v3.3 §十五（alert_type 扩展 QUAL_EXPIRY，后续里程碑接入扫描）
-- =====================================================================

-- 表15-1：enterprise_qualification 企业资质主档
CREATE TABLE IF NOT EXISTS `enterprise_qualification` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 qual_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
    `ent_id` bigint NOT NULL COMMENT '关联企业主体（组织架构/租户）',
    `qual_serial` varchar(16) NOT NULL COMMENT '资质序列 code：CONSTRUCTION 施工/SURVEY 勘察/DESIGN 设计/SUPERVISION 监理/TESTING 检测（v3.3 15-1）',
    `qual_category` varchar(64) NOT NULL COMMENT '资质类别：总承包/专业承包/劳务，如“市政公用工程施工总承包”',
    `qual_grade` varchar(32) NOT NULL COMMENT '资质等级（特级/一级/二级…，对齐国标表110 资质等级）',
    `cert_no` varchar(128) NOT NULL COMMENT '资质证书编号；UK(tenant,serial,category,grade,cert_no)',
    `issuing_authority` varchar(256) DEFAULT NULL COMMENT '发证/许可机关（契约标为可选 ⇒ 可空）',
    `issue_date` date DEFAULT NULL COMMENT '发证日期',
    `valid_to` date DEFAULT NULL COMMENT '有效期至（NULL=长期；驱动到期预警扫描）',
    `approval_scope` text COMMENT '批准承包/承揽范围（长文本随表存储）',
    `qual_status` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '资质状态 code：VALID/EXPIRING_SOON/EXPIRED/PENDING_RENEW 待续期/LIMITED 限期/REVOKED（v3.3 15-1）',
    `binded_cert_id` bigint DEFAULT NULL COMMENT '关联 certificate_info（证书影像/验真复用通用台账）',
    `source_channel` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源 code：MANUAL/IMPORT/API_SYNC/PLATFORM',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（开发规范 §4.5）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_qual_cert_no` (`tenant_id`, `qual_serial`, `qual_category`, `qual_grade`, `cert_no`, `deleted`),
    KEY `idx_qual_ent_status` (`tenant_id`, `ent_id`, `qual_status`),
    KEY `idx_qual_valid_to` (`valid_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质主档（qualification_ 前缀例外）';

-- 表15-7：qualification_apply_record 资质业务申报流（状态机 + 乐观锁；办结回写主档）
CREATE TABLE IF NOT EXISTS `qualification_apply_record` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 apply_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `qual_id` bigint DEFAULT NULL COMMENT '目标资质（新办可空，表15-7）',
    `apply_type` varchar(16) NOT NULL COMMENT '申报类型 code：NEW 新办/UPGRADE 升级/RENEW 延续/ADDITION 增项/CHANGE 变更/CANCEL 注销',
    `target_grade` varchar(32) DEFAULT NULL COMMENT '目标等级（升级/增项用）',
    `submit_date` date DEFAULT NULL COMMENT '提交日期',
    `due_date` date DEFAULT NULL COMMENT '预计办结日期',
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '申报状态 code：DRAFT/SUBMITTED/ACCEPTED 受理/REVIEWING 审核/APPROVED/PUBLISHED 公示/ISSUED 领证/REJECTED 退回',
    `material_snapshot` json DEFAULT NULL COMMENT '申报材料清单快照（四要素佐证引用）',
    `review_ids` json DEFAULT NULL COMMENT '关联达标核查记录（表15-6）',
    `result_no` varchar(128) DEFAULT NULL COMMENT '批复/许可决定文号',
    `new_cert_no` varchar(128) DEFAULT NULL COMMENT '办结后回写资质主档 cert_no（同事务 + version 乐观锁）',
    `new_valid_to` date DEFAULT NULL COMMENT '办结后回写资质主档 valid_to（延续 RENEW 用）',
    `owner_by` bigint DEFAULT NULL COMMENT '经办人',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（状态机 CAS，表15-7）',
    PRIMARY KEY (`id`),
    KEY `idx_qual_apply_ent` (`tenant_id`, `ent_id`, `status`),
    KEY `idx_qual_apply_qual` (`qual_id`, `apply_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质业务申报流（状态机+乐观锁）';
