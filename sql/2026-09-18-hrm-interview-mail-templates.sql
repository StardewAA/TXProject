-- ============================================================
-- HRM 迭代 1.2：面试通知邮件模板
--   1) hrm_interview_notice   面试安排通知（候选人）
--   2) hrm_interview_reminder 面试前 1 天提醒（候选人）
--   发件账号：id=5（当前唯一可用账号 3167994758@qq.com）
--   参数占位符为单花括号 {xxx}，params 必须与代码传入一致
-- 幂等：可重复执行
-- ============================================================

INSERT INTO `system_mail_template`
  (`name`, `code`, `account_id`, `nickname`, `title`, `content`, `params`, `status`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'HRM 面试通知（候选人）', 'hrm_interview_notice', 5, '秦川化工产业园',
       '【面试通知】{candidateName} 您好，诚邀您参加面试',
       '<p>{candidateName} 您好：</p><p>感谢您应聘我司岗位，现安排面试如下：</p><p><b>面试时间</b>：{interviewTime}<br/><b>面试地点/方式</b>：{interviewAddress}<br/><b>面试官</b>：{interviewer}<br/><b>面试轮次</b>：第 {roundNo} 轮</p><p>请携带身份证原件、特种作业操作证及相关岗位资格证书（如有）；提前 15 分钟到达现场或进入线上会议。</p><p>如需调整时间，请尽快与招聘负责人联系。</p><p>秦川化工产业园发展有限公司 人力资源部</p>',
       '["candidateName","interviewTime","interviewAddress","interviewer","roundNo"]',
       0, 'HRM 安排面试后自动发送给候选人', 'interview-dev', NOW(), 'interview-dev', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_mail_template` WHERE `code` = 'hrm_interview_notice');

INSERT INTO `system_mail_template`
  (`name`, `code`, `account_id`, `nickname`, `title`, `content`, `params`, `status`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'HRM 面试提醒（候选人）', 'hrm_interview_reminder', 5, '秦川化工产业园',
       '【面试提醒】{candidateName} 您的面试将于 {interviewTime} 开始',
       '<p>{candidateName} 您好：</p><p>温馨提醒：您的面试将于 <b>{interviewTime}</b> 开始，地点/方式：<b>{interviewAddress}</b>。</p><p>请提前 15 分钟到达或进入线上会议，并携带身份证原件、特种作业操作证及相关岗位资格证书（如有）。</p><p>祝面试顺利！</p><p>秦川化工产业园发展有限公司 人力资源部</p>',
       '["candidateName","interviewTime","interviewAddress"]',
       0, 'HRM 面试前 1 天提醒 Job 使用', 'interview-dev', NOW(), 'interview-dev', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_mail_template` WHERE `code` = 'hrm_interview_reminder');

-- 核对
SELECT `id`, `name`, `code`, `account_id`, `title`, `status`
FROM `system_mail_template` WHERE `code` IN ('hrm_interview_notice', 'hrm_interview_reminder');
