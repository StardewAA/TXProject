-- 第 21 节：企业采购留在 member 端，供应经营迁入 system_user 后台。
-- 本脚本不创建默认密码、不把 member token 转换为后台权限，也不自动启用旧的已删除后台账号。
-- 执行顺序：先执行 2026-09-21-mall-inquiry-market.sql，再执行本脚本。
SET NAMES utf8mb4;

-- 菜单依赖与固定编号先 fail-fast，不能覆盖其他模块已经占用的菜单。
SET @missing_menu_parent_count=(
  SELECT 2-COUNT(*) FROM system_menu WHERE id IN (2362,25005) AND deleted=b'0');
SET @menu_parent_guard=IF(@missing_menu_parent_count>0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''第21节迁移中止：商城系统或企业店铺父菜单不存在''',
  'SELECT 1');
PREPARE menu_parent_guard_stmt FROM @menu_parent_guard;
EXECUTE menu_parent_guard_stmt;
DEALLOCATE PREPARE menu_parent_guard_stmt;

SET @occupied_menu_id_count=(
  SELECT COUNT(*) FROM system_menu
  WHERE id BETWEEN 25200 AND 25221 AND COALESCE(creator,'')<>'mall-section21');
SET @menu_id_guard=IF(@occupied_menu_id_count>0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''第21节迁移中止：25200-25221 菜单编号已被其他功能占用''',
  'SELECT 1');
PREPARE menu_id_guard_stmt FROM @menu_id_guard;
EXECUTE menu_id_guard_stmt;
DEALLOCATE PREPARE menu_id_guard_stmt;

