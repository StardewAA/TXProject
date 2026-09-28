-- 容器环境内 127.0.0.1 指向支付服务自身，支付回调应通过 Docker 网络访问网关。
UPDATE pay_app
SET order_notify_url = 'http://gateway:48080/app-api/trade/order/update-paid',
    refund_notify_url = 'http://gateway:48080/admin-api/trade/after-sale/update-refunded',
    transfer_notify_url = 'http://gateway:48080/admin-api/trade/brokerage-withdraw/update-transferred',
    update_time = NOW()
WHERE id = 1 AND tenant_id = 1;
