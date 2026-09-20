-- ============================================================
-- 新增「劳务合同待签署通知」站内信模板（HRM 转员工后自动发送）
-- 说明：system_notify_template 目前是空表，HRM 既有的员工通知也都依赖该表；
--      本脚本只补这一条模板，可按需再补其它模板。
-- 执行库：ruoyi-vue-pro；可重复执行
-- ============================================================

INSERT INTO `system_notify_template`
  (`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '劳务合同待签署通知', 'hrm_contract_sign_notice', '系统通知',
       '您的劳务合同（编号 {contractNo}）已生成，请登录系统查看合同内容并完成签署。',
       1, '["contractNo"]', 0, 'HRM 转员工后自动发送（合同待签署）',
       'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `system_notify_template`
  WHERE `code` = 'hrm_contract_sign_notice' AND `deleted` = b'0'
);

-- 兼容：params 字段是 JSON 数组，若历史数据写成普通字符串会导致反序列化失败，这里统一修正
UPDATE `system_notify_template`
SET `params` = '["contractNo"]'
WHERE `code` = 'hrm_contract_sign_notice' AND `params` NOT LIKE '[%';

-- 【补充】HRM 其它站内信模板（原先整表为空，导致员工端通知全部发送失败）
INSERT INTO `system_notify_template`
  (`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT '招聘面试安排通知' AS name, 'hrm_recruit_interview_arranged' AS code, '系统通知' AS nickname,
         '{candidateName} 您好，您的第 {stageNumber} 轮面试已安排，时间：{interviewTime}，请准时参加。' AS content,
         1 AS type, '["candidateName","stageNumber","interviewTime"]' AS params, 0 AS status,
         'HRM 面试安排通知' AS remark, 'admin' AS creator, NOW() AS create_time,
         'admin' AS updater, NOW() AS update_time, b'0' AS deleted
  UNION ALL SELECT '员工端开通通知', 'hrm_employee_opened', '系统通知',
         '{employeeName} 您好，您的员工端账号已开通，可登录查看个人档案、考勤、工资条等信息。',
         1, '["employeeName"]', 0, 'HRM 员工端开通通知', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT '填写员工档案通知', 'hrm_employee_archive_fill', '系统通知',
         '{employeeName} 您好，请尽快登录员工端完善您的个人档案信息。',
         1, '["employeeName"]', 0, 'HRM 档案补全通知', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT '月度社保表生成通知', 'hrm_insurance_month_record_created', '系统通知',
         '{employeeName} 您好，{year}年{month}月的社保数据已生成，请前往 {route} 查看。',
         1, '["employeeName","year","month","route"]', 0, 'HRM 社保月结通知', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT '绩效待办通知', 'hrm_performance_task_pending', '系统通知',
         '您的绩效计划「{planName}」进入「{stageName}」阶段，请前往 {route} 处理（考核编号 {assessmentId}）。',
         1, '["planName","stageName","assessmentId","route"]', 0, 'HRM 绩效待办通知', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT '绩效处理结果通知', 'hrm_performance_process_result', '系统通知',
         '您的绩效计划「{planName}」有新的处理结果：{actionName} - {result}（考核编号 {assessmentId}）。',
         1, '["planName","actionName","result","assessmentId"]', 0, 'HRM 绩效结果通知', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT '工资条发放通知', 'hrm_salary_slip_sent', '系统通知',
         '{employeeName} 您好，{year}年{month}月工资条已发放，实发金额 {realSalary} 元，请登录员工端查看。',
         1, '["employeeName","year","month","realSalary"]', 0, 'HRM 工资条通知', 'admin', NOW(), 'admin', NOW(), b'0'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM `system_notify_template` x
  WHERE x.`code` = t.`code` AND x.`deleted` = b'0'
);

-- 核对
SELECT `id`, `code`, `name`, `type`, `params`, `status`
FROM `system_notify_template` WHERE `deleted` = b'0' ORDER BY `id`;