-- ---------------------------------------------------------------------------
-- 1. 企业后台成员、店铺授权、定向询价与操作审计
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_enterprise_admin_member (
  id bigint NOT NULL AUTO_INCREMENT,
  admin_user_id bigint NOT NULL COMMENT 'system_users.id',
  enterprise_id bigint NOT NULL COMMENT '商城企业编号',
  scope_type tinyint NOT NULL DEFAULT 2 COMMENT '1本企业全部店铺 2指定店铺',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_enterprise (tenant_id, admin_user_id, enterprise_id, deleted),
  KEY idx_enterprise_status (tenant_id, enterprise_id, status),
  KEY idx_admin_status (tenant_id, admin_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城企业后台成员';

CREATE TABLE IF NOT EXISTS product_shop_admin_authorization (
  id bigint NOT NULL AUTO_INCREMENT,
  admin_user_id bigint NOT NULL COMMENT 'system_users.id',
  enterprise_id bigint NOT NULL,
  shop_id bigint NOT NULL,
  status tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_shop (tenant_id, admin_user_id, shop_id, deleted),
  KEY idx_shop_status (tenant_id, shop_id, status),
  KEY idx_admin_status (tenant_id, admin_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城后台店铺经营授权';

CREATE TABLE IF NOT EXISTS product_purchase_inquiry_invited_shop (
  id bigint NOT NULL AUTO_INCREMENT,
  inquiry_id bigint NOT NULL,
  shop_id bigint NOT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inquiry_shop (tenant_id, inquiry_id, shop_id, deleted),
  KEY idx_shop_inquiry (tenant_id, shop_id, inquiry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定向询价获邀店铺';

CREATE TABLE IF NOT EXISTS product_business_operation_audit (
  id bigint NOT NULL AUTO_INCREMENT,
  enterprise_id bigint NULL,
  shop_id bigint NULL,
  actor_type varchar(16) NOT NULL COMMENT 'MEMBER/ADMIN/SYSTEM',
  actor_id bigint NULL,
  action varchar(64) NOT NULL,
  resource_type varchar(64) NOT NULL,
  resource_id bigint NULL,
  result varchar(16) NOT NULL DEFAULT 'SUCCESS',
  detail varchar(1000) NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  KEY idx_enterprise_time (tenant_id, enterprise_id, create_time),
  KEY idx_shop_time (tenant_id, shop_id, create_time),
  KEY idx_actor_time (tenant_id, actor_type, actor_id, create_time),
  KEY idx_resource (tenant_id, resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城企业经营操作审计';

CREATE TABLE IF NOT EXISTS product_business_account_migration (
  id bigint NOT NULL AUTO_INCREMENT,
  member_id bigint NOT NULL,
  enterprise_id bigint NOT NULL,
  shop_id bigint NULL,
  admin_user_id bigint NULL,
  original_login_name varchar(64) NULL,
  target_role_code varchar(100) NOT NULL DEFAULT 'mall_enterprise_admin',
  keep_purchase_login bit(1) NULL DEFAULT NULL COMMENT '废弃兼容列：不得用于自动推断采购权限',
  purchase_login_disposition varchar(32) NOT NULL DEFAULT 'UNVERIFIED'
    COMMENT '人工核验：UNVERIFIED/KEEP/REVOKE_AFTER_HANDOFF',
  account_preparation_status varchar(32) NOT NULL DEFAULT 'NOT_FOUND'
    COMMENT 'NOT_FOUND/SECURE_RESET_REQUIRED/ROLE_OR_SCOPE_REVIEW/ELIGIBLE_FOR_VERIFICATION',
  migration_status varchar(32) NOT NULL
    COMMENT 'READY/NEEDS_VERIFICATION/NEEDS_ROLE_PRECONFIG/NEEDS_SECURE_RESET/NEEDS_ACCOUNT',
  verified_by bigint NULL,
  verified_at datetime NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_enterprise (tenant_id, member_id, enterprise_id, deleted),
  KEY idx_status (tenant_id, migration_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员经营账号迁移核对清单';

-- 旧版 keep_purchase_login 默认恒为 0，无法表达同一自然人同时保留采购登录的情况。
-- 将其降级为仅兼容读取的可空列；新流程只接受人工填写 purchase_login_disposition，绝不根据
-- member_identity、同名账号或既有角色自动推断采购权限。
SET @purchase_disposition_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_business_account_migration'
    AND column_name='purchase_login_disposition')=0,
  'ALTER TABLE product_business_account_migration ADD COLUMN purchase_login_disposition varchar(32) NOT NULL DEFAULT ''UNVERIFIED'' COMMENT ''人工核验：UNVERIFIED/KEEP/REVOKE_AFTER_HANDOFF'' AFTER keep_purchase_login',
  'SELECT 1');
PREPARE purchase_disposition_stmt FROM @purchase_disposition_ddl;
EXECUTE purchase_disposition_stmt;
DEALLOCATE PREPARE purchase_disposition_stmt;

SET @account_preparation_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_business_account_migration'
    AND column_name='account_preparation_status')=0,
  'ALTER TABLE product_business_account_migration ADD COLUMN account_preparation_status varchar(32) NOT NULL DEFAULT ''NOT_FOUND'' COMMENT ''NOT_FOUND/SECURE_RESET_REQUIRED/ROLE_OR_SCOPE_REVIEW/ELIGIBLE_FOR_VERIFICATION'' AFTER purchase_login_disposition',
  'SELECT 1');
PREPARE account_preparation_stmt FROM @account_preparation_ddl;
EXECUTE account_preparation_stmt;
DEALLOCATE PREPARE account_preparation_stmt;

SET @legacy_keep_purchase_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_business_account_migration' AND column_name='keep_purchase_login'
    AND is_nullable='NO')>0,
  'ALTER TABLE product_business_account_migration MODIFY COLUMN keep_purchase_login bit(1) NULL DEFAULT NULL COMMENT ''废弃兼容列：不得用于自动推断采购权限''',
  'SELECT 1');
PREPARE legacy_keep_purchase_stmt FROM @legacy_keep_purchase_ddl;
EXECUTE legacy_keep_purchase_stmt;
DEALLOCATE PREPARE legacy_keep_purchase_stmt;

UPDATE product_business_account_migration
SET keep_purchase_login=NULL,updater='mall-section21',update_time=NOW()
WHERE verified_by IS NULL AND keep_purchase_login IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. 既有表安全升级
-- ---------------------------------------------------------------------------
SET @application_admin_user_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_enterprise_application' AND column_name='admin_user_id')=0,
  'ALTER TABLE product_enterprise_application ADD COLUMN admin_user_id bigint NULL COMMENT ''审核关联的后台企业管理员'' AFTER shop_id',
  'SELECT 1');
PREPARE application_admin_user_stmt FROM @application_admin_user_ddl;
EXECUTE application_admin_user_stmt;
DEALLOCATE PREPARE application_admin_user_stmt;

SET @inquiry_visibility_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry' AND column_name='visibility')=0,
  'ALTER TABLE product_purchase_inquiry ADD COLUMN visibility varchar(24) NOT NULL DEFAULT ''TENANT_PUBLIC'' COMMENT ''TENANT_PUBLIC/INVITED_SHOPS'' AFTER selected_quote_id',
  'SELECT 1');
PREPARE inquiry_visibility_stmt FROM @inquiry_visibility_ddl;
EXECUTE inquiry_visibility_stmt;
DEALLOCATE PREPARE inquiry_visibility_stmt;
UPDATE product_purchase_inquiry SET visibility='TENANT_PUBLIC'
WHERE visibility IS NULL OR visibility='';

-- erp_status 是历史兼容列，旧记录原值保持不动；仅调整新记录的数据库默认值。
-- 第 21 节的新询价不产生 ERP 待接入状态，成交后的库存与出库由 WMS 直连链路复核。
SET @inquiry_wms_default_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry' AND column_name='erp_status'
    AND COALESCE(column_default,'')<>'WMS_DIRECT')>0,
  'ALTER TABLE product_purchase_inquiry MODIFY COLUMN erp_status varchar(32) NOT NULL DEFAULT ''WMS_DIRECT'' COMMENT ''历史兼容字段：新询价固定 WMS_DIRECT''',
  'SELECT 1');
PREPARE inquiry_wms_default_stmt FROM @inquiry_wms_default_ddl;
EXECUTE inquiry_wms_default_stmt;
DEALLOCATE PREPARE inquiry_wms_default_stmt;

