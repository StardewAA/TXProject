-- 企业化商城：订单店铺归属与按店铺履约基础字段
ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS shop_id bigint DEFAULT NULL COMMENT '单店铺订单归属；多店铺订单为空' AFTER id;
ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS shop_name varchar(64) DEFAULT NULL COMMENT '店铺名称快照' AFTER shop_id;
ALTER TABLE trade_order_item ADD COLUMN IF NOT EXISTS shop_id bigint DEFAULT NULL COMMENT '商品项履约店铺编号' AFTER spu_id;
ALTER TABLE trade_order_item ADD COLUMN IF NOT EXISTS shop_name varchar(64) DEFAULT NULL COMMENT '店铺名称快照' AFTER shop_id;
CREATE INDEX IF NOT EXISTS idx_trade_order_shop_id ON trade_order(shop_id);
CREATE INDEX IF NOT EXISTS idx_trade_order_item_shop_id ON trade_order_item(shop_id);
