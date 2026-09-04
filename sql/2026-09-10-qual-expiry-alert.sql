-- =====================================================================
-- 资质/证书域增量脚本 —— txgy-module-bidding 小模块 · M8 安许 15-8 + 资质到期并入预警（QUAL_EXPIRY）
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 §十五 表15-8（qualification_safety_permit：permit_no UK、
--      permit_status VALID/EXPIRING_SOON/EXPIRED/SUSPENDED/REVOKED、3 年一延续驱动预警）
--   2) v3.3 §十五-到期预警复用：enterprise_qualification.valid_to / qualification_safety_permit.valid_to /
--      qualification_staff_config.reg_valid_to 一并纳入 cert_alert_config/cert_alert_records 扫描，
--      alert_type 扩展 QUAL_EXPIRY；经 cert_notify_outbox 可靠送达
--   3) 《招投标模块开发规范》§5 一-4（qualification_ 前缀例外已登记）
-- 说明：cert_alert_records.cert_id 语义为 certificate_info.id，资质类业务对象无对应证照 → 增量加
--   ref_type/ref_id/ref_name 可空列（QUAL_EXPIRY 行填 ref_*、cert_id 置 NULL；既有 EXPIRY 行不受影响）。
--   幂等键泛化：资质行用 ref_type:ref_id:yyyyMMdd（uk_alert_idempotent 已全局唯一，无需改动）。
-- =====================================================================

-- 1) cert_alert_records 泛化：cert_id 允许 NULL（资质类对象无 certificate_info 对应），增 ref 定位列
ALTER TABLE `cert_alert_records`
    MODIFY COLUMN `cert_id` bigint DEFAULT NULL COMMENT '关联证照 certificate_info.id（EXPIRY 等证照类填；QUAL_EXPIRY 资质类置 NULL，用 ref_* 定位）',
    ADD COLUMN `ref_type` varchar(24) DEFAULT NULL COMMENT '业务对象大类 code（QUAL_EXPIRY 用）：QUAL 企业资质/SAFETY_PERMIT 安许/STAFF 资质人员' AFTER `cert_id`,
    ADD COLUMN `ref_id` bigint DEFAULT NULL COMMENT '业务对象主键（qual_id/permit_id/staff_id）' AFTER `ref_type`,
    ADD COLUMN `ref_name` varchar(256) DEFAULT NULL COMMENT '业务对象显示名（脱敏）' AFTER `ref_id`;

-- 2) QUAL_EXPIRY 全局预警配置行（平台默认常量同款；cert_type IS NULL=全局，与 EXPIRY 行并存）
INSERT INTO `cert_alert_config`
    (`tenant_id`, `creator`, `updater`, `cert_type`, `alert_type`, `thresholds`, `enabled`, `notify_channels`, `version`, `deleted`)
SELECT 0, '1', '1', NULL, 'QUAL_EXPIRY', '[7,30,60,90]', b'1', '["EMAIL","IN_APP"]', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `cert_alert_config` WHERE `alert_type` = 'QUAL_EXPIRY' AND `cert_type` IS NULL AND `deleted` = b'0');

-- 3) 表15-8：qualification_safety_permit 安全生产许可证
CREATE TABLE IF NOT EXISTS `qualification_safety_permit` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 permit_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业（建筑施工企业必备，常与资质联动核查/预警）',
    `permit_no` varchar(128) NOT NULL COMMENT '安全生产许可证编号；UK(tenant, permit_no)',
    `issuing_authority` varchar(256) NOT NULL COMMENT '发证机关（省级住建主管部门）',
    `issue_date` date DEFAULT NULL COMMENT '发证日期',
    `valid_to` date DEFAULT NULL COMMENT '有效期至（3 年一延续；驱动到期预警）',
    `permit_status` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '状态 code：VALID/EXPIRING_SOON/EXPIRED/SUSPENDED 暂扣/REVOKED',
    `legal_person` varchar(128) DEFAULT NULL COMMENT '法定代表人（脱敏）',
    `binded_cert_id` bigint DEFAULT NULL COMMENT '关联 certificate_info 影像',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（状态机 CAS）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permit_no` (`tenant_id`, `permit_no`, `deleted`),
    KEY `idx_permit_valid` (`valid_to`),
    KEY `idx_permit_ent_status` (`tenant_id`, `ent_id`, `permit_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '安全生产许可证（v3.3 表15-8）';
