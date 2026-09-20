-- Compatibility with the WMS master-data fields already present in remote master.
-- Adds nullable columns only; no inventory or ERP records are changed.
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='batch_manage'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `batch_manage` boolean NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='shelf_life_manage'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `shelf_life_manage` boolean NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='serial_number_manage'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `serial_number_manage` boolean NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='shelf_life_months'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `shelf_life_months` int NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='production_date'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `production_date` date NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='expiration_date'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `expiration_date` date NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='batch_number_rule'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `batch_number_rule` varchar(255) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='expiration_rule'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `expiration_rule` varchar(255) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='expiry_warning_days'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `expiry_warning_days` int NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='expired_outbound_blocked'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `expired_outbound_blocked` boolean NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='package_spec'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `package_spec` varchar(255) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='box_spec'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `box_spec` varchar(100) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='pallet_spec'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `pallet_spec` varchar(100) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='min_package_unit'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `min_package_unit` varchar(20) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='storage_condition'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `storage_condition` varchar(500) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='temperature_requirement'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `temperature_requirement` varchar(255) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item' AND column_name='dangerous_goods'),'SELECT 1','ALTER TABLE `wms_item` ADD COLUMN `dangerous_goods` boolean NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_brand' AND column_name='category_id'),'SELECT 1','ALTER TABLE `wms_item_brand` ADD COLUMN `category_id` bigint NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_brand' AND column_name='supplier_id'),'SELECT 1','ALTER TABLE `wms_item_brand` ADD COLUMN `supplier_id` bigint NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_brand' AND column_name='description'),'SELECT 1','ALTER TABLE `wms_item_brand` ADD COLUMN `description` varchar(1000) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_brand' AND column_name='qualification_files'),'SELECT 1','ALTER TABLE `wms_item_brand` ADD COLUMN `qualification_files` varchar(8000) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- Category metadata required by the master-baseline SKU query.
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_category' AND column_name='storage_description'),'SELECT 1','ALTER TABLE `wms_item_category` ADD COLUMN `storage_description` varchar(1000) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- SKU 包装字段：WmsItemSkuDO 已定义但历史迁移遗漏（只补了 wms_item），
-- 缺列会导致 WmsItemSkuMapper 查询报 Unknown column 'package_spec'，进而 SKU 校验/出库单创建整体失败。
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_sku' AND column_name='package_spec'),'SELECT 1','ALTER TABLE `wms_item_sku` ADD COLUMN `package_spec` varchar(255) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_sku' AND column_name='box_spec'),'SELECT 1','ALTER TABLE `wms_item_sku` ADD COLUMN `box_spec` varchar(100) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_sku' AND column_name='pallet_spec'),'SELECT 1','ALTER TABLE `wms_item_sku` ADD COLUMN `pallet_spec` varchar(100) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_item_sku' AND column_name='min_package_unit'),'SELECT 1','ALTER TABLE `wms_item_sku` ADD COLUMN `min_package_unit` varchar(20) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 往来企业分方向版本：WmsMerchantDO 已定义但历史迁移遗漏，
-- 缺列会导致 WmsPartnerIntegrationService / 采购入库等按 merchant 查询时报 Unknown column。
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_merchant' AND column_name='customer_source_version'),'SELECT 1','ALTER TABLE `wms_merchant` ADD COLUMN `customer_source_version` bigint NOT NULL DEFAULT 0 COMMENT ''ERP 客户主数据版本''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wms_merchant' AND column_name='supplier_source_version'),'SELECT 1','ALTER TABLE `wms_merchant` ADD COLUMN `supplier_source_version` bigint NOT NULL DEFAULT 0 COMMENT ''ERP 供应商主数据版本''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
