-- 商城店铺结算、交易统计演示数据（tenant_id = 1）
-- 说明：
-- 1. 交易统计按最近 7 天真实订单汇总，只补充缺失日期，不覆盖已有统计。
-- 2. 店铺结算为明确标记的演示单，用于后续结算持久化功能联调。
-- 3. 当前店铺结算页面使用服务内存数据，执行本脚本后仍需通过现有接口生成页面数据。

SET NAMES utf8mb4;

-- 最近 7 天交易统计：基于 trade_order 的真实订单生成。
INSERT INTO trade_statistics (
    `time`, order_create_count, order_pay_count, order_pay_price,
    after_sale_count, after_sale_refund_price, brokerage_settlement_price,
    wallet_pay_price, recharge_pay_count, recharge_pay_price,
    recharge_refund_count, recharge_refund_price,
    creator, updater, deleted, tenant_id
)
SELECT
    DATE(o.create_time) AS `time`,
    COUNT(*) AS order_create_count,
    SUM(CASE WHEN o.pay_status = b'1' THEN 1 ELSE 0 END) AS order_pay_count,
    COALESCE(SUM(CASE WHEN o.pay_status = b'1' THEN o.pay_price ELSE 0 END), 0) AS order_pay_price,
    0 AS after_sale_count,
    0 AS after_sale_refund_price,
    0 AS brokerage_settlement_price,
    COALESCE(SUM(CASE WHEN o.pay_status = b'1' AND o.pay_channel_code = 'wallet'
                      THEN o.pay_price ELSE 0 END), 0) AS wallet_pay_price,
    0 AS recharge_pay_count,
    0 AS recharge_pay_price,
    0 AS recharge_refund_count,
    0 AS recharge_refund_price,
    'mall_demo_20260911' AS creator,
    'mall_demo_20260911' AS updater,
    b'0' AS deleted,
    1 AS tenant_id
FROM trade_order o
WHERE o.tenant_id = 1
  AND o.deleted = b'0'
  AND o.create_time >= CURDATE() - INTERVAL 7 DAY
  AND NOT EXISTS (
      SELECT 1
      FROM trade_statistics s
      WHERE s.tenant_id = 1
        AND s.deleted = b'0'
        AND DATE(s.`time`) = DATE(o.create_time)
  )
GROUP BY DATE(o.create_time);

-- 持久化的店铺结算演示单。单号带 TEST 标识，避免与正式结算单混淆。
INSERT INTO trade_shop_settlement (
    no, shop_id, shop_name, period_start_time, period_end_time,
    order_count, order_pay_amount, refund_amount, settlement_base_amount,
    commission_rate, platform_commission_amount, settlement_amount,
    status, audit_user_id, audit_time, audit_remark,
    settle_user_id, settle_time,
    creator, updater, deleted, tenant_id
)
SELECT
    'TESTSET-202609-910109', 910109, '科盾工业防护店',
    CURDATE() - INTERVAL 30 DAY, CURDATE() - INTERVAL 1 SECOND,
    3, 286800, 0, 286800,
    500, 14340, 272460,
    0, NULL, NULL, '商城店铺结算演示数据',
    NULL, NULL,
    'mall_demo_20260911', 'mall_demo_20260911', b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM trade_shop_settlement
    WHERE tenant_id = 1 AND no = 'TESTSET-202609-910109'
);

INSERT INTO trade_shop_settlement (
    no, shop_id, shop_name, period_start_time, period_end_time,
    order_count, order_pay_amount, refund_amount, settlement_base_amount,
    commission_rate, platform_commission_amount, settlement_amount,
    status, audit_user_id, audit_time, audit_remark,
    settle_user_id, settle_time,
    creator, updater, deleted, tenant_id
)
SELECT
    'TESTSET-202609-910111', 910111, '锐捷管道防护店',
    CURDATE() - INTERVAL 30 DAY, CURDATE() - INTERVAL 1 SECOND,
    5, 632500, 12000, 620500,
    500, 31025, 589475,
    10, 1, NOW(), '商城店铺结算演示数据：审核通过',
    NULL, NULL,
    'mall_demo_20260911', 'mall_demo_20260911', b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM trade_shop_settlement
    WHERE tenant_id = 1 AND no = 'TESTSET-202609-910111'
);

INSERT INTO trade_shop_settlement (
    no, shop_id, shop_name, period_start_time, period_end_time,
    order_count, order_pay_amount, refund_amount, settlement_base_amount,
    commission_rate, platform_commission_amount, settlement_amount,
    status, audit_user_id, audit_time, audit_remark,
    settle_user_id, settle_time,
    creator, updater, deleted, tenant_id
)
SELECT
    'TESTSET-202609-910108', 910108, '陕安防腐保温制品店',
    CURDATE() - INTERVAL 30 DAY, CURDATE() - INTERVAL 1 SECOND,
    2, 198000, 0, 198000,
    500, 9900, 188100,
    20, 1, NOW(), '商城店铺结算演示数据：已确认结算',
    1, NOW(),
    'mall_demo_20260911', 'mall_demo_20260911', b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM trade_shop_settlement
    WHERE tenant_id = 1 AND no = 'TESTSET-202609-910108'
);
