-- ============================================================
-- P5（采购入库→恢复 MES）所需主数据，租户 2010
-- 1) ERP 仓库：采购入库明细必填 warehouseId，且 erp_warehouse.wms_warehouse_code 用于关联 WMS 仓库
-- 2) ERP 结算账户：采购入库页面可选结算账户
-- 3) WMS 往来企业（供应商）：ERP 采购入库推送 WMS 时若带 partnerCode 需能匹配
-- 幂等。
-- ============================================================

SET @tenant := 2010;

-- 1. ERP 仓库（绑定 WMS 原料仓编码 SH）
SET @w := (SELECT id FROM erp_warehouse WHERE wms_warehouse_code = 'SH' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO erp_warehouse (wms_warehouse_code, name, address, sort, principal, status, default_status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'SH', '华瀚原料仓', '本机联调', 1, 'seed', 0, b'1', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @w IS NULL;

-- 2. ERP 结算账户
SET @a := (SELECT id FROM erp_account WHERE no = 'ACC-2010-001' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO erp_account (name, no, status, sort, default_status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '华瀚测试账户', 'ACC-2010-001', 0, 1, b'1', 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @a IS NULL;

-- 3. WMS 往来企业（type=2 供应商），编码与 ERP 供应商 partner_code 一致
SET @m := (SELECT id FROM wms_merchant WHERE code = 'SUP-2010-001' AND tenant_id = @tenant AND deleted = b'0' LIMIT 1);
INSERT INTO wms_merchant (code, name, type, source_version, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'SUP-2010-001', '华瀚测试原料供应商', 2, 1, 0, 'seed', NOW(), 'seed', NOW(), b'0', @tenant
FROM DUAL WHERE @m IS NULL;

-- 校验
SELECT id, wms_warehouse_code, name, tenant_id FROM erp_warehouse WHERE tenant_id = @tenant AND deleted = b'0';
SELECT id, name, no, tenant_id FROM erp_account WHERE tenant_id = @tenant AND deleted = b'0';
SELECT id, code, name, type, tenant_id FROM wms_merchant WHERE tenant_id = @tenant AND deleted = b'0';
