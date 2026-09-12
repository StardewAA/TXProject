-- WMS SKU 仓储包装字段增量脚本（可重复执行）
SET @db_name = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='wms_item_sku' AND COLUMN_NAME='package_spec')=0,
  'ALTER TABLE `wms_item_sku` ADD COLUMN `package_spec` varchar(255) NULL COMMENT ''最小包装说明'' AFTER `selling_price`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='wms_item_sku' AND COLUMN_NAME='box_spec')=0,
  'ALTER TABLE `wms_item_sku` ADD COLUMN `box_spec` varchar(100) NULL COMMENT ''箱装数量'' AFTER `package_spec`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='wms_item_sku' AND COLUMN_NAME='pallet_spec')=0,
  'ALTER TABLE `wms_item_sku` ADD COLUMN `pallet_spec` varchar(100) NULL COMMENT ''托盘装载量'' AFTER `box_spec`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='wms_item_sku' AND COLUMN_NAME='min_package_unit')=0,
  'ALTER TABLE `wms_item_sku` ADD COLUMN `min_package_unit` varchar(20) NULL COMMENT ''最小包装单位'' AFTER `pallet_spec`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
