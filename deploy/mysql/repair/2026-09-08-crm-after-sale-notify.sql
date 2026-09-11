-- =====================================================================
-- CRM 售后（质保/回访/工单）站内信模板 SQL
-- 日期：2026-09-08
-- 说明：V1 提醒仅站内信（短信二期接入）。type=2 系统消息，status=1 启用。
--       本脚本幂等：先按 code 清理旧模板，再重新插入。
--       模板编码常量：txgy-module-crm-api .../enums/MessageTemplateConstants.java
-- 应用范围：ruoyi-vue-pro 业务库（system_notify_template）
-- =====================================================================

USE `ruoyi-vue-pro`;

SET NAMES utf8mb4;

DELETE FROM `system_notify_template`
WHERE `code` IN ('crm_after_sale_visit_task_remind',
                 'crm_after_sale_warranty_expire_remind',
                 'crm_after_sale_ticket_assign');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
('回访任务到期提醒', 'crm_after_sale_visit_task_remind', '售后系统',
 '您好，您负责的客户【{customerName}】有一项质保回访任务计划于 {taskDate} 执行，请按时完成回访并登记记录。',
 2, '["customerName","taskDate"]', 1, 'CRM 售后-回访任务临期/到期提醒（XXL-JOB crmAfterSaleVisitTaskRemindJob）', '1', NOW(), '1', NOW(), b'0'),
('质保即将到期提醒', 'crm_after_sale_warranty_expire_remind', '售后系统',
 '您好，客户【{customerName}】的项目【{projectName}】质保将于 {endDate} 到期，请及时安排到期回访与续保/转有偿跟进。',
 2, '["customerName","projectName","endDate"]', 1, 'CRM 售后-质保到期提前提醒（XXL-JOB crmAfterSaleWarrantyRemindJob）', '1', NOW(), '1', NOW(), b'0'),
('问题工单指派通知', 'crm_after_sale_ticket_assign', '售后系统',
 '您好，您被指派处理售后问题工单【{ticketNo}】{ticketTitle}，请及时跟进处理。',
 2, '["ticketNo","ticketTitle"]', 1, 'CRM 售后-问题工单指派/创建指定处理人通知（站内信）', '1', NOW(), '1', NOW(), b'0');
