-- ----------------------------------------------------------------------------
-- 为全部租户补齐"示例应用"(app_key=demo) + mock 模拟支付渠道
-- 背景：接入示例（示例订单/提现转账案例）代码写死 app_key='demo'，
--      非租户1 的账号发起示例订单会报"App 不存在"
-- 幂等：已存在的租户跳过，可重复执行
-- ----------------------------------------------------------------------------

-- 1. 每个启用租户插入示例应用
INSERT INTO `pay_app`
  (`app_key`, `name`, `status`, `remark`,
   `order_notify_url`, `refund_notify_url`, `transfer_notify_url`,
   `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 'demo', '示例应用', 0, '支付接入示例（系统自动补齐）',
       'http://127.0.0.1:48080/admin-api/pay/demo-order/update-paid',
       'http://127.0.0.1:48080/admin-api/pay/demo-order/update-refunded',
       'http://127.0.0.1:48080/admin-api/pay/demo-withdraw/update-transferred',
       '1', '1', b'0', t.`id`
FROM `system_tenant` t
WHERE t.`deleted` = 0 AND t.`status` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `pay_app` p
    WHERE p.`app_key` = 'demo' AND p.`tenant_id` = t.`id` AND p.`deleted` = 0
  );

-- 2. 给每个示例应用配 mock 模拟支付渠道
-- 注意：config 必须携带 @class 多态属性（PayClientConfig 使用 @JsonTypeInfo），否则支付时报"系统异常"
INSERT INTO `pay_channel`
  (`app_id`, `code`, `status`, `remark`, `config`, `fee_rate`,
   `creator`, `updater`, `deleted`, `tenant_id`)
SELECT a.`id`, 'mock', 0, '模拟支付',
       '{"@class":"cn.iocoder.txgy.module.pay.framework.pay.core.client.impl.NonePayClientConfig","name":"none-config"}',
       0, '1', '1', b'0', a.`tenant_id`
FROM `pay_app` a
WHERE a.`app_key` = 'demo' AND a.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `pay_channel` c
    WHERE c.`app_id` = a.`id` AND c.`code` = 'mock' AND c.`deleted` = 0
  );

-- 3. 验证：各租户 demo 应用数量
SELECT t.`id` AS tenant_id, COUNT(p.`id`) AS demo_apps
FROM `system_tenant` t
LEFT JOIN `pay_app` p ON p.`tenant_id` = t.`id` AND p.`app_key` = 'demo' AND p.`deleted` = 0
WHERE t.`deleted` = 0
GROUP BY t.`id`
ORDER BY t.`id`;
