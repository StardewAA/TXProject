-- 2026-09-20：恢复租户 1（OPENLAB BS）的 CRM 售后配置行。
-- 背景：sql/202609201700/CRM_202609201615.sql 里 crm_after_sale_config 的主键 id=1 属于租户 2012，
--       覆盖导入时把本地租户 1 的同 id 配置行顶掉了；两个配置项内容一致，这里补回租户 1 的一行。
-- 幂等：租户 1 已有未删除配置行时不会再插入。
SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM `crm_after_sale_config` WHERE `tenant_id` = 1 AND `deleted` = b'0') = 0,
  'INSERT INTO `crm_after_sale_config` (`warranty_warn_days`,`auto_visit_enabled`,`auto_visit_cycle_type`,`auto_visit_cycle_value`,`task_remind_days`,`task_overdue_days`,`allow_early_visit`,`ticket_overdue_days`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES (30, b''0'', 1, 0, 3, 3, b''0'', 7, ''1'', ''2026-09-20 11:55:51'', ''1'', ''2026-09-20 11:55:51'', b''0'', 1)',
  'SELECT "crm_after_sale_config tenant 1 already present" AS note'));
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;