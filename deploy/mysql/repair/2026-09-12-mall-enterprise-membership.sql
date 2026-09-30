-- 商城增量：先在目标库备份后执行；不更新历史会员身份或租户。
-- member_user 字段采用 information_schema 检查，可重复执行。
SET @mall_identity_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='member_user' AND column_name='member_identity')=0,
  'ALTER TABLE member_user ADD COLUMN member_identity varchar(16) NULL COMMENT ''申请身份，不等于经营权限''',
  'SELECT 1');
PREPARE mall_identity_stmt FROM @mall_identity_ddl;
EXECUTE mall_identity_stmt;
DEALLOCATE PREPARE mall_identity_stmt;

CREATE TABLE IF NOT EXISTS product_enterprise_application (
 id bigint NOT NULL AUTO_INCREMENT, member_id bigint NOT NULL,
 identity varchar(16) NOT NULL, name varchar(100) NOT NULL, credit_code varchar(18) NOT NULL,
 license_data mediumtext NOT NULL COMMENT '私有资质，仅申请人及授权审核人员可读',
 contact_name varchar(50) NOT NULL, contact_mobile varchar(16) NOT NULL,
 address varchar(255) NOT NULL, introduction varchar(2000),
 status int NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2驳回',
 audit_remark varchar(500), auditor_id bigint, audit_time datetime, enterprise_id bigint, shop_id bigint,
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_member_tenant (tenant_id,member_id,deleted),
 KEY idx_status(tenant_id,status), KEY idx_enterprise(tenant_id,enterprise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_purchase_inquiry (
 id bigint NOT NULL AUTO_INCREMENT, member_id bigint NOT NULL, enterprise_id bigint NOT NULL,
 request_key varchar(64) NOT NULL, no varchar(40), title varchar(100) NOT NULL,
 contact_name varchar(50) NOT NULL, contact_mobile varchar(16) NOT NULL, address varchar(255) NOT NULL,
 remark varchar(2000), deadline bigint NOT NULL, expected_date bigint,
 status int NOT NULL DEFAULT 0 COMMENT '0草稿 1提交(过截止时间显示2已截止) 3关闭',
 erp_status varchar(32) NOT NULL DEFAULT 'NOT_CONNECTED', erp_no varchar(100),
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_request(tenant_id,member_id,request_key), UNIQUE KEY uk_no(no),
 KEY idx_owner(tenant_id,member_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @mall_shop_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_enterprise_application' AND column_name='shop_id')=0,
  'ALTER TABLE product_enterprise_application ADD COLUMN shop_id bigint NULL COMMENT ''后台明确授权的店铺''',
  'SELECT 1');
PREPARE mall_shop_stmt FROM @mall_shop_ddl;
EXECUTE mall_shop_stmt;
DEALLOCATE PREPARE mall_shop_stmt;

CREATE TABLE IF NOT EXISTS product_purchase_inquiry_item (
 id bigint NOT NULL AUTO_INCREMENT, inquiry_id bigint NOT NULL, sku_id bigint,
 code varchar(100), name varchar(200) NOT NULL, category varchar(100) NOT NULL,
 specification varchar(500), quantity decimal(12,3) NOT NULL, unit varchar(20) NOT NULL, remark varchar(500),
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
 PRIMARY KEY(id), KEY idx_inquiry(tenant_id,inquiry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
