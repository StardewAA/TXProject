-- =====================================================================
-- 资质/证书域（qualification_）建表脚本 —— txgy-module-bidding 小模块 · M7 达标四要素核查域
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 §十五：表15-2 资质标准库（可版本化）/15-3 人员配置台账/
--      15-4 工程业绩台账（qual_id 归属，§17.6 修订）/15-5 资产与技术装备要素/15-6 达标动态核查（只追加·防抵赖）
--   2) 《招投标模块开发规范》§5 一-4（qualification_ 前缀例外）四-1（审计列）三-7（code/JSON 列存储口径）
-- 说明：本文件仅 M7 五表；15-6 按季度 RANGE 分区生产可另行部署（评审留痕结论可复算，std_version 依据）
-- =====================================================================

-- 表15-2：qualification_standard 资质标准库（核查依据，可版本化）
CREATE TABLE IF NOT EXISTS `qualification_standard` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 std_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `std_version` varchar(32) NOT NULL COMMENT '标准版本（如“建市〔2014〕159号+2021 修订”）',
    `qual_serial` varchar(16) NOT NULL COMMENT '适用的资质序列 code',
    `qual_category` varchar(64) NOT NULL COMMENT '适用的资质类别',
    `qual_grade` varchar(32) NOT NULL COMMENT '适用的资质等级',
    `req_assets` json DEFAULT NULL COMMENT '资产指标：净资产 min 等（JSON）',
    `req_staff` json NOT NULL COMMENT '人员指标：注册建造师/职称/技工/技术负责人条件（JSON）',
    `req_performance` json DEFAULT NULL COMMENT '工程业绩指标（类型/规模/数量/年限，JSON）',
    `req_equipment` json DEFAULT NULL COMMENT '技术装备/检测仪器指标（JSON）',
    `effective_from` date NOT NULL COMMENT '标准生效起始',
    `effective_to` date DEFAULT NULL COMMENT '标准生效截止（NULL=现行）',
    `source_ref` varchar(256) DEFAULT NULL COMMENT '依据文号/国标表110等级/表111类型来源',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_std_scope_version` (`tenant_id`, `qual_serial`, `qual_category`, `qual_grade`, `std_version`, `deleted`),
    KEY `idx_std_effective` (`effective_from`, `effective_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质标准库（可版本化核查依据）';

