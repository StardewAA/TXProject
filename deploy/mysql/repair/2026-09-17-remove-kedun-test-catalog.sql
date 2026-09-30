-- 清理科盾租户（2009）中名称明确标识为“测试”的旧商城配置。
-- 采用软删除保留审计痕迹；不删除正式商品、订单、支付或结算数据。
SET NAMES utf8mb4;
START TRANSACTION;

-- 测试店铺、品牌、分类和属性。引用它们的旧测试商品已经软删除。
UPDATE product_shop
SET status=1,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=2 AND name='测试店铺' AND deleted=b'0';

UPDATE product_brand
SET status=1,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id IN (4,5) AND name LIKE '%测试%' AND deleted=b'0';

UPDATE product_category
SET status=1,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id IN (85,86,87,88) AND name LIKE '%测试%' AND deleted=b'0';

UPDATE product_property_value
SET deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND property_id=19 AND deleted=b'0';

UPDATE product_property
SET status=1,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=19 AND name='测试属性' AND deleted=b'0';

-- 测试优惠券模板及其领取记录。
UPDATE promotion_coupon
SET status=4,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND template_id=25 AND deleted=b'0';

UPDATE promotion_coupon_template
SET status=-1,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=25 AND name LIKE '%测试%' AND deleted=b'0';

-- 测试限时折扣、拼团、砍价和积分活动；同步清理活动商品关联。
UPDATE promotion_discount_product
SET deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND activity_id=14 AND deleted=b'0';
UPDATE promotion_discount_activity
SET status=-1,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=14 AND name LIKE '%测试%' AND deleted=b'0';

UPDATE promotion_combination_product
SET deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND activity_id=9 AND deleted=b'0';
UPDATE promotion_combination_activity
SET status=-1,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=9 AND name LIKE '%测试%' AND deleted=b'0';

UPDATE promotion_bargain_help
SET deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND activity_id=26 AND deleted=b'0';
UPDATE promotion_bargain_record
SET deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND activity_id=26 AND deleted=b'0';
UPDATE promotion_bargain_activity
SET status=-1,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=26 AND name LIKE '%测试%' AND deleted=b'0';

UPDATE promotion_point_product
SET deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND activity_id=3 AND deleted=b'0';
-- 库存返还表属于不可变业务流水，不做删除；该测试活动当前无返还记录。
UPDATE promotion_point_activity
SET status=-1,deleted=b'1',updater='mall-test-cleanup-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=3 AND spu_id=657 AND deleted=b'0';

COMMIT;

SELECT 'active-test-shop' item,COUNT(*) count FROM product_shop
 WHERE tenant_id=2009 AND deleted=b'0' AND name LIKE '%测试%'
UNION ALL SELECT 'active-test-brand',COUNT(*) FROM product_brand
 WHERE tenant_id=2009 AND deleted=b'0' AND name LIKE '%测试%'
UNION ALL SELECT 'active-test-category',COUNT(*) FROM product_category
 WHERE tenant_id=2009 AND deleted=b'0' AND name LIKE '%测试%'
UNION ALL SELECT 'active-test-property',COUNT(*) FROM product_property
 WHERE tenant_id=2009 AND deleted=b'0' AND name LIKE '%测试%'
UNION ALL SELECT 'active-test-coupon',COUNT(*) FROM promotion_coupon_template
 WHERE tenant_id=2009 AND deleted=b'0' AND name LIKE '%测试%'
UNION ALL SELECT 'active-test-activity',
 (SELECT COUNT(*) FROM promotion_discount_activity WHERE tenant_id=2009 AND deleted=b'0' AND name LIKE '%测试%')
 +(SELECT COUNT(*) FROM promotion_combination_activity WHERE tenant_id=2009 AND deleted=b'0' AND name LIKE '%测试%')
 +(SELECT COUNT(*) FROM promotion_bargain_activity WHERE tenant_id=2009 AND deleted=b'0' AND name LIKE '%测试%');
