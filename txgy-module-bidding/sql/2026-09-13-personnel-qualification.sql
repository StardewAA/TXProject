-- =====================================================================
-- 员工资质（人员资格）域建表脚本 —— txgy-module-bidding 小模块 · M11 员工资质管理
-- 依据：
--   1) 用户业务口径《资质管理规范文件》目录 + 电子资质管理功能范围：
--      企业身份证明/资质证明/实力证明 + 员工自己的身份证明、资质证明，覆盖招投标全流程；
--   2) 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（qualification_staff_config：注册人员台账，
--      在职/社保/注册有效期=挂靠核查）、表 15-6（PASS/RISK/FAIL 三档 + diff_items 明细）、
--      表 1/2（certificate_info/cert_file_versions：证照事实与文件实体，本域经 binded_cert_id/file_id 复用）；
--   3) 《建筑业企业资质管理规定》（住建部令第22号）+《注册建造师管理规定》（建设部令第153号）
--      —— 人员指标须为本企业正式员工、注册单位须与申报企业一致（人证合一，防挂证）；
--   4) 《电子招标投标办法》（20号令）第42条 —— 应公开并提交从业人员职称/职业资格与电子证书编号；
--   5) 《招投标模块开发规范》§5：一（表结构分轨，本域沿用 qualification_ 前缀例外并新增 bidding_personnel_ 前缀）、
--      四-1（审计列由 BaseDO 提供）、五（T 轨继承 TenantBaseDO，自动租户隔离）、六-1（UK 含 deleted 与 tenant_id）。
-- 说明：本文件 5 张新表（员工档案/证书/培训/社保/劳动合同），全部幂等（CREATE TABLE IF NOT EXISTS）；
--       范围口径（用户 2026-09-13 确认）：做到“证照全生命周期管理（中级）”，不做签发；
--       故原设计的“一键配证/人证合一合规核验”判定引擎不在本期落地（要求库/配证结果表暂不建）。
--       证件生命周期（变更/续期/注销吊销/年检）统一落 sql/2026-09-14-cert-lifecycle.sql 的 cert_lifecycle_record。
-- 业务目标：把"员工资质"从 Excel 台账升级为可检索、可预警、可核验的电子化人员主数据，
--          并与企业资质（15-1/15-3 人员配置）、社保/劳动合同数据互联互通（关联列在后续配证阶段再增，本期不增）。
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 表 1：bidding_personnel_profile 员工档案（人员主数据，一人一档）
-- 依据：v3.3 表 15-3 person_name/id_card_enc/id_card_blind_index/edu_hours 等人员口径的"人"侧主档；
--       证件号等敏感字段 L4 只存密文 + 盲索引（开发规范 §4.8 密级分级）。
-- 目标：集中管理员工基本信息，作为证书、培训、社保、劳动合同的挂载点与"一人一档"判重依据。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_personnel_profile` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业（组织/租户下属主体）',
    `person_no` varchar(64) NOT NULL COMMENT '员工编号（企业内部唯一，UK 组成）',
    `person_name` varchar(128) NOT NULL COMMENT '姓名（L3 脱敏展示，保留姓氏）',
    `id_card_enc` varchar(256) DEFAULT NULL COMMENT '身份证号密文（L4，AES-256-GCM + KMS，业务侧不落明文）',
    `id_card_blind_index` varchar(64) DEFAULT NULL COMMENT '身份证盲索引（HMAC，用于一人一档等值判重，不解密）',
    `id_card_valid_to` date DEFAULT NULL COMMENT '身份证有效期止（驱动身份证到期预警，用户明确要求项）',
    `gender` varchar(8) DEFAULT NULL COMMENT '性别 code：MALE/FEMALE/UNKNOWN',
    `birth_date` date DEFAULT NULL COMMENT '出生日期',
    `mobile` varchar(32) DEFAULT NULL COMMENT '手机（L3，日志/出参脱敏）',
    `email` varchar(128) DEFAULT NULL COMMENT '邮箱（L3）',
    `edu_level` varchar(16) DEFAULT NULL COMMENT '学历层次 code：DOCTOR/MASTER/BACHELOR/COLLEGE/SECONDARY/OTHER（PersonnelEduLevelEnum）',
    `edu_major` varchar(128) DEFAULT NULL COMMENT '所学专业（人岗匹配度分析用）',
    `school` varchar(256) DEFAULT NULL COMMENT '毕业院校',
    `graduate_date` date DEFAULT NULL COMMENT '毕业时间',
    `title_level` varchar(32) DEFAULT NULL COMMENT '职称等级 code（初级/中级/高级/正高级，与 15-3 title_level 同口径）',
    `title_major` varchar(128) DEFAULT NULL COMMENT '职称专业',
    `work_start_date` date DEFAULT NULL COMMENT '参加工作时间（工龄/工作年限计算基准）',
    `join_date` date DEFAULT NULL COMMENT '入职日期',
    `dept_name` varchar(128) DEFAULT NULL COMMENT '所属部门',
    `position_name` varchar(128) DEFAULT NULL COMMENT '岗位名称',
    `employment_type` varchar(24) NOT NULL DEFAULT 'FULL_TIME' COMMENT '用工形式 code：FULL_TIME/PART_TIME/LABOR_DISPATCH/RETIRED_REHIRE/OTHER（挂靠风险提示）',
    `is_full_time` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否本单位在职（与 15-3 is_full_time 同口径：仅 FULL_TIME 为真）',
    `personnel_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '在职状态 code：ACTIVE/LEAVE/SUSPENDED/RETIRED（仅 ACTIVE 可配证）',
    `status_change_date` date DEFAULT NULL COMMENT '最近状态变更日期（离职/复职留痕）',
    `binded_auth_user_id` bigint DEFAULT NULL COMMENT '关联平台登录用户（员工可自助维护本人证书时使用；无账号为空）',
    `archive_no` varchar(128) DEFAULT NULL COMMENT '人事档案号',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（开发规范 §4.5）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_person_no` (`tenant_id`, `ent_id`, `person_no`, `deleted`),
    UNIQUE KEY `uk_profile_id_card` (`tenant_id`, `id_card_blind_index`, `deleted`),
    KEY `idx_profile_ent_status` (`tenant_id`, `ent_id`, `personnel_status`),
    KEY `idx_profile_id_card_valid` (`id_card_valid_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工档案（人员主数据，一人一档）';