SET @quote_admin_user_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry_quote' AND column_name='admin_user_id')=0,
  'ALTER TABLE product_purchase_inquiry_quote ADD COLUMN admin_user_id bigint NULL COMMENT ''实际报价后台操作人'' AFTER enterprise_id',
  'SELECT 1');
PREPARE quote_admin_user_stmt FROM @quote_admin_user_ddl;
EXECUTE quote_admin_user_stmt;
DEALLOCATE PREPARE quote_admin_user_stmt;

SET @quote_admin_index_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry_quote' AND index_name='idx_admin_user')=0,
  'ALTER TABLE product_purchase_inquiry_quote ADD INDEX idx_admin_user (tenant_id, admin_user_id, status)',
  'SELECT 1');
PREPARE quote_admin_index_stmt FROM @quote_admin_index_ddl;
EXECUTE quote_admin_index_stmt;
DEALLOCATE PREPARE quote_admin_index_stmt;

SET @message_admin_user_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry_quote_message' AND column_name='sender_admin_user_id')=0,
  'ALTER TABLE product_purchase_inquiry_quote_message ADD COLUMN sender_admin_user_id bigint NULL COMMENT ''后台供应商发送人'' AFTER sender_member_id',
  'SELECT 1');
PREPARE message_admin_user_stmt FROM @message_admin_user_ddl;
EXECUTE message_admin_user_stmt;
DEALLOCATE PREPARE message_admin_user_stmt;

SET @message_member_nullable_ddl = IF(
  (SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry_quote_message' AND column_name='sender_member_id')='NO',
  'ALTER TABLE product_purchase_inquiry_quote_message MODIFY COLUMN sender_member_id bigint NULL COMMENT ''用户端采购消息发送人''',
  'SELECT 1');
PREPARE message_member_nullable_stmt FROM @message_member_nullable_ddl;
EXECUTE message_member_nullable_stmt;
DEALLOCATE PREPARE message_member_nullable_stmt;

-- 先验证历史行，避免在坏数据上建立看似成功但实际可绕过的唯一约束。
SET @invalid_message_actor_count=(
  SELECT COUNT(*) FROM product_purchase_inquiry_quote_message
  WHERE deleted=b'0' AND ((sender_member_id IS NULL AND sender_admin_user_id IS NULL)
    OR (sender_member_id IS NOT NULL AND sender_admin_user_id IS NOT NULL)));
SET @message_actor_guard_ddl=IF(@invalid_message_actor_count>0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''询价消息升级中止：发送人必须且只能是 member 或 admin 之一''',
  'SELECT 1');
PREPARE message_actor_guard_stmt FROM @message_actor_guard_ddl;
EXECUTE message_actor_guard_stmt;
DEALLOCATE PREPARE message_actor_guard_stmt;

SET @message_actor_type_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry_quote_message' AND column_name='sender_actor_type')=0,
  'ALTER TABLE product_purchase_inquiry_quote_message ADD COLUMN sender_actor_type tinyint GENERATED ALWAYS AS (CASE WHEN sender_admin_user_id IS NOT NULL THEN 2 ELSE 1 END) STORED COMMENT ''1会员 2后台账号''',
  'SELECT 1');
PREPARE message_actor_type_stmt FROM @message_actor_type_ddl;
EXECUTE message_actor_type_stmt;
DEALLOCATE PREPARE message_actor_type_stmt;

SET @message_actor_id_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry_quote_message' AND column_name='sender_actor_id')=0,
  'ALTER TABLE product_purchase_inquiry_quote_message ADD COLUMN sender_actor_id bigint GENERATED ALWAYS AS (COALESCE(sender_admin_user_id,sender_member_id)) STORED COMMENT ''发送人统一编号''',
  'SELECT 1');
PREPARE message_actor_id_stmt FROM @message_actor_id_ddl;
EXECUTE message_actor_id_stmt;
DEALLOCATE PREPARE message_actor_id_stmt;

SET @message_actor_check_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
    AND table_name='product_purchase_inquiry_quote_message' AND constraint_name='chk_message_one_actor')=0,
  'ALTER TABLE product_purchase_inquiry_quote_message ADD CONSTRAINT chk_message_one_actor CHECK ((sender_member_id IS NULL) <> (sender_admin_user_id IS NULL))',
  'SELECT 1');
PREPARE message_actor_check_stmt FROM @message_actor_check_ddl;
EXECUTE message_actor_check_stmt;
DEALLOCATE PREPARE message_actor_check_stmt;

SET @message_old_index_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry_quote_message' AND index_name='uk_sender_message')>0,
  'ALTER TABLE product_purchase_inquiry_quote_message DROP INDEX uk_sender_message',
  'SELECT 1');
PREPARE message_old_index_stmt FROM @message_old_index_ddl;
EXECUTE message_old_index_stmt;
DEALLOCATE PREPARE message_old_index_stmt;

