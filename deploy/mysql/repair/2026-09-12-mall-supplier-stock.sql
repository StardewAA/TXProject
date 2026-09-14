-- 仅新增商城本地库存调整流水，不修改原商品库存、不接入WMS。
CREATE TABLE IF NOT EXISTS product_supplier_stock_adjustment (
 id bigint NOT NULL AUTO_INCREMENT,
 shop_id bigint NOT NULL, spu_id bigint NOT NULL, sku_id bigint NOT NULL, member_id bigint NOT NULL,
 request_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expected_stock int NOT NULL, delta int NOT NULL, before_stock int NOT NULL, after_stock int NOT NULL,
 reason varchar(200) NOT NULL,
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_request (tenant_id,sku_id,member_id,request_key),
 KEY idx_shop_spu (tenant_id,shop_id,spu_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商本地库存调整记录';
