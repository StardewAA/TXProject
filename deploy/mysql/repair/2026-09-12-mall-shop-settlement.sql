-- Additive migration: no existing order, payment or shop data is rewritten.
-- Amounts are integer cents, rate is basis points (0..10000).
CREATE TABLE IF NOT EXISTS trade_shop_settlement_lock (
 tenant_id BIGINT NOT NULL, shop_id BIGINT NOT NULL, credit_amount BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (tenant_id,shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS trade_shop_settlement_bill (
 id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, shop_id BIGINT NOT NULL,
 shop_name VARCHAR(100) NOT NULL, bill_month CHAR(7) NOT NULL, no VARCHAR(50) NOT NULL,
 version INT NOT NULL DEFAULT 1, status INT NOT NULL DEFAULT 10, commission_rate INT NOT NULL,
 order_count INT NOT NULL DEFAULT 0, order_pay_amount BIGINT NOT NULL DEFAULT 0,
 refund_amount BIGINT NOT NULL DEFAULT 0, commission_amount BIGINT NOT NULL DEFAULT 0,
 adjustment_amount BIGINT NOT NULL DEFAULT 0, carry_amount BIGINT NOT NULL DEFAULT 0,
 settlement_amount BIGINT NOT NULL DEFAULT 0, audit_remark VARCHAR(500),
 payment_reference VARCHAR(100), payment_time DATETIME, payment_proof MEDIUMTEXT,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), UNIQUE KEY uk_shop_month(tenant_id,shop_id,bill_month),
 UNIQUE KEY uk_payment_reference(tenant_id,payment_reference), KEY idx_shop_status(tenant_id,shop_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS trade_shop_settlement_item (
 id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, bill_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL, order_no VARCHAR(64) NOT NULL, kind INT NOT NULL,
 original_order_id BIGINT NULL, original_bill_id BIGINT NULL,
 pay_amount BIGINT NOT NULL, delivery_amount BIGINT NOT NULL, refund_before BIGINT NOT NULL,
 refund_total BIGINT NOT NULL, commission_rate INT NOT NULL,
 commission_amount BIGINT NOT NULL, amount BIGINT NOT NULL, finish_time DATETIME,
 PRIMARY KEY(id), UNIQUE KEY uk_bill_order(tenant_id,bill_id,order_id),
 UNIQUE KEY uk_original_order(tenant_id,original_order_id), KEY idx_order(tenant_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS trade_shop_settlement_log (
 id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, bill_id BIGINT NOT NULL,
 version INT NOT NULL, action VARCHAR(32) NOT NULL, actor_id BIGINT NOT NULL, actor_type INT NOT NULL,
 remark VARCHAR(500) NOT NULL, snapshot_json MEDIUMTEXT NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), KEY idx_bill(tenant_id,bill_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
