-- 清理科盾租户本批数据前已有的 3 个测试商品（642、657、658）。
-- 不影响本批 940201-940235 商品及任何历史订单。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE product_comment
SET deleted=b'1', updater='mall-kedun-cleanup-20260917', update_time=NOW()
WHERE tenant_id=2009 AND spu_id IN (642,657,658) AND deleted=b'0';

UPDATE product_sku
SET deleted=b'1', updater='mall-kedun-cleanup-20260917', update_time=NOW()
WHERE tenant_id=2009 AND spu_id IN (642,657,658) AND deleted=b'0';

UPDATE product_spu
SET deleted=b'1', status=0, updater='mall-kedun-cleanup-20260917', update_time=NOW()
WHERE tenant_id=2009 AND id IN (642,657,658) AND deleted=b'0';

COMMIT;

SELECT id,name,deleted FROM product_spu WHERE tenant_id=2009 AND id IN (642,657,658) ORDER BY id;
