-- ============================================================================
-- 修复：报价单「BPM 审批已通过，但 CRM 审批状态仍停留在审批中」
-- 日期：2026-09-15
-- 背景：
--   CrmQuotationServiceImpl.submitQuotation 先创建 BPM 流程实例，后回写「审批中」。
--   当「审批人与提交人为同一人」时，BPM 在创建流程实例过程中即自动审批通过，
--   并【同步】回调 updateQuotationAuditStatus；此时 submitQuotation 事务尚未提交（甚至
--   还没把状态写成「审批中」），回调因「必须审批中才允许更新」被拒绝，导致报价单
--   永久停留在「审批中」，无法「一键转为合同」。
--   本脚本用于修复线上/本地已存在的脏数据（代码侧修复见 CrmQuotationServiceImpl）。
-- 注意：BPM 流程数据存放在独立数据源（act_* 表不在本库），此处仅修正 CRM 侧状态。
--       下方 id 列表来自本次复现（crm-server 日志确认回调 status=2=通过）。
--       执行前请先执行末尾的「校验查询」确认范围；如有更多记录，按需补充 id。
-- ============================================================================

USE `ruoyi-vue-pro`;

-- 1) 将「审批中(audit_status=10) + 已关联流程实例 + 仍未转合同」的报价单修正为「审核通过(20)」
--    条件加 deleted=0 防止误改已删除数据；process_instance_id IS NOT NULL 确保确实发起过流程。
UPDATE `crm_quotation`
SET `audit_status` = 20
WHERE `deleted` = 0
  AND `audit_status` = 10
  AND `process_instance_id` IS NOT NULL
  AND `contract_id` IS NULL
  AND `id` IN (3, 4);

-- 2) 校验查询：确认修复后没有仍停留在「审批中」的已发起流程报价单
SELECT id, name, audit_status, process_instance_id, contract_id, deleted
FROM `crm_quotation`
WHERE `deleted` = 0
  AND `audit_status` = 10
  AND `process_instance_id` IS NOT NULL;
-- 期望结果：0 行
