-- 专家咨询支付应用。应用按租户创建，支付单与发起咨询的账号保持同一租户。
-- mock 渠道仅用于本地/测试环境验证完整支付回调；生产环境应在支付管理中替换为正式渠道。

INSERT INTO pay_app
  (app_key, name, status, remark, order_notify_url, refund_notify_url, transfer_notify_url,
   creator, updater, deleted, tenant_id)
SELECT 'cms', 'CMS 专家咨询', 0, '专家咨询订单支付',
       'http://127.0.0.1:48080/admin-api/cms/expert-consult/order/update-paid',
       'http://127.0.0.1:48080/admin-api/cms/expert-consult/order/update-refunded',
       NULL, 'system', 'system', b'0', tenants.tenant_id
FROM (
  SELECT 0 AS tenant_id
  UNION
  SELECT id FROM system_tenant WHERE deleted = b'0'
) tenants
WHERE NOT EXISTS (
  SELECT 1 FROM pay_app app
  WHERE app.tenant_id = tenants.tenant_id AND app.app_key = 'cms' AND app.deleted = b'0'
);

INSERT INTO pay_channel
  (code, status, remark, fee_rate, app_id, config, creator, updater, deleted, tenant_id)
SELECT 'mock', 0, '专家咨询测试支付渠道', 0, app.id,
       '{"@class":"cn.iocoder.txgy.framework.pay.core.client.impl.NonePayClientConfig","name":"cms-consult-mock"}',
       'system', 'system', b'0', app.tenant_id
FROM pay_app app
WHERE app.app_key = 'cms' AND app.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM pay_channel channel
    WHERE channel.tenant_id = app.tenant_id AND channel.app_id = app.id
      AND channel.code = 'mock' AND channel.deleted = b'0'
  );
