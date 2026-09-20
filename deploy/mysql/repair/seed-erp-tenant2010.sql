-- ============================================================
-- ERP 租户 2010 统一物料编码（供 WMS 协作单B 接收校验）
-- 目的：ErpWmsCollaborationBService.receive 要求 item.materialCode 存在于 erp_product.material_code，
--       而 MES 物料编码（ITEM2026...）此前未同步到 ERP，导致协作单B 被拒（统一物料编码不存在）。
-- 说明：分类复用现有 管材原材料=2 / 成品=1；单位复用租户 2010 的 unit_id=5(PCS)。
--       正式环境应通过主数据同步统一编码，本脚本仅用于本机联调。
-- 幂等：按 material_code + tenant_id 判断。
-- ============================================================

SET @tenant := 2010;

SET @p := (SELECT id FROM erp_product WHERE material_code = 'ITEM20260916000027' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO erp_product (material_code, material_type, quality_required, sync_version, name, bar_code, category_id, unit_id, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000027', 'RAW_MATERIAL', b'0', 1, '热轧卷板L360M', 'ITEM20260916000027', 2, 5, 0, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @p IS NULL;

SET @p := (SELECT id FROM erp_product WHERE material_code = 'ITEM20260916000005' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO erp_product (material_code, material_type, quality_required, sync_version, name, bar_code, category_id, unit_id, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000005', 'RAW_MATERIAL', b'0', 1, '埋弧焊丝', 'ITEM20260916000005', 2, 5, 0, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @p IS NULL;

SET @p := (SELECT id FROM erp_product WHERE material_code = 'ITEM20260916000007' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO erp_product (material_code, material_type, quality_required, sync_version, name, bar_code, category_id, unit_id, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000007', 'RAW_MATERIAL', b'0', 1, '埋弧焊剂', 'ITEM20260916000007', 2, 5, 0, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @p IS NULL;

SET @p := (SELECT id FROM erp_product WHERE material_code = 'ITEM20260916000016' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO erp_product (material_code, material_type, quality_required, sync_version, name, bar_code, category_id, unit_id, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'ITEM20260916000016', 'FINISHED_GOODS', b'0', 1, '螺旋焊管720', 'ITEM20260916000016', 1, 5, 0, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @p IS NULL;

-- 供应商（接受协作单B 生成采购订单草稿时需选择供应商）
SET @s := (SELECT id FROM erp_supplier WHERE partner_code = 'SUP-2010-001' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO erp_supplier (partner_code, sync_version, name, status, sort, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'SUP-2010-001', 1, '华瀚测试原料供应商', 0, 0, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @s IS NULL;

-- 校验
SELECT id, material_code, material_type, name, tenant_id FROM erp_product WHERE tenant_id = @tenant AND deleted = b'0';
SELECT id, partner_code, name, status, tenant_id FROM erp_supplier WHERE tenant_id = @tenant AND deleted = b'0';
