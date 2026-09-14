-- =====================================================================
-- CRM 售后（质保/回访/工单）前端菜单 + 字典数据 SQL
-- 日期：2026-09-08
-- 说明：
--   1. 菜单 ID 段 32600~32699（避开 FMS 32000~32132、HRM 1476~1636）。
--   2. 顶级「售后管理」目录挂到 CRM 顶级目录（path='/crm'）下，
--      父级 ID 通过查询动态获取，避免不同环境 ID 不一致。
--   3. 本脚本幂等：先按 ID/字典类型清理，再重新插入。
--   4. 超级管理员自动拥有全部菜单，无需绑定 system_role_menu；
--      其它角色请在【系统管理-角色管理】中勾选以下菜单后生效。
--   5. 前端页面组件路径：
--      crm/after-sale/warranty/index、plan/index、task/index、record/index、
--      ticket/index、config/index（apps/web-antd/src/views 下）
-- 应用范围：ruoyi-vue-pro 业务库
-- =====================================================================

USE `ruoyi-vue-pro`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 清理历史数据（幂等）
-- ---------------------------------------------------------------------
DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 32600 AND 32699;
DELETE FROM `system_menu` WHERE `id` BETWEEN 32600 AND 32699;
DELETE FROM `system_dict_data`
WHERE `dict_type` IN ('crm_after_sale_warranty_status',
                      'crm_after_sale_visit_plan_type',
                      'crm_after_sale_visit_plan_status',
                      'crm_after_sale_visit_cycle',
                      'crm_after_sale_visit_task_status',
                      'crm_after_sale_visit_type',
                      'crm_after_sale_satisfaction',
                      'crm_after_sale_ticket_status',
                      'crm_after_sale_ticket_source',
                      'crm_after_sale_ticket_priority',
                      'crm_after_sale_ticket_operate_type',
                      'crm_after_sale_business_source_type');
DELETE FROM `system_dict_type`
WHERE `type` IN ('crm_after_sale_warranty_status',
                 'crm_after_sale_visit_plan_type',
                 'crm_after_sale_visit_plan_status',
                 'crm_after_sale_visit_cycle',
                 'crm_after_sale_visit_task_status',
                 'crm_after_sale_visit_type',
                 'crm_after_sale_satisfaction',
                 'crm_after_sale_ticket_status',
                 'crm_after_sale_ticket_source',
                 'crm_after_sale_ticket_priority',
                 'crm_after_sale_ticket_operate_type',
                 'crm_after_sale_business_source_type');

