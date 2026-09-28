-- ============================================================
-- 合并远程「资格证件域」：把本地证照中心迁移到远程实现
-- 日期：2026-09-20
-- 背景：本地自建了一套「证照管理（证照类型 + 证照台账 + 人工核验）」，
--       远程本次合并带来了一整套「资格证件域（证件类型 + 证件台账 + 到期预警 + 续证/复审 + 状态自动计算）」，
--       两者表名相同、列不同，远程原版 SQL（sql/2026-09-18-hrm-certificate-domain.sql）
--       在本库上会因 hrm_employee_certificate.type_id 已存在 / hrm_certificate_type 已存在而失败。
--       本脚本按实际差异做增量迁移，可重复执行（除 ALTER 加列外，重复执行会报重复列，属预期）。
-- 执行库：ruoyi-vue-pro
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------
-- 1. hrm_certificate_type：补齐远程列；并把 code 放宽为可空（远程保存允许不填编码）
-- ----------------------------------------------------------------------
ALTER TABLE `hrm_certificate_type`
  ADD COLUMN `review_cycle_months` int DEFAULT NULL COMMENT '复审周期（月）',
  ADD COLUMN `advance_days` int NOT NULL DEFAULT 30 COMMENT '默认提前预警天数',
  ADD COLUMN `require_attachment` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必须上传证件附件';

ALTER TABLE `hrm_certificate_type`
  MODIFY COLUMN `code` varchar(64) DEFAULT NULL COMMENT '证件类型编码';

-- 本地 warn_days（到期预警提前天数）语义与远程 advance_days 相同，做一次映射
UPDATE `hrm_certificate_type`
   SET `advance_days` = `warn_days`
 WHERE `warn_days` IS NOT NULL AND `warn_days` > 0;

-- 本地独有列 mandatory / warn_days / issuing_authority 暂时保留（不删，数据不丢，便于回退）

-- ----------------------------------------------------------------------
-- 2. hrm_employee_certificate：只补缺失的 3 列（type_id 本地已存在，不能重复加）
-- ----------------------------------------------------------------------
ALTER TABLE `hrm_employee_certificate`
  ADD COLUMN `review_time` datetime DEFAULT NULL COMMENT '复审日期',
  ADD COLUMN `attachment_url` varchar(512) DEFAULT NULL COMMENT '证件附件地址',
  ADD COLUMN `status` tinyint DEFAULT NULL COMMENT '证件状态：1 有效，2 即将到期，3 过期，4 注销';

-- 数据迁移：本地的 review_date / file_urls(逗号分隔多张) 映射到远程的 review_time / attachment_url(单张)
UPDATE `hrm_employee_certificate`
   SET `review_time` = `review_date`
 WHERE `review_date` IS NOT NULL AND `review_time` IS NULL;

UPDATE `hrm_employee_certificate`
   SET `attachment_url` = SUBSTRING_INDEX(`file_urls`, ',', 1)
 WHERE `file_urls` IS NOT NULL AND `file_urls` <> ''
   AND (`attachment_url` IS NULL OR `attachment_url` = '');

-- 按有效期、提前 30 天预警口径初始化证件状态
UPDATE `hrm_employee_certificate`
   SET `status` = CASE
         WHEN `end_time` IS NULL THEN 1
         WHEN `end_time` < NOW() THEN 3
         WHEN `end_time` <= DATE_ADD(NOW(), INTERVAL 30 DAY) THEN 2
         ELSE 1
       END
 WHERE `status` IS NULL;

-- 本地独有列 review_date / file_urls / verify_status / verify_user_id / verify_time 暂时保留

-- ----------------------------------------------------------------------
-- 3. 新建远程的两张表（续证/复审记录、预警记录）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hrm_certificate_renewal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '续证/复审记录编号',
  `certificate_id` bigint NOT NULL COMMENT '证件台账编号',
  `employee_id` bigint NOT NULL COMMENT '员工编号',
  `type` tinyint NOT NULL COMMENT '类型：1 续证，2 复审',
  `old_no` varchar(255) DEFAULT NULL COMMENT '办理前证件编号',
  `new_no` varchar(255) DEFAULT NULL COMMENT '办理后证件编号',
  `old_start_time` datetime DEFAULT NULL COMMENT '办理前有效开始日期',
  `old_end_time` datetime DEFAULT NULL COMMENT '办理前有效结束日期',
  `new_start_time` datetime DEFAULT NULL COMMENT '办理后有效开始日期',
  `new_end_time` datetime DEFAULT NULL COMMENT '办理后有效结束日期',
  `review_time` datetime DEFAULT NULL COMMENT '复审日期',
  `issuing_authority` varchar(255) DEFAULT NULL COMMENT '发证机构',
  `issuing_time` datetime DEFAULT NULL COMMENT '发证日期',
  `attachment_url` varchar(512) DEFAULT NULL COMMENT '新证件附件地址',
  `operator_user_id` bigint DEFAULT NULL COMMENT '经办人用户编号',
  `renew_time` datetime DEFAULT NULL COMMENT '办理时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hrm_certificate_renewal_certificate_id` (`certificate_id`),
  KEY `idx_hrm_certificate_renewal_employee_id` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 证件续证/复审记录表';

CREATE TABLE IF NOT EXISTS `hrm_certificate_warning` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '预警记录编号',
  `certificate_id` bigint NOT NULL COMMENT '证件台账编号',
  `employee_id` bigint NOT NULL COMMENT '员工编号',
  `type` tinyint NOT NULL COMMENT '预警类型：1 证件到期，2 证件复审',
  `warning_date` date NOT NULL COMMENT '预警触发日期',
  `expire_date` datetime DEFAULT NULL COMMENT '到期日期或复审日期',
  `remain_days` int DEFAULT NULL COMMENT '剩余天数，负数表示已过期',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态：0 待处理，1 已处理，2 已忽略',
  `handle_user_id` bigint DEFAULT NULL COMMENT '处理人用户编号',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_remark` varchar(500) DEFAULT NULL COMMENT '处理意见',
  `notify_status` tinyint NOT NULL DEFAULT 0 COMMENT '通知状态：0 待通知，1 已通知，2 通知失败',
  `notify_time` datetime DEFAULT NULL COMMENT '通知时间',
  `notify_fail_reason` varchar(500) DEFAULT NULL COMMENT '通知失败原因',
  `notify_retry_count` int NOT NULL DEFAULT 0 COMMENT '通知重试次数',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hrm_certificate_warning_unique` (`certificate_id`, `type`, `warning_date`),
  KEY `idx_hrm_certificate_warning_status` (`status`),
  KEY `idx_hrm_certificate_warning_notify` (`notify_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 证件预警记录表';

-- ----------------------------------------------------------------------
-- 4. 菜单：先清掉本地自建的「证照管理」菜单（6783-6790），再建远程的「资格证件」目录
-- ----------------------------------------------------------------------
DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 6783 AND 6790;
DELETE FROM `system_menu`      WHERE `id`      BETWEEN 6783 AND 6790;

ALTER TABLE `hrm_certificate_type` COMMENT='HRM 证件类型表';

