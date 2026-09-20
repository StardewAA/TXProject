-- 商城专用 ERP 请求及模拟账本；不修改任何 WMS/ERP 表，不初始化业务库存。
CREATE TABLE IF NOT EXISTS trade_erp_inventory_request (
 id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
 order_no VARCHAR(64) NOT NULL, operation VARCHAR(16) NOT NULL, business_key VARCHAR(64) NOT NULL,
 mode VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL, payload TEXT NOT NULL,
 logistics_id BIGINT NULL, logistics_no VARCHAR(64) NULL, attempts INT NOT NULL DEFAULT 1,
 erp_no VARCHAR(128) NULL, message VARCHAR(500) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), UNIQUE KEY uk_erp_business(tenant_id,order_id,operation,business_key)
);
CREATE TABLE IF NOT EXISTS trade_erp_mock_stock (
 tenant_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 initial_quantity INT NOT NULL, quantity INT NOT NULL, reserved INT NOT NULL DEFAULT 0,
 PRIMARY KEY(tenant_id,sku_id)
);
CREATE TABLE IF NOT EXISTS trade_erp_mock_order_stock (
 tenant_id BIGINT NOT NULL, order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 reserved INT NOT NULL DEFAULT 0, released INT NOT NULL DEFAULT 0,
 outbound INT NOT NULL DEFAULT 0, returned INT NOT NULL DEFAULT 0,
 PRIMARY KEY(tenant_id,order_id,sku_id)
);
