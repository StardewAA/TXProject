-- Scoped text/FAQ service only. Does not alter legacy numeric conversations, grant agents, or seed messages.
CREATE TABLE IF NOT EXISTS promotion_kefu_chat_meta (
    tenant_id BIGINT NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    human_requested BOOLEAN NOT NULL DEFAULT FALSE,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, session_id)
);
CREATE TABLE IF NOT EXISTS promotion_kefu_chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    sender_type INT NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_role VARCHAR(16) NOT NULL,
    client_message_id VARCHAR(80) NOT NULL,
    text_body VARCHAR(2000) NOT NULL,
    card_json MEDIUMTEXT NULL,
    faq_id VARCHAR(40) NULL,
    faq_version BIGINT NULL,
    faq_scope VARCHAR(16) NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kefu_chat_message_retry (tenant_id,session_id,sender_type,sender_id,client_message_id),
    KEY idx_kefu_chat_message_cursor (tenant_id,session_id,id),
    CONSTRAINT ck_kefu_chat_role CHECK (sender_role IN ('MEMBER','AGENT','FAQ','SYSTEM'))
);
CREATE TABLE IF NOT EXISTS promotion_kefu_chat_read (
    tenant_id BIGINT NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    actor_type INT NOT NULL,
    actor_id BIGINT NOT NULL,
    viewer_role VARCHAR(16) NOT NULL,
    message_id BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id,session_id,actor_type,actor_id,viewer_role)
);
CREATE TABLE IF NOT EXISTS promotion_kefu_chat_presence (
    tenant_id BIGINT NOT NULL,
    scope VARCHAR(16) NOT NULL,
    shop_id BIGINT NOT NULL,
    actor_type INT NOT NULL,
    actor_id BIGINT NOT NULL,
    expires_at BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id,scope,shop_id,actor_type,actor_id),
    KEY idx_kefu_chat_presence_scope (tenant_id,scope,shop_id,expires_at)
);
CREATE TABLE IF NOT EXISTS promotion_kefu_faq_config (
    tenant_id BIGINT NOT NULL,
    scope VARCHAR(16) NOT NULL,
    shop_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    draft_json MEDIUMTEXT NOT NULL,
    published_version BIGINT NOT NULL DEFAULT 0,
    published_json MEDIUMTEXT NOT NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id,scope,shop_id),
    CONSTRAINT ck_kefu_faq_scope CHECK ((scope='PLATFORM' AND shop_id=0) OR (scope='SHOP' AND shop_id>0))
);
