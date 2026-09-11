-- 修复商城统计菜单乱码（执行后刷新菜单缓存/重新登录）
UPDATE system_menu
SET name = '商品运营统计', updater = 'admin', update_time = NOW()
WHERE component LIKE '%statistics/product/shop-operation%'
   OR path = 'shop-operation';

UPDATE system_menu
SET name = '商品统计', updater = 'admin', update_time = NOW()
WHERE (component LIKE '%statistics/product/index%' OR path = 'product')
  AND parent_id IN (SELECT id FROM (SELECT id FROM system_menu WHERE name IN ('统计中心','商品统计')) t);

UPDATE system_menu
SET name = '交易统计', updater = 'admin', update_time = NOW()
WHERE component LIKE '%statistics/trade%' OR path = 'trade';