SET @message_actor_index_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='product_purchase_inquiry_quote_message' AND index_name='uk_actor_message')=0,
  'ALTER TABLE product_purchase_inquiry_quote_message ADD UNIQUE KEY uk_actor_message (tenant_id,quote_id,sender_actor_type,sender_actor_id,client_message_id,deleted)',
  'SELECT 1');
PREPARE message_actor_index_stmt FROM @message_actor_index_ddl;
EXECUTE message_actor_index_stmt;
DEALLOCATE PREPARE message_actor_index_stmt;

-- ---------------------------------------------------------------------------
-- 3. 后台页面、按钮权限。固定 ID 避免各租户菜单漂移。
-- ---------------------------------------------------------------------------
INSERT INTO system_menu
 (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,
  creator,create_time,updater,update_time,deleted)
VALUES
 (25200,'销售管理','',1,80,2362,'supplier-sales','ep:sell','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25201,'询价机会','',2,1,25200,'inquiry-opportunity','ep:search','mall/product/inquiry-supplier/opportunity/index','MallSupplierInquiryOpportunity',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25202,'我的报价','',2,2,25200,'inquiry-quote','ep:document','mall/product/inquiry-supplier/quote/index','MallSupplierInquiryQuote',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25203,'报价沟通','',2,3,25200,'inquiry-message','ep:chat-dot-round','mall/product/inquiry-supplier/message/index','MallSupplierInquiryMessage',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25204,'销售订单','',2,4,25200,'supplier-order','ep:tickets','mall/trade/supplier/order/index','MallSupplierOrder',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25205,'销售售后','',2,5,25200,'supplier-after-sale','ep:refresh-left','mall/trade/supplier/after-sale/index','MallSupplierAfterSale',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25206,'店铺结算','',2,6,25200,'supplier-settlement','ep:money','mall/trade/supplier/settlement/index','MallSupplierSettlement',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25207,'企业员工授权','',2,20,25005,'member','ep:user','mall/product/enterprise/member/index','MallProductEnterpriseMember',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25211,'询价机会查询','product:inquiry-supplier:query',3,1,25201,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25212,'店铺报价','product:inquiry-supplier:quote',3,1,25202,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25213,'报价沟通','product:inquiry-supplier:message',3,1,25203,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25214,'销售订单查询','trade:supplier-order:query',3,1,25204,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25215,'销售订单处理','trade:supplier-order:update',3,2,25204,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25216,'销售售后查询','trade:supplier-after-sale:query',3,1,25205,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25217,'销售售后处理','trade:supplier-after-sale:process',3,2,25205,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25218,'店铺结算查询','trade:supplier-settlement:query',3,1,25206,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25219,'店铺账单确认','trade:supplier-settlement:confirm',3,2,25206,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25220,'企业员工查询','product:business-member:query',3,1,25207,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0'),
 (25221,'企业员工授权','product:business-member:update',3,2,25207,'','','','',0,b'1',b'1',b'1','mall-section21',NOW(),'mall-section21',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),type=VALUES(type),sort=VALUES(sort),
 parent_id=VALUES(parent_id),path=VALUES(path),icon=VALUES(icon),component=VALUES(component),
 component_name=VALUES(component_name),status=0,visible=b'1',deleted=b'0',updater='mall-section21',update_time=NOW();

-- ---------------------------------------------------------------------------
-- 4. 角色模板。企业角色和店铺角色均不包含商品审核、企业审核或平台通用交易权限。
-- ---------------------------------------------------------------------------
UPDATE system_role SET name='商城企业管理员',status=0,deleted=b'0',updater='mall-section21',update_time=NOW()
WHERE code='mall_enterprise_admin'
  AND tenant_id IN (SELECT id FROM system_tenant WHERE status=0 AND deleted=b'0');
INSERT INTO system_role
 (name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,updater,deleted,tenant_id)
SELECT '商城企业管理员','mall_enterprise_admin',80,2,'',0,2,'商城企业与本企业全部店铺经营；仍受业务数据范围约束',
       'mall-section21','mall-section21',b'0',t.id
FROM system_tenant t
WHERE t.status=0 AND t.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role r WHERE r.tenant_id=t.id AND r.code='mall_enterprise_admin');

UPDATE system_role SET name='商城店铺运营',status=0,deleted=b'0',updater='mall-section21',update_time=NOW()
WHERE code='mall_shop_merchant'
  AND tenant_id IN (SELECT id FROM system_tenant WHERE status=0 AND deleted=b'0');
INSERT INTO system_role
 (name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,updater,deleted,tenant_id)
SELECT '商城店铺运营','mall_shop_merchant',81,2,'',0,2,'仅经营显式授权店铺；不含审核和企业员工授权',
       'mall-section21','mall-section21',b'0',t.id
FROM system_tenant t
WHERE t.status=0 AND t.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role r WHERE r.tenant_id=t.id AND r.code='mall_shop_merchant');

UPDATE system_role SET name='商城店铺财务',status=0,deleted=b'0',updater='mall-section21',update_time=NOW()
WHERE code='mall_shop_finance'
  AND tenant_id IN (SELECT id FROM system_tenant WHERE status=0 AND deleted=b'0');
