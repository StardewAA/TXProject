-- =============================================================================
-- 售后模块租户归属纠正（按「关联业务对象的租户」对齐，而不是按表无脑统一）
--
-- 【背景】
--   售后数据曾出现 tenant_id 被误置为 1（租户 1 = 演示租户「西安鸥鹏互联科技有限公司」）
--   的情况，而实际业务租户是 2012（固邦防腐材料制造有限公司）。由此导致两类故障：
--     1) 创建质保档案报「系统错误」（500）：
--        档案在租户 2012 下查不到 → 「该合同是否已建档案」校验放行 → 插入时
--        撞唯一索引 uk_contract_id → DuplicateKeyException → 前端只看到「系统错误」；
--     2) 售后设置读不到 → getConfig() 回退代码默认值，界面上配置的
--        「质保到期提前提醒天数」实际不生效。
--
-- 【纠正依据（关键，勿改成无脑统一）】
--   必须以「关联业务对象的租户」为准，而不是把所有 tenant_id=1 的售后记录一律改成 2012。
--   反例：质保档案 id=3 关联的合同是租户 1 的演示合同（Q100「我是合同」），
--   它本就应该留在租户 1；若强行改成 2012，就会制造「跨租户幽灵数据」——
--   租户内校验查不到它、数据库唯一索引却已被它占用，于是创建接口直接 500。
--   本脚本对此做了自我纠正：先按合同租户对齐，把误改的记录改回去。
--
-- 【幂等】可重复执行。
-- 【执行】mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
-- =============================================================================

SELECT '=== 1. 对齐前：档案租户 vs 其合同租户 ===' AS s;
SELECT w.id, w.no, w.contract_id,
       w.tenant_id AS warranty_tenant, c.tenant_id AS contract_tenant,
       CASE WHEN w.tenant_id = c.tenant_id THEN 'OK' ELSE '待修正' END AS chk
FROM crm_after_sale_warranty w
LEFT JOIN crm_contract c ON c.id = w.contract_id
ORDER BY w.id;

-- 1.1 质保档案：以「来源合同的租户」为准（同时纠正历史误置与上一次的误改）
UPDATE crm_after_sale_warranty w
JOIN crm_contract c ON c.id = w.contract_id
SET w.tenant_id = c.tenant_id,
    w.updater = '1',
    w.update_time = NOW()
WHERE w.tenant_id <> c.tenant_id;

-- 1.2 关联子表：以「所属质保档案的租户」为准
UPDATE crm_after_sale_visit_plan t
JOIN crm_after_sale_warranty w ON w.id = t.warranty_id
SET t.tenant_id = w.tenant_id, t.updater = '1', t.update_time = NOW()
WHERE t.tenant_id <> w.tenant_id;

UPDATE crm_after_sale_visit_task t
JOIN crm_after_sale_warranty w ON w.id = t.warranty_id
SET t.tenant_id = w.tenant_id, t.updater = '1', t.update_time = NOW()
WHERE t.tenant_id <> w.tenant_id;

UPDATE crm_after_sale_visit_record t
JOIN crm_after_sale_warranty w ON w.id = t.warranty_id
SET t.tenant_id = w.tenant_id, t.updater = '1', t.update_time = NOW()
WHERE t.tenant_id <> w.tenant_id;

-- 2. 售后设置表：该表是「每租户一份」的配置，没有可关联的业务对象，
--    因此不做搬迁，只做「缺配置的租户补齐」，避免把某个租户的配置搬走。
--    这里为「存在 CRM 合同的租户」各补一份默认配置（不存在才插，幂等）。
INSERT INTO crm_after_sale_config
    (tenant_id, warranty_warn_days, auto_visit_enabled, auto_visit_cycle_type, auto_visit_cycle_value,
     task_remind_days, task_overdue_days, allow_early_visit, ticket_overdue_days,
     creator, create_time, updater, update_time, deleted)
SELECT t.tenant_id, 30, b'0', 1, 0, 3, 3, b'0', 7,
       '1', NOW(), '1', NOW(), b'0'
FROM (SELECT DISTINCT tenant_id FROM crm_contract WHERE deleted = b'0') t
WHERE NOT EXISTS (
    SELECT 1 FROM crm_after_sale_config c
    WHERE c.tenant_id = t.tenant_id AND c.deleted = b'0'
);

SELECT '=== 2. 对齐后自查（全部应为 OK）===' AS s;
SELECT w.id, w.no, w.contract_id,
       w.tenant_id AS warranty_tenant, c.tenant_id AS contract_tenant,
       CASE WHEN w.tenant_id = c.tenant_id THEN 'OK' ELSE '❌ 仍不一致' END AS chk
FROM crm_after_sale_warranty w
LEFT JOIN crm_contract c ON c.id = w.contract_id
ORDER BY w.id;

SELECT '=== 3. 各租户的售后设置（每个有 CRM 合同的租户都应有一份）===' AS s;
SELECT c.tenant_id, t.name AS tenant_name, c.warranty_warn_days
FROM crm_after_sale_config c
LEFT JOIN system_tenant t ON t.id = c.tenant_id
WHERE c.deleted = b'0' ORDER BY c.tenant_id;
