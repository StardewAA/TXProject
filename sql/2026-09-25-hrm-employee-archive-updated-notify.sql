-- 与 deploy/mysql/repair/2026-09-25-hrm-employee-archive-updated-notify.sql 内容一致
-- HRM：员工自助完善档案后，提醒直属上级复核（站内信模板）

INSERT INTO `system_notify_template`
  (`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `updater`)
-- 注意：system_notify_template.params 是 JSON 数组（必须是合法 JSON），写成逗号分隔串会导致站内信发送直接报
--       JsonParseException（本轮踩过）。
SELECT '员工档案已更新提醒', 'hrm_employee_archive_updated', '系统通知',
       '员工 {employeeName} 在员工端{changeAction}了{changeType}，请到员工档案中复核。',
       1, '["employeeName","changeType","changeAction"]', 0, 'HRM 员工自助完善档案后的复核提醒', 'hrm-dev', 'hrm-dev'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template`
                  WHERE `code` = 'hrm_employee_archive_updated' AND `deleted` = b'0');

-- 若此前已用逗号分隔串写入过，执行下面这句修正：
UPDATE `system_notify_template`
SET `params` = '["employeeName","changeType","changeAction"]'
WHERE `code` = 'hrm_employee_archive_updated' AND `params` = 'employeeName,changeType,changeAction';