INSERT INTO system_role
 (name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,updater,deleted,tenant_id)
SELECT '商城店铺财务','mall_shop_finance',82,2,'',0,2,'仅查看并确认显式授权店铺账单；不含商品、报价和履约权限',
       'mall-section21','mall-section21',b'0',t.id
FROM system_tenant t
WHERE t.status=0 AND t.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role r WHERE r.tenant_id=t.id AND r.code='mall_shop_finance');

-- 若旧版脚本曾把供应经营菜单发给租户治理角色，先显式撤销；租户监管继续使用平台交易/审核页面。
UPDATE system_role_menu rm
JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
SET rm.deleted=b'1',rm.updater='mall-section21',rm.update_time=NOW()
WHERE r.code='tenant_admin' AND rm.menu_id IN
 (25200,25201,25202,25203,25204,25205,25206,25211,25212,25213,25214,25215,25216,25217,25218,25219);

-- 店铺运营和企业管理员默认只能查看账单；确认动作单独授予店铺财务角色。
UPDATE system_role_menu rm
JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
SET rm.deleted=b'1',rm.updater='mall-section21',rm.update_time=NOW()
WHERE r.code IN ('mall_enterprise_admin','mall_shop_merchant') AND rm.menu_id=25219;

-- 先恢复已有角色菜单关系，再只插入不存在的关系。
UPDATE system_role_menu rm
JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
SET rm.deleted=b'0',rm.updater='mall-section21',rm.update_time=NOW()
WHERE r.deleted=b'0' AND r.status=0
  AND ((r.code='tenant_admin' AND rm.menu_id IN (25005,25207,25220,25221))
    OR (r.code='mall_enterprise_admin' AND rm.menu_id IN (2362,2000,2014,2015,2016,2017,2018,2003,2009,2020,25000,25001,25003,25005,25100,25102,25200,25201,25202,25203,25204,25205,25206,25207,25211,25212,25213,25214,25215,25216,25217,25218,25220,25221))
    OR (r.code='mall_shop_merchant' AND rm.menu_id IN (2362,2000,2014,2015,2016,2017,2018,2003,2009,2020,25000,25001,25003,25200,25201,25202,25203,25204,25205,25206,25211,25212,25213,25214,25215,25216,25217,25218))
    OR (r.code='mall_shop_finance' AND rm.menu_id IN (2362,25200,25206,25218,25219)));

INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT r.id,m.menu_id,'mall-section21','mall-section21',b'0',r.tenant_id
FROM system_role r
JOIN (
  SELECT 25005 menu_id UNION ALL SELECT 25207 UNION ALL SELECT 25220 UNION ALL SELECT 25221
) m
WHERE r.code='tenant_admin' AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.menu_id);

INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT r.id,m.menu_id,'mall-section21','mall-section21',b'0',r.tenant_id
FROM system_role r
JOIN (
  SELECT 2362 menu_id UNION ALL SELECT 2000 UNION ALL SELECT 2014 UNION ALL SELECT 2015
  UNION ALL SELECT 2016 UNION ALL SELECT 2017 UNION ALL SELECT 2018 UNION ALL SELECT 2003
  UNION ALL SELECT 2009 UNION ALL SELECT 2020 UNION ALL SELECT 25000 UNION ALL SELECT 25001
  UNION ALL SELECT 25003 UNION ALL SELECT 25005 UNION ALL SELECT 25100 UNION ALL SELECT 25102
  UNION ALL SELECT 25200 UNION ALL SELECT 25201 UNION ALL SELECT 25202 UNION ALL SELECT 25203
  UNION ALL SELECT 25204 UNION ALL SELECT 25205 UNION ALL SELECT 25206 UNION ALL SELECT 25207
  UNION ALL SELECT 25211 UNION ALL SELECT 25212 UNION ALL SELECT 25213 UNION ALL SELECT 25214
  UNION ALL SELECT 25215 UNION ALL SELECT 25216 UNION ALL SELECT 25217 UNION ALL SELECT 25218
  UNION ALL SELECT 25220 UNION ALL SELECT 25221
) m
WHERE r.code='mall_enterprise_admin' AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.menu_id);

INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT r.id,m.menu_id,'mall-section21','mall-section21',b'0',r.tenant_id
FROM system_role r
JOIN (
  SELECT 2362 menu_id UNION ALL SELECT 2000 UNION ALL SELECT 2014 UNION ALL SELECT 2015
  UNION ALL SELECT 2016 UNION ALL SELECT 2017 UNION ALL SELECT 2018 UNION ALL SELECT 2003
  UNION ALL SELECT 2009 UNION ALL SELECT 2020 UNION ALL SELECT 25000 UNION ALL SELECT 25001
  UNION ALL SELECT 25003 UNION ALL SELECT 25200 UNION ALL SELECT 25201 UNION ALL SELECT 25202
  UNION ALL SELECT 25203 UNION ALL SELECT 25204 UNION ALL SELECT 25205 UNION ALL SELECT 25206
  UNION ALL SELECT 25211 UNION ALL SELECT 25212 UNION ALL SELECT 25213 UNION ALL SELECT 25214
  UNION ALL SELECT 25215 UNION ALL SELECT 25216 UNION ALL SELECT 25217 UNION ALL SELECT 25218
) m
WHERE r.code='mall_shop_merchant' AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.menu_id);

INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT r.id,m.menu_id,'mall-section21','mall-section21',b'0',r.tenant_id
FROM system_role r
JOIN (
  SELECT 2362 menu_id UNION ALL SELECT 25200 UNION ALL SELECT 25206
  UNION ALL SELECT 25218 UNION ALL SELECT 25219
) m
WHERE r.code='mall_shop_finance' AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.menu_id);

-- ---------------------------------------------------------------------------
-- 5. 生成账号迁移核对清单。用户名相同只能作为检索线索，不能自动证明账号归属。
-- 运维人员核验自然人、企业授权和账号交付后，须显式填写 verified_by/verified_at，
-- purchase_login_disposition 及 migration_status；只有 READY 且核验完整的记录才会建立经营范围。
-- 系统不会创建默认密码。只有当前有效、已经具有目标经营角色且未绑定其他企业的后台账号
-- 才会写入 admin_user_id，其余账号给出明确的预配置状态，交由后台安全建号/一次性重置流程处理。
-- ---------------------------------------------------------------------------
INSERT INTO product_business_account_migration
 (member_id,enterprise_id,shop_id,admin_user_id,original_login_name,target_role_code,
  purchase_login_disposition,account_preparation_status,migration_status,creator,updater,deleted,tenant_id)
SELECT a.member_id,a.enterprise_id,a.shop_id,u.id,m.login_name,'mall_enterprise_admin',
       'UNVERIFIED',
       CASE WHEN u.id IS NOT NULL THEN 'ELIGIBLE_FOR_VERIFICATION'
            WHEN u_any.id IS NULL THEN 'NOT_FOUND'
            WHEN u_any.deleted<>b'0' OR u_any.status<>0 THEN 'SECURE_RESET_REQUIRED'
            ELSE 'ROLE_OR_SCOPE_REVIEW' END,
       CASE WHEN u.id IS NOT NULL THEN 'NEEDS_VERIFICATION'
            WHEN u_any.id IS NULL THEN 'NEEDS_ACCOUNT'
            WHEN u_any.deleted<>b'0' OR u_any.status<>0 THEN 'NEEDS_SECURE_RESET'
            ELSE 'NEEDS_ROLE_PRECONFIG' END,
       'mall-section21','mall-section21',b'0',a.tenant_id
FROM product_enterprise_application a
JOIN member_user m ON m.id=a.member_id AND m.tenant_id=a.tenant_id AND m.deleted=b'0'
LEFT JOIN system_users u_any ON u_any.id=(
  SELECT MIN(any_user.id) FROM system_users any_user
  WHERE any_user.tenant_id=a.tenant_id AND any_user.username=m.login_name)
LEFT JOIN system_users u ON u.id=(
  SELECT MIN(candidate.id)
  FROM system_users candidate
  WHERE candidate.tenant_id=a.tenant_id AND candidate.username=m.login_name
    AND candidate.deleted=b'0' AND candidate.status=0
    AND EXISTS (
      SELECT 1 FROM system_user_role ur
      JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id
        AND r.code='mall_enterprise_admin' AND r.deleted=b'0' AND r.status=0
      WHERE ur.user_id=candidate.id AND ur.tenant_id=candidate.tenant_id AND ur.deleted=b'0')
    AND NOT EXISTS (
      SELECT 1 FROM product_enterprise_admin_member bound
      WHERE bound.admin_user_id=candidate.id AND bound.tenant_id=candidate.tenant_id
        AND bound.enterprise_id<>a.enterprise_id AND bound.status=0 AND bound.deleted=b'0'))
WHERE a.deleted=b'0' AND a.status=1 AND a.identity='supplier'
  AND a.enterprise_id IS NOT NULL
ON DUPLICATE KEY UPDATE shop_id=IF(product_business_account_migration.verified_by IS NULL,
                    VALUES(shop_id),product_business_account_migration.shop_id),
 admin_user_id=IF(product_business_account_migration.verified_by IS NULL,
                  VALUES(admin_user_id),product_business_account_migration.admin_user_id),
 original_login_name=IF(product_business_account_migration.verified_by IS NULL,
                         VALUES(original_login_name),product_business_account_migration.original_login_name),
 account_preparation_status=IF(product_business_account_migration.verified_by IS NULL,
                    VALUES(account_preparation_status),product_business_account_migration.account_preparation_status),
 migration_status=IF(product_business_account_migration.verified_by IS NULL,
                     VALUES(migration_status),product_business_account_migration.migration_status),
 updater='mall-section21',update_time=NOW(),deleted=b'0';

