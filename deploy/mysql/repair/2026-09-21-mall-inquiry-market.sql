-- 采购询价市场：公开询价、企业店铺报价、报价会话及单一采纳结果。
-- 报价金额全部使用“分”；freight_price 仅为预计运费，成交结算仍须由交易模块重算运费。

SET @inquiry_selected_quote_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry' AND column_name = 'selected_quote_id') = 0,
  'ALTER TABLE product_purchase_inquiry ADD COLUMN selected_quote_id bigint NULL COMMENT ''已采纳报价编号'' AFTER status',
  'SELECT 1');
PREPARE inquiry_selected_quote_stmt FROM @inquiry_selected_quote_ddl;
EXECUTE inquiry_selected_quote_stmt;
DEALLOCATE PREPARE inquiry_selected_quote_stmt;

SET @inquiry_market_index_signature = (
  SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index), '')
  FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry' AND index_name = 'idx_market_status_deadline');
SET @inquiry_market_index_non_unique = (
  SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry' AND index_name = 'idx_market_status_deadline');
SET @inquiry_market_index_ddl = CASE
  WHEN @inquiry_market_index_signature = '' THEN
    'ALTER TABLE product_purchase_inquiry ADD INDEX idx_market_status_deadline (tenant_id, status, deadline, id)'
  WHEN @inquiry_market_index_signature <> 'tenant_id,status,deadline,id'
       OR @inquiry_market_index_non_unique <> 1 THEN
    'ALTER TABLE product_purchase_inquiry DROP INDEX idx_market_status_deadline, ADD INDEX idx_market_status_deadline (tenant_id, status, deadline, id)'
  ELSE 'SELECT 1'
END;
PREPARE inquiry_market_index_stmt FROM @inquiry_market_index_ddl;
EXECUTE inquiry_market_index_stmt;
DEALLOCATE PREPARE inquiry_market_index_stmt;

CREATE TABLE IF NOT EXISTS product_purchase_inquiry_quote (
  id bigint NOT NULL AUTO_INCREMENT,
  inquiry_id bigint NOT NULL,
  shop_id bigint NOT NULL,
  enterprise_id bigint NOT NULL,
  request_key varchar(64) NOT NULL COMMENT '最近一次保存的幂等键',
  status int NOT NULL DEFAULT 0 COMMENT '0草稿 1已提交 2已采纳 3未采纳 4已撤回',
  version bigint NOT NULL DEFAULT 1 COMMENT '报价快照版本',
  goods_price bigint NOT NULL DEFAULT 0 COMMENT '商品行合计，单位分',
  freight_price int NOT NULL DEFAULT 0 COMMENT '预计运费，单位分；结算时重算',
  total_price bigint NOT NULL DEFAULT 0 COMMENT '商品行合计加预计运费，单位分',
  delivery_date bigint NOT NULL COMMENT '预计送达日期，毫秒时间戳',
  valid_until bigint NOT NULL COMMENT '报价有效期，毫秒时间戳',
  remark varchar(1000) NULL,
  submitted_at datetime NULL,
  withdrawn_at datetime NULL,
  accepted_at datetime NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  accepted_inquiry_id bigint GENERATED ALWAYS AS (
    CASE WHEN status = 2 AND deleted = b'0' THEN inquiry_id ELSE NULL END
  ) STORED COMMENT '数据库单一胜出约束辅助列',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inquiry_shop (tenant_id, inquiry_id, shop_id, deleted),
  UNIQUE KEY uk_shop_request (tenant_id, shop_id, request_key, deleted),
  UNIQUE KEY uk_single_accepted (tenant_id, accepted_inquiry_id),
  KEY idx_inquiry_status (tenant_id, inquiry_id, status),
  KEY idx_shop_status (tenant_id, shop_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购询价企业报价';

-- CREATE TABLE IF NOT EXISTS 不会升级早期已存在的表；补齐询价+店铺、
-- 店铺保存幂等键以及单一采纳的数据库最终防线。若存量数据重复，
-- ADD UNIQUE 会明确中止升级，不会在缺少约束的状态下继续启动。
SET @quote_freight_price_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND column_name = 'freight_price') = 0,
  'ALTER TABLE product_purchase_inquiry_quote ADD COLUMN freight_price int NOT NULL DEFAULT 0 COMMENT ''预计运费，单位分；结算时重算'' AFTER goods_price',
  'SELECT 1');
PREPARE quote_freight_price_stmt FROM @quote_freight_price_ddl;
EXECUTE quote_freight_price_stmt;
DEALLOCATE PREPARE quote_freight_price_stmt;

SET @quote_delivery_date_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND column_name = 'delivery_date') = 0,
  'ALTER TABLE product_purchase_inquiry_quote ADD COLUMN delivery_date bigint NOT NULL DEFAULT 0 COMMENT ''预计送达日期，毫秒时间戳'' AFTER total_price',
  'SELECT 1');
