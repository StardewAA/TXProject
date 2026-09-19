-- ============================================================================
-- 修正 CRM 审批状态口径：历史数据用的旧选项(0/1/2/3) → 代码枚举口径(0/10/20/30/40)
-- 日期：2026-09-18
--
-- 问题背景：
--   字典 crm_audit_status 里同时存在两套值：
--     旧：0 未提交 / 1 审核中 / 2 审核通过 / 3 审核拒绝
--     新：0 未提交 / 10 审批中 / 20 审核通过 / 30 审核不通过 / 40 已取消
--   而后端代码统一使用 CrmAuditStatusEnum（0/10/20/30/40）：
--     - 合同提交审批时写入 PROCESS(10)，审批回调写 APPROVE(20)/REJECT(30)
--     - 待办「待审核合同」按 audit_status = 10 统计
--   历史测试数据按旧口径写入（contract 里是 0/1/2，receivable 里是 2），
--   导致「待办事项 → 待审核合同」数字恒为 0，与合同模块的"审核中"数据不同步。
--
-- 处理：仅迁移旧口径的 1/2/3（0 两套一致、无需处理），精确无歧义。
-- 幂等，可重复执行。
-- ============================================================================

SET @c_before := 0;

-- 合同：1→10 审核中；2→20 审核通过；3→30 审核不通过
UPDATE `crm_contract`
SET audit_status = CASE audit_status WHEN 1 THEN 10 WHEN 2 THEN 20 WHEN 3 THEN 30 ELSE audit_status END
WHERE deleted = 0 AND audit_status IN (1, 2, 3);

-- 回款：1→10 审核中；2→20 审核通过；3→30 审核不通过
UPDATE `crm_receivable`
SET audit_status = CASE audit_status WHEN 1 THEN 10 WHEN 2 THEN 20 WHEN 3 THEN 30 ELSE audit_status END
WHERE deleted = 0 AND audit_status IN (1, 2, 3);

-- 回款计划（如存在旧口径）
UPDATE `crm_receivable_plan`
SET return_type = return_type
WHERE 1 = 0; -- 占位：回款计划无 audit_status 列，避免误改

-- ---------------------------------------------------------------------------
-- 结果自查
-- ---------------------------------------------------------------------------
SELECT '=== 租户2012 合同 audit_status 分布（迁移后，应只剩 0/10/20/30/40） ===' AS info;
SELECT audit_status, COUNT(*) AS c FROM crm_contract WHERE tenant_id = 2012 AND deleted = 0 GROUP BY audit_status;

SELECT '=== 待办「待审核合同」口径：我负责的 + 审批中(10) ===' AS info;
SELECT COUNT(*) AS 待审核合同数 FROM crm_contract c
JOIN crm_permission p ON p.biz_type = 5 AND p.biz_id = c.id AND p.deleted = 0
WHERE c.tenant_id = 2012 AND c.deleted = 0 AND c.audit_status = 10 AND p.user_id = 80446 AND p.level = 1;

SELECT '=== 租户2012 回款 audit_status 分布（迁移后） ===' AS info;
SELECT audit_status, COUNT(*) AS c FROM crm_receivable WHERE tenant_id = 2012 AND deleted = 0 GROUP BY audit_status;

SELECT '=== 全库自查：仍为旧口径的合同/回款（应为空） ===' AS info;
SELECT 'contract' AS t, id, audit_status FROM crm_contract WHERE deleted = 0 AND audit_status IN (1, 2, 3)
UNION ALL
SELECT 'receivable', id, audit_status FROM crm_receivable WHERE deleted = 0 AND audit_status IN (1, 2, 3);