SET @invalid_verified_migration_count=(
  SELECT COUNT(*)
  FROM product_business_account_migration m
  LEFT JOIN system_users u ON u.id=m.admin_user_id AND u.tenant_id=m.tenant_id
  LEFT JOIN system_users verifier ON verifier.id=m.verified_by AND verifier.tenant_id=m.tenant_id
  WHERE m.deleted=b'0' AND m.migration_status='READY'
    AND (m.verified_by IS NULL OR m.verified_at IS NULL
      OR m.purchase_login_disposition NOT IN ('KEEP','REVOKE_AFTER_HANDOFF')
      OR m.target_role_code NOT IN ('mall_enterprise_admin','mall_shop_merchant','mall_shop_finance')
      OR u.id IS NULL OR u.deleted<>b'0' OR u.status<>0
      OR verifier.id IS NULL OR verifier.deleted<>b'0' OR verifier.status<>0
      OR verifier.id=m.admin_user_id
      OR NOT EXISTS (
        SELECT 1 FROM system_user_role verifier_ur
        JOIN system_role verifier_role
          ON verifier_role.id=verifier_ur.role_id AND verifier_role.tenant_id=verifier_ur.tenant_id
         AND verifier_role.code IN ('tenant_admin','super_admin')
         AND verifier_role.deleted=b'0' AND verifier_role.status=0
        WHERE verifier_ur.user_id=m.verified_by AND verifier_ur.tenant_id=m.tenant_id
          AND verifier_ur.deleted=b'0')
      OR NOT EXISTS (
        SELECT 1 FROM system_user_role ur
        JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id
          AND r.code COLLATE utf8mb4_unicode_ci=m.target_role_code COLLATE utf8mb4_unicode_ci
          AND r.deleted=b'0' AND r.status=0
        WHERE ur.user_id=m.admin_user_id AND ur.tenant_id=m.tenant_id AND ur.deleted=b'0')
      OR EXISTS (
        SELECT 1 FROM product_enterprise_admin_member bound
        WHERE bound.admin_user_id=m.admin_user_id AND bound.tenant_id=m.tenant_id
          AND bound.enterprise_id<>m.enterprise_id AND bound.status=0 AND bound.deleted=b'0')
      OR (m.target_role_code IN ('mall_shop_merchant','mall_shop_finance') AND
          (m.shop_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM product_shop scoped_shop
            WHERE scoped_shop.id=m.shop_id AND scoped_shop.enterprise_id=m.enterprise_id
              AND scoped_shop.tenant_id=m.tenant_id
              AND scoped_shop.status=0 AND scoped_shop.deleted=b'0')))
      OR (m.target_role_code='mall_shop_merchant' AND EXISTS (
            SELECT 1 FROM system_user_role extra_ur
            JOIN system_role extra_role
              ON extra_role.id=extra_ur.role_id AND extra_role.tenant_id=extra_ur.tenant_id
             AND extra_role.deleted=b'0' AND extra_role.status=0
            WHERE extra_ur.user_id=m.admin_user_id AND extra_ur.tenant_id=m.tenant_id
              AND extra_ur.deleted=b'0' AND extra_role.code<>'mall_shop_merchant'))
      OR (m.target_role_code IN ('mall_shop_merchant','mall_shop_finance') AND EXISTS (
            SELECT 1 FROM product_shop_admin_authorization other_grant
            WHERE other_grant.admin_user_id=m.admin_user_id AND other_grant.tenant_id=m.tenant_id
              AND other_grant.status=0 AND other_grant.deleted=b'0'
              AND (other_grant.enterprise_id<>m.enterprise_id OR other_grant.shop_id<>m.shop_id)))));
