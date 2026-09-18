-- =====================================================================
-- 安许 A/B/C 三类人员联动与 risk_flag（方案 §1 全量落地）
-- 依据：
--   1) 用户业务口径：A/B/C 任一不达标→黄；任一为 0→红；全为 0→失效；一年预警 ≥4 次→频繁；
--      法定代表人变更致仅剩 1 名 A 证给 180 天宽限；劳务分包企业 B 证为 0 不触发红色；
--      企业级 ent_risk_flag 与人员级 person_risk_flag 分离；人员 12 个月内更换受聘企业 ≥4 次打异常标签；
--      同一项目关键岗位人员频繁变更监控；兼任 ≥2 岗须持另一岗位考核合格证；
--      招投标/施工许可/现场监督检查读取同一风险状态。
--   2) 《资质管理/06-电子证照设计材料对比与推进依据-2026-09-10.md》§二-1/§二-2（陕西 2026-03-26 起
--      A/B/C 动态核查、黄色/红色/失效预警、3 个月不整改列失效）、§二-3（C1/C2/C3、G1/G2/G3 细分）、
--      §二-4（复审独立时间维度）；
--   3) 《建筑业企业资质管理规定》（住建部令第22号）+《安全生产许可证条例》（国务院令第653号修正）；
--   4) 《电子资质七大模块技术方案.md》§1.1~§1.5（表结构/判定顺序/Job/接口/拦截点/迁移回滚）；
--   5) 《招投标模块开发规范》§4.5（乐观锁）、§5 六-1（UK 含 tenant_id 与 deleted）、§4.8（L4 只回掩码）。
-- 数值参数：全部走 bidding_rule_config（见文末 §5 种子），代码禁止硬编码。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE；ALTER 部分为一次性增量（重复执行会报 Duplicate column，
--       可忽略或先按 information_schema 核查）。
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1) 安许证主档扩展：企业级风险（与人员级 person_risk_flag 分离）
-- 业务目标：招投标、施工许可、现场监督检查读同一份风险状态，避免各页面各自算、口径不一。
-- ---------------------------------------------------------------------
ALTER TABLE `qualification_safety_permit`
    ADD COLUMN `risk_flag` varchar(16) DEFAULT NULL COMMENT '企业级风险档 code：NORMAL/YELLOW/RED/INVALID/FREQUENT；NULL=未评估（不阻断）',
    ADD COLUMN `risk_level` varchar(16) DEFAULT NULL COMMENT '档位映射 code：LOW/MEDIUM/HIGH/MAJOR（供周检 Job 直接使用）',
    ADD COLUMN `risk_evaluated_at` datetime DEFAULT NULL COMMENT '最近风险评估时刻',
    ADD COLUMN `grace_expire_at` datetime DEFAULT NULL COMMENT '宽限截止（NULL=无宽限）',
    ADD COLUMN `grace_reason` varchar(256) DEFAULT NULL COMMENT '宽限原因（须在配置白名单内）',
    ADD COLUMN `grace_source_ref` varchar(128) DEFAULT NULL COMMENT '宽限依据（变更单号/决定书号）',
    ADD COLUMN `frequent_alert_count` int DEFAULT NULL COMMENT '滑动窗口内预警次数（频繁判定用）',
    ADD COLUMN `frequent_window_start` datetime DEFAULT NULL COMMENT '频繁统计滑动窗口起点',
    ADD COLUMN `rule_version` varchar(32) DEFAULT NULL COMMENT '本次评估所用规则版本（可复算）';

-- ---------------------------------------------------------------------
-- 2) 员工档案扩展：人员级风险（不与企业级混用）
-- 业务目标：企业级回答"这家企业能不能投标"，人员级回答"这名人员能不能上岗"，两种处置互不覆盖。
-- ---------------------------------------------------------------------
ALTER TABLE `bidding_personnel_profile`
    ADD COLUMN `person_risk_flag` varchar(16) DEFAULT NULL COMMENT '人员级标签 code：NORMAL/ABNORMAL/FREQUENT',
    ADD COLUMN `job_change_count12m` int DEFAULT NULL COMMENT '12 个月内更换受聘企业次数（列名与 MyBatis-Plus 默认驼峰映射一致，勿加下划线）',
    ADD COLUMN `job_change_window_start` date DEFAULT NULL COMMENT '统计窗口起点',
    ADD COLUMN `person_risk_evaluated_at` datetime DEFAULT NULL COMMENT '最近人员风险评估时刻';