-- ---------------------------------------------------------------------
-- 2. 字典类型 + 字典数据
-- ---------------------------------------------------------------------
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
('CRM 售后的质保状态', 'crm_after_sale_warranty_status', 0, 'CRM 售后-质保状态', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的回访计划类型', 'crm_after_sale_visit_plan_type', 0, 'CRM 售后-回访计划类型', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的回访计划状态', 'crm_after_sale_visit_plan_status', 0, 'CRM 售后-回访计划状态', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的回访周期', 'crm_after_sale_visit_cycle', 0, 'CRM 售后-回访周期', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的回访任务状态', 'crm_after_sale_visit_task_status', 0, 'CRM 售后-回访任务状态', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的回访方式', 'crm_after_sale_visit_type', 0, 'CRM 售后-回访方式', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的回访满意度', 'crm_after_sale_satisfaction', 0, 'CRM 售后-回访满意度', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的工单状态', 'crm_after_sale_ticket_status', 0, 'CRM 售后-工单状态', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的工单来源', 'crm_after_sale_ticket_source', 0, 'CRM 售后-工单来源', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的工单优先级', 'crm_after_sale_ticket_priority', 0, 'CRM 售后-工单优先级', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的工单流转类型', 'crm_after_sale_ticket_operate_type', 0, 'CRM 售后-工单流转类型', '1', NOW(), '1', NOW(), b'0'),
('CRM 售后的转商机类型', 'crm_after_sale_business_source_type', 0, 'CRM 售后-转商机类型', '1', NOW(), '1', NOW(), b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '质保中', '1', 'crm_after_sale_warranty_status', 0, 'primary', '', '质保中', '1', NOW(), '1', NOW(), b'0'),
(2, '即将到期', '2', 'crm_after_sale_warranty_status', 0, 'warning', '', '即将到期', '1', NOW(), '1', NOW(), b'0'),
(3, '已到期终止', '3', 'crm_after_sale_warranty_status', 0, 'danger', '', '已到期终止', '1', NOW(), '1', NOW(), b'0'),
(1, '手动', '1', 'crm_after_sale_visit_plan_type', 0, 'primary', '', '手动创建', '1', NOW(), '1', NOW(), b'0'),
(2, '自动', '2', 'crm_after_sale_visit_plan_type', 0, 'success', '', '自动生成', '1', NOW(), '1', NOW(), b'0'),
(1, '启用', '1', 'crm_after_sale_visit_plan_status', 0, 'success', '', '启用', '1', NOW(), '1', NOW(), b'0'),
(2, '停用', '2', 'crm_after_sale_visit_plan_status', 0, 'danger', '', '停用', '1', NOW(), '1', NOW(), b'0'),
(3, '已完成', '3', 'crm_after_sale_visit_plan_status', 0, 'default', '', '已完成', '1', NOW(), '1', NOW(), b'0'),
(1, '每季度', '1', 'crm_after_sale_visit_cycle', 0, 'primary', '', '每季度', '1', NOW(), '1', NOW(), b'0'),
(2, '每半年', '2', 'crm_after_sale_visit_cycle', 0, 'primary', '', '每半年', '1', NOW(), '1', NOW(), b'0'),
(3, '每年', '3', 'crm_after_sale_visit_cycle', 0, 'primary', '', '每年', '1', NOW(), '1', NOW(), b'0'),
(4, '质保到期前重点回访', '4', 'crm_after_sale_visit_cycle', 0, 'warning', '', '质保到期前重点回访', '1', NOW(), '1', NOW(), b'0'),
(1, '待处理', '1', 'crm_after_sale_visit_task_status', 0, 'warning', '', '待处理', '1', NOW(), '1', NOW(), b'0'),
(2, '已完成', '2', 'crm_after_sale_visit_task_status', 0, 'success', '', '已完成', '1', NOW(), '1', NOW(), b'0'),
(3, '已逾期', '3', 'crm_after_sale_visit_task_status', 0, 'danger', '', '已逾期', '1', NOW(), '1', NOW(), b'0'),
(1, '电话', '1', 'crm_after_sale_visit_type', 0, 'primary', '', '电话回访', '1', NOW(), '1', NOW(), b'0'),
(2, '上门', '2', 'crm_after_sale_visit_type', 0, 'success', '', '上门回访', '1', NOW(), '1', NOW(), b'0'),
(3, '微信', '3', 'crm_after_sale_visit_type', 0, 'success', '', '微信回访', '1', NOW(), '1', NOW(), b'0'),
(4, '邮件', '4', 'crm_after_sale_visit_type', 0, 'primary', '', '邮件回访', '1', NOW(), '1', NOW(), b'0'),
(1, '非常不满意', '1', 'crm_after_sale_satisfaction', 0, 'danger', '', '非常不满意', '1', NOW(), '1', NOW(), b'0'),
(2, '不满意', '2', 'crm_after_sale_satisfaction', 0, 'danger', '', '不满意', '1', NOW(), '1', NOW(), b'0'),
(3, '一般', '3', 'crm_after_sale_satisfaction', 0, 'warning', '', '一般', '1', NOW(), '1', NOW(), b'0'),
(4, '满意', '4', 'crm_after_sale_satisfaction', 0, 'success', '', '满意', '1', NOW(), '1', NOW(), b'0'),
(5, '非常满意', '5', 'crm_after_sale_satisfaction', 0, 'success', '', '非常满意', '1', NOW(), '1', NOW(), b'0'),
(1, '待处理', '1', 'crm_after_sale_ticket_status', 0, 'warning', '', '待处理', '1', NOW(), '1', NOW(), b'0'),
(2, '处理中', '2', 'crm_after_sale_ticket_status', 0, 'primary', '', '处理中', '1', NOW(), '1', NOW(), b'0'),
(3, '已完成', '3', 'crm_after_sale_ticket_status', 0, 'success', '', '已完成', '1', NOW(), '1', NOW(), b'0'),
(4, '已关闭', '4', 'crm_after_sale_ticket_status', 0, 'default', '', '已关闭', '1', NOW(), '1', NOW(), b'0'),
(1, '回访发现', '1', 'crm_after_sale_ticket_source', 0, 'primary', '', '回访发现', '1', NOW(), '1', NOW(), b'0'),
(2, '客户报障', '2', 'crm_after_sale_ticket_source', 0, 'warning', '', '客户报障', '1', NOW(), '1', NOW(), b'0'),
(1, '高', '1', 'crm_after_sale_ticket_priority', 0, 'danger', '', '高', '1', NOW(), '1', NOW(), b'0'),
(2, '中', '2', 'crm_after_sale_ticket_priority', 0, 'warning', '', '中', '1', NOW(), '1', NOW(), b'0'),
(3, '低', '3', 'crm_after_sale_ticket_priority', 0, 'default', '', '低', '1', NOW(), '1', NOW(), b'0'),
(1, '指派', '1', 'crm_after_sale_ticket_operate_type', 0, 'primary', '', '指派', '1', NOW(), '1', NOW(), b'0'),
(2, '状态变更', '2', 'crm_after_sale_ticket_operate_type', 0, 'primary', '', '状态变更', '1', NOW(), '1', NOW(), b'0'),
(3, '处理记录', '3', 'crm_after_sale_ticket_operate_type', 0, 'success', '', '处理记录', '1', NOW(), '1', NOW(), b'0'),
(4, '转有偿', '4', 'crm_after_sale_ticket_operate_type', 0, 'warning', '', '转有偿', '1', NOW(), '1', NOW(), b'0'),
(5, '关闭', '5', 'crm_after_sale_ticket_operate_type', 0, 'default', '', '关闭', '1', NOW(), '1', NOW(), b'0'),
(1, '复购', '1', 'crm_after_sale_business_source_type', 0, 'primary', '', '复购', '1', NOW(), '1', NOW(), b'0'),
(2, '续签', '2', 'crm_after_sale_business_source_type', 0, 'success', '', '续签', '1', NOW(), '1', NOW(), b'0'),
(3, '扩项/新工程', '3', 'crm_after_sale_business_source_type', 0, 'warning', '', '扩项/新工程', '1', NOW(), '1', NOW(), b'0');

