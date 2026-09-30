-- 与 deploy/mysql/repair/2026-09-24-hrm-offer-notify-status.sql 内容一致
-- HRM：Offer 录用通知发送结果落库（交接文档 ①）

SET @schema_name := DATABASE();

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='hrm_recruit_offer'
                  AND COLUMN_NAME='notify_status') = 0,
  'ALTER TABLE `hrm_recruit_offer` ADD COLUMN `notify_status` tinyint NULL COMMENT ''录用通知发送状态：0 未发送 / 1 已发送 / 2 发送失败'' AFTER `approval_status`',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='hrm_recruit_offer'
                  AND COLUMN_NAME='notify_time') = 0,
  'ALTER TABLE `hrm_recruit_offer` ADD COLUMN `notify_time` datetime NULL COMMENT ''录用通知最近发送时间'' AFTER `notify_status`',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='hrm_recruit_offer'
                  AND COLUMN_NAME='notify_fail_reason') = 0,
  'ALTER TABLE `hrm_recruit_offer` ADD COLUMN `notify_fail_reason` varchar(500) NULL COMMENT ''录用通知发送失败原因'' AFTER `notify_time`',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
