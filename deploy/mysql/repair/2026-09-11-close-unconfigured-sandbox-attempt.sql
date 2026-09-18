-- 本地联调修复：支付订单 598（商城订单 323）残留支付宝沙箱尝试，
-- 沙箱 TLS 查询失败导致切换 mock/wallet 被阻断。仅关闭此条已确认不用的沙箱记录。
-- 不适用于生产支付宝订单；真实渠道状态不明时必须核实，不能跳过防重复扣款校验。
-- 执行前备份 pay_order_extension.id=1188 和 pay_channel.id=44。
START TRANSACTION;
UPDATE pay_order_extension e
JOIN pay_order o ON o.id = e.order_id AND o.tenant_id = e.tenant_id
JOIN pay_channel c ON c.id = e.channel_id AND c.tenant_id = e.tenant_id
SET e.status = 30, e.channel_error_code = 'LOCAL_SANDBOX_DISABLED',
    e.channel_error_msg = '本地未接通支付宝，停用历史沙箱支付尝试', e.update_time = NOW()
WHERE e.id = 1188 AND e.order_id = 598 AND e.no = 'P202609110141171'
  AND e.tenant_id = 1 AND e.deleted = 0 AND e.status = 0
  AND o.app_id = 1 AND o.status = 0 AND o.deleted = 0
  AND c.id = 44 AND c.code = 'alipay_pc' AND c.app_id = 1
  AND JSON_UNQUOTE(JSON_EXTRACT(c.config, '$.serverUrl')) = 'https://openapi-sandbox.dl.alipaydev.com/gateway.do';

-- 第三方付款当前只做前端预览，停用商城应用中这条未接通的沙箱渠道，避免产生新残留。
UPDATE pay_channel SET status = 1, update_time = NOW()
WHERE id = 44 AND app_id = 1 AND tenant_id = 1 AND deleted = 0 AND code = 'alipay_pc'
  AND JSON_UNQUOTE(JSON_EXTRACT(config, '$.serverUrl')) = 'https://openapi-sandbox.dl.alipaydev.com/gateway.do';
COMMIT;