SET @verified_migration_guard=IF(@invalid_verified_migration_count>0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''第21节迁移中止：READY账号必须完成人工采购登录核验、角色预配置且未绑定其他企业''',
  'SELECT 1');
PREPARE verified_migration_guard_stmt FROM @verified_migration_guard;
EXECUTE verified_migration_guard_stmt;
DEALLOCATE PREPARE verified_migration_guard_stmt;

UPDATE product_enterprise_application a
JOIN product_business_account_migration m ON m.member_id=a.member_id
  AND m.enterprise_id=a.enterprise_id AND m.tenant_id=a.tenant_id
  AND m.deleted=b'0' AND m.migration_status='READY'
  AND m.verified_by IS NOT NULL AND m.verified_at IS NOT NULL
JOIN system_users u ON u.id=m.admin_user_id AND u.tenant_id=a.tenant_id
  AND u.deleted=b'0' AND u.status=0
SET a.admin_user_id=u.id,a.updater='mall-section21',a.update_time=NOW()
WHERE a.deleted=b'0' AND a.status=1 AND a.identity='supplier' AND a.enterprise_id IS NOT NULL
  AND m.target_role_code='mall_enterprise_admin';

INSERT INTO product_enterprise_admin_member
 (admin_user_id,enterprise_id,scope_type,status,creator,updater,deleted,tenant_id)
SELECT m.admin_user_id,a.enterprise_id,
       CASE WHEN m.target_role_code='mall_enterprise_admin' THEN 1 ELSE 2 END,
       0,'mall-section21','mall-section21',b'0',a.tenant_id
FROM product_enterprise_application a
JOIN product_business_account_migration m ON m.member_id=a.member_id
  AND m.enterprise_id=a.enterprise_id AND m.tenant_id=a.tenant_id
  AND m.deleted=b'0' AND m.migration_status='READY'
  AND m.verified_by IS NOT NULL AND m.verified_at IS NOT NULL
  AND m.purchase_login_disposition IN ('KEEP','REVOKE_AFTER_HANDOFF')
JOIN system_users u ON u.id=m.admin_user_id AND u.tenant_id=a.tenant_id AND u.deleted=b'0' AND u.status=0
WHERE a.deleted=b'0' AND a.status=1 AND a.identity='supplier'
  AND m.admin_user_id IS NOT NULL
  AND m.target_role_code IN ('mall_enterprise_admin','mall_shop_merchant','mall_shop_finance')
  AND EXISTS (
    SELECT 1 FROM system_user_role ur
    JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id
      AND r.code COLLATE utf8mb4_unicode_ci=m.target_role_code COLLATE utf8mb4_unicode_ci
      AND r.deleted=b'0' AND r.status=0
    WHERE ur.user_id=m.admin_user_id AND ur.tenant_id=a.tenant_id AND ur.deleted=b'0')
ON DUPLICATE KEY UPDATE scope_type=VALUES(scope_type),status=0,
 updater='mall-section21',update_time=NOW(),deleted=b'0';

INSERT INTO product_shop_admin_authorization
 (admin_user_id,enterprise_id,shop_id,status,creator,updater,deleted,tenant_id)
SELECT m.admin_user_id,a.enterprise_id,m.shop_id,0,'mall-section21','mall-section21',b'0',a.tenant_id
FROM product_enterprise_application a
JOIN product_business_account_migration m ON m.member_id=a.member_id
  AND m.enterprise_id=a.enterprise_id AND m.tenant_id=a.tenant_id
  AND m.deleted=b'0' AND m.migration_status='READY'
  AND m.verified_by IS NOT NULL AND m.verified_at IS NOT NULL
  AND m.purchase_login_disposition IN ('KEEP','REVOKE_AFTER_HANDOFF')
JOIN system_users u ON u.id=m.admin_user_id AND u.tenant_id=a.tenant_id AND u.deleted=b'0' AND u.status=0
JOIN product_shop s ON s.id=m.shop_id AND s.enterprise_id=a.enterprise_id AND s.tenant_id=a.tenant_id
  AND s.deleted=b'0' AND s.status=0
WHERE a.deleted=b'0' AND a.status=1 AND a.identity='supplier' AND m.admin_user_id IS NOT NULL
  AND m.target_role_code IN ('mall_enterprise_admin','mall_shop_merchant','mall_shop_finance')
  AND EXISTS (
    SELECT 1 FROM system_user_role ur
    JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id
      AND r.code COLLATE utf8mb4_unicode_ci=m.target_role_code COLLATE utf8mb4_unicode_ci
      AND r.deleted=b'0' AND r.status=0
    WHERE ur.user_id=m.admin_user_id AND ur.tenant_id=a.tenant_id AND ur.deleted=b'0')
ON DUPLICATE KEY UPDATE enterprise_id=VALUES(enterprise_id),status=0,
 updater='mall-section21',update_time=NOW(),deleted=b'0';

-- 本迁移不会为候选账号自动补角色，也不会创建或重置密码。NEEDS_ROLE_PRECONFIG/
-- NEEDS_SECURE_RESET/NEEDS_ACCOUNT 必须在后台完成安全建号、一次性重置及人工复核后，
-- 再显式将迁移记录标记 READY；因此这里仅绑定已具备有效目标角色的账号。

-- 验证结果：第二个结果集中 NEEDS_* 必须经过后台安全建号/重置密码后再启用，脚本不会绕过。
SELECT id,name,permission,component FROM system_menu WHERE id BETWEEN 25200 AND 25221 ORDER BY id;
SELECT tenant_id,account_preparation_status,purchase_login_disposition,migration_status,COUNT(*) account_count
FROM product_business_account_migration WHERE deleted=b'0'
GROUP BY tenant_id,account_preparation_status,purchase_login_disposition,migration_status
ORDER BY tenant_id,account_preparation_status,migration_status;
SELECT table_name,column_name,is_nullable
FROM information_schema.columns
WHERE table_schema=DATABASE() AND (
  (table_name='product_enterprise_application' AND column_name='admin_user_id') OR
  (table_name='product_purchase_inquiry' AND column_name='visibility') OR
  (table_name='product_purchase_inquiry_quote' AND column_name='admin_user_id') OR
  (table_name='product_purchase_inquiry_quote_message'
    AND column_name IN ('sender_member_id','sender_admin_user_id','sender_actor_type','sender_actor_id')))
ORDER BY table_name,column_name;
