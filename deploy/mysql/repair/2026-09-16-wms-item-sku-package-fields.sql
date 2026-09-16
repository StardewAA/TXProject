-- 补齐 wms_item_sku 的包装字段（可重复执行）
--
-- 背景：WmsItemSkuDO 已声明 packageSpec / boxSpec / palletSpec / minPackageUnit，
-- 但 deploy/mysql 下的既有脚本只覆盖了 wms_item 的包装字段，SKU 侧的 4 列在
-- 由全量 dump 初始化的本地库中缺失，导致包含这些列的 WMS 数据包导入报
-- `Unknown column 'package_spec' in 'field list'`。
-- 列长度与 wms_item 的同名字段保持一致。
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_sku' AND column_name='package_spec'),'SELECT 1','ALTER TABLE `wms_item_sku` ADD COLUMN `package_spec` varchar(255) NULL COMMENT ''最小包装说明''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_sku' AND column_name='box_spec'),'SELECT 1','ALTER TABLE `wms_item_sku` ADD COLUMN `box_spec` varchar(100) NULL COMMENT ''箱装数量''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_sku' AND column_name='pallet_spec'),'SELECT 1','ALTER TABLE `wms_item_sku` ADD COLUMN `pallet_spec` varchar(100) NULL COMMENT ''托盘装载量''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_sku' AND column_name='min_package_unit'),'SELECT 1','ALTER TABLE `wms_item_sku` ADD COLUMN `min_package_unit` varchar(20) NULL COMMENT ''最小包装单位''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
