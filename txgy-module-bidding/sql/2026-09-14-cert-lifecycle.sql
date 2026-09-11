-- =====================================================================
-- 证件/资质生命周期记录建表脚本 —— txgy-module-bidding 小模块 · M12 全生命周期（变更/续期/注销吊销/年检）
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 表 3（cert_review_record 资质审查，review_type 含 ANNUAL 年检）、
--      表 5（cert_renewal_record 续期/补换证：old/new 有效期、新文件版本、办结回写）、
--      表 6（cert_change_record 变更：change_group_id 归组、field_name/old_value/new_value 字段级留痕、status 流转）、
--      表 7（cert_revoke_record 吊销/注销/暂扣/恢复）；
--   2) 用户范围口径（2026-09-13 确认）：做到"证照全生命周期管理（中级）"——签发、变更、注销、年检、到期提醒、
--      版本管理，**不做签发**；
--   3) 《招投标模块开发规范》§5 四-1（审计列由 BaseDO 提供）、五（T 轨 TenantBaseDO 自动租户隔离）、
--      六-1（UK 含 deleted 与 tenant_id）、§4.5（状态机 + 乐观锁 CAS）。
-- 设计说明（与 v3.3 四张专表的差异，登记在 docs/模块设计与规范/员工资质与证件生命周期-设计说明.md）：
--   v3.3 把生命周期拆成 cert_review_record / cert_renewal_record / cert_change_record / cert_revoke_record 四表，
--   但四表约八成字段同构（业务对象 + 申请人/时间 + 状态 + 结论 + 新文件 + 备注），且本模块还要同时承载
--   "通用证照（certificate_info）"与"员工证书（personnel_certificate）"两个域的生命周期；
--   若按四表 × 两域展开即为 8 张近乎同构的表与 8 套 Mapper/Service。
--   故本模块归并为**一张 cert_lifecycle_record**，用 biz_domain 区分业务对象、lifecycle_type 区分四类流程，
--   与既有 cert_alert_records 的 ref_type/ref_id 泛化范式保持一致（sql/2026-09-10-qual-expiry-alert.sql 先例）。
--   如后续评审要求拆表，按 lifecycle_type 过滤即可无损拆分（字段已按四类分组排列）。
-- 业务目标：每一次证件/证书的变更、续期、注销吊销、年检都留痕可追溯，并与主档同事务回写，
--          替代"只在 Excel 改个数字、事后说不清谁改的"的台账模式。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `cert_lifecycle_record` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    -- 业务对象定位（泛化：一表服务于证照台账/员工证书/企业资质/安许证四类对象）
    `biz_domain` varchar(24) NOT NULL COMMENT '业务对象域 code：CERT 证照台账/PERSON_CERT 员工证书/ENTERPRISE_QUAL 企业资质/SAFETY_PERMIT 安许证',
    `biz_id` bigint NOT NULL COMMENT '业务对象主键（certificate_info.id / personnel_certificate.id / enterprise_qualification.id / qualification_safety_permit.id）',
    `ref_name` varchar(256) DEFAULT NULL COMMENT '业务对象名称快照（防主档改名后无法复算）',
    `ref_no` varchar(128) DEFAULT NULL COMMENT '证书编号快照',
    `ent_id` bigint DEFAULT NULL COMMENT '归属企业（便于按企业聚合生命周期台账）',
    -- 流程定位
    `lifecycle_type` varchar(16) NOT NULL COMMENT '生命周期类型 code：CHANGE 变更/RENEW 续期/REVOKE 注销吊销/ANNUAL 年检',
    `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态 code：PENDING 待办/REVIEWING 审核中/APPROVED 通过/REJECTED 驳回/EFFECTIVE 已生效',
    `apply_no` varchar(64) DEFAULT NULL COMMENT '申请单号（对外可引用；同类型同日同对象唯一）',
    -- 变更（v3.3 表 6）：一行一字段，同一次申请多字段共用 change_group_id
    `change_group_id` varchar(64) DEFAULT NULL COMMENT '变更归组号（同一次变更申请涉及多字段时共用一组）',
    `field_name` varchar(64) DEFAULT NULL COMMENT '变更字段名（台账列名，如 cert_status/valid_to/cert_level）',
    `old_value` text COMMENT '变更前值',
    `new_value` text COMMENT '变更后值（生效时同步主档）',
    `change_summary` varchar(512) DEFAULT NULL COMMENT '变更摘要',
    -- 续期（v3.3 表 5）
    `renewal_type` varchar(16) DEFAULT NULL COMMENT '续期类型 code：RENEW 延续/REISSUE 补换证（补换证可能产生新编号）',
    `old_valid_from` date DEFAULT NULL COMMENT '续期前有效期起始（快照）',
    `old_valid_to` date DEFAULT NULL COMMENT '续期前有效期止（快照）',
    `new_valid_from` date DEFAULT NULL COMMENT '续期后有效期起始（生效回写主档）',
    `new_valid_to` date DEFAULT NULL COMMENT '续期后有效期止（生效回写主档；驱动下一轮到期预警）',
    `new_next_review_date` date DEFAULT NULL COMMENT '下次年检日（生效回写主档 next_review_date）',
    `new_cert_no` varchar(128) DEFAULT NULL COMMENT '新证书编号（补换证产生新编号时填写）',
    `result_no` varchar(128) DEFAULT NULL COMMENT '批复/许可决定文号',
    -- 注销/吊销（v3.3 表 7）
    `revoke_type` varchar(16) DEFAULT NULL COMMENT '注销吊销类型 code：REVOKE 吊销/CANCEL 注销/SUSPEND 暂扣/RESTORE 恢复',
    `revoke_authority` varchar(256) DEFAULT NULL COMMENT '决定机关',
    `revoke_reason` varchar(512) DEFAULT NULL COMMENT '决定事由',
    `revoke_effective_at` date DEFAULT NULL COMMENT '决定生效日期',
    -- 年检（v3.3 表 3 ANNUAL）
    `review_year` smallint DEFAULT NULL COMMENT '年检年度（如 2026）',
    `review_date` date DEFAULT NULL COMMENT '年检日期',
    `review_result` tinyint DEFAULT NULL COMMENT '年检/审查结论：NULL 未出结论 / 0 不通过 / 1 通过',
    -- 附件与过程留痕
    `new_cert_file_id` bigint DEFAULT NULL COMMENT '新文件 id（变更页/延续页/新证扫描件，经 FileApi 上传后登记）',
    `evidence_file_ids` json DEFAULT NULL COMMENT '佐证材料文件 id 列表（决定书/年检材料/变更证明）',
    `applied_by` bigint DEFAULT NULL COMMENT '申请人（用户 id）',
    `applied_at` datetime DEFAULT NULL COMMENT '申请时间',
    `handled_by` bigint DEFAULT NULL COMMENT '办理人（审批/登记人）',
    `handled_at` datetime DEFAULT NULL COMMENT '办理时间',
    `effective_at` datetime DEFAULT NULL COMMENT '生效时间（回写主档的时间，审计用）',
    `idempotent_key` varchar(160) DEFAULT NULL COMMENT '幂等键（biz_domain:biz_id:lifecycle_type:apply_no），防重复提交',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（状态机 CAS）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lifecycle_idem` (`tenant_id`, `idempotent_key`, `deleted`),
    KEY `idx_lifecycle_biz` (`tenant_id`, `biz_domain`, `biz_id`, `lifecycle_type`, `status`),
    KEY `idx_lifecycle_status` (`tenant_id`, `lifecycle_type`, `status`, `applied_at`),
    KEY `idx_lifecycle_group` (`tenant_id`, `change_group_id`),
    KEY `idx_lifecycle_review` (`tenant_id`, `review_year`, `review_result`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证件/资质生命周期记录（变更/续期/注销吊销/年检，只追加）';
