-- WMS shortage notification inbox only. Review and apply to ERP/MES databases manually.
-- No stock, procurement, workflow, menu, or existing-table changes.

CREATE TABLE IF NOT EXISTS erp_wms_shortage_inbox (
 tenant_id BIGINT NOT NULL,
 event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_version BIGINT NOT NULL,
 identity_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 shortage_status VARCHAR(16) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY (tenant_id,event_id),
 KEY idx_erp_shortage_updated (tenant_id,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS erp_wms_shortage_version (
 tenant_id BIGINT NOT NULL,
 event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_version BIGINT NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 PRIMARY KEY (tenant_id,event_id,event_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS erp_wms_shortage_read (
 tenant_id BIGINT NOT NULL,
 event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id BIGINT NOT NULL,
 event_version BIGINT NOT NULL,
 PRIMARY KEY (tenant_id,event_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mes_wms_shortage_inbox (
 tenant_id BIGINT NOT NULL,
 event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_version BIGINT NOT NULL,
 identity_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 shortage_status VARCHAR(16) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY (tenant_id,event_id),
 KEY idx_mes_shortage_updated (tenant_id,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mes_wms_shortage_version (
 tenant_id BIGINT NOT NULL,
 event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_version BIGINT NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 PRIMARY KEY (tenant_id,event_id,event_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mes_wms_shortage_read (
 tenant_id BIGINT NOT NULL,
 event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id BIGINT NOT NULL,
 event_version BIGINT NOT NULL,
 PRIMARY KEY (tenant_id,event_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

