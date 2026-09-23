-- 第 22.5 节：平台公开询价的结构化交付地区。
--
-- 只新增地区编号快照，不从历史自由文本 address 猜测地区。历史平台公开询价
-- 在补录区县前按失败关闭处理：同租户仍可按原规则查看，跨租户不参与匹配。

SET @inquiry_delivery_area_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_purchase_inquiry'
      AND column_name = 'delivery_area_id') = 0,
  'ALTER TABLE product_purchase_inquiry ADD COLUMN delivery_area_id INT NULL COMMENT ''交付区县编号'' AFTER contact_mobile',
  'SELECT 1');
PREPARE inquiry_delivery_area_stmt FROM @inquiry_delivery_area_ddl;
EXECUTE inquiry_delivery_area_stmt;
DEALLOCATE PREPARE inquiry_delivery_area_stmt;

SET @inquiry_delivery_city_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_purchase_inquiry'
      AND column_name = 'delivery_city_id') = 0,
  'ALTER TABLE product_purchase_inquiry ADD COLUMN delivery_city_id INT NULL COMMENT ''交付城市编号快照'' AFTER delivery_area_id',
  'SELECT 1');
PREPARE inquiry_delivery_city_stmt FROM @inquiry_delivery_city_ddl;
EXECUTE inquiry_delivery_city_stmt;
DEALLOCATE PREPARE inquiry_delivery_city_stmt;

SET @inquiry_delivery_province_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_purchase_inquiry'
      AND column_name = 'delivery_province_id') = 0,
  'ALTER TABLE product_purchase_inquiry ADD COLUMN delivery_province_id INT NULL COMMENT ''交付省份编号快照'' AFTER delivery_city_id',
  'SELECT 1');
PREPARE inquiry_delivery_province_stmt FROM @inquiry_delivery_province_ddl;
EXECUTE inquiry_delivery_province_stmt;
DEALLOCATE PREPARE inquiry_delivery_province_stmt;

-- 已有同名列不能静默复用不兼容定义，否则 Java Integer 映射和跨租户配送判断
-- 会在上线后产生不可预期结果。这里仅校验结构，不修改任何历史询价内容。
SET @inquiry_delivery_column_mismatch = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry'
    AND column_name IN ('delivery_area_id', 'delivery_city_id', 'delivery_province_id')
    AND (data_type <> 'int' OR is_nullable <> 'YES'));
SET @inquiry_delivery_column_count = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry'
    AND column_name IN ('delivery_area_id', 'delivery_city_id', 'delivery_province_id'));
SET @inquiry_delivery_schema_guard = IF(
  @inquiry_delivery_column_count <> 3 OR @inquiry_delivery_column_mismatch <> 0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''询价地区升级中止：地区列定义不兼容，应为可空 INT，请先人工核对''',
  'SELECT 1');
PREPARE inquiry_delivery_schema_guard_stmt FROM @inquiry_delivery_schema_guard;
EXECUTE inquiry_delivery_schema_guard_stmt;
DEALLOCATE PREPARE inquiry_delivery_schema_guard_stmt;

SET @inquiry_delivery_index_signature = (
  SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index), '')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry'
    AND index_name = 'idx_visibility_delivery_region');
SET @inquiry_delivery_index_non_unique = (
  SELECT MIN(non_unique)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'product_purchase_inquiry'
    AND index_name = 'idx_visibility_delivery_region');
SET @inquiry_delivery_index_ddl = CASE
  WHEN @inquiry_delivery_index_signature = '' THEN
    'ALTER TABLE product_purchase_inquiry ADD INDEX idx_visibility_delivery_region (tenant_id,visibility,status,delivery_province_id,delivery_city_id,delivery_area_id)'
  WHEN @inquiry_delivery_index_signature <> 'tenant_id,visibility,status,delivery_province_id,delivery_city_id,delivery_area_id'
       OR @inquiry_delivery_index_non_unique <> 1 THEN
    'ALTER TABLE product_purchase_inquiry DROP INDEX idx_visibility_delivery_region, ADD INDEX idx_visibility_delivery_region (tenant_id,visibility,status,delivery_province_id,delivery_city_id,delivery_area_id)'
  ELSE 'SELECT 1'
END;
PREPARE inquiry_delivery_index_stmt FROM @inquiry_delivery_index_ddl;
EXECUTE inquiry_delivery_index_stmt;
DEALLOCATE PREPARE inquiry_delivery_index_stmt;

-- 部署核对：大于 0 表示仍有旧平台公开询价等待人工补录完整省/市/区快照；
-- 不从 address 自动猜测或回填，未补齐记录按失败关闭处理。
SELECT COUNT(*) AS legacy_platform_public_with_incomplete_delivery_region
FROM product_purchase_inquiry
WHERE deleted = b'0'
  AND visibility = 'PLATFORM_PUBLIC'
  AND (delivery_area_id IS NULL OR delivery_city_id IS NULL OR delivery_province_id IS NULL);

SELECT id, tenant_id, no, delivery_province_id, delivery_city_id, delivery_area_id
FROM product_purchase_inquiry
WHERE deleted = b'0'
  AND visibility = 'PLATFORM_PUBLIC'
  AND (delivery_area_id IS NULL OR delivery_city_id IS NULL OR delivery_province_id IS NULL)
ORDER BY tenant_id, id;
