-- ============================================================
-- WMS 租户 2010（华瀚节能建材有限公司）测试主数据
-- 目的：为 MES→WMS 生产领料链路（P2→P3→P4→P5）准备对齐的主数据
-- 说明：
--   1) MES 数据在租户 2010；WMS 原本只有租户 1 数据，故需在本租户补齐。
--   2) wms_item.code / wms_item_sku.code 必须与 MES 物料编码一致（MES 报文以物料编码作为 SKU 编码）。
--   3) 热轧卷板L360M 库存故意设为 5（工单需求 10），以触发缺口检测 P3→协作单B P4。
--   4) 脚本幂等：按 code + tenant_id 判断是否已存在。
-- 执行：mysql --default-character-set=utf8mb4 -D ruoyi-vue-pro  (source 本文件)
-- ============================================================

SET @tenant := 2010;

-- ---------- 1. 物料分类（wms_item.category_id 为必填） ----------
SET @cat := (SELECT id FROM wms_item_category WHERE code = 'C20100001' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item_category (parent_id, code, name, sort, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 0, 'C20100001', '防腐保温原材料', 0, 1, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @cat IS NULL;
SET @cat := (SELECT id FROM wms_item_category WHERE code = 'C20100001' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);

-- ---------- 2. 仓库 ----------
-- 原料仓 SH（MES 领料目标仓，对应 mes.wms.warehouse.raw-material-code=SH）
SET @wh := (SELECT id FROM wms_warehouse WHERE code = 'SH' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_warehouse (code, name, warehouse_type, status, data_version, sort, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'SH', '华瀚原料仓', 'RAW_MATERIAL', 0, 1, 0, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @wh IS NULL;
SET @wh_sh := (SELECT id FROM wms_warehouse WHERE code = 'SH' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);

-- 成品仓 FG（MES 完工入库目标仓，对应 mes.wms.warehouse.finished-goods-code=FG）
SET @wh := (SELECT id FROM wms_warehouse WHERE code = 'FG' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_warehouse (code, name, warehouse_type, status, data_version, sort, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'FG', '华瀚成品仓', 'FINISHED_GOODS', 0, 1, 0, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @wh IS NULL;
SET @wh_fg := (SELECT id FROM wms_warehouse WHERE code = 'FG' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);

-- ---------- 3. 物料（wms_item，code 与 MES 物料编码一致） ----------
SET @it := (SELECT id FROM wms_item WHERE code = 'ITEM20260916000027' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item (code, material_type, quality_required, source_version, status, name, category_id, unit, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000027', 'RAW_MATERIAL', b'0', 1, 0, '热轧卷板L360M', @cat, '吨', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @it IS NULL;

SET @it := (SELECT id FROM wms_item WHERE code = 'ITEM20260916000005' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item (code, material_type, quality_required, source_version, status, name, category_id, unit, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000005', 'RAW_MATERIAL', b'0', 1, 0, '埋弧焊丝', @cat, '千克', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @it IS NULL;

SET @it := (SELECT id FROM wms_item WHERE code = 'ITEM20260916000007' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item (code, material_type, quality_required, source_version, status, name, category_id, unit, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000007', 'RAW_MATERIAL', b'0', 1, 0, '埋弧焊剂', @cat, '千克', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @it IS NULL;

SET @it := (SELECT id FROM wms_item WHERE code = 'ITEM20260916000016' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item (code, material_type, quality_required, source_version, status, name, category_id, unit, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000016', 'FINISHED_GOODS', b'0', 1, 0, '螺旋焊管720', @cat, '根', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @it IS NULL;

-- ---------- 4. SKU（wms_item_sku，code 与物料编码一致） ----------
SET @sku := (SELECT id FROM wms_item_sku WHERE code = 'ITEM20260916000027' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item_sku (name, item_id, code, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '热轧卷板L360M', (SELECT id FROM wms_item WHERE code = 'ITEM20260916000027' AND tenant_id = @tenant AND deleted = b'0'), 'ITEM20260916000027', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @sku IS NULL;

SET @sku := (SELECT id FROM wms_item_sku WHERE code = 'ITEM20260916000005' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item_sku (name, item_id, code, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '埋弧焊丝', (SELECT id FROM wms_item WHERE code = 'ITEM20260916000005' AND tenant_id = @tenant AND deleted = b'0'), 'ITEM20260916000005', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @sku IS NULL;

SET @sku := (SELECT id FROM wms_item_sku WHERE code = 'ITEM20260916000007' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item_sku (name, item_id, code, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '埋弧焊剂', (SELECT id FROM wms_item WHERE code = 'ITEM20260916000007' AND tenant_id = @tenant AND deleted = b'0'), 'ITEM20260916000007', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @sku IS NULL;

SET @sku := (SELECT id FROM wms_item_sku WHERE code = 'ITEM20260916000016' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_item_sku (name, item_id, code, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '螺旋焊管720', (SELECT id FROM wms_item WHERE code = 'ITEM20260916000016' AND tenant_id = @tenant AND deleted = b'0'), 'ITEM20260916000016', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @sku IS NULL;

-- ---------- 5. 库存（原料仓 SH；热轧卷板不足以触发缺口） ----------
SET @inv := (SELECT id FROM wms_inventory WHERE sku_id = (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000027' AND tenant_id=@tenant AND deleted=b'0') AND warehouse_id = @wh_sh AND deleted = b'0' LIMIT 1);
INSERT INTO wms_inventory (sku_id, warehouse_id, area_id, quantity, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000027' AND tenant_id=@tenant AND deleted=b'0'), @wh_sh, 0, 5, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @inv IS NULL;

SET @inv := (SELECT id FROM wms_inventory WHERE sku_id = (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000005' AND tenant_id=@tenant AND deleted=b'0') AND warehouse_id = @wh_sh AND deleted = b'0' LIMIT 1);
INSERT INTO wms_inventory (sku_id, warehouse_id, area_id, quantity, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000005' AND tenant_id=@tenant AND deleted=b'0'), @wh_sh, 0, 100, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @inv IS NULL;

SET @inv := (SELECT id FROM wms_inventory WHERE sku_id = (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000007' AND tenant_id=@tenant AND deleted=b'0') AND warehouse_id = @wh_sh AND deleted = b'0' LIMIT 1);
INSERT INTO wms_inventory (sku_id, warehouse_id, area_id, quantity, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT (SELECT id FROM wms_item_sku WHERE code='ITEM20260916000007' AND tenant_id=@tenant AND deleted=b'0'), @wh_sh, 0, 100, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @inv IS NULL;

-- ---------- 6. 校验输出 ----------
SELECT id, code, name, warehouse_type, tenant_id FROM wms_warehouse WHERE tenant_id = @tenant AND deleted = b'0';
SELECT id, code, name, material_type, tenant_id FROM wms_item WHERE tenant_id = @tenant AND deleted = b'0';
SELECT s.id, s.code, s.name, i.material_type, s.tenant_id FROM wms_item_sku s LEFT JOIN wms_item i ON s.item_id = i.id WHERE s.tenant_id = @tenant AND s.deleted = b'0';
SELECT inv.id, sku.code AS sku_code, wh.code AS warehouse_code, inv.quantity
FROM wms_inventory inv
LEFT JOIN wms_item_sku sku ON inv.sku_id = sku.id
LEFT JOIN wms_warehouse wh ON inv.warehouse_id = wh.id
WHERE inv.tenant_id = @tenant AND inv.deleted = b'0';
