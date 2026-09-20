-- Additive schema only. Apply explicitly after backup and isolated validation.
-- Apply to a backed-up isolated copy first; verify the resulting columns and unique index.
-- No tenant/company seeds, existing-user backfill, membership/role/shop grants or data movement.
CREATE TABLE IF NOT EXISTS member_mall_customer_affiliation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Customer relationship record',
    tenant_id BIGINT NOT NULL COMMENT 'Account tenant, not enterprise tenant override',
    member_id BIGINT NOT NULL COMMENT 'Member in this tenant',
    service_enterprise_id BIGINT NOT NULL COMMENT 'Customer service owner; NOT employer or authorization',
    source VARCHAR(32) NOT NULL COMMENT 'PUBLIC_REGISTRATION',
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_member (tenant_id, member_id),
    KEY idx_tenant_service_enterprise (tenant_id, service_enterprise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Mall customer affiliation, never enterprise staff permissions';
-- Unique key deliberately retains deleted records: no silent rebinding or historical overwrite.
-- Rollback: disable new auth first; preserve this table and all relationship history. Do not DROP.
