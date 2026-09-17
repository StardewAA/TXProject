-- ============================================================================
-- 把 WMS 基础数据（商品管理：wms_item + wms_item_sku）复制到固邦租户 2012
-- 日期：2026-09-16
--
-- 背景：本地 WMS 商品数据只有租户 2005（金岩矿物棉，来自 CRM/WMS 联调数据包），
--       固邦租户（2012，管理员「朱琳」gubang）下 `wms_item` / `wms_item_sku`
--       为空，导致「WMS 基础数据 → 商品管理」页面没有数据。
--
-- 做法：
--   1. 商品、SKU 的 ID 分别加偏移量 800000 后复制，避免与源记录主键冲突；
--   2. 分类/品牌：数据包本身不含分类、品牌（源库引用的 category_id 153-160、
--      brand_id 107-110 在本机也不存在），故按「未分类 / 无品牌」导入；
--   3. 创建人/更新人写为固邦管理员「朱琳」(用户编号 80446)；
--   4. 幂等：目标租户已有未删除商品时，整段跳过。
--
-- 执行（务必 utf8mb4）：
--   docker exec -i txgy-mysql mysql --default-character-set=utf8mb4 -uroot -p*** ruoyi-vue-pro < 本文件
-- ============================================================================
SET NAMES utf8mb4;

SET @src_tenant = 2005;
SET @dst_tenant = 2012;
SET @item_off = 800000;
SET @sku_off = 800000;
SET @operator = '80446';

SET @already := (SELECT COUNT(*) FROM `wms_item` WHERE tenant_id = @dst_tenant AND deleted = 0);

-- ---------------------------------------------------------------------------
-- 1. 商品
-- ---------------------------------------------------------------------------
INSERT INTO `wms_item`
(id, code, name, category_id, unit, brand_id, remark, creator, create_time, updater, update_time, deleted, tenant_id,
 material_type, quality_required, source_version, status, current_quality_report_id,
 batch_manage, shelf_life_manage, serial_number_manage, shelf_life_months, production_date, expiration_date,
 batch_number_rule, expiration_rule, expiry_warning_days, expired_outbound_blocked,
 package_spec, box_spec, pallet_spec, min_package_unit, storage_condition, temperature_requirement, dangerous_goods)
SELECT i.id + @item_off, i.code, i.name, 0, i.unit, NULL, i.remark, @operator, NOW(), @operator, NOW(), b'0', @dst_tenant,
       i.material_type, i.quality_required, i.source_version, i.status, NULL,
       i.batch_manage, i.shelf_life_manage, i.serial_number_manage, i.shelf_life_months, i.production_date, i.expiration_date,
       i.batch_number_rule, i.expiration_rule, i.expiry_warning_days, i.expired_outbound_blocked,
       i.package_spec, i.box_spec, i.pallet_spec, i.min_package_unit, i.storage_condition, i.temperature_requirement,
       i.dangerous_goods
FROM `wms_item` i
WHERE @already = 0 AND i.tenant_id = @src_tenant AND i.deleted = 0;

-- ---------------------------------------------------------------------------
-- 2. 商品规格 SKU（item_id 同步加偏移量）
-- ---------------------------------------------------------------------------
INSERT INTO `wms_item_sku`
(id, name, item_id, bar_code, code, length, width, height, gross_weight, net_weight, cost_price, selling_price,
 creator, create_time, updater, update_time, deleted, tenant_id,
 package_spec, box_spec, pallet_spec, min_package_unit)
SELECT s.id + @sku_off, s.name, s.item_id + @item_off, s.bar_code, s.code, s.length, s.width, s.height,
       s.gross_weight, s.net_weight, s.cost_price, s.selling_price,
       @operator, NOW(), @operator, NOW(), b'0', @dst_tenant,
       s.package_spec, s.box_spec, s.pallet_spec, s.min_package_unit
FROM `wms_item_sku` s
WHERE @already = 0 AND s.tenant_id = @src_tenant AND s.deleted = 0;

-- ---------------------------------------------------------------------------
-- 3. 校验（与商品数据包同口径）
-- ---------------------------------------------------------------------------
SELECT 'wms_item_tenant_2012' AS check_name, COUNT(*) AS actual_count, 14 AS expected_count
FROM `wms_item` WHERE tenant_id = @dst_tenant AND deleted = 0 AND status = 0;

SELECT 'wms_item_sku_tenant_2012' AS check_name, COUNT(*) AS actual_count, 15 AS expected_count
FROM `wms_item_sku` WHERE tenant_id = @dst_tenant AND deleted = 0;

SELECT 'orphan_sku_2012' AS check_name, COUNT(*) AS actual_count, 0 AS expected_count
FROM `wms_item_sku` s
LEFT JOIN `wms_item` i ON i.id = s.item_id AND i.tenant_id = s.tenant_id
WHERE s.tenant_id = @dst_tenant AND s.deleted = 0 AND i.id IS NULL;
