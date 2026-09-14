-- 将租户 1 的交易快照切换到防腐保温 ERP 商品目录。
-- 订单金额、状态、支付/退款记录保持原值，只替换商品与店铺快照。
-- 通过订单明细 id 轮换 30 个 ERP SKU，脚本可重复执行。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE trade_order_item i
JOIN product_spu p
  ON p.id = 910001 + MOD(i.id - 1, 30)
 AND p.tenant_id = 1 AND p.deleted = b'0'
JOIN product_sku s
  ON s.id = 920001 + MOD(i.id - 1, 30)
 AND s.tenant_id = 1 AND s.deleted = b'0'
SET i.spu_id = p.id,
    i.spu_name = p.name,
    i.sku_id = s.id,
    i.properties = s.properties,
    i.pic_url = s.pic_url,
    i.shop_id = 1,
    i.shop_name = '测试1111'
WHERE i.tenant_id = 1 AND i.deleted = b'0';

UPDATE trade_order o
JOIN (
    SELECT DISTINCT order_id
    FROM trade_order_item
    WHERE tenant_id = 1 AND deleted = b'0'
) i ON i.order_id = o.id
SET o.shop_id = 1,
    o.shop_name = '测试1111',
    o.shop_logo_url = (SELECT logo_url FROM product_shop WHERE id = 1 AND tenant_id = 1 AND deleted = b'0' LIMIT 1)
WHERE o.tenant_id = 1 AND o.deleted = b'0';

UPDATE trade_after_sale a
JOIN trade_order_item i
  ON i.id = a.order_item_id
 AND i.tenant_id = 1 AND i.deleted = b'0'
SET a.spu_id = i.spu_id,
    a.spu_name = i.spu_name,
    a.sku_id = i.sku_id,
    a.properties = i.properties,
    a.pic_url = i.pic_url,
    a.shop_id = 1
WHERE a.tenant_id = 1 AND a.deleted = b'0';

COMMIT;