PREPARE quote_delivery_date_stmt FROM @quote_delivery_date_ddl;
EXECUTE quote_delivery_date_stmt;
DEALLOCATE PREPARE quote_delivery_date_stmt;

SET @quote_valid_until_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND column_name = 'valid_until') = 0,
  'ALTER TABLE product_purchase_inquiry_quote ADD COLUMN valid_until bigint NOT NULL DEFAULT 0 COMMENT ''报价有效期，毫秒时间戳'' AFTER delivery_date',
  'SELECT 1');
PREPARE quote_valid_until_stmt FROM @quote_valid_until_ddl;
EXECUTE quote_valid_until_stmt;
DEALLOCATE PREPARE quote_valid_until_stmt;

SET @quote_accepted_column_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND column_name = 'accepted_inquiry_id') = 0,
  'ALTER TABLE product_purchase_inquiry_quote ADD COLUMN accepted_inquiry_id bigint GENERATED ALWAYS AS (CASE WHEN status = 2 AND deleted = b''0'' THEN inquiry_id ELSE NULL END) STORED COMMENT ''数据库单一胜出约束辅助列''',
  'SELECT 1');
PREPARE quote_accepted_column_stmt FROM @quote_accepted_column_ddl;
EXECUTE quote_accepted_column_stmt;
DEALLOCATE PREPARE quote_accepted_column_stmt;

SET @quote_inquiry_shop_index_signature = (
  SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index), '')
  FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND index_name = 'uk_inquiry_shop');
SET @quote_inquiry_shop_index_non_unique = (
  SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND index_name = 'uk_inquiry_shop');
SET @quote_inquiry_shop_index_ddl = CASE
  WHEN @quote_inquiry_shop_index_signature = '' THEN
    'ALTER TABLE product_purchase_inquiry_quote ADD UNIQUE KEY uk_inquiry_shop (tenant_id, inquiry_id, shop_id, deleted)'
  WHEN @quote_inquiry_shop_index_signature <> 'tenant_id,inquiry_id,shop_id,deleted'
       OR @quote_inquiry_shop_index_non_unique <> 0 THEN
    'ALTER TABLE product_purchase_inquiry_quote DROP INDEX uk_inquiry_shop, ADD UNIQUE KEY uk_inquiry_shop (tenant_id, inquiry_id, shop_id, deleted)'
  ELSE 'SELECT 1'
END;
PREPARE quote_inquiry_shop_index_stmt FROM @quote_inquiry_shop_index_ddl;
EXECUTE quote_inquiry_shop_index_stmt;
DEALLOCATE PREPARE quote_inquiry_shop_index_stmt;

SET @quote_request_index_signature = (
  SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index), '')
  FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND index_name = 'uk_shop_request');
SET @quote_request_index_non_unique = (
  SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND index_name = 'uk_shop_request');
SET @quote_request_index_ddl = CASE
  WHEN @quote_request_index_signature = '' THEN
    'ALTER TABLE product_purchase_inquiry_quote ADD UNIQUE KEY uk_shop_request (tenant_id, shop_id, request_key, deleted)'
  WHEN @quote_request_index_signature <> 'tenant_id,shop_id,request_key,deleted'
       OR @quote_request_index_non_unique <> 0 THEN
    'ALTER TABLE product_purchase_inquiry_quote DROP INDEX uk_shop_request, ADD UNIQUE KEY uk_shop_request (tenant_id, shop_id, request_key, deleted)'
  ELSE 'SELECT 1'
END;
PREPARE quote_request_index_stmt FROM @quote_request_index_ddl;
EXECUTE quote_request_index_stmt;
DEALLOCATE PREPARE quote_request_index_stmt;

SET @quote_single_accepted_index_signature = (
  SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index), '')
  FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND index_name = 'uk_single_accepted');
