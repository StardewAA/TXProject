-- 修复科盾商城演示商品的评论规格 JSON 与快递配送配置。
START TRANSACTION;

UPDATE product_comment comment
JOIN product_sku sku
  ON sku.id=comment.sku_id AND sku.tenant_id=comment.tenant_id AND sku.deleted=b'0'
SET comment.sku_properties=sku.properties,
    comment.updater='mall-kedun-fix-20260917',comment.update_time=NOW()
WHERE comment.tenant_id=2009 AND comment.deleted=b'0'
  AND (comment.sku_properties IS NULL OR JSON_VALID(comment.sku_properties)=0);

UPDATE product_spu
SET delivery_types='1',delivery_template_id=940901,
    updater='mall-kedun-fix-20260917',update_time=NOW()
WHERE tenant_id=2009 AND deleted=b'0' AND id BETWEEN 940201 AND 940235;

-- 全国模板按省级行政区覆盖；结算服务会把区县地址逐级匹配到城市和省份。
UPDATE trade_delivery_express_template_charge
SET area_ids='110000,120000,130000,140000,150000,210000,220000,230000,310000,320000,330000,340000,350000,360000,370000,410000,420000,430000,440000,450000,460000,500000,510000,520000,530000,540000,610000,620000,630000,640000,650000,710000,810000,820000',
    updater='mall-kedun-fix-20260917',update_time=NOW()
WHERE id=940902 AND template_id=940901 AND tenant_id=2009 AND deleted=b'0';

-- 本地模拟 ERP 使用独立账本；首次导入商品时以商品 SKU 库存初始化，已有账本不覆盖。
INSERT IGNORE INTO trade_erp_mock_stock (tenant_id,sku_id,initial_quantity,quantity,reserved)
SELECT sku.tenant_id,sku.id,sku.stock,sku.stock,0
FROM product_sku sku
WHERE sku.tenant_id=2009 AND sku.deleted=b'0';

-- 交易服务固定使用 appKey=mall；同步本租户支付应用，并开放本地模拟/余额渠道。
UPDATE pay_app
SET app_key='mall',name='科盾商城演示支付',status=0,remark='用于本地商城订单与模拟支付',
    updater='mall-kedun-fix-20260917',update_time=NOW()
WHERE id=940001 AND tenant_id=2009 AND deleted=b'0';

INSERT INTO pay_channel
 (id,code,status,remark,fee_rate,app_id,config,creator,updater,deleted,tenant_id)
VALUES
 (940011,'mock',0,'本地模拟支付',0,940001,'{"@class":"cn.iocoder.txgy.framework.pay.core.client.impl.NonePayClientConfig","name":"mock-conf"}','mall-kedun-fix-20260917','mall-kedun-fix-20260917',b'0',2009),
 (940012,'wallet',0,'会员余额支付',0,940001,'{"@class":"cn.iocoder.txgy.framework.pay.core.client.impl.NonePayClientConfig","name":"mock-conf"}','mall-kedun-fix-20260917','mall-kedun-fix-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE code=VALUES(code),status=VALUES(status),remark=VALUES(remark),fee_rate=VALUES(fee_rate),
 app_id=VALUES(app_id),config=VALUES(config),updater=VALUES(updater),update_time=NOW(),deleted=b'0';

COMMIT;
