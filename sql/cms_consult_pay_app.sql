-- M3：CMS 专家咨询支付闭环 —— 支付应用与本地模拟渠道
-- 生产环境请把 notify 地址改为网关域名，并按真实渠道替换 mock 渠道。
INSERT INTO pay_app (app_key, name, status, remark, order_notify_url, refund_notify_url, creator, updater, tenant_id)
SELECT 'cms', 'CMS专家咨询', 0, '专家咨询订单支付（M3）',
       'http://local-cms-1:48080/admin-api/cms/expert-consult/order/update-paid',
       'http://local-cms-1:48080/admin-api/cms/expert-consult/order/update-refunded',
       '1', '1', 1
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM pay_app WHERE app_key = 'cms' AND deleted = b'0') t);

INSERT INTO pay_channel (code, status, remark, fee_rate, app_id, config, creator, updater, tenant_id)
SELECT 'mock', 0, '模拟支付（本地联调）', 0, a.id,
       '{"@class":"cn.iocoder.txgy.framework.pay.core.client.impl.NonePayClientConfig","name":"mock-conf"}',
       '1', '1', 1
FROM (SELECT id FROM pay_app WHERE app_key = 'cms' AND deleted = b'0' LIMIT 1) a
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM pay_channel WHERE app_id = a.id AND code = 'mock' AND deleted = b'0') t);