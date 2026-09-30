-- ============================================================
-- 商城询价成交订单来源与报价快照
--
-- 约束：
-- 1. 成交价只从商品服务的已采纳报价快照读取；
-- 2. 一个租户内同一报价永久只能生成一张商城订单；
-- 3. 询价订单同时记录采购企业和实际下单会员，企业内换人不丢历史；
-- 4. 普通下单保持 NORMAL，历史订单统一回填为 NORMAL。
--
-- 本脚本幂等，可重复执行。
-- ============================================================

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
       AND COLUMN_NAME = 'source_type') = 0,
    'ALTER TABLE trade_order ADD COLUMN source_type varchar(16) NOT NULL DEFAULT ''NORMAL'' COMMENT ''订单业务来源：NORMAL 普通下单，INQUIRY 询价成交'' AFTER type',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
       AND COLUMN_NAME = 'inquiry_id') = 0,
    'ALTER TABLE trade_order ADD COLUMN inquiry_id bigint NULL COMMENT ''询价单编号'' AFTER source_type',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
       AND COLUMN_NAME = 'inquiry_quote_id') = 0,
    'ALTER TABLE trade_order ADD COLUMN inquiry_quote_id bigint NULL COMMENT ''已采纳报价编号'' AFTER inquiry_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
       AND COLUMN_NAME = 'inquiry_quote_version') = 0,
    'ALTER TABLE trade_order ADD COLUMN inquiry_quote_version bigint NULL COMMENT ''成交时报价快照版本'' AFTER inquiry_quote_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
       AND COLUMN_NAME = 'buyer_enterprise_id') = 0,
    'ALTER TABLE trade_order ADD COLUMN buyer_enterprise_id bigint NULL COMMENT ''采购企业编号，仅询价成交订单有值；user_id 记录实际下单会员'' AFTER inquiry_quote_version',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE trade_order
SET source_type = 'NORMAL'
WHERE source_type IS NULL OR source_type = '';

ALTER TABLE trade_order
    MODIFY COLUMN source_type varchar(16) NOT NULL DEFAULT 'NORMAL'
    COMMENT '订单业务来源：NORMAL 普通下单，INQUIRY 询价成交';

-- 为已存在的询价成交订单补齐企业归属。仅按同租户询价单回填，严禁跨租户串联。
UPDATE trade_order orders
JOIN product_purchase_inquiry inquiry
  ON inquiry.tenant_id = orders.tenant_id
 AND inquiry.id = orders.inquiry_id
 AND inquiry.deleted = b'0'
SET orders.buyer_enterprise_id = inquiry.enterprise_id
WHERE orders.source_type = 'INQUIRY'
  AND orders.buyer_enterprise_id IS NULL;

SET @missing_buyer_enterprise_count = (
    SELECT COUNT(*) FROM trade_order
    WHERE source_type = 'INQUIRY' AND buyer_enterprise_id IS NULL);
SET @ddl = IF(
    @missing_buyer_enterprise_count > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''询价订单升级中止：存在无法确认采购企业的询价订单''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 询价成交是永久幂等边界：即使历史订单被逻辑删除，也不允许绕过并再生成一张。
-- 先展示可能阻断升级的历史重复数据。新永久索引必须先创建成功，随后才删除早期的
-- (tenant_id, inquiry_quote_id, deleted) 索引；这样即使 ADD UNIQUE 因脏数据失败，旧保护仍在。
SELECT tenant_id, inquiry_quote_id, COUNT(*) AS duplicate_count
FROM trade_order
WHERE inquiry_quote_id IS NOT NULL
GROUP BY tenant_id, inquiry_quote_id
HAVING COUNT(*) > 1;

SET @old_inquiry_index_signature = (
    SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index), '')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
      AND INDEX_NAME = 'uk_trade_order_inquiry_quote');
SET @old_inquiry_index_non_unique = (
    SELECT MIN(non_unique) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
      AND INDEX_NAME = 'uk_trade_order_inquiry_quote');
SET @permanent_inquiry_index_signature = (
    SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index), '')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
      AND INDEX_NAME = 'uk_trade_order_inquiry_quote_permanent');
SET @permanent_inquiry_index_non_unique = (
    SELECT MIN(non_unique) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
      AND INDEX_NAME = 'uk_trade_order_inquiry_quote_permanent');

SET @duplicate_inquiry_order_count = (
    SELECT COUNT(*) FROM (
        SELECT tenant_id, inquiry_quote_id
        FROM trade_order
        WHERE inquiry_quote_id IS NOT NULL
        GROUP BY tenant_id, inquiry_quote_id
        HAVING COUNT(*) > 1
    ) duplicate_inquiry_orders);
SET @ddl = IF(
    @duplicate_inquiry_order_count > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''询价订单升级中止：同一报价存在多张历史订单，请先完成数据核对''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = CASE
    WHEN @permanent_inquiry_index_signature = ''
         AND @old_inquiry_index_signature = 'tenant_id,inquiry_quote_id'
         AND @old_inquiry_index_non_unique = 0 THEN
      'ALTER TABLE trade_order RENAME INDEX uk_trade_order_inquiry_quote TO uk_trade_order_inquiry_quote_permanent'
    WHEN @permanent_inquiry_index_signature = '' THEN
      'ALTER TABLE trade_order ADD UNIQUE KEY uk_trade_order_inquiry_quote_permanent (tenant_id, inquiry_quote_id)'
    WHEN @permanent_inquiry_index_signature <> 'tenant_id,inquiry_quote_id'
         OR @permanent_inquiry_index_non_unique <> 0 THEN
      'ALTER TABLE trade_order DROP INDEX uk_trade_order_inquiry_quote_permanent, ADD UNIQUE KEY uk_trade_order_inquiry_quote_permanent (tenant_id, inquiry_quote_id)'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    @old_inquiry_index_signature <> ''
      AND (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
             AND INDEX_NAME = 'uk_trade_order_inquiry_quote_permanent') > 0,
    'ALTER TABLE trade_order DROP INDEX uk_trade_order_inquiry_quote',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 校验
SELECT column_name, column_type, is_nullable, column_default, column_comment
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
  AND column_name IN ('source_type', 'inquiry_id', 'inquiry_quote_id', 'inquiry_quote_version',
                      'buyer_enterprise_id')
ORDER BY ordinal_position;

SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
  AND index_name IN ('uk_trade_order_inquiry_quote', 'uk_trade_order_inquiry_quote_permanent')
ORDER BY seq_in_index;
