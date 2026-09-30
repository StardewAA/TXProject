-- HRM：试用期到期提醒站内信模板（测试报告第 3.2 项）
-- 幂等：可重复执行

INSERT INTO `system_notify_template`
  (`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `updater`)
SELECT '试用期到期提醒', 'hrm_probation_expiring', '系统通知',
       '您的试用期将于 {probationEndDate} 到期，请与 HR/主管确认转正评估安排。',
       1, 'employeeName,probationEndDate', 0, 'HRM 试用期到期待办提醒', 'hrm-dev', 'hrm-dev'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template`
                  WHERE `code` = 'hrm_probation_expiring' AND `deleted` = b'0');
