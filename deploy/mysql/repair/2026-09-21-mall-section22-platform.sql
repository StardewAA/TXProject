-- 第 22 节：平台门户、平台账号映射、跨租户购物车与平台结算基础模型。
-- 设计原则：平台域不属于任何经营租户；所有关联的业务资源都保存真实 tenant_id。
-- 本脚本不改写历史订单事实，不迁移 3000 号公共租户业务，也不创建默认密码。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 平台公开租户目录与热门租户日汇总
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_portal_tenant (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  tenant_slug varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  tenant_name varchar(128) NOT NULL,
  tenant_logo varchar(512) NULL,
  main_categories varchar(512) NULL,
  open_status tinyint NOT NULL DEFAULT 0 COMMENT '0开放 1关闭',
  sort int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_portal_tenant (tenant_id,deleted),
  UNIQUE KEY uk_portal_slug (tenant_slug,deleted),
  KEY idx_portal_open_sort (open_status,sort,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城平台公开租户目录';

CREATE TABLE IF NOT EXISTS mall_tenant_sales_daily (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  statistic_date date NOT NULL,
  paid_quantity bigint NOT NULL DEFAULT 0,
  refunded_quantity bigint NOT NULL DEFAULT 0,
  valid_quantity bigint NOT NULL DEFAULT 0,
  paid_order_count bigint NOT NULL DEFAULT 0,
  paid_amount bigint NOT NULL DEFAULT 0 COMMENT '有效支付金额，单位分',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_stat_date (tenant_id,statistic_date,deleted),
  KEY idx_stat_date (statistic_date,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城租户有效销量日汇总';

-- 订单没有通用的“测试单”字段。mock 支付渠道由统计 SQL 自动排除；其他测试、演示或
-- 人工造数订单必须在此按真实租户和订单编号显式登记，禁止依赖名称、备注等文本猜测。
CREATE TABLE IF NOT EXISTS mall_portal_sales_order_exclusion (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  order_id bigint NOT NULL,
  reason varchar(255) NOT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_portal_sales_exclusion (tenant_id,order_id,deleted),
  KEY idx_portal_sales_exclusion_order (order_id,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台热门租户销量显式排除订单';

-- 只开放具备有效企业、店铺和已审核上架商品的正式经营租户；3000 永不进入门户经营目录。
INSERT INTO mall_portal_tenant
 (tenant_id,tenant_slug,tenant_name,tenant_logo,main_categories,open_status,sort,
  creator,updater,deleted)
SELECT t.id,CONCAT('tenant-',t.id),t.name,NULL,NULL,0,0,
       'mall-section22','mall-section22',b'0'
FROM system_tenant t
WHERE t.deleted=b'0' AND t.status=0 AND t.id<>3000
  AND EXISTS (
    SELECT 1
    FROM product_spu p
    JOIN product_shop s ON s.id=p.shop_id AND s.tenant_id=p.tenant_id
      AND s.deleted=b'0' AND s.status=0
    JOIN product_enterprise e ON e.id=s.enterprise_id AND e.tenant_id=s.tenant_id
      AND e.deleted=b'0' AND e.audit_status=1
    WHERE p.tenant_id=t.id AND p.deleted=b'0' AND p.status=1
      AND p.review_status=20 AND p.approved_version=p.content_version)
-- 已由运营明确关闭的门户租户不能因脚本重跑被自动重新开放；这里只同步公开名称。
ON DUPLICATE KEY UPDATE tenant_name=VALUES(tenant_name),
 updater='mall-section22',update_time=NOW();

-- 公共租户仅保留迁移期历史查询，不允许出现在平台门户。
UPDATE mall_portal_tenant
SET open_status=1,updater='mall-section22',update_time=NOW()
WHERE tenant_id=3000 AND deleted=b'0';

-- 首次按最近 30 天交易事实重建日汇总。取消或全额退款不计有效销量和有效金额。
-- SKU 当前库存和现价属于可变经营状态，不得反向抹掉已支付订单的历史销量；
-- 租户当前是否开放且至少有一个可售商品，由热门租户读取查询独立校验。
INSERT INTO mall_tenant_sales_daily
 (tenant_id,statistic_date,paid_quantity,refunded_quantity,valid_quantity,
  paid_order_count,paid_amount,creator,updater,deleted)
SELECT o.tenant_id,DATE(o.pay_time),
       SUM(items.paid_quantity) AS paid_quantity,
       SUM(CASE WHEN o.status=40 OR o.refund_status=20
                THEN items.paid_quantity ELSE items.refunded_quantity END) AS refunded_quantity,
       SUM(CASE WHEN o.status=40 OR o.refund_status=20
                THEN 0 ELSE items.valid_quantity END) AS valid_quantity,
       COUNT(DISTINCT CASE WHEN o.status<>40 AND o.refund_status<>20 AND items.valid_quantity>0
                           THEN o.id END) AS paid_order_count,
       SUM(CASE WHEN o.status=40 OR o.refund_status=20 THEN 0
                ELSE GREATEST(COALESCE(items.paid_amount,0)-COALESCE(o.refund_price,0),0) END) AS paid_amount,
       'mall-section22','mall-section22',b'0'
FROM trade_order o
JOIN (
  SELECT oi.tenant_id,oi.order_id,
         SUM(oi.count) AS paid_quantity,
         SUM(LEAST(oi.count,COALESCE(refunds.refunded_quantity,0))) AS refunded_quantity,
         SUM(GREATEST(oi.count-COALESCE(refunds.refunded_quantity,0),0)) AS valid_quantity,
         SUM(COALESCE(oi.pay_price,0)) AS paid_amount
  FROM trade_order_item oi
  LEFT JOIN (
    SELECT af.tenant_id,af.order_item_id,SUM(af.count) AS refunded_quantity
    FROM trade_after_sale af
    WHERE af.deleted=b'0' AND af.status=50
    GROUP BY af.tenant_id,af.order_item_id
  ) refunds ON refunds.tenant_id=oi.tenant_id AND refunds.order_item_id=oi.id
  JOIN product_sku sku ON sku.id=oi.sku_id
    AND sku.tenant_id=oi.tenant_id AND sku.spu_id=oi.spu_id
    AND sku.deleted=b'0'
  JOIN product_spu spu ON spu.id=oi.spu_id
    AND spu.tenant_id=oi.tenant_id AND spu.id=sku.spu_id
    AND spu.deleted=b'0' AND spu.status=1 AND spu.review_status=20
    AND spu.approved_version IS NOT NULL AND spu.approved_version=spu.content_version
  JOIN product_shop ps ON ps.id=oi.shop_id
    AND ps.id=spu.shop_id AND ps.tenant_id=oi.tenant_id
    AND ps.deleted=b'0' AND ps.status=0
  JOIN product_enterprise pe ON pe.id=ps.enterprise_id
    AND pe.tenant_id=oi.tenant_id
    AND pe.deleted=b'0' AND pe.audit_status=1
  WHERE oi.deleted=b'0'
  GROUP BY oi.tenant_id,oi.order_id
) items ON items.order_id=o.id AND items.tenant_id=o.tenant_id
JOIN mall_portal_tenant pt ON pt.tenant_id=o.tenant_id AND pt.open_status=0 AND pt.deleted=b'0'
JOIN system_tenant st ON st.id=o.tenant_id AND st.deleted=b'0' AND st.status=0
  AND (st.expire_time IS NULL OR st.expire_time>NOW())
WHERE o.deleted=b'0' AND o.pay_status=b'1' AND o.pay_time>=CURRENT_DATE-INTERVAL 30 DAY
  AND o.tenant_id<>3000
  AND LOWER(TRIM(COALESCE(o.pay_channel_code,'')))<>'mock'
  AND NOT EXISTS (
    SELECT 1 FROM mall_portal_sales_order_exclusion ex
    WHERE ex.tenant_id=o.tenant_id AND ex.order_id=o.id AND ex.deleted=b'0')
GROUP BY o.tenant_id,DATE(o.pay_time)
ON DUPLICATE KEY UPDATE paid_quantity=VALUES(paid_quantity),
 refunded_quantity=VALUES(refunded_quantity),valid_quantity=VALUES(valid_quantity),
 paid_order_count=VALUES(paid_order_count),paid_amount=VALUES(paid_amount),
 updater='mall-section22',updated_at=NOW(),deleted=b'0';

-- ---------------------------------------------------------------------------
-- 2. 平台账号与旧租户会员映射。凭据仍由既有认证链路保管，禁止在映射表复制密码。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS member_platform_account (
  id bigint NOT NULL AUTO_INCREMENT,
  platform_no varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  credential_type varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'LOGIN_NAME/MOBILE',
  credential_identifier varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '平台级规范化登录标识，不保存密码',
  login_name varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '平台全局登录名',
  mobile varchar(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '平台全局手机号',
  canonical_member_id bigint NOT NULL COMMENT '当前兼容认证使用的主会员编号',
  status tinyint NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_platform_no_global (platform_no),
  UNIQUE KEY uk_platform_credential (credential_type,credential_identifier,deleted),
  UNIQUE KEY uk_platform_login_name_global (login_name),
  UNIQUE KEY uk_platform_mobile_global (mobile),
  UNIQUE KEY uk_canonical_member (canonical_member_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城平台会员账号';

CREATE TABLE IF NOT EXISTS member_platform_account_link (
  id bigint NOT NULL AUTO_INCREMENT,
  platform_account_id bigint NULL,
  legacy_member_id bigint NOT NULL,
  source_tenant_id bigint NOT NULL,
  link_status varchar(32) NOT NULL DEFAULT 'PENDING'
    COMMENT 'READY/PENDING/CREDENTIAL_CONFLICT/IDENTITY_CONFLICT',
  conflict_reason varchar(512) NULL,
  link_origin varchar(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED'
    COMMENT 'SELF_REGISTRATION/MIGRATION_UNIQUE/MANUAL_IDENTITY_REVIEW/LEGACY_UNVERIFIED',
  identity_review_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '人工身份归并审核单号；同一平台账号的多链接必须一致',
  identity_verified_by varchar(64) NULL,
  identity_verified_at datetime NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_legacy_member (source_tenant_id,legacy_member_id,deleted),
  KEY idx_platform_link (platform_account_id,link_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号与旧租户会员映射';

CREATE TABLE IF NOT EXISTS member_platform_tenant_membership (
  id bigint NOT NULL AUTO_INCREMENT,
  platform_account_id bigint NOT NULL,
  tenant_id bigint NOT NULL,
  enterprise_id bigint NULL,
  member_type varchar(32) NOT NULL COMMENT 'CUSTOMER/BUYER/ENTERPRISE_CONTACT',
  status tinyint NOT NULL DEFAULT 0,
  valid_from datetime NULL,
  valid_until datetime NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_platform_tenant_enterprise
    (platform_account_id,tenant_id,enterprise_id,member_type,deleted),
  KEY idx_tenant_enterprise (tenant_id,enterprise_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台会员租户企业身份';

-- 已部署过早期脚本的环境原地增加平台级登录标识；密码仍只保留在 member_user。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account' AND COLUMN_NAME='credential_type')=0,
  'ALTER TABLE member_platform_account ADD COLUMN credential_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''LOGIN_NAME/MOBILE'' AFTER platform_no',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧部署补齐身份链接来源和人工审核凭证。多链接不能靠相同账号、手机号或密码哈希自动放行。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account_link' AND COLUMN_NAME='link_origin')=0,
  'ALTER TABLE member_platform_account_link ADD COLUMN link_origin VARCHAR(32) NOT NULL DEFAULT ''LEGACY_UNVERIFIED'' COMMENT ''SELF_REGISTRATION/MIGRATION_UNIQUE/MANUAL_IDENTITY_REVIEW/LEGACY_UNVERIFIED'' AFTER conflict_reason',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account_link' AND COLUMN_NAME='identity_review_no')=0,
  'ALTER TABLE member_platform_account_link ADD COLUMN identity_review_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''人工身份归并审核单号；同一平台账号的多链接必须一致'' AFTER link_origin',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account_link' AND COLUMN_NAME='identity_verified_by')=0,
  'ALTER TABLE member_platform_account_link ADD COLUMN identity_verified_by VARCHAR(64) NULL AFTER identity_review_no',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account_link' AND COLUMN_NAME='identity_verified_at')=0,
  'ALTER TABLE member_platform_account_link ADD COLUMN identity_verified_at DATETIME NULL AFTER identity_verified_by',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account' AND COLUMN_NAME='credential_identifier')=0,
  'ALTER TABLE member_platform_account ADD COLUMN credential_identifier VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT ''平台级规范化登录标识，不保存密码'' AFTER credential_type',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account' AND COLUMN_NAME='login_name')=0,
  'ALTER TABLE member_platform_account ADD COLUMN login_name VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT ''平台全局登录名'' AFTER credential_identifier',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account' AND COLUMN_NAME='mobile')=0,
  'ALTER TABLE member_platform_account ADD COLUMN mobile VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''平台全局手机号'' AFTER login_name',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧部署只在登录名、手机号分别全局无冲突时回填；任何重复值都不自动判断为同一自然人。
-- WHERE 中先排除当前冲突标识，保证唯一索引已存在时重跑也不会在隔离冲突前中止。
UPDATE member_platform_account pa
JOIN member_user u ON u.id=pa.canonical_member_id AND u.deleted=b'0' AND u.status=0
SET pa.credential_type=CASE WHEN NULLIF(TRIM(u.login_name),'') IS NOT NULL THEN 'LOGIN_NAME' ELSE 'MOBILE' END,
    pa.credential_identifier=CASE WHEN NULLIF(TRIM(u.login_name),'') IS NOT NULL
      THEN LOWER(TRIM(u.login_name)) ELSE TRIM(u.mobile) END,
    pa.login_name=CASE WHEN NULLIF(TRIM(u.login_name),'') IS NOT NULL AND 1=(
      SELECT COUNT(*) FROM member_user u2 WHERE u2.deleted=b'0' AND u2.tenant_id<>3000
        AND LOWER(TRIM(u2.login_name))=LOWER(TRIM(u.login_name)))
      THEN LOWER(TRIM(u.login_name)) ELSE NULL END,
    pa.mobile=CASE WHEN NULLIF(TRIM(u.mobile),'') IS NOT NULL AND 1=(
      SELECT COUNT(*) FROM member_user u2 WHERE u2.deleted=b'0' AND u2.tenant_id<>3000
        AND TRIM(u2.mobile)=TRIM(u.mobile))
      THEN TRIM(u.mobile) ELSE NULL END,
    pa.updater='mall-section22',pa.update_time=NOW()
WHERE pa.deleted=b'0' AND u.tenant_id<>3000
  AND (NULLIF(TRIM(u.login_name),'') IS NOT NULL OR NULLIF(TRIM(u.mobile),'') IS NOT NULL)
  AND (NULLIF(TRIM(u.login_name),'') IS NULL OR 1=(
    SELECT COUNT(*) FROM member_user u2 WHERE u2.deleted=b'0' AND u2.tenant_id<>3000
      AND LOWER(TRIM(u2.login_name))=LOWER(TRIM(u.login_name))))
  AND (NULLIF(TRIM(u.mobile),'') IS NULL OR 1=(
    SELECT COUNT(*) FROM member_user u2 WHERE u2.deleted=b'0' AND u2.tenant_id<>3000
      AND TRIM(u2.mobile)=TRIM(u.mobile)));

SET @idx_ok = (SELECT COUNT(*)=1 AND COALESCE(MAX(NON_UNIQUE),1)=0
  AND COALESCE(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX),'')='platform_no'
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='member_platform_account' AND INDEX_NAME='uk_platform_no_global');
SET @ddl = IF(@idx_ok=0 AND (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account'
    AND INDEX_NAME='uk_platform_no_global')>0,
  'ALTER TABLE member_platform_account DROP INDEX uk_platform_no_global','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@idx_ok=0,
  'ALTER TABLE member_platform_account ADD UNIQUE KEY uk_platform_no_global (platform_no)','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 早期脚本若把多个旧会员自动合为一个平台账号，或任一登录名/手机号在非 3000 租户重复，
-- 一律解除 READY 映射；即使密码哈希相同也不据此推断为同一自然人。
DROP TEMPORARY TABLE IF EXISTS tmp_mall_s22_conflict_account;
CREATE TEMPORARY TABLE tmp_mall_s22_conflict_account ENGINE=MEMORY AS
SELECT platform_account_id
FROM member_platform_account_link
WHERE deleted=b'0' AND link_status='READY' AND source_tenant_id<>3000
  AND platform_account_id IS NOT NULL
GROUP BY platform_account_id
HAVING COUNT(*)<>1 AND NOT (
  COUNT(*)>1
  AND SUM(CASE WHEN link_origin='MANUAL_IDENTITY_REVIEW'
                 AND NULLIF(TRIM(identity_review_no),'') IS NOT NULL
                 AND NULLIF(TRIM(identity_verified_by),'') IS NOT NULL
                 AND identity_verified_at IS NOT NULL
               THEN 0 ELSE 1 END)=0
  AND COUNT(DISTINCT identity_review_no)=1
);

UPDATE member_platform_account_link l
JOIN tmp_mall_s22_conflict_account conflict_account
  ON conflict_account.platform_account_id=l.platform_account_id
SET l.platform_account_id=NULL,l.link_status='IDENTITY_CONFLICT',
    l.conflict_reason='旧平台账号关联多个租户会员，禁止自动合并',
    l.link_origin='LEGACY_UNVERIFIED',l.identity_review_no=NULL,
    l.identity_verified_by=NULL,l.identity_verified_at=NULL,
    l.updater='mall-section22',l.update_time=NOW()
WHERE l.deleted=b'0' AND l.link_status='READY';

DROP TEMPORARY TABLE tmp_mall_s22_conflict_account;

UPDATE member_platform_account_link l
LEFT JOIN member_user u ON u.id=l.legacy_member_id AND u.tenant_id=l.source_tenant_id
SET l.platform_account_id=NULL,l.link_status='CREDENTIAL_CONFLICT',
    l.conflict_reason='登录名或手机号存在跨账号冲突，必须人工核验',
    l.link_origin='LEGACY_UNVERIFIED',l.identity_review_no=NULL,
    l.identity_verified_by=NULL,l.identity_verified_at=NULL,
    l.updater='mall-section22',l.update_time=NOW()
WHERE l.deleted=b'0' AND l.link_status='READY' AND (
    u.id IS NULL OR u.deleted=b'1' OR u.status<>0 OR u.tenant_id=3000
    OR (NULLIF(TRIM(u.login_name),'') IS NULL AND NULLIF(TRIM(u.mobile),'') IS NULL)
    OR (NULLIF(TRIM(u.login_name),'') IS NOT NULL AND 1<>(
      SELECT COUNT(*) FROM member_user u2 WHERE u2.deleted=b'0' AND u2.tenant_id<>3000
        AND LOWER(TRIM(u2.login_name))=LOWER(TRIM(u.login_name))))
    OR (NULLIF(TRIM(u.mobile),'') IS NOT NULL AND 1<>(
      SELECT COUNT(*) FROM member_user u2 WHERE u2.deleted=b'0' AND u2.tenant_id<>3000
        AND TRIM(u2.mobile)=TRIM(u.mobile)))
  );

-- 仅剩一条 READY 映射的平台账号不是“账号合并”，可标记为唯一迁移来源；
-- 自助注册和已有人工审核记录不被脚本重跑覆盖。
UPDATE member_platform_account_link l
JOIN (
  SELECT platform_account_id
  FROM member_platform_account_link
  WHERE deleted=b'0' AND link_status='READY' AND source_tenant_id<>3000
    AND platform_account_id IS NOT NULL
  GROUP BY platform_account_id HAVING COUNT(*)=1
) single_link ON single_link.platform_account_id=l.platform_account_id
SET l.link_origin='MIGRATION_UNIQUE',l.identity_review_no=NULL,
    l.identity_verified_by='mall-section22',l.identity_verified_at=COALESCE(l.identity_verified_at,NOW()),
    l.updater='mall-section22',l.update_time=NOW()
WHERE l.deleted=b'0' AND l.link_status='READY'
  AND l.link_origin NOT IN ('SELF_REGISTRATION','MANUAL_IDENTITY_REVIEW');

-- 先隔离无安全 READY 映射的旧账号，并清空其别名，再建立真正的全局唯一索引。
UPDATE member_platform_account pa
SET pa.credential_type='RETIRED',pa.credential_identifier=CONCAT('retired:',pa.id),
    pa.login_name=NULL,pa.mobile=NULL,pa.status=1,pa.deleted=b'1',
    pa.updater='mall-section22',pa.update_time=NOW()
WHERE pa.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM member_platform_account_link l
  WHERE l.platform_account_id=pa.id AND l.link_status='READY'
    AND l.source_tenant_id<>3000 AND l.deleted=b'0');

-- 旧表可能已有重复凭据，必须在冲突链接隔离、失效账号改为唯一退役标识之后再建索引。
-- 否则从早期第 22 节表升级时会在 ALTER TABLE 阶段直接 Duplicate entry 中止。
SET @idx_ok = (SELECT COUNT(*)=3 AND COALESCE(MAX(NON_UNIQUE),1)=0
  AND COALESCE(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX),'')='credential_type,credential_identifier,deleted'
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='member_platform_account' AND INDEX_NAME='uk_platform_credential');
SET @ddl = IF(@idx_ok=0 AND (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account'
    AND INDEX_NAME='uk_platform_credential')>0,
  'ALTER TABLE member_platform_account DROP INDEX uk_platform_credential','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@idx_ok=0,
  'ALTER TABLE member_platform_account ADD UNIQUE KEY uk_platform_credential (credential_type,credential_identifier,deleted)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_ok = (SELECT COUNT(*)=1 AND COALESCE(MAX(NON_UNIQUE),1)=0
  AND COALESCE(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX),'')='login_name'
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='member_platform_account' AND INDEX_NAME='uk_platform_login_name_global');
SET @ddl = IF(@idx_ok=0 AND (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account'
    AND INDEX_NAME='uk_platform_login_name_global')>0,
  'ALTER TABLE member_platform_account DROP INDEX uk_platform_login_name_global','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@idx_ok=0,
  'ALTER TABLE member_platform_account ADD UNIQUE KEY uk_platform_login_name_global (login_name)','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_ok = (SELECT COUNT(*)=1 AND COALESCE(MAX(NON_UNIQUE),1)=0
  AND COALESCE(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX),'')='mobile'
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='member_platform_account' AND INDEX_NAME='uk_platform_mobile_global');
SET @ddl = IF(@idx_ok=0 AND (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_platform_account'
    AND INDEX_NAME='uk_platform_mobile_global')>0,
  'ALTER TABLE member_platform_account DROP INDEX uk_platform_mobile_global','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@idx_ok=0,
  'ALTER TABLE member_platform_account ADD UNIQUE KEY uk_platform_mobile_global (mobile)','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量会员仅在登录名、手机号各自都没有冲突时，一人一号创建平台账号。
-- INSERT IGNORE 用于脚本重跑；若遇到任何既有唯一键矛盾，不抢占、不重指主账号，随后进入冲突映射。
INSERT IGNORE INTO member_platform_account
 (platform_no,credential_type,credential_identifier,login_name,mobile,
  canonical_member_id,status,creator,updater,deleted)
SELECT CONCAT('PM',LPAD(u.id,18,'0')),
       CASE WHEN NULLIF(TRIM(u.login_name),'') IS NOT NULL THEN 'LOGIN_NAME' ELSE 'MOBILE' END,
       CASE WHEN NULLIF(TRIM(u.login_name),'') IS NOT NULL
         THEN LOWER(TRIM(u.login_name)) ELSE TRIM(u.mobile) END,
       NULLIF(LOWER(TRIM(u.login_name)),''),NULLIF(TRIM(u.mobile),''),
       u.id,0,'mall-section22','mall-section22',b'0'
FROM member_user u
WHERE u.deleted=b'0' AND u.status=0 AND u.tenant_id<>3000
  AND (NULLIF(TRIM(u.login_name),'') IS NOT NULL OR NULLIF(TRIM(u.mobile),'') IS NOT NULL)
  AND NOT EXISTS (
    SELECT 1 FROM member_platform_account_link old_link
    WHERE old_link.legacy_member_id=u.id AND old_link.source_tenant_id=u.tenant_id
      AND old_link.deleted=b'0'
      AND old_link.link_status IN ('CREDENTIAL_CONFLICT','IDENTITY_CONFLICT'))
  AND (NULLIF(TRIM(u.login_name),'') IS NULL OR 1=(
    SELECT COUNT(*) FROM member_user u2 WHERE u2.deleted=b'0' AND u2.tenant_id<>3000
      AND LOWER(TRIM(u2.login_name))=LOWER(TRIM(u.login_name))))
  AND (NULLIF(TRIM(u.mobile),'') IS NULL OR 1=(
    SELECT COUNT(*) FROM member_user u2 WHERE u2.deleted=b'0' AND u2.tenant_id<>3000
      AND TRIM(u2.mobile)=TRIM(u.mobile)));

INSERT INTO member_platform_account_link
 (platform_account_id,legacy_member_id,source_tenant_id,link_status,conflict_reason,
  link_origin,identity_review_no,identity_verified_by,identity_verified_at,
  creator,updater,deleted)
SELECT pa.id,u.id,u.tenant_id,
       CASE WHEN pa.id IS NULL THEN 'CREDENTIAL_CONFLICT' ELSE 'READY' END,
       CASE WHEN pa.id IS NULL THEN '登录名或手机号冲突，或既有平台账号唯一键不一致，必须人工核验' ELSE NULL END,
       CASE WHEN pa.id IS NULL THEN 'LEGACY_UNVERIFIED' ELSE 'MIGRATION_UNIQUE' END,
       NULL,CASE WHEN pa.id IS NULL THEN NULL ELSE 'mall-section22' END,
       CASE WHEN pa.id IS NULL THEN NULL ELSE NOW() END,
       'mall-section22','mall-section22',b'0'
FROM member_user u
LEFT JOIN member_platform_account pa ON pa.canonical_member_id=u.id
  AND pa.login_name<=>NULLIF(LOWER(TRIM(u.login_name)),'')
  AND pa.mobile<=>NULLIF(TRIM(u.mobile),'')
  AND pa.status=0 AND pa.deleted=b'0'
WHERE u.deleted=b'0' AND u.status=0 AND u.tenant_id<>3000
  AND (NULLIF(TRIM(u.login_name),'') IS NOT NULL OR NULLIF(TRIM(u.mobile),'') IS NOT NULL)
  AND NOT EXISTS (
    SELECT 1 FROM member_platform_account_link ready_link
    WHERE ready_link.legacy_member_id=u.id AND ready_link.source_tenant_id=u.tenant_id
      AND ready_link.deleted=b'0' AND ready_link.link_status='READY'
      AND ready_link.platform_account_id IS NOT NULL)
ON DUPLICATE KEY UPDATE platform_account_id=VALUES(platform_account_id),
 link_status=VALUES(link_status),conflict_reason=VALUES(conflict_reason),
 link_origin=VALUES(link_origin),identity_review_no=VALUES(identity_review_no),
 identity_verified_by=VALUES(identity_verified_by),identity_verified_at=VALUES(identity_verified_at),
 updater='mall-section22',update_time=NOW(),deleted=b'0';

-- 早期脚本可能为公共租户建过映射：明确隔离，并把兼容认证主会员重指向非 3000 READY 映射。
UPDATE member_platform_account_link
SET platform_account_id=NULL,link_status='IDENTITY_CONFLICT',
    conflict_reason='原公共租户账号不允许作为平台统一身份',
    link_origin='LEGACY_UNVERIFIED',identity_review_no=NULL,
    identity_verified_by=NULL,identity_verified_at=NULL,
    updater='mall-section22',update_time=NOW()
WHERE source_tenant_id=3000 AND deleted=b'0';

UPDATE member_platform_account pa
JOIN (
  SELECT l.platform_account_id,MIN(l.legacy_member_id) canonical_member_id
  FROM member_platform_account_link l
  WHERE l.deleted=b'0' AND l.link_status='READY' AND l.source_tenant_id<>3000
    AND l.platform_account_id IS NOT NULL
  GROUP BY l.platform_account_id
) ready ON ready.platform_account_id=pa.id
SET pa.canonical_member_id=ready.canonical_member_id,pa.status=0,
    pa.updater='mall-section22',pa.update_time=NOW()
WHERE pa.deleted=b'0';

UPDATE member_platform_account pa
SET pa.status=1,pa.deleted=b'1',pa.updater='mall-section22',pa.update_time=NOW()
WHERE pa.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM member_platform_account_link l
  WHERE l.platform_account_id=pa.id AND l.link_status='READY'
    AND l.source_tenant_id<>3000 AND l.deleted=b'0');

INSERT INTO member_platform_tenant_membership
 (platform_account_id,tenant_id,enterprise_id,member_type,status,creator,updater,deleted)
SELECT DISTINCT l.platform_account_id,a.tenant_id,a.enterprise_id,
       CASE WHEN a.identity='buyer' THEN 'BUYER' ELSE 'ENTERPRISE_CONTACT' END,
       0,'mall-section22','mall-section22',b'0'
FROM member_platform_account_link l
JOIN product_enterprise_application a ON a.member_id=l.legacy_member_id
  AND a.tenant_id=l.source_tenant_id AND a.deleted=b'0' AND a.status=1
WHERE l.deleted=b'0' AND l.link_status='READY' AND l.platform_account_id IS NOT NULL
  AND a.tenant_id<>3000
ON DUPLICATE KEY UPDATE status=0,updater='mall-section22',update_time=NOW(),deleted=b'0';

-- ---------------------------------------------------------------------------
-- 3. 平台购物车、结算单、子订单关联和资金分摊
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trade_platform_cart (
  id bigint NOT NULL AUTO_INCREMENT,
  platform_member_id bigint NOT NULL,
  tenant_id bigint NOT NULL,
  enterprise_id bigint NOT NULL,
  shop_id bigint NOT NULL,
  spu_id bigint NOT NULL,
  sku_id bigint NOT NULL,
  quantity int NOT NULL,
  selected bit(1) NOT NULL DEFAULT b'1',
  invalid_reason varchar(255) NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_platform_cart_item (platform_member_id,tenant_id,shop_id,sku_id,deleted),
  KEY idx_platform_cart_selected (platform_member_id,selected,update_time),
  KEY idx_platform_cart_owner (tenant_id,enterprise_id,shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城平台跨租户购物车';

CREATE TABLE IF NOT EXISTS trade_platform_checkout (
  id bigint NOT NULL AUTO_INCREMENT,
  checkout_no varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  idempotency_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_digest char(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '规范结算请求 SHA-256',
  platform_member_id bigint NOT NULL,
  address_id bigint NULL COMMENT '旧会员地址编号，仅用于来源审计',
  address_source_tenant_id bigint NULL COMMENT '旧地址所属源租户',
  receiver_name varchar(64) NULL,
  receiver_mobile varchar(32) NULL,
  receiver_area_id bigint NULL,
  receiver_detail_address varchar(512) NULL,
  receiver_longitude decimal(10,6) NULL,
  receiver_latitude decimal(9,6) NULL,
  receiver_coord_system varchar(16) NULL,
  remark varchar(200) NULL COMMENT '结算请求备注快照',
  currency char(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY',
  goods_amount bigint NOT NULL DEFAULT 0,
  freight_amount bigint NOT NULL DEFAULT 0,
  discount_amount bigint NOT NULL DEFAULT 0,
  pay_amount bigint NOT NULL DEFAULT 0,
  paid_amount bigint NOT NULL DEFAULT 0,
  refunded_amount bigint NOT NULL DEFAULT 0,
  pay_status varchar(24) NOT NULL DEFAULT 'UNPAID',
  aggregate_status varchar(32) NOT NULL DEFAULT 'CREATING',
  failure_reason varchar(512) NULL,
  platform_pay_order_id bigint NULL,
  platform_pay_order_no varchar(64) NULL,
  version bigint NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkout_no (checkout_no,deleted),
  UNIQUE KEY uk_checkout_idempotency (platform_member_id,idempotency_key,deleted),
  KEY idx_checkout_member_time (platform_member_id,create_time),
  KEY idx_checkout_status (aggregate_status,pay_status,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城平台结算单';

CREATE TABLE IF NOT EXISTS trade_platform_checkout_order (
  id bigint NOT NULL AUTO_INCREMENT,
  checkout_id bigint NOT NULL,
  checkout_no varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_id bigint NOT NULL,
  order_no varchar(32) NOT NULL,
  tenant_id bigint NOT NULL,
  buyer_member_id bigint NULL,
  buyer_enterprise_id bigint NULL,
  seller_enterprise_id bigint NOT NULL,
  shop_id bigint NOT NULL,
  goods_amount bigint NOT NULL DEFAULT 0,
  freight_amount bigint NOT NULL DEFAULT 0,
  discount_amount bigint NOT NULL DEFAULT 0,
  pay_amount bigint NOT NULL DEFAULT 0,
  refunded_amount bigint NOT NULL DEFAULT 0,
  order_status int NOT NULL,
  after_sale_status int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkout_order (checkout_id,order_id,deleted),
  UNIQUE KEY uk_checkout_order_no (order_no,deleted),
  KEY idx_checkout_owner (checkout_id,tenant_id,shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台结算单租户店铺子订单';

CREATE TABLE IF NOT EXISTS trade_platform_checkout_plan (
  id bigint NOT NULL AUTO_INCREMENT,
  checkout_id bigint NOT NULL,
  checkout_no varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  plan_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  tenant_id bigint NOT NULL,
  seller_enterprise_id bigint NOT NULL,
  shop_id bigint NOT NULL,
  tenant_name varchar(255) NULL,
  enterprise_name varchar(255) NULL,
  shop_name varchar(255) NULL,
  item_count int NOT NULL,
  total_quantity int NOT NULL,
  goods_amount bigint NOT NULL,
    freight_amount bigint NOT NULL DEFAULT 0 COMMENT '服务端按真实租户运费模板核定，仍不可支付',
  discount_amount bigint NOT NULL DEFAULT 0 COMMENT '规划阶段尚未核定，固定为 0',
    pay_amount bigint NOT NULL COMMENT '商品金额加已核定运费的规划值，不可支付',
  plan_status varchar(24) NOT NULL DEFAULT 'DRAFT_ONLY',
  block_reason varchar(512) NOT NULL,
  version bigint NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkout_plan_no (plan_no,deleted),
  UNIQUE KEY uk_checkout_plan_owner (checkout_id,tenant_id,shop_id,deleted),
  KEY idx_checkout_plan_checkout (checkout_id,checkout_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台结算不可支付拆单规划快照';

CREATE TABLE IF NOT EXISTS trade_platform_checkout_plan_item (
  id bigint NOT NULL AUTO_INCREMENT,
  checkout_id bigint NOT NULL,
  checkout_no varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  plan_id bigint NOT NULL,
  plan_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  cart_item_id bigint NOT NULL,
  tenant_id bigint NOT NULL,
  enterprise_id bigint NOT NULL,
  shop_id bigint NOT NULL,
  spu_id bigint NOT NULL,
  sku_id bigint NOT NULL,
  spu_name varchar(255) NULL,
  properties_json text NOT NULL,
  pic_url varchar(1024) NULL,
  quantity int NOT NULL,
  unit_price int NOT NULL COMMENT '单位：分',
  goods_amount bigint NOT NULL COMMENT '单位：分',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkout_plan_cart (checkout_id,cart_item_id,deleted),
  KEY idx_checkout_plan_item (plan_id,id),
  KEY idx_checkout_plan_item_owner (tenant_id,shop_id,sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台结算拆单商品价格及归属快照';

CREATE TABLE IF NOT EXISTS trade_platform_checkout_formalization (
  id bigint NOT NULL AUTO_INCREMENT,
  checkout_id bigint NOT NULL,
  checkout_no varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  platform_member_id bigint NOT NULL,
  idempotency_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  snapshot_digest char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端正式化输入快照 SHA-256',
  attempt_status varchar(24) NOT NULL DEFAULT 'BLOCKED' COMMENT '只记录准备状态，不是订单状态',
  blocker_codes varchar(1024) NOT NULL,
  blocker_reason varchar(2048) NOT NULL,
  version bigint NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkout_formalization_key (checkout_id,idempotency_key,deleted),
  KEY idx_checkout_formalization_status (checkout_id,attempt_status,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='平台结算正式化准备与阻断审计（非订单/支付事实）';

-- 已部署过旧版第 22 节脚本的环境必须原地升级；每个 ALTER 均可重复执行。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='request_digest')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''规范结算请求 SHA-256'' AFTER idempotency_key',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='address_id')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN address_id BIGINT NULL COMMENT ''旧会员地址编号，仅用于来源审计'' AFTER platform_member_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='address_source_tenant_id')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN address_source_tenant_id BIGINT NULL COMMENT ''旧地址所属源租户'' AFTER address_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='receiver_name')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN receiver_name VARCHAR(64) NULL AFTER address_source_tenant_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='receiver_mobile')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN receiver_mobile VARCHAR(32) NULL AFTER receiver_name',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='receiver_area_id')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN receiver_area_id BIGINT NULL AFTER receiver_mobile',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='receiver_detail_address')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN receiver_detail_address VARCHAR(512) NULL AFTER receiver_area_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='receiver_longitude')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN receiver_longitude DECIMAL(10,6) NULL AFTER receiver_detail_address',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='receiver_latitude')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN receiver_latitude DECIMAL(9,6) NULL AFTER receiver_longitude',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='receiver_coord_system')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN receiver_coord_system VARCHAR(16) NULL AFTER receiver_latitude',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='remark')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN remark VARCHAR(200) NULL COMMENT ''结算请求备注快照'' AFTER receiver_coord_system',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='platform_pay_order_id')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN platform_pay_order_id BIGINT NULL AFTER failure_reason',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout' AND COLUMN_NAME='platform_pay_order_no')=0,
  'ALTER TABLE trade_platform_checkout ADD COLUMN platform_pay_order_no VARCHAR(64) NULL AFTER platform_pay_order_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_platform_checkout_order' AND COLUMN_NAME='buyer_member_id')=0,
  'ALTER TABLE trade_platform_checkout_order ADD COLUMN buyer_member_id BIGINT NULL AFTER tenant_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS trade_platform_fund_allocation (
  id bigint NOT NULL AUTO_INCREMENT,
  checkout_id bigint NOT NULL,
  checkout_no varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_id bigint NOT NULL,
  tenant_id bigint NOT NULL,
  shop_id bigint NOT NULL,
  allocated_amount bigint NOT NULL,
  refunded_amount bigint NOT NULL DEFAULT 0,
  allocation_status varchar(24) NOT NULL DEFAULT 'PENDING',
  version bigint NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_allocation_order (checkout_id,order_id,deleted),
  KEY idx_allocation_owner (tenant_id,shop_id,allocation_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台结算资金分摊台账';

-- ---------------------------------------------------------------------------
-- 4. 跨租户询价定向范围与原公共租户迁移审计
-- ---------------------------------------------------------------------------
-- 报价数据继续保存在询价所属租户，显式记录供应方真实租户，避免依赖当前请求租户猜测归属。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_purchase_inquiry_quote'
    AND COLUMN_NAME='supplier_tenant_id')=0,
  'ALTER TABLE product_purchase_inquiry_quote ADD COLUMN supplier_tenant_id BIGINT NULL COMMENT ''供应企业真实租户'' AFTER enterprise_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE product_purchase_inquiry_quote
SET supplier_tenant_id=tenant_id,updater='mall-section22',update_time=NOW()
WHERE supplier_tenant_id IS NULL AND deleted=b'0';

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_purchase_inquiry_quote'
    AND INDEX_NAME='idx_supplier_owner')=0,
  'ALTER TABLE product_purchase_inquiry_quote ADD INDEX idx_supplier_owner (supplier_tenant_id,shop_id,status,id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS product_purchase_inquiry_invited_scope (
  id bigint NOT NULL AUTO_INCREMENT,
  inquiry_id bigint NOT NULL,
  target_type varchar(16) NOT NULL COMMENT 'TENANT/ENTERPRISE/SHOP',
  target_tenant_id bigint NOT NULL,
  target_enterprise_id bigint NULL,
  target_shop_id bigint NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inquiry_scope
    (inquiry_id,target_type,target_tenant_id,target_enterprise_id,target_shop_id,deleted),
  KEY idx_scope_target (target_tenant_id,target_enterprise_id,target_shop_id,inquiry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨租户询价定向邀请范围';

CREATE TABLE IF NOT EXISTS mall_public_tenant_migration (
  id bigint NOT NULL AUTO_INCREMENT,
  batch_no varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  resource_type varchar(32) NOT NULL,
  source_tenant_id bigint NOT NULL,
  source_resource_id bigint NOT NULL,
  target_tenant_id bigint NULL,
  target_resource_id bigint NULL,
  migration_status varchar(24) NOT NULL COMMENT 'PENDING/MIGRATED/QUARANTINED/FAILED',
  failure_reason varchar(512) NULL,
  verified_by bigint NULL,
  verified_at datetime NULL,
  rollback_payload json NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_public_migration
    (batch_no,resource_type,source_tenant_id,source_resource_id,deleted),
  KEY idx_public_migration_status (migration_status,resource_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原公共租户业务迁移审计';

INSERT INTO mall_public_tenant_migration
 (batch_no,resource_type,source_tenant_id,source_resource_id,migration_status,failure_reason,
  creator,updater,deleted)
SELECT 'S22-3000-20260921','ENTERPRISE',3000,e.id,'QUARANTINED',
       '原公共租户经营数据待确认真实目标租户，停止公开与新增交易',
       'mall-section22','mall-section22',b'0'
FROM product_enterprise e
WHERE e.tenant_id=3000 AND e.deleted=b'0'
ON DUPLICATE KEY UPDATE migration_status=IF(target_tenant_id IS NULL,'QUARANTINED',migration_status),
 failure_reason=IF(target_tenant_id IS NULL,VALUES(failure_reason),failure_reason),
 updater='mall-section22',update_time=NOW(),deleted=b'0';

-- 验证摘要。3000 出现在前三个结果集中均视为部署失败。
SELECT tenant_id,tenant_slug,tenant_name,open_status
FROM mall_portal_tenant WHERE deleted=b'0' ORDER BY sort,tenant_id;
SELECT COUNT(*) AS public_tenant_open_count
FROM mall_portal_tenant WHERE tenant_id=3000 AND open_status=0 AND deleted=b'0';
SELECT statistic_date,COUNT(*) tenant_count,SUM(valid_quantity) valid_quantity,SUM(paid_amount) paid_amount
FROM mall_tenant_sales_daily WHERE deleted=b'0'
GROUP BY statistic_date ORDER BY statistic_date DESC;
SELECT link_status,COUNT(*) account_count
FROM member_platform_account_link WHERE deleted=b'0' GROUP BY link_status;
SELECT platform_account_id,COUNT(*) AS ready_link_count,
       COUNT(DISTINCT identity_review_no) AS review_no_count
FROM member_platform_account_link
WHERE deleted=b'0' AND link_status='READY' AND source_tenant_id<>3000
GROUP BY platform_account_id
HAVING COUNT(*)>1 AND (
  SUM(CASE WHEN link_origin='MANUAL_IDENTITY_REVIEW'
             AND NULLIF(TRIM(identity_review_no),'') IS NOT NULL
             AND NULLIF(TRIM(identity_verified_by),'') IS NOT NULL
             AND identity_verified_at IS NOT NULL
           THEN 0 ELSE 1 END)<>0
  OR COUNT(DISTINCT identity_review_no)<>1
);
SELECT COUNT(*) AS invalid_active_platform_account_count
FROM member_platform_account pa
WHERE pa.deleted=b'0' AND (pa.status<>0 OR pa.credential_type NOT IN ('LOGIN_NAME','MOBILE')
  OR NULLIF(pa.credential_identifier,'') IS NULL
  OR (pa.login_name IS NULL AND pa.mobile IS NULL)
  OR (pa.credential_type='LOGIN_NAME' AND NOT (pa.credential_identifier<=>pa.login_name))
  OR (pa.credential_type='MOBILE' AND NOT (pa.credential_identifier<=>pa.mobile)));
SELECT COUNT(*) AS public_tenant_ready_link_count
FROM member_platform_account_link l
WHERE l.deleted=b'0' AND l.source_tenant_id=3000 AND l.link_status='READY';
SELECT credential_type,credential_identifier,COUNT(*) duplicate_count
FROM member_platform_account pa
WHERE pa.deleted=b'0'
GROUP BY credential_type,credential_identifier HAVING COUNT(*)>1;
SELECT login_name,COUNT(*) duplicate_count
FROM member_platform_account pa
WHERE pa.deleted=b'0' AND pa.login_name IS NOT NULL
GROUP BY login_name HAVING COUNT(*)>1;
SELECT mobile,COUNT(*) duplicate_count
FROM member_platform_account pa
WHERE pa.deleted=b'0' AND pa.mobile IS NOT NULL
GROUP BY mobile HAVING COUNT(*)>1;
SELECT migration_status,resource_type,COUNT(*) resource_count
FROM mall_public_tenant_migration WHERE deleted=b'0'
GROUP BY migration_status,resource_type ORDER BY migration_status,resource_type;
