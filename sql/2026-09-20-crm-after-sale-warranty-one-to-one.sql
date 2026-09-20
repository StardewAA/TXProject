-- =============================================================================
-- 售后质保档案：一个合同仅允许一份（一对一）—— 数据层唯一约束 + 历史重复清理
--
-- 【业务规则（明确口径）】
--   1. 质保档案只能基于「审核通过」的合同创建（既有业务校验，不变）；
--   2. 一个合同最多只能有一份「有效（未删除）」的质保档案：
--        * 已存在有效档案          -> 阻止创建，提示「该合同已创建质保档案，不可重复创建」；
--        * 档案被删除（逻辑删除）  -> 允许基于该合同重新创建；
--        * 档案已到期终止          -> 仍占用该合同，不允许重复创建（历史事实需保留）；
--        * 合同审批状态被回退 / 取消（作废）-> 已创建的档案保留、不被自动删除，
--          但同样不允许重复创建；若要重建，需先删除原档案。
--   3. 应用层已在 CrmAfterSaleWarrantyServiceImpl#createWarranty 做业务校验并给出友好提示；
--      本脚本再从数据库层面加唯一约束，防止并发或绕过接口写入造成重复数据。
--
-- 【实现要点（为什么用生成列）】
--   质保档案是「逻辑删除」（deleted 位）。若直接在 contract_id 上加唯一索引，
--   被删除的历史记录会一直占位，导致「删除后无法重新创建」。因此采用生成列：
--       未删除：uk_contract_id = contract_id（唯一 → 保证一对一）
--       已删除：uk_contract_id = NULL（MySQL 唯一索引允许多个 NULL → 可重新创建）
--
-- 【特性】幂等，可重复执行。
-- 【执行】mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
-- =============================================================================

-- 0. 清理前自查：列出历史重复（如有）
SELECT '--- 清理前的重复情况 ---' AS s;
SELECT contract_id, COUNT(*) AS cnt, GROUP_CONCAT(id ORDER BY id) AS warranty_ids
FROM crm_after_sale_warranty
WHERE deleted = b'0'
GROUP BY contract_id
HAVING COUNT(*) > 1;

-- 1. 清理历史重复：同一合同下只保留一条有效档案
--    保留优先级：关联业务数据（回访计划 / 回访任务 / 工单）最多 → 创建最早（id 最小）；
--    这样可保证被删除的重复档案不会带着「孤立的回访计划 / 任务」。
DROP TEMPORARY TABLE IF EXISTS tmp_warranty_keep;
CREATE TEMPORARY TABLE tmp_warranty_keep AS
SELECT contract_id,
       SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY rel_cnt DESC, id ASC), ',', 1) + 0 AS keep_id
FROM (
    SELECT w.id, w.contract_id,
           (SELECT COUNT(*) FROM crm_after_sale_visit_plan p WHERE p.warranty_id = w.id AND p.deleted = b'0')
         + (SELECT COUNT(*) FROM crm_after_sale_visit_task t WHERE t.warranty_id = w.id AND t.deleted = b'0')
         + (SELECT COUNT(*) FROM crm_after_sale_ticket     k WHERE k.warranty_id = w.id AND k.deleted = b'0') AS rel_cnt
    FROM crm_after_sale_warranty w
    WHERE w.deleted = b'0'
) x
GROUP BY contract_id
HAVING COUNT(*) > 1;

UPDATE crm_after_sale_warranty w
JOIN tmp_warranty_keep k ON k.contract_id = w.contract_id
SET w.deleted = b'1', w.updater = '1', w.update_time = NOW()
WHERE w.deleted = b'0' AND w.id <> k.keep_id;

DROP TEMPORARY TABLE IF EXISTS tmp_warranty_keep;

-- 2. 数据层唯一约束（幂等：索引已存在则跳过）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'crm_after_sale_warranty'
                     AND INDEX_NAME = 'uk_contract_id');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE crm_after_sale_warranty
       ADD COLUMN uk_contract_id BIGINT
         GENERATED ALWAYS AS (IF(deleted = b''0'', contract_id, NULL)) VIRTUAL
         COMMENT ''一对一约束生成列：未删除=contract_id，已删除=NULL'',
       ADD UNIQUE KEY uk_contract_id (uk_contract_id)',
    'SELECT ''uk_contract_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 自查
SELECT '--- 唯一索引 ---' AS s;
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'crm_after_sale_warranty'
  AND INDEX_NAME = 'uk_contract_id'
GROUP BY INDEX_NAME, NON_UNIQUE;

SELECT '--- 剩余重复（应为空）---' AS s;
SELECT contract_id, COUNT(*) AS cnt
FROM crm_after_sale_warranty
WHERE deleted = b'0'
GROUP BY contract_id
HAVING COUNT(*) > 1;

SELECT '--- 清理后的有效质保档案 ---' AS s;
SELECT id, no, contract_id, customer_id, status, deleted
FROM crm_after_sale_warranty
WHERE deleted = b'0'
ORDER BY id;