-- ---------------------------------------------------------------------
-- 表 2：personnel_certificate 员工证书台账
-- 依据：用户要求"身份证、学历证、职称证等证明文件数字化管理"；v3.3 §一（PERSON 类证书走人员扩展）、
--       表 15-3（注册/执业编号、注册有效期）；《注册建造师管理规定》——注册单位须与聘用企业一致（防挂证）。
-- 目标：证书集中管理与分类检索；有效期驱动预警；register_unit 支撑人证合一核验。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_certificate` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `person_id` bigint NOT NULL COMMENT '持证人（bidding_personnel_profile.id）',
    `cert_type` varchar(24) NOT NULL COMMENT '证书类型 code：ID_CARD/EDUCATION_CERT/DEGREE_CERT/TITLE_CERT/REG_PRACTICE_CERT/SAFETY_ASSESS_CERT/SKILL_CERT/TRAINING_CERT/OTHER',
    `cert_name` varchar(256) NOT NULL COMMENT '证书名称（照面全称）',
    `cert_no` varchar(128) NOT NULL COMMENT '证书编号（电子证书编号，20号令第42条要求可公开）',
    `cert_level` varchar(64) DEFAULT NULL COMMENT '等级（一级/二级，或安管人员 A/B/C 证）',
    `cert_sub_type` varchar(16) DEFAULT NULL COMMENT '证书细分 code：安管人员 A/B/C（C 可再分 C1 机械/C2 土建/C3 综合）、特种设备作业 G1/G2/G3、RT/UT 等级等（06 文档 §二-3；码表 bidding_ 前缀）',
    `major` varchar(128) DEFAULT NULL COMMENT '专业（与资质要求专业比对，人岗匹配）',
    `next_review_date` date DEFAULT NULL COMMENT '下次复审/年审日期（与有效期独立的时间维度：司炉证届满前 1 个月复审、ISO 监督审核 ≤12 个月一次；06 文档 §二-4）',
    `edu_level` varchar(16) DEFAULT NULL COMMENT '学历层次 code（学历/学位证书用）',
    `issuing_authority` varchar(256) DEFAULT NULL COMMENT '发证机关',
    `issue_date` date DEFAULT NULL COMMENT '发证日期',
    `valid_from` date DEFAULT NULL COMMENT '有效期起始',
    `valid_to` date DEFAULT NULL COMMENT '有效期止（NULL=长期；驱动到期预警扫描）',
    `register_unit` varchar(256) DEFAULT NULL COMMENT '注册/执业单位名称（防挂证核验：须与申报企业一致）',
    `register_unit_code` varchar(64) DEFAULT NULL COMMENT '注册单位统一社会信用代码（GB 32100-2015，一致性核验键）',
    `register_no` varchar(128) DEFAULT NULL COMMENT '注册号/执业印章号（与 15-3 reg_no 同口径）',
    `cert_status` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '台账状态 code：VALID/EXPIRING_SOON/EXPIRED/SUSPENDED 暂扣/REVOKED/CANCELLED（对齐建办市〔2024〕51号 ZZZT 口径）',
    `platform_verified` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已在四库一平台线上核验（jzsc.mohurd.gov.cn）',
    `verify_url` varchar(512) DEFAULT NULL COMMENT '官方核验链接（照面二维码/查询网址）',
    `verified_at` datetime DEFAULT NULL COMMENT '最近核验时间',
    `binded_cert_id` bigint DEFAULT NULL COMMENT '关联 certificate_info（复用通用台账的影像/验真/变更日志能力）',
    `file_id` bigint DEFAULT NULL COMMENT '证书扫描件文件 id（经 FileApi 上传，业务表只存 id，开发规范 §5 三-5）',
    `is_current` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否当前有效记录（换证后旧证置 0，历史留痕）',
    `source_channel` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源 code：MANUAL/IMPORT/OCR/API_SYNC',
    `extra_props` json DEFAULT NULL COMMENT '动态扩展（如继续教育学时、批准专业明细）',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pcert_no` (`tenant_id`, `person_id`, `cert_type`, `cert_no`, `deleted`),
    KEY `idx_pcert_person` (`tenant_id`, `person_id`, `cert_type`, `is_current`),
    KEY `idx_pcert_ent_type` (`tenant_id`, `ent_id`, `cert_type`, `cert_status`),
    KEY `idx_pcert_valid` (`cert_status`, `valid_to`),
    KEY `idx_pcert_review` (`cert_status`, `next_review_date`),
    KEY `idx_pcert_sub_type` (`tenant_id`, `ent_id`, `cert_type`, `cert_sub_type`, `cert_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工证书台账（身份/学历/职称/执业/安全/技能类）';

-- ---------------------------------------------------------------------
-- 表 3：personnel_training_record 员工培训记录
-- 依据：用户要求"记录员工基本信息、资质证书、培训记录等"；v3.3 表 15-3 edu_hours/edu_hours_required（继续教育学时）。
-- 目标：培训与继续教育学时留痕，作为资质人员指标与安全生产教育的佐证材料。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_training_record` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `person_id` bigint NOT NULL COMMENT '参训员工',
    `training_name` varchar(256) NOT NULL COMMENT '培训名称',
    `training_type` varchar(32) NOT NULL DEFAULT 'OTHER' COMMENT '培训类型 code：SAFETY 安全培训/CONTINUE_EDU 继续教育/SKILL 技能/PRE_JOB 岗前/OTHER',
    `organizer` varchar(256) DEFAULT NULL COMMENT '培训机构/组织方',
    `start_date` date DEFAULT NULL COMMENT '开始日期',
    `end_date` date DEFAULT NULL COMMENT '结束日期',
    `edu_hours` decimal(6,1) DEFAULT NULL COMMENT '学时（继续教育达标要素）',
    `result` varchar(32) DEFAULT NULL COMMENT '结果 code：QUALIFIED 合格/UNQUALIFIED 不合格/ATTENDING 在读',
    `score` decimal(5,2) DEFAULT NULL COMMENT '考核成绩',
    `cert_no` varchar(128) DEFAULT NULL COMMENT '培训合格证编号',
    `cert_valid_to` date DEFAULT NULL COMMENT '培训合格证有效期止（可并入到期预警）',
    `file_id` bigint DEFAULT NULL COMMENT '培训证明文件 id',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    KEY `idx_ptrain_person` (`tenant_id`, `person_id`, `training_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工培训记录';

-- ---------------------------------------------------------------------
-- 表 4：personnel_social_security 员工社保缴纳记录
-- 依据：用户要求"核验员工社保缴纳单位、劳动合同主体是否与申报企业完全一致，杜绝人证分离和挂证"；
--       v3.3 表 15-3 social_security_status/social_security_tenant。
-- 目标：以"缴纳单位主体 + 缴纳月份"为证据链，判定申报/投标主体一致性。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_social_security` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '缴纳单位（本企业主体 id，一致性核验锚点）',
    `person_id` bigint NOT NULL COMMENT '参保员工',
    `payer_name` varchar(256) DEFAULT NULL COMMENT '缴纳单位名称（照抄社保明细，供人工比对）',
    `payer_credit_code` varchar(64) DEFAULT NULL COMMENT '缴纳单位统一社会信用代码（不一致即判人证分离）',
    `pay_month` varchar(7) NOT NULL COMMENT '缴纳月份（yyyy-MM）',
    `pay_status` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '缴纳状态 code：NORMAL/SUSPENDED/TRANSFERRED/NONE',
    `pay_base` decimal(18,2) DEFAULT NULL COMMENT '缴费基数',
    `pay_amount` decimal(18,2) DEFAULT NULL COMMENT '缴费金额',
    `social_no` varchar(64) DEFAULT NULL COMMENT '社保个人编号',
    `source_channel` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源 code：MANUAL/IMPORT/API_SYNC',
    `evidence_file_id` bigint DEFAULT NULL COMMENT '参保证明文件 id（社保缴纳证明）',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pss_month` (`tenant_id`, `person_id`, `payer_credit_code`, `pay_month`, `deleted`),
    KEY `idx_pss_person` (`tenant_id`, `person_id`, `pay_month`),
    KEY `idx_pss_payer` (`tenant_id`, `payer_credit_code`, `pay_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工社保缴纳记录（主体一致性核验证据）';

-- ---------------------------------------------------------------------
-- 表 5：personnel_labor_contract 劳动合同
-- 依据：用户要求核验"劳动合同主体是否与申报企业完全一致"；《劳动合同法》第十条（书面劳动合同）。
-- 目标：与社保共同构成劳动关系双证据；合同到期亦进入提醒范围（人员到期提醒的组成部分）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_labor_contract` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '用人单位主体 id（一致性核验锚点）',
    `person_id` bigint NOT NULL COMMENT '员工',
    `contract_no` varchar(128) NOT NULL COMMENT '劳动合同编号',
    `contract_type` varchar(24) DEFAULT NULL COMMENT '合同类型 code：FIXED 固定期限/OPEN 无固定期限/COMPLETE 完成一定工作',
    `sign_date` date DEFAULT NULL COMMENT '签订日期',
    `start_date` date NOT NULL COMMENT '合同起始日',
    `end_date` date DEFAULT NULL COMMENT '合同终止日（NULL=无固定期限）',
    `contract_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '合同状态 code：ACTIVE/EXPIRED/TERMINATED',
    `position_name` varchar(128) DEFAULT NULL COMMENT '合同岗位',
    `file_id` bigint DEFAULT NULL COMMENT '合同扫描件文件 id',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plc_no` (`tenant_id`, `person_id`, `contract_no`, `deleted`),
    KEY `idx_plc_person` (`tenant_id`, `person_id`, `contract_status`, `end_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工劳动合同（劳动关系证据）';