-- ---------------------------------------------------------------------
-- 3. 菜单：售后管理（挂到 CRM 顶级目录下）
-- ---------------------------------------------------------------------
SET @crmParentId = (SELECT `id` FROM `system_menu` WHERE `parent_id` = 0 AND `path` = '/crm' AND `deleted` = b'0' LIMIT 1);
SET @crmParentId = IFNULL(@crmParentId, 0);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(32600, '售后管理', '', 1, 90, @crmParentId, 'after-sale', 'ep:service', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 3.1 质保档案
(32601, '质保档案', '', 2, 1, 32600, 'warranty', 'ep:files', 'crm/after-sale/warranty/index', 'CrmAfterSaleWarranty', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32602, '质保档案查询', 'crm:after-sale-warranty:query', 3, 1, 32601, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32603, '质保档案创建', 'crm:after-sale-warranty:create', 3, 2, 32601, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32604, '质保档案修改', 'crm:after-sale-warranty:update', 3, 3, 32601, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32605, '质保档案删除', 'crm:after-sale-warranty:delete', 3, 4, 32601, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 3.2 回访计划
(32610, '回访计划', '', 2, 2, 32600, 'plan', 'ep:calendar', 'crm/after-sale/plan/index', 'CrmAfterSaleVisitPlan', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32611, '回访计划查询', 'crm:after-sale-visit-plan:query', 3, 1, 32610, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32612, '回访计划创建', 'crm:after-sale-visit-plan:create', 3, 2, 32610, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32613, '回访计划修改', 'crm:after-sale-visit-plan:update', 3, 3, 32610, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32614, '回访计划删除', 'crm:after-sale-visit-plan:delete', 3, 4, 32610, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 3.3 回访任务
(32620, '回访任务', '', 2, 3, 32600, 'task', 'ep:tickets', 'crm/after-sale/task/index', 'CrmAfterSaleVisitTask', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32621, '回访任务查询', 'crm:after-sale-visit-task:query', 3, 1, 32620, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32622, '回访任务创建', 'crm:after-sale-visit-task:create', 3, 2, 32620, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32623, '回访任务修改', 'crm:after-sale-visit-task:update', 3, 3, 32620, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32624, '回访任务删除', 'crm:after-sale-visit-task:delete', 3, 4, 32620, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 3.4 回访记录
(32630, '回访记录', '', 2, 4, 32600, 'record', 'ep:notebook', 'crm/after-sale/record/index', 'CrmAfterSaleVisitRecord', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32631, '回访记录查询', 'crm:after-sale-visit-record:query', 3, 1, 32630, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32632, '回访记录创建', 'crm:after-sale-visit-record:create', 3, 2, 32630, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32633, '回访记录修改', 'crm:after-sale-visit-record:update', 3, 3, 32630, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32634, '回访记录删除', 'crm:after-sale-visit-record:delete', 3, 4, 32630, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 3.5 问题工单
(32640, '问题工单', '', 2, 5, 32600, 'ticket', 'ep:warning', 'crm/after-sale/ticket/index', 'CrmAfterSaleTicket', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32641, '问题工单查询', 'crm:after-sale-ticket:query', 3, 1, 32640, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32642, '问题工单创建', 'crm:after-sale-ticket:create', 3, 2, 32640, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32643, '问题工单修改', 'crm:after-sale-ticket:update', 3, 3, 32640, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32644, '问题工单删除', 'crm:after-sale-ticket:delete', 3, 4, 32640, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 3.6 售后设置
(32650, '售后设置', '', 2, 6, 32600, 'config', 'ep:setting', 'crm/after-sale/config/index', 'CrmAfterSaleConfig', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32651, '售后设置查询', 'crm:after-sale-config:query', 3, 1, 32650, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(32652, '售后设置修改', 'crm:after-sale-config:update', 3, 2, 32650, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
