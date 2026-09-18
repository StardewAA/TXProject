-- ============================================================================
-- CRM 报价单测试数据导入（固邦租户 2012）
-- 日期：2026-09-17
--
-- 数据来源（与 sql/crm_test_data 下那套测试数据同源，属缺失的 16/17 两个文件）：
--   sql/crm_test_data/16_报价_报价单.csv         6 条（5501~5506）
--   sql/crm_test_data/17_报价_报价产品明细.csv   10 条
--
-- 落库规则：
--   1. 表：crm_quotation（报价单）+ crm_quotation_product（报价产品明细）
--      + crm_permission（biz_type=9 CRM_QUOTATION，OWNER 级，供详情页数据权限使用）；
--   2. 负责人 owner_user_id 取「该客户在固邦(2012) 现有负责人」，与客户/商机数据保持一致，
--      不沿用原 CSV 的 owner_user_id（那是原租户 1 的用户 id）；
--   3. creator/updater 一律写真实负责人 id（字符串），避免出现「创建人查不到 → 页面 500」
--      （历史脚本常写 creator='1'，而租户 2012 下不存在 id=1 的用户）；
--   4. 审批状态按 CrmAuditStatusEnum 映射（表里没有独立业务状态列）：
--        CSV 0 草稿   → 0  未提交
--        CSV 1 待审批 → 10 审批中
--        CSV 2 已确认 → 20 审核通过
--        CSV 3 已失效 → 40 已取消（原数据集无对应枚举，取最接近的终态）
--        CSV 4 已驳回 → 30 审核不通过
--   5. 金额：total_product_price = total_price = CSV 的 total_amount，discount_percent = 0；
--      明细 product_price=成本价、quotation_price=报价单价、count=数量、total_price=小计；
--   6. 已转合同的报价单保留 contract_id（6001/6002 已在本库中）。
--
-- 幂等：先按 5501~5506 区间清理本次数据，再重新写入，可重复执行。
-- ============================================================================

USE `ruoyi-vue-pro`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0. 幂等清理
-- ---------------------------------------------------------------------------
DELETE FROM crm_quotation_product WHERE tenant_id = 2012 AND quotation_id BETWEEN 5501 AND 5506;
DELETE FROM crm_quotation WHERE tenant_id = 2012 AND id BETWEEN 5501 AND 5506;
DELETE FROM crm_permission WHERE tenant_id = 2012 AND biz_type = 9 AND biz_id BETWEEN 5501 AND 5506;

