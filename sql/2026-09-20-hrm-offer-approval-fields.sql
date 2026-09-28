-- ============================================================
-- HRM Offer 审批留痕：补充审批人 / 审批时间 / 审批意见字段
--
-- 背景：Offer 表原来只有 approval_status / process_instance_id / approval_version，
--       没有审批人与审批时间，「谁批的、什么时候批的」只能去接口日志翻；
--       且审批通过时不写意见。本次补齐留痕字段，供 Offer 详情直接展示。
-- 幂等：可重复执行。
-- ============================================================

SET @c1 := (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'hrm_recruit_offer' AND column_name = 'approver_user_id');
SET @sql := IF(@c1 = 0,
    'ALTER TABLE `hrm_recruit_offer` ADD COLUMN `approver_user_id` bigint NULL COMMENT ''审批人用户编号'' AFTER `approval_version`',
    'SELECT ''approver_user_id 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c2 := (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'hrm_recruit_offer' AND column_name = 'approve_time');
SET @sql := IF(@c2 = 0,
    'ALTER TABLE `hrm_recruit_offer` ADD COLUMN `approve_time` datetime NULL COMMENT ''审批时间'' AFTER `approver_user_id`',
    'SELECT ''approve_time 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c3 := (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'hrm_recruit_offer' AND column_name = 'approve_remark');
SET @sql := IF(@c3 = 0,
    'ALTER TABLE `hrm_recruit_offer` ADD COLUMN `approve_remark` varchar(500) NULL COMMENT ''审批意见（通过/驳回都记录）'' AFTER `approve_time`',
    'SELECT ''approve_remark 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 核对：应返回 3 行
-- SELECT column_name, column_comment FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'hrm_recruit_offer'
--    AND column_name IN ('approver_user_id','approve_time','approve_remark');

-- ============================================================
-- Offer 审批口径参数：是否允许提交人本人审批自己提交的 Offer
--   空 / true  = 允许（默认口径：Offer 由 HR 发，也由 HR 批）
--   false / 0  = 不允许，需由其他有 hrm:recruit:offer:approve 权限的人审批
-- 幂等：可重复执行
-- ============================================================
INSERT INTO `infra_config` (`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`, `creator`, `updater`)
SELECT 'biz', 1, 'HRM-Offer 审批允许自审自批', 'hrm.offer.approval.allow-self-approve', 'true', b'1',
       'true/空=允许提交人自己审批；false/0=必须由其他人审批', 'hrm-offer-dev', 'hrm-offer-dev'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT config_key FROM `infra_config` WHERE config_key = 'hrm.offer.approval.allow-self-approve') t);
