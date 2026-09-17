-- Mall-only, additive and repeatable. No real SKU, price, stock or user data is seeded.
-- One live row per tenant/member; optimistic version prevents PC/H5 lost updates.
CREATE TABLE IF NOT EXISTS product_comparison_list (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    sku_ids VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'Up to four positive SKU ids; comma-separated, no snapshots',
    quantity INT NOT NULL DEFAULT 1 COMMENT 'Sales units, not physical measurement units',
    preference VARCHAR(16) NOT NULL DEFAULT 'PRICE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comparison_tenant_member (tenant_id, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Member comparison selection; not a quotation or inventory reservation';