SET @quote_single_accepted_index_non_unique = (
  SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote' AND index_name = 'uk_single_accepted');
SET @quote_single_accepted_index_ddl = CASE
  WHEN @quote_single_accepted_index_signature = '' THEN
    'ALTER TABLE product_purchase_inquiry_quote ADD UNIQUE KEY uk_single_accepted (tenant_id, accepted_inquiry_id)'
  WHEN @quote_single_accepted_index_signature <> 'tenant_id,accepted_inquiry_id'
       OR @quote_single_accepted_index_non_unique <> 0 THEN
    'ALTER TABLE product_purchase_inquiry_quote DROP INDEX uk_single_accepted, ADD UNIQUE KEY uk_single_accepted (tenant_id, accepted_inquiry_id)'
  ELSE 'SELECT 1'
END;
PREPARE quote_single_accepted_index_stmt FROM @quote_single_accepted_index_ddl;
EXECUTE quote_single_accepted_index_stmt;
DEALLOCATE PREPARE quote_single_accepted_index_stmt;

CREATE TABLE IF NOT EXISTS product_purchase_inquiry_quote_item (
  id bigint NOT NULL AUTO_INCREMENT,
  quote_id bigint NOT NULL,
  inquiry_item_id bigint NOT NULL,
  sku_id bigint NOT NULL,
  product_name varchar(200) NULL COMMENT '保存报价时的SPU名称快照',
  sku_name varchar(500) NULL COMMENT '保存报价时的SKU展示名称快照',
  sku_specification varchar(500) NULL COMMENT '保存报价时的SKU规格快照',
  spu_content_version bigint NULL COMMENT '保存报价时的SPU内容版本',
  count int NOT NULL,
  unit_price int NOT NULL COMMENT '成交单价，单位分',
  total_price bigint NOT NULL COMMENT '行小计，单位分',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_quote_inquiry_item (tenant_id, quote_id, inquiry_item_id),
  KEY idx_quote (tenant_id, quote_id),
  KEY idx_sku (tenant_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购询价报价明细快照';

-- CREATE TABLE IF NOT EXISTS 不会为已存在的表补列，以下升级语句均可重复执行。
SET @quote_item_product_name_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote_item' AND column_name = 'product_name') = 0,
  'ALTER TABLE product_purchase_inquiry_quote_item ADD COLUMN product_name varchar(200) NULL COMMENT ''保存报价时的SPU名称快照'' AFTER sku_id',
  'SELECT 1');
PREPARE quote_item_product_name_stmt FROM @quote_item_product_name_ddl;
EXECUTE quote_item_product_name_stmt;
DEALLOCATE PREPARE quote_item_product_name_stmt;

SET @quote_item_sku_name_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote_item' AND column_name = 'sku_name') = 0,
  'ALTER TABLE product_purchase_inquiry_quote_item ADD COLUMN sku_name varchar(500) NULL COMMENT ''保存报价时的SKU展示名称快照'' AFTER product_name',
  'SELECT 1');
PREPARE quote_item_sku_name_stmt FROM @quote_item_sku_name_ddl;
EXECUTE quote_item_sku_name_stmt;
DEALLOCATE PREPARE quote_item_sku_name_stmt;

SET @quote_item_sku_specification_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote_item' AND column_name = 'sku_specification') = 0,
  'ALTER TABLE product_purchase_inquiry_quote_item ADD COLUMN sku_specification varchar(500) NULL COMMENT ''保存报价时的SKU规格快照'' AFTER sku_name',
  'SELECT 1');
PREPARE quote_item_sku_specification_stmt FROM @quote_item_sku_specification_ddl;
EXECUTE quote_item_sku_specification_stmt;
DEALLOCATE PREPARE quote_item_sku_specification_stmt;

SET @quote_item_spu_version_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry_quote_item' AND column_name = 'spu_content_version') = 0,
  'ALTER TABLE product_purchase_inquiry_quote_item ADD COLUMN spu_content_version bigint NULL COMMENT ''保存报价时的SPU内容版本'' AFTER sku_specification',
  'SELECT 1');
PREPARE quote_item_spu_version_stmt FROM @quote_item_spu_version_ddl;
EXECUTE quote_item_spu_version_stmt;
DEALLOCATE PREPARE quote_item_spu_version_stmt;

-- 早期脚本若已经产生已提交/已采纳报价，不能带着空商品快照继续升级，否则成交时才会失败。
-- 草稿可由供应商重新保存自动补齐；非草稿缺快照则明确阻断部署，人工撤回并重新保存后再执行。
SELECT q.id AS quote_id, q.status, COUNT(qi.id) AS item_count,
       SUM(CASE WHEN qi.id IS NULL OR qi.product_name IS NULL OR qi.sku_name IS NULL
                    OR qi.sku_specification IS NULL OR qi.spu_content_version IS NULL
                THEN 1 ELSE 0 END) AS missing_snapshot_count
