-- 企业化商城：企业入驻与资质审核基础表
CREATE TABLE IF NOT EXISTS product_enterprise (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '企业编号',
  tenant_id bigint NOT NULL DEFAULT 0,
  name varchar(128) NOT NULL COMMENT '企业名称',
  credit_code varchar(32) NOT NULL COMMENT '统一社会信用代码',
  type tinyint DEFAULT NULL COMMENT '企业类型',
  license_url varchar(512) DEFAULT NULL COMMENT '营业执照地址',
  contact_name varchar(32) DEFAULT NULL,
  contact_mobile varchar(32) DEFAULT NULL,
  address varchar(255) DEFAULT NULL,
  introduction varchar(1024) DEFAULT NULL,
  audit_status tinyint NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回 3已停用',
  audit_remark varchar(512) DEFAULT NULL,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_credit_code_tenant (credit_code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城企业入驻档案';
