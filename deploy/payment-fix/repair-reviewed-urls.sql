-- Only audited paid mall orders. Does not change retry status or replay tasks.
CREATE TABLE IF NOT EXISTS pay_notify_url_backup_20260911 (
  id BIGINT PRIMARY KEY, notify_url VARCHAR(1024), backed_up_at DATETIME
);
START TRANSACTION;
INSERT IGNORE INTO pay_notify_url_backup_20260911 (id, notify_url, backed_up_at)
SELECT n.id,n.notify_url,NOW() FROM pay_notify_task n
JOIN pay_order p ON p.id=n.data_id AND p.tenant_id=n.tenant_id
JOIN trade_order o ON o.id=p.merchant_order_id AND o.pay_order_id=p.id AND o.tenant_id=n.tenant_id
WHERE n.id IN (558,559,563,564,569,570,574,575,578)
AND n.app_id=1 AND n.type=1 AND n.status IN (0,20,21)
AND n.notify_url='http://127.0.0.1:48080/app-api/trade/order/update-paid'
AND p.status=10 AND o.pay_status=1 AND n.deleted=0 AND p.deleted=0 AND o.deleted=0;
UPDATE pay_notify_task n
JOIN pay_notify_url_backup_20260911 b ON b.id=n.id
JOIN pay_order p ON p.id=n.data_id AND p.tenant_id=n.tenant_id
JOIN trade_order o ON o.id=p.merchant_order_id AND o.pay_order_id=p.id AND o.tenant_id=n.tenant_id
SET n.notify_url='http://gateway:48080/app-api/trade/order/update-paid'
WHERE n.id IN (558,559,563,564,569,570,574,575,578)
AND n.app_id=1 AND n.type=1 AND n.status IN (0,20,21)
AND n.notify_url=b.notify_url AND p.status=10 AND o.pay_status=1
AND n.deleted=0 AND p.deleted=0 AND o.deleted=0;
SELECT ROW_COUNT() AS repaired_urls;
COMMIT;
