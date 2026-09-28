-- Tenant 2009 wallet recharge must use its own payment application.
-- Reusing the mall application sends recharge callbacks to the trade order endpoint
-- and incorrectly runs product-order/WMS validation against a recharge record id.
SET @tenant_id := 2009;

INSERT INTO pay_app
    (app_key, name, status, remark, order_notify_url, refund_notify_url,
     transfer_notify_url, creator, updater, deleted, tenant_id)
SELECT 'wallet', '会员钱包', 0, '用户余额充值专用支付应用',
       'http://gateway:48080/admin-api/pay/wallet-recharge/update-paid',
       'http://gateway:48080/admin-api/pay/wallet-recharge/update-refunded',
       '', '1', '1', b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM pay_app
    WHERE tenant_id = @tenant_id AND app_key = 'wallet' AND deleted = b'0'
);

SET @wallet_app_id := (
    SELECT id FROM pay_app
    WHERE tenant_id = @tenant_id AND app_key = 'wallet' AND deleted = b'0'
    ORDER BY id LIMIT 1
);
SET @mall_app_id := (
    SELECT id FROM pay_app
    WHERE tenant_id = @tenant_id AND app_key = 'mall' AND deleted = b'0'
    ORDER BY id LIMIT 1
);

-- Recharge currently exposes the local mock channel only. Real third-party channels
-- can be added to this wallet application when their credentials are configured.
INSERT INTO pay_channel
    (code, status, remark, fee_rate, app_id, config, creator, updater, deleted, tenant_id)
SELECT source.code, source.status, '会员钱包充值模拟支付', source.fee_rate,
       @wallet_app_id, source.config, '1', '1', b'0', @tenant_id
FROM pay_channel source
WHERE source.tenant_id = @tenant_id AND source.app_id = @mall_app_id
  AND source.code = 'mock' AND source.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM pay_channel target
      WHERE target.tenant_id = @tenant_id AND target.app_id = @wallet_app_id
        AND target.code = source.code AND target.deleted = b'0'
  )
LIMIT 1;

-- Repair only unpaid recharge orders which have never reached a payment attempt.
UPDATE pay_order payment
JOIN pay_wallet_recharge recharge
  ON recharge.pay_order_id = payment.id
 AND recharge.tenant_id = @tenant_id
 AND recharge.deleted = b'0'
LEFT JOIN pay_order_extension attempt
  ON attempt.order_id = payment.id
 AND attempt.tenant_id = @tenant_id
 AND attempt.deleted = b'0'
SET payment.app_id = @wallet_app_id,
    payment.notify_url = 'http://gateway:48080/admin-api/pay/wallet-recharge/update-paid',
    payment.updater = '1'
WHERE payment.tenant_id = @tenant_id
  AND payment.status = 0
  AND payment.deleted = b'0'
  AND attempt.id IS NULL;
