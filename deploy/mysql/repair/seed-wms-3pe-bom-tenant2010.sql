-- ============================================================
-- WMS 租户 2010：补齐「3PE防腐钢管」BOM 物料（用于工单审批 + 领料缺料链路验证）
-- 背景：产品 3PE防腐钢管（MES id=135）在工艺路线 15 下已配 BOM：
--         螺旋焊管820  ITEM20260916000024  用量 1/根
--         环氧粉末     ITEM20260916000008  用量 1/根
--       但 WMS 租户 2010 尚无对应 SKU，导致 MES 领料申请因 SKU 不存在被拒。
-- 库存策略（工单数量 40）：
--       螺旋焊管820 库存 15  → 需求 40，缺口 25，用于触发缺口检测 P3 → 协作单B P4
--       环氧粉末    库存 100 → 充足，不触发缺口
-- 幂等：按 code + tenant_id 判断是否已存在，可重复执行。
-- ============================================================

SET @tenant := 2010;
SET @cat := (SELECT id FROM wms_item_category WHERE code = 'C20100001' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
SET @wh_sh := (SELECT id FROM wms_warehouse WHERE code = 'SH' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);

-- ---------- 1. 物料 ----------
SET @it := (SELECT id FROM wms_item WHERE code = 'ITEM20260916000024' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item (code, material_type, quality_required, source_version, status, name, category_id, unit, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000024', 'RAW_MATERIAL', b'0', 1, 0, '螺旋焊管820', @cat, '根', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @it IS NULL;

SET @it := (SELECT id FROM wms_item WHERE code = 'ITEM20260916000008' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item (code, material_type, quality_required, source_version, status, name, category_id, unit, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000008', 'RAW_MATERIAL', b'0', 1, 0, '环氧粉末', @cat, '千克', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @it IS NULL;

-- ---------- 2. SKU（code 与 MES 物料编码一致） ----------
SET @sku := (SELECT id FROM wms_item_sku WHERE code = 'ITEM20260916000024' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item_sku (name, item_id, code, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '螺旋焊管820', (SELECT id FROM wms_item WHERE code = 'ITEM20260916000024' AND tenant_id = @tenant AND deleted = b'0'), 'ITEM20260916000024', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @sku IS NULL;

SET @sku := (SELECT id FROM wms_item_sku WHERE code = 'ITEM20260916000008' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item_sku (name, item_id, code, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '环氧粉末', (SELECT id FROM wms_item WHERE code = 'ITEM20260916000008' AND tenant_id = @tenant AND deleted = b'0'), 'ITEM20260916000008', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @sku IS NULL;

-- ---------- 3. 库存（原料仓 SH） ----------
SET @inv := (SELECT id FROM wms_inventory WHERE sku_id = (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000024' AND tenant_id=@tenant AND deleted=b'0') AND warehouse_id = @wh_sh AND deleted = b'0' LIMIT 1);
INSERT INTO wms_inventory (sku_id, warehouse_id, area_id, quantity, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000024' AND tenant_id=@tenant AND deleted=b'0'), @wh_sh, 0, 15, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @inv IS NULL;

SET @inv := (SELECT id FROM wms_inventory WHERE sku_id = (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000008' AND tenant_id=@tenant AND deleted=b'0') AND warehouse_id = @wh_sh AND deleted = b'0' LIMIT 1);
INSERT INTO wms_inventory (sku_id, warehouse_id, area_id, quantity, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000008' AND tenant_id=@tenant AND deleted=b'0'), @wh_sh, 0, 100, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @inv IS NULL;

-- ---------- 4. 校验 ----------
SELECT s.id, s.code, s.name, i.material_type FROM wms_item_sku s LEFT JOIN wms_item i ON s.item_id = i.id WHERE s.tenant_id = @tenant AND s.deleted = b'0';
SELECT inv.id, sku.code AS sku_code, wh.code AS warehouse_code, inv.quantity
FROM wms_inventory inv
LEFT JOIN wms_item_sku sku ON inv.sku_id = sku.id
LEFT JOIN wms_warehouse wh ON inv.warehouse_id = wh.id
WHERE inv.tenant_id = @tenant AND inv.deleted = b'0';