-- 表15-3：qualification_staff_config 资质人员配置台账
CREATE TABLE IF NOT EXISTS `qualification_staff_config` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 staff_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `qual_id` bigint DEFAULT NULL COMMENT '关联资质（分资质核算人数）',
    `staff_role` varchar(24) NOT NULL COMMENT '角色 code：REGISTERED_BUILDER 注册建造师/TECH_LEAD 技术负责人/MID_TITLE 中级职称/SKILLED_WORKER 技工/SITE_MGR 现场管理人员',
    `person_name` varchar(128) NOT NULL COMMENT '姓名（脱敏展示，保留姓氏）',
    `id_card_enc` varchar(256) DEFAULT NULL COMMENT '身份证加密（L4）',
    `id_card_blind_index` varchar(64) DEFAULT NULL COMMENT '身份证盲索引判重（一人一岗）',
    `major` varchar(128) DEFAULT NULL COMMENT '专业（对口资质专业）',
    `reg_no` varchar(128) DEFAULT NULL COMMENT '注册/执业编号',
    `title_level` varchar(32) DEFAULT NULL COMMENT '职称等级',
    `is_full_time` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否本单位在职（挂靠核查）',
    `social_security_status` varchar(16) DEFAULT NULL COMMENT '社保状态 code',
    `social_security_tenant` varchar(256) DEFAULT NULL COMMENT '社保缴纳单位（须与投标主体一致）',
    `edu_hours` decimal(6,1) DEFAULT NULL COMMENT '继续教育学时',
    `edu_hours_required` decimal(6,1) DEFAULT NULL COMMENT '所需学时（达标要素之一）',
    `reg_valid_to` date DEFAULT NULL COMMENT '注册有效期止（NULL 即不满足）',
    `binded_cert_id` bigint DEFAULT NULL COMMENT '关联个人证书 certificate_info',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    KEY `idx_qual_staff_ent` (`tenant_id`, `ent_id`, `qual_id`, `staff_role`),
    KEY `idx_qual_staff_reg_valid` (`reg_valid_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质人员配置台账';

-- 表15-4：qualification_performance 企业工程业绩台账（§17.6 修订：qual_id 必填归属具体资质）
CREATE TABLE IF NOT EXISTS `qualification_performance` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 perf_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `qual_id` bigint NOT NULL COMMENT '归属资质主档（按资质聚合业绩核查，§17.6）',
    `project_name` varchar(256) NOT NULL COMMENT '业绩项目名称',
    `project_type` varchar(64) DEFAULT NULL COMMENT '工程类型（对口资质类别）',
    `scale_metrics` json DEFAULT NULL COMMENT '规模指标 JSON（面积/跨度/长度/层数/单跨等）',
    `contract_amount` decimal(18,2) DEFAULT NULL COMMENT '合同额',
    `completion_date` date DEFAULT NULL COMMENT '竣工验收时间（业绩年限窗口）',
    `role` varchar(16) DEFAULT NULL COMMENT '角色 code：GENERAL 总承包/SUB 分包',
    `quality_result` varchar(64) DEFAULT NULL COMMENT '质量验收结论',
    `owner_name` varchar(256) DEFAULT NULL COMMENT '业主单位',
    `is_platform_verified` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已录入四库一平台可线上核验',
    `evidence_file_ids` json DEFAULT NULL COMMENT '中标通知/合同/竣工验收等佐证（文件 id 列表）',
    PRIMARY KEY (`id`),
    KEY `idx_qual_perf_qual` (`tenant_id`, `qual_id`, `completion_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业工程业绩台账';

-- 表15-5：qualification_asset 资产与技术装备要素
CREATE TABLE IF NOT EXISTS `qualification_asset` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 asset_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `asset_type` varchar(16) NOT NULL COMMENT '类型 code：NET_ASSET 净资产/PLANT 厂房/MAJOR_EQUIP 主要设备/TEST_INSTR 检测仪器',
    `name` varchar(256) NOT NULL COMMENT '名称',
    `spec` varchar(256) DEFAULT NULL COMMENT '规格型号',
    `quantity` decimal(12,2) DEFAULT NULL COMMENT '数量',
    `value_amount` decimal(18,2) DEFAULT NULL COMMENT '原值/净值（净资产核查取审计值）',
    `stat_year` smallint DEFAULT NULL COMMENT '统计年度（净资产按申报前一年度审计报告）',
    `evidence_file_ids` json DEFAULT NULL COMMENT '审计报告/购置发票/权属证明（文件 id 列表）',
    PRIMARY KEY (`id`),
    KEY `idx_qual_asset_ent` (`tenant_id`, `ent_id`, `asset_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资产与技术装备要素';

-- 表15-6：qualification_compliance_review 达标动态核查（只追加 · 防抵赖）
CREATE TABLE IF NOT EXISTS `qualification_compliance_review` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 review_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '企业',
    `qual_id` bigint DEFAULT NULL COMMENT '被核查资质',
    `std_version` varchar(32) NOT NULL COMMENT '依据的资质标准版本（结论可复算）',
    `review_type` varchar(16) NOT NULL DEFAULT 'SELF' COMMENT '类型 code：SELF 达标自查/DYNAMIC 动态核查/PRE_APPLY 申报前核查',
    `element_snapshot` json NOT NULL COMMENT '资产/人员/业绩/装备四要素时点快照（防事后篡改，类比自查双快照）',
    `review_result` varchar(8) NOT NULL DEFAULT 'PASS' COMMENT '结论 code：PASS/RISK/FAIL 三档',
    `diff_items` json DEFAULT NULL COMMENT '不达标/风险项明细（如“一级建造师少2人”“净资产未达”）',
    `reviewer` bigint DEFAULT NULL COMMENT '核查人',
    `reviewed_at` datetime DEFAULT NULL COMMENT '核查时间（生产按季度 RANGE 分区）',
    `linked_apply_id` bigint DEFAULT NULL COMMENT '若为申报前核查，关联申报流',
    PRIMARY KEY (`id`),
    KEY `idx_qual_review_qual` (`tenant_id`, `qual_id`, `reviewed_at`),
    KEY `idx_qual_review_ent` (`tenant_id`, `ent_id`, `reviewed_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质达标动态核查（只追加·防抵赖）';
