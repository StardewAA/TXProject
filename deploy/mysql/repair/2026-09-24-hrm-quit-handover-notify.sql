-- 与 deploy/mysql/repair/2026-09-24-hrm-quit-handover-notify.sql 内容一致
-- HRM：离职交接未完成提醒站内信模板（QA 报告 Q-010 / I-010）
-- 到点自动离职因「交接未完成」被拦下时，给员工本人发提醒，提示尽快补齐交接。

INSERT INTO `system_notify_template`
  (`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `updater`)
SELECT '离职交接未完成提醒', 'hrm_quit_handover_pending', '系统通知',
       '您的离职交接尚未完成（计划离职日期 {planQuitTime}），请在「离职交接」中补齐交接明细并确认，'
       '完成后方可办理离职生效。',
       1, 'employeeName,planQuitTime', 0, 'HRM 离职交接未完成提醒', 'hrm-dev', 'hrm-dev'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template`
                  WHERE `code` = 'hrm_quit_handover_pending' AND `deleted` = b'0');
