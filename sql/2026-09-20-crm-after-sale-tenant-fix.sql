-- =============================================================================
-- 售后模块历史数据租户纠正
--
-- 【问题】
--   部分售后数据是在「租户 1（默认演示租户）」下导入/产生的，而实际业务租户是
--   2012（西安鸥鹏互联科技有限公司 / 固邦防腐）。由于这两张表都带 tenant_id 且
--   受多租户插件过滤，导致在租户 2012 下：
--     1) crm_after_sale_warranty：档案「查不到」——点编辑/详情直接报「质保档案不存在」；
--     2) crm_after_sale_config：售后设置「读不到」——接口回退到代码默认值，
--        在界面改的「质保到期提前提醒天数 / 回访临期天数」实际不生效。
--
-- 【影响范围】仅纠正 tenant_id 误置为 1 的记录；已经是 2012 的数据不动。
-- 【幂等】可重复执行。
-- 【执行】mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
--         执行后清缓存：redis-cli --scan --pattern '*after_sale*' | xargs -r redis-cli del
-- =============================================================================

-- 0. 变更前快照
SELECT '--- 变更前：租户分布 ---' AS s;
SELECT 'crm_after_sale_warranty' AS tbl, tenant_id, COUNT(*) AS cnt,
       GROUP_CONCAT(id ORDER BY id) AS ids
FROM crm_after_sale_warranty GROUP BY tenant_id
UNION ALL
SELECT 'crm_after_sale_config' AS tbl, tenant_id, COUNT(*) AS cnt,
       GROUP_CONCAT(id ORDER BY id) AS ids
FROM crm_after_sale_config GROUP BY tenant_id;

-- 1. 质保档案：租户 1 → 2012
UPDATE crm_after_sale_warranty
SET tenant_id = 2012, updater = '1', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0';

-- 2. 售后设置：租户 1 → 2012
UPDATE crm_after_sale_config
SET tenant_id = 2012, updater = '1', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0';

-- 3. 关联的售后回访计划 / 任务 / 工单 / 回访记录同样纠正（避免档案可见、子表不可见）
UPDATE crm_after_sale_visit_plan
SET tenant_id = 2012, updater = '1', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0';

UPDATE crm_after_sale_visit_task
SET tenant_id = 2012, updater = '1', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0';

UPDATE crm_after_sale_visit_record
SET tenant_id = 2012, updater = '1', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0';

UPDATE crm_after_sale_ticket
SET tenant_id = 2012, updater = '1', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0';

-- 4. 变更后自查（应全部为 2012，且不应再有 tenant_id = 1 的行）
SELECT '--- 变更后：租户分布（应只剩 2012）---' AS s;
SELECT 'crm_after_sale_warranty' AS tbl, tenant_id, COUNT(*) AS cnt
FROM crm_after_sale_warranty GROUP BY tenant_id
UNION ALL
SELECT 'crm_after_sale_config' AS tbl, tenant_id, COUNT(*) AS cnt
FROM crm_after_sale_config GROUP BY tenant_id;

SELECT '--- 售后设置最终值（租户 2012 下应能读到）---' AS s;
SELECT id, tenant_id, warranty_warn_days, task_remind_days, ticket_overdue_days
FROM crm_after_sale_config WHERE deleted = b'0';
