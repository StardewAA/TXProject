-- 增量、可重复执行；只新增商城待办表，不修改历史订单、售后、库存或支付。
CREATE TABLE IF NOT EXISTS trade_order_workbench_clock (
 tenant_id BIGINT NOT NULL PRIMARY KEY,
 sequence_no BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS trade_order_workbench_change (
 tenant_id BIGINT NOT NULL,
 sequence_no BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 invalidate_actions VARCHAR(160) NOT NULL DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (tenant_id,sequence_no),
 KEY idx_workbench_change_order (tenant_id,order_id,sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS trade_order_workbench_scope (
 tenant_id BIGINT NOT NULL,
 scope_kind VARCHAR(16) NOT NULL,
 scope_id BIGINT NOT NULL,
 sequence_no BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (tenant_id,scope_kind,scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS trade_order_workbench_event (
 tenant_id BIGINT NOT NULL,
 sequence_no BIGINT NOT NULL,
 event_key VARCHAR(180) NOT NULL,
 role_name VARCHAR(16) NOT NULL,
 member_id BIGINT NOT NULL DEFAULT 0,
 shop_id BIGINT NOT NULL DEFAULT 0,
 order_id BIGINT NOT NULL,
 entity_type VARCHAR(16) NOT NULL,
 entity_id BIGINT NOT NULL,
 after_sale_id BIGINT NULL,
 request_id VARCHAR(64) NULL,
 business_no VARCHAR(64) NOT NULL,
 action_name VARCHAR(32) NOT NULL,
 reason VARCHAR(200) NOT NULL,
 active BIT NOT NULL DEFAULT 1,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (tenant_id,sequence_no),
 KEY idx_workbench_event_scope (tenant_id,role_name,shop_id,member_id,active,sequence_no),
 KEY idx_workbench_event_order (tenant_id,order_id,active),
 KEY idx_workbench_event_key (tenant_id,event_key,active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS trade_order_workbench_read (
 tenant_id BIGINT NOT NULL,
 user_type INT NOT NULL,
 user_id BIGINT NOT NULL,
 role_name VARCHAR(16) NOT NULL,
 shop_id BIGINT NOT NULL DEFAULT 0,
 event_id BIGINT NOT NULL,
 read_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (tenant_id,user_type,user_id,role_name,shop_id,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