FROM product_purchase_inquiry_quote q
LEFT JOIN product_purchase_inquiry_quote_item qi
  ON qi.quote_id = q.id AND qi.tenant_id = q.tenant_id AND qi.deleted = b'0'
WHERE q.deleted = b'0' AND q.status IN (1, 2)
GROUP BY q.id, q.status
HAVING item_count = 0 OR missing_snapshot_count > 0;

SET @missing_quote_snapshot_count = (
  SELECT COUNT(*) FROM product_purchase_inquiry_quote q
  WHERE q.deleted = b'0' AND q.status IN (1, 2)
    AND (NOT EXISTS (
          SELECT 1 FROM product_purchase_inquiry_quote_item qi
          WHERE qi.quote_id = q.id AND qi.tenant_id = q.tenant_id AND qi.deleted = b'0'
        ) OR EXISTS (
          SELECT 1 FROM product_purchase_inquiry_quote_item qi
          WHERE qi.quote_id = q.id AND qi.tenant_id = q.tenant_id AND qi.deleted = b'0'
            AND (qi.product_name IS NULL OR qi.sku_name IS NULL OR qi.sku_specification IS NULL
                 OR qi.spu_content_version IS NULL)
        )));
SET @quote_snapshot_guard_ddl = IF(
  @missing_quote_snapshot_count > 0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''询价市场升级中止：存在缺少商品快照的已提交或已采纳报价，请先撤回并重新保存''',
  'SELECT 1');
PREPARE quote_snapshot_guard_stmt FROM @quote_snapshot_guard_ddl;
EXECUTE quote_snapshot_guard_stmt;
DEALLOCATE PREPARE quote_snapshot_guard_stmt;

-- 早期表若没有送达日期/有效期，会补为 0。已提交或已采纳报价不能带着无效日期继续升级；
-- 草稿可由供应商重新保存后补齐。
SET @missing_quote_date_count = (
  SELECT COUNT(*) FROM product_purchase_inquiry_quote
  WHERE deleted = b'0' AND status IN (1, 2)
    AND (delivery_date IS NULL OR delivery_date <= 0 OR valid_until IS NULL OR valid_until <= 0));
SET @quote_date_guard_ddl = IF(
  @missing_quote_date_count > 0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''询价市场升级中止：存在缺少送达日期或有效期的已提交/已采纳报价，请先撤回并重新保存''',
  'SELECT 1');
PREPARE quote_date_guard_stmt FROM @quote_date_guard_ddl;
EXECUTE quote_date_guard_stmt;
DEALLOCATE PREPARE quote_date_guard_stmt;

CREATE TABLE IF NOT EXISTS product_purchase_inquiry_quote_message (
  id bigint NOT NULL AUTO_INCREMENT,
  quote_id bigint NOT NULL,
  sender_member_id bigint NOT NULL,
  sender_role varchar(16) NOT NULL COMMENT 'BUYER或SUPPLIER',
  client_message_id varchar(64) NOT NULL COMMENT '客户端消息幂等键',
  content varchar(2000) NOT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sender_message (tenant_id, quote_id, sender_member_id, client_message_id, deleted),
  KEY idx_quote_message (tenant_id, quote_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购询价报价专属沟通消息';

-- 部署日志校验：应返回 4 张表和询价市场新增字段。
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('product_purchase_inquiry', 'product_purchase_inquiry_quote',
                     'product_purchase_inquiry_quote_item', 'product_purchase_inquiry_quote_message')
ORDER BY table_name;

SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'product_purchase_inquiry' AND column_name = 'selected_quote_id')
    OR (table_name = 'product_purchase_inquiry_quote'
      AND column_name IN ('freight_price', 'delivery_date', 'valid_until'))
    OR (table_name = 'product_purchase_inquiry_quote_item'
      AND column_name IN ('product_name', 'sku_name', 'sku_specification', 'spu_content_version')))
ORDER BY table_name, column_name;

SELECT table_name, index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND ((table_name = 'product_purchase_inquiry' AND index_name = 'idx_market_status_deadline')
    OR (table_name = 'product_purchase_inquiry_quote'
      AND index_name IN ('uk_inquiry_shop', 'uk_shop_request', 'uk_single_accepted')))
ORDER BY table_name, index_name, seq_in_index;
