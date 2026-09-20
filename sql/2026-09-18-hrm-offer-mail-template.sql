-- ============================================================
-- HRM Offer 录用通知邮件模板
--   用途：发放 Offer 时自动给候选人发邮件（正文 + Offer 通知 PDF 附件）
--   模板编码：hrm_offer_notice
--   发件账号：id=5（当前唯一可用账号 3167994758@qq.com）
-- 执行库：ruoyi-vue-pro
-- 说明：参数占位符为单花括号 {xxx}（后端用 Hutool StrUtil.format 替换）；
--       params 列必须与代码传入的参数一一对应，缺失会直接报「邮件参数缺失」。
-- ============================================================

INSERT INTO `system_mail_template`
  (`name`, `code`, `account_id`, `nickname`, `title`, `content`, `params`, `status`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'HRM 录用通知（Offer）', 'hrm_offer_notice', 5, '秦川化工产业园',
       '【录用通知】{companyName} 诚邀您加入',
       '<p>{candidateName} 您好：</p><p>经面试评估，{companyName} 决定录用您。</p><p>录用岗位：{postName}<br/>计划入职日期：{plannedOnboardDate}<br/>薪酬标准：{salaryAmount} 元/月<br/>Offer 有效期至：{validUntil}</p><p>详细录用条件、入职需携带材料（身份证原件、特种作业操作证及岗位资格证书、体检报告、银行卡、免冠照片）与相关说明，请查看邮件附件《录用通知书》PDF。</p><p>请在有效期内登录招聘平台确认接受本录用通知；如有疑问请联系招聘负责人。</p><p>{companyName}</p>',
       '["candidateName","companyName","postName","plannedOnboardDate","validUntil","salaryAmount"]',
       0, 'Offer 发放时自动发送，附件为系统生成的录用通知书 PDF',
       'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_mail_template` WHERE `code` = 'hrm_offer_notice');

-- 核对
SELECT `id`, `name`, `code`, `account_id`, `title`, `params`, `status`
FROM `system_mail_template` WHERE `code` = 'hrm_offer_notice';
