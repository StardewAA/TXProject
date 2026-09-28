-- Additive, rerunnable S1 foundation. No legacy conversation/message changes or agent grants.
-- Run against the selected mall database, not system/ERP/WMS databases.
CREATE TABLE IF NOT EXISTS promotion_kefu_agent (
    tenant_id BIGINT NOT NULL,
    scope VARCHAR(16) NOT NULL,
    shop_id BIGINT NOT NULL,
    actor_type INT NOT NULL,
    actor_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, scope, shop_id, actor_type, actor_id),
    CONSTRAINT ck_kefu_agent_scope CHECK ((scope = 'PLATFORM' AND shop_id = 0) OR (scope = 'SHOP' AND shop_id > 0)),
    CONSTRAINT ck_kefu_agent_actor CHECK (tenant_id > 0 AND actor_id > 0 AND actor_type IN (1, 2))
);

CREATE TABLE IF NOT EXISTS promotion_kefu_session (
    id VARCHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    scope VARCHAR(16) NOT NULL,
    shop_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    agent_type INT NULL,
    agent_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_kefu_session_owner UNIQUE (tenant_id, scope, shop_id, member_id),
    KEY idx_kefu_session_queue (tenant_id, scope, shop_id, state, update_time),
    KEY idx_kefu_session_agent (tenant_id, scope, shop_id, agent_type, agent_id, state),
    CONSTRAINT ck_kefu_session_scope CHECK ((scope = 'PLATFORM' AND shop_id = 0) OR (scope = 'SHOP' AND shop_id > 0)),
    CONSTRAINT ck_kefu_session_owner CHECK (tenant_id > 0 AND member_id > 0 AND version >= 0),
    CONSTRAINT ck_kefu_session_state CHECK (state IN ('WAITING', 'ACTIVE', 'CLOSED')),
    CONSTRAINT ck_kefu_session_agent CHECK (
        (state = 'WAITING' AND agent_type IS NULL AND agent_id IS NULL) OR
        (state IN ('ACTIVE', 'CLOSED') AND agent_type IN (1, 2) AND agent_id > 0
            AND agent_type IS NOT NULL AND agent_id IS NOT NULL))
);

-- Platform members keep their login/account tenant while a shop conversation is stored in the
-- shop's owning tenant.  This link is intentionally separate from promotion_kefu_session so old
-- tenant-local conversations keep their original isolation semantics.  A server-resolved public
-- SKU is required before a row can be inserted; clients cannot nominate a target tenant directly.
CREATE TABLE IF NOT EXISTS promotion_kefu_platform_session (
    session_id VARCHAR(36) NOT NULL,
    session_tenant_id BIGINT NOT NULL,
    member_tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    product_sku_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    KEY idx_kefu_platform_session_owner (member_tenant_id, member_id, session_id),
    CONSTRAINT ck_kefu_platform_session_ids CHECK (
        session_tenant_id > 0 AND member_tenant_id > 0 AND member_id > 0 AND product_sku_id > 0)
);

CREATE TABLE IF NOT EXISTS promotion_kefu_scope_audit (
    id VARCHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    scope VARCHAR(16) NOT NULL,
    shop_id BIGINT NOT NULL,
    session_id VARCHAR(36) NULL,
    event_type VARCHAR(24) NOT NULL,
    actor_type INT NOT NULL,
    actor_id BIGINT NOT NULL,
    target_type INT NULL,
    target_id BIGINT NULL,
    version BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kefu_scope_audit (tenant_id, scope, shop_id, create_time),
    KEY idx_kefu_session_audit (tenant_id, session_id, version)
);
