-- Execute once after backup in the mall database; no existing addresses are inferred or overwritten.
CREATE TABLE IF NOT EXISTS product_shop_return_address (
 shop_id BIGINT NOT NULL PRIMARY KEY,
 receiver_name VARCHAR(32) NOT NULL,
 receiver_mobile VARCHAR(32) NOT NULL,
 address VARCHAR(512) NOT NULL,
 tenant_id BIGINT NOT NULL DEFAULT 0,
 creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT(1) NOT NULL DEFAULT b'0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城店铺私有退货地址';
ALTER TABLE trade_after_sale
 ADD COLUMN return_receiver_name VARCHAR(32) NULL COMMENT '同意退货时收件人快照',
 ADD COLUMN return_receiver_mobile VARCHAR(32) NULL COMMENT '同意退货时电话快照',
 ADD COLUMN return_address VARCHAR(512) NULL COMMENT '同意退货时地址快照';
