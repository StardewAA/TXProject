-- ERP 商品替换后的订单/售后快照修复（租户 1）
--
-- 订单金额字段的数据库单位始终是“分”。测试订单
-- o202609060832371 的实付金额为 122 分，售后单 r202609060832591
-- 的退款金额为 122 分，均对应 1.22 元；本脚本不会修改这些金额。
--
-- 订单商品和售后记录保存的是下单时的快照。该测试订单原来引用了
-- 租户 111 的商品/店铺（SPU 659、店铺 3），在租户 1 导入 ERP
-- 商品后会导致后台看不到正确的店铺。这里把快照指向租户 1 的
-- ERP 商品 CP-7004（自攻螺钉，SPU 910026/SKU 920026）和店铺 1，
-- 同时保留原有价格、优惠、数量和退款金额。
SET NAMES utf8mb4;
START TRANSACTION;

-- 单一店铺订单应带上店铺快照，便于订单列表和履约分组显示。
UPDATE trade_order o
JOIN product_shop s ON s.id = 1 AND s.tenant_id = 1 AND s.deleted = b'0'
SET o.shop_id = s.id,
    o.shop_name = s.name,
    o.shop_logo_url = s.logo_url,
    o.updater = 'erp-import',
    o.update_time = NOW()
WHERE o.id = 304
  AND o.no = 'o202609060832371'
  AND o.tenant_id = 1
  AND o.deleted = b'0';

-- 订单项快照切换到租户 1 的 ERP 商品；金额字段保持原值（分）。
UPDATE trade_order_item oi
JOIN product_spu spu
  ON spu.id = 910026
 AND spu.tenant_id = 1
 AND spu.deleted = b'0'
JOIN product_sku sku
  ON sku.id = 920026
 AND sku.spu_id = spu.id
 AND sku.tenant_id = 1
 AND sku.deleted = b'0'
JOIN product_shop s
  ON s.id = spu.shop_id
 AND s.tenant_id = 1
 AND s.deleted = b'0'
SET oi.spu_id = spu.id,
    oi.spu_name = spu.name,
    oi.sku_id = sku.id,
    oi.pic_url = sku.pic_url,
    oi.properties = sku.properties,
    oi.shop_id = s.id,
    oi.shop_name = s.name,
    oi.updater = 'erp-import',
    oi.update_time = NOW()
WHERE oi.id = 299
  AND oi.order_id = 304
  AND oi.tenant_id = 1
  AND oi.deleted = b'0';

-- 售后商品快照与订单项保持一致；退款金额 122 分（1.22 元）不变。
UPDATE trade_after_sale a
JOIN product_spu spu
  ON spu.id = 910026
 AND spu.tenant_id = 1
 AND spu.deleted = b'0'
JOIN product_sku sku
  ON sku.id = 920026
 AND sku.spu_id = spu.id
 AND sku.tenant_id = 1
 AND sku.deleted = b'0'
JOIN product_shop s
  ON s.id = spu.shop_id
 AND s.tenant_id = 1
 AND s.deleted = b'0'
SET a.spu_id = spu.id,
    a.spu_name = spu.name,
    a.sku_id = sku.id,
    a.pic_url = sku.pic_url,
    a.properties = sku.properties,
    a.shop_id = s.id,
    a.updater = 'erp-import',
    a.update_time = NOW()
WHERE a.id = 33
  AND a.order_id = 304
  AND a.order_item_Id = 299
  AND a.no = 'r202609060832591'
  AND a.tenant_id = 1
  AND a.deleted = b'0'
  AND a.refund_price = 122;

COMMIT;