-- ---------------------------------------------------------------------------
-- 1. 报价单主表
-- ---------------------------------------------------------------------------
INSERT INTO crm_quotation (id, name, no, customer_id, business_id, owner_user_id, process_instance_id, audit_status,
                           order_date, valid_until, contract_id, total_product_price, discount_percent, total_price,
                           remark, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES (5501, '靖边采油厂油管防腐报价(早期版)', 'TXGY-BJ-2026-0001', 3002, 5002,
        (SELECT owner_user_id FROM crm_customer WHERE id = 3002 AND tenant_id = 2012), NULL, 40,
        '2026-07-20 00:00:00', '2026-08-20 00:00:00', NULL, 1177000.00, 0, 1177000.00,
        '早期报价有效期届满未成交，系统自动置为已失效',
        (SELECT CAST(owner_user_id AS CHAR) FROM crm_customer WHERE id = 3002 AND tenant_id = 2012),
        '2026-07-20 00:00:00', NULL, '2026-08-20 00:00:00', b'0', 2012),

       (5502, '延安炼油厂管道内检测服务报价', 'TXGY-BJ-2026-0002', 3005, 5005,
        (SELECT owner_user_id FROM crm_customer WHERE id = 3005 AND tenant_id = 2012), NULL, 20,
        '2026-08-22 00:00:00', '2026-09-22 00:00:00', 6002, 36000.00, 0, 36000.00,
        '审批通过，已一键转管道内检测服务合同',
        (SELECT CAST(owner_user_id AS CHAR) FROM crm_customer WHERE id = 3005 AND tenant_id = 2012),
        '2026-08-22 00:00:00', NULL, '2026-08-30 00:00:00', b'0', 2012),

       (5503, '靖边采油厂油管防腐报价(初版)', 'TXGY-BJ-2026-0003', 3002, 5002,
        (SELECT owner_user_id FROM crm_customer WHERE id = 3002 AND tenant_id = 2012), NULL, 30,
        '2026-08-26 00:00:00', '2026-09-25 00:00:00', NULL, 1485000.00, 0, 1485000.00,
        '销售售价偏高客户异议，审批驳回，退回修改后重报',
        (SELECT CAST(owner_user_id AS CHAR) FROM crm_customer WHERE id = 3002 AND tenant_id = 2012),
        '2026-08-26 00:00:00', NULL, '2026-09-01 00:00:00', b'0', 2012),

       (5504, '靖边采油厂油管防腐报价(修订版)', 'TXGY-BJ-2026-0004', 3002, 5002,
        (SELECT owner_user_id FROM crm_customer WHERE id = 3002 AND tenant_id = 2012), NULL, 20,
        '2026-09-04 00:00:00', '2026-10-04 00:00:00', 6001, 1429000.00, 0, 1429000.00,
        '审批通过，已一键转油管防腐采购合同',
        (SELECT CAST(owner_user_id AS CHAR) FROM crm_customer WHERE id = 3002 AND tenant_id = 2012),
        '2026-09-04 00:00:00', NULL, '2026-09-10 00:00:00', b'0', 2012),

       (5505, '延安炼油厂管道内检测服务报价(增补检测段)', 'TXGY-BJ-2026-0005', 3005, 5005,
        (SELECT owner_user_id FROM crm_customer WHERE id = 3005 AND tenant_id = 2012), NULL, 10,
        '2026-09-08 00:00:00', '2026-10-08 00:00:00', NULL, 24000.00, 0, 24000.00,
        '增补检测段报价，已提交待审批',
        (SELECT CAST(owner_user_id AS CHAR) FROM crm_customer WHERE id = 3005 AND tenant_id = 2012),
        '2026-09-08 00:00:00', NULL, '2026-09-08 00:00:00', b'0', 2012),

       (5506, '靖边采油厂油管防腐报价(第二批追加)', 'TXGY-BJ-2026-0006', 3002, 5002,
        (SELECT owner_user_id FROM crm_customer WHERE id = 3002 AND tenant_id = 2012), NULL, 0,
        '2026-09-10 00:00:00', '2026-10-10 00:00:00', NULL, 622000.00, 0, 622000.00,
        '第二批追加需求，草稿待提交',
        (SELECT CAST(owner_user_id AS CHAR) FROM crm_customer WHERE id = 3002 AND tenant_id = 2012),
        '2026-09-10 00:00:00', NULL, '2026-09-10 00:00:00', b'0', 2012);

-- ---------------------------------------------------------------------------
-- 2. 报价产品明细（creator 取所属报价单的负责人）
-- ---------------------------------------------------------------------------
INSERT INTO crm_quotation_product (quotation_id, product_id, product_price, quotation_price, count, total_price,
                                   creator, create_time, updater, update_time, deleted, tenant_id)
SELECT d.quotation_id, d.product_id, d.product_price, d.quotation_price, d.count, d.total_price,
       CAST(q.owner_user_id AS CHAR), q.order_date, NULL, q.order_date, b'0', 2012
FROM (SELECT 5501 AS quotation_id, 1003 AS product_id, 142.00 AS product_price, 188.00 AS quotation_price, 4000 AS count, 752000.00 AS total_price
      UNION ALL SELECT 5501, 1004, 128.00, 170.00, 2500, 425000.00
      UNION ALL SELECT 5502, 1012, 8600.00, 12000.00, 3, 36000.00
      UNION ALL SELECT 5503, 1003, 142.00, 192.00, 5000, 960000.00
      UNION ALL SELECT 5503, 1004, 128.00, 175.00, 3000, 525000.00
      UNION ALL SELECT 5504, 1003, 142.00, 185.00, 5000, 925000.00
      UNION ALL SELECT 5504, 1004, 128.00, 168.00, 3000, 504000.00
      UNION ALL SELECT 5505, 1012, 8600.00, 12000.00, 2, 24000.00
      UNION ALL SELECT 5506, 1003, 142.00, 185.00, 2000, 370000.00
      UNION ALL SELECT 5506, 1004, 128.00, 168.00, 1500, 252000.00) d
         JOIN crm_quotation q ON q.id = d.quotation_id AND q.tenant_id = 2012;

-- ---------------------------------------------------------------------------
-- 3. 数据权限（biz_type=9 报价单，OWNER 级）
-- ---------------------------------------------------------------------------
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9, q.id, q.owner_user_id, 1, CAST(q.owner_user_id AS CHAR), q.order_date, NULL, q.order_date, b'0', 2012
FROM crm_quotation q
WHERE q.tenant_id = 2012
  AND q.id BETWEEN 5501 AND 5506
  AND q.owner_user_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 4. 校验
-- ---------------------------------------------------------------------------
SELECT '=== 校验：报价单 ===' AS section;
SELECT q.id, q.no, q.name, u.nickname AS 负责人, q.audit_status AS 审批状态, q.total_price AS 金额,
       q.contract_id AS 关联合同, q.valid_until AS 有效期至
FROM crm_quotation q
         LEFT JOIN system_users u ON u.id = q.owner_user_id
WHERE q.tenant_id = 2012 AND q.id BETWEEN 5501 AND 5506
ORDER BY q.id;

SELECT '=== 校验：明细条数与金额是否与主表一致 ===' AS section;
SELECT q.id, COUNT(p.id) AS 明细数, SUM(p.total_price) AS 明细合计, q.total_price AS 主表金额,
       IF(SUM(p.total_price) = q.total_price, '一致', '不一致') AS 校验
FROM crm_quotation q
         LEFT JOIN crm_quotation_product p ON p.quotation_id = q.id AND p.tenant_id = 2012
WHERE q.tenant_id = 2012 AND q.id BETWEEN 5501 AND 5506
GROUP BY q.id, q.total_price
ORDER BY q.id;