-- ---------------------------------------------------------------------
-- 3) 风险快照（只追加 + 哈希链）：方案 §1.1(c)
-- 业务目标：每次评估可复算（hit_rules_json + rule_version），且记录不可篡改（等保三级 ≥3 年）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `safety_permit_risk_snapshot` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（SYSTEM=定时任务）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（本表只追加，恒为 0）',
    `permit_id` bigint NOT NULL COMMENT '安许证主键',
    `ent_id` bigint DEFAULT NULL COMMENT '企业主体',
    `risk_flag` varchar(16) NOT NULL COMMENT '本次风险档',
    `prev_risk_flag` varchar(16) DEFAULT NULL COMMENT '上次风险档（识别升降级）',
    `risk_level` varchar(16) NOT NULL COMMENT '档位映射结果',
    `a_count` int NOT NULL DEFAULT 0 COMMENT 'A 类有效人数',
    `b_count` int NOT NULL DEFAULT 0 COMMENT 'B 类有效人数',
    `c_count` int NOT NULL DEFAULT 0 COMMENT 'C 类有效人数',
    `min_required_json` json DEFAULT NULL COMMENT '本次判定所用配备要求（留证）',
    `grace_applied` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否处于宽限期',
    `hit_rules_json` json DEFAULT NULL COMMENT '命中的规则键与取值（可复算）',
    `rule_version` varchar(32) DEFAULT NULL COMMENT '规则版本',
    `batch_no` varchar(48) DEFAULT NULL COMMENT '评估批次号',
    `evaluated_at` datetime NOT NULL COMMENT '评估时刻',
    `record_hash` varchar(64) DEFAULT NULL COMMENT '本记录哈希（SHA-256）',
    `prev_hash` varchar(64) DEFAULT NULL COMMENT '上一条哈希（哈希链）',
    PRIMARY KEY (`id`),
    KEY `idx_risk_permit` (`permit_id`, `evaluated_at`),
    KEY `idx_risk_ent` (`tenant_id`, `ent_id`, `evaluated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '安许风险快照（只追加 + 哈希链）';

-- ---------------------------------------------------------------------
-- 4) 人员受聘企业变更流水：方案 §1.1(d)
-- 业务目标：12 个月内换企业 ≥N 次 → 打"一年异常"标签，识别挂证/职业化挂靠。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_job_change_log` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `person_id` bigint NOT NULL COMMENT '员工档案主键',
    `from_ent_id` bigint DEFAULT NULL COMMENT '原受聘企业（首次入职为空）',
    `to_ent_id` bigint DEFAULT NULL COMMENT '新受聘企业',
    `change_type` varchar(16) NOT NULL DEFAULT 'TRANSFER' COMMENT '类型 code：TRANSFER 变更单位/RESIGN 离职/REHIRE 复职',
    `effective_date` date NOT NULL COMMENT '生效日期',
    `source_ref` varchar(128) DEFAULT NULL COMMENT '来源单据号（劳动合同/社保变更单）',
    `remark` varchar(256) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_job_change_person` (`person_id`, `effective_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '人员受聘企业变更流水';

-- ---------------------------------------------------------------------
-- 5) 项目关键岗位任职流水：方案 §1.1(e)
-- 业务目标：同一项目同一关键岗位频繁换人 = 挂靠或管理失控信号，需可统计、可预警。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `project_key_post_assignment` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `project_id` bigint NOT NULL COMMENT '项目主键 bidding_project.id',
    `tender_project_code` varchar(64) DEFAULT NULL COMMENT '招标项目编号（追溯）',
    `post_code` varchar(32) NOT NULL COMMENT '关键岗位 code：PROJECT_MANAGER/TECH_LEADER/SAFETY_OFFICER/QUALITY_OFFICER 等',
    `person_id` bigint NOT NULL COMMENT '任职人员',
    `start_at` date NOT NULL COMMENT '任职开始',
    `end_at` date DEFAULT NULL COMMENT '任职结束（NULL=在任）',
    `change_seq` int NOT NULL DEFAULT 1 COMMENT '该岗位第几次变更（从 1 起）',
    `change_reason` varchar(256) DEFAULT NULL COMMENT '变更原因',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_seq` (`tenant_id`, `project_id`, `post_code`, `change_seq`, `deleted`),
    KEY `idx_post_project` (`project_id`, `post_code`, `start_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目关键岗位任职流水';

-- ---------------------------------------------------------------------
-- 6) 多岗合规校验结论（只追加）：方案 §1.1(f)
-- 业务目标：兼任两个及以上岗位的，必须持另一岗位的考核合格证书，校验结论留痕可追溯。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_multi_post_check` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（本表只追加）',
    `person_id` bigint NOT NULL COMMENT '员工档案主键',
    `primary_post` varchar(32) NOT NULL COMMENT '主岗位 code',
    `secondary_post` varchar(32) NOT NULL COMMENT '兼任岗位 code',
    `required_cert_type` varchar(32) DEFAULT NULL COMMENT '兼任岗位要求的证书类型 code',
    `required_cert_sub_type` varchar(16) DEFAULT NULL COMMENT '要求的证书细分（如 A/B/C）',
    `matched_cert_id` bigint DEFAULT NULL COMMENT '命中的员工证书主键（校验通过时）',
    `check_result` varchar(16) NOT NULL COMMENT '结论 code：PASS/FAIL',
    `fail_reason` varchar(256) DEFAULT NULL COMMENT '不通过原因',
    `checked_at` datetime NOT NULL COMMENT '校验时刻',
    `rule_version` varchar(32) DEFAULT NULL COMMENT '规则版本',
    PRIMARY KEY (`id`),
    KEY `idx_multi_post_person` (`person_id`, `checked_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '多岗合规校验结论（只追加）';
