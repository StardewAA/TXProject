-- ERP 审批管理脱敏演示数据
-- 适用租户：西北恒泰新材料有限公司（tenant_id = 2001）
-- 所有外部企业、联系人、联系方式、税号、银行账号及地址均为虚构测试数据。

SET NAMES utf8mb4;
START TRANSACTION;

SET @tenant_id = 2001;
SET @admin_user_id = 80000;
SET @purchase_creator_id = 80016; -- 供应链业务员（测试账号）
SET @sale_creator_id = 80008;     -- 订单业务员（测试账号）

-- 1. 产品分类
INSERT INTO erp_product_category
    (parent_id, name, code, sort, status, creator, updater, deleted, tenant_id)
SELECT 0, '演示-钢管基材', 'DEMO_RAW_PIPE', 10, 0, @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product_category WHERE code = 'DEMO_RAW_PIPE' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_product_category
    (parent_id, name, code, sort, status, creator, updater, deleted, tenant_id)
SELECT 0, '演示-防腐材料', 'DEMO_ANTI_CORROSION', 20, 0, @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product_category WHERE code = 'DEMO_ANTI_CORROSION' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_product_category
    (parent_id, name, code, sort, status, creator, updater, deleted, tenant_id)
SELECT 0, '演示-保温材料', 'DEMO_INSULATION', 30, 0, @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product_category WHERE code = 'DEMO_INSULATION' AND tenant_id = @tenant_id AND deleted = b'0');

SET @category_pipe = (SELECT id FROM erp_product_category WHERE code = 'DEMO_RAW_PIPE' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @category_anti = (SELECT id FROM erp_product_category WHERE code = 'DEMO_ANTI_CORROSION' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @category_insulation = (SELECT id FROM erp_product_category WHERE code = 'DEMO_INSULATION' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);

-- 2. 计量单位
INSERT INTO erp_product_unit (name, status, creator, updater, deleted, tenant_id)
SELECT '米', 0, @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product_unit WHERE name = '米' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_product_unit (name, status, creator, updater, deleted, tenant_id)
SELECT '千克', 0, @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product_unit WHERE name = '千克' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_product_unit (name, status, creator, updater, deleted, tenant_id)
SELECT '桶', 0, @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product_unit WHERE name = '桶' AND tenant_id = @tenant_id AND deleted = b'0');

SET @unit_meter = (SELECT id FROM erp_product_unit WHERE name = '米' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @unit_kg = (SELECT id FROM erp_product_unit WHERE name = '千克' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @unit_barrel = (SELECT id FROM erp_product_unit WHERE name = '桶' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);

-- 3. 供应商（完全虚构）
INSERT INTO erp_supplier
    (name, contact, mobile, telephone, email, fax, remark, status, sort, tax_no, tax_percent,
     bank_name, bank_account, bank_address, creator, updater, deleted, tenant_id)
SELECT '演示-西秦钢材供应中心', '李某（演示）', '13800000001', '029-00000001', 'supplier01@example.invalid', '029-00009001',
       '脱敏演示供应商，仅用于采购审批测试', 0, 10, 'DEMO-TAX-SUP-001', 13,
       '演示银行西安测试支行', 'DEMO-ACCOUNT-SUP-001', '脱敏-西安市测试工业园A区',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_supplier WHERE name = '演示-西秦钢材供应中心' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_supplier
    (name, contact, mobile, telephone, email, fax, remark, status, sort, tax_no, tax_percent,
     bank_name, bank_account, bank_address, creator, updater, deleted, tenant_id)
SELECT '演示-北辰防腐材料商行', '王某（演示）', '13800000002', '029-00000002', 'supplier02@example.invalid', '029-00009002',
       '脱敏演示供应商，仅用于采购审批测试', 0, 20, 'DEMO-TAX-SUP-002', 13,
       '演示银行咸阳测试支行', 'DEMO-ACCOUNT-SUP-002', '脱敏-咸阳市示范产业园B区',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_supplier WHERE name = '演示-北辰防腐材料商行' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_supplier
    (name, contact, mobile, telephone, email, fax, remark, status, sort, tax_no, tax_percent,
     bank_name, bank_account, bank_address, creator, updater, deleted, tenant_id)
SELECT '演示-华岭保温材料中心', '赵某（演示）', '13800000003', '029-00000003', 'supplier03@example.invalid', '029-00009003',
       '脱敏演示供应商，仅用于采购审批测试', 0, 30, 'DEMO-TAX-SUP-003', 13,
       '演示银行渭南测试支行', 'DEMO-ACCOUNT-SUP-003', '脱敏-渭南市示范工业园C区',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_supplier WHERE name = '演示-华岭保温材料中心' AND tenant_id = @tenant_id AND deleted = b'0');

SET @supplier_pipe = (SELECT id FROM erp_supplier WHERE name = '演示-西秦钢材供应中心' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @supplier_anti = (SELECT id FROM erp_supplier WHERE name = '演示-北辰防腐材料商行' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @supplier_insulation = (SELECT id FROM erp_supplier WHERE name = '演示-华岭保温材料中心' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);

-- 4. 客户（完全虚构）
INSERT INTO erp_customer
    (name, contact, mobile, telephone, email, fax, remark, status, sort, tax_no, tax_percent,
     bank_name, bank_account, bank_address, creator, updater, deleted, tenant_id)
SELECT '演示-秦岭市政工程项目部', '陈某（演示）', '13900000001', '029-10000001', 'customer01@example.invalid', '029-10009001',
       '脱敏演示客户，仅用于销售审批测试', 0, 10, 'DEMO-TAX-CUS-001', 13,
       '演示银行西安项目支行', 'DEMO-ACCOUNT-CUS-001', '脱敏-西安市示范工程区1号',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_customer WHERE name = '演示-秦岭市政工程项目部' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_customer
    (name, contact, mobile, telephone, email, fax, remark, status, sort, tax_no, tax_percent,
     bank_name, bank_account, bank_address, creator, updater, deleted, tenant_id)
SELECT '演示-西川能源管网项目部', '刘某（演示）', '13900000002', '028-10000002', 'customer02@example.invalid', '028-10009002',
       '脱敏演示客户，仅用于销售审批测试', 0, 20, 'DEMO-TAX-CUS-002', 13,
       '演示银行成都项目支行', 'DEMO-ACCOUNT-CUS-002', '脱敏-成都市示范工程区2号',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_customer WHERE name = '演示-西川能源管网项目部' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_customer
    (name, contact, mobile, telephone, email, fax, remark, status, sort, tax_no, tax_percent,
     bank_name, bank_account, bank_address, creator, updater, deleted, tenant_id)
SELECT '演示-关中工业设备安装部', '孙某（演示）', '13900000003', '029-10000003', 'customer03@example.invalid', '029-10009003',
       '脱敏演示客户，仅用于销售审批测试', 0, 30, 'DEMO-TAX-CUS-003', 13,
       '演示银行宝鸡项目支行', 'DEMO-ACCOUNT-CUS-003', '脱敏-宝鸡市示范工程区3号',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_customer WHERE name = '演示-关中工业设备安装部' AND tenant_id = @tenant_id AND deleted = b'0');

SET @customer_municipal = (SELECT id FROM erp_customer WHERE name = '演示-秦岭市政工程项目部' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @customer_energy = (SELECT id FROM erp_customer WHERE name = '演示-西川能源管网项目部' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @customer_install = (SELECT id FROM erp_customer WHERE name = '演示-关中工业设备安装部' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);

-- 5. 结算账户与仓库
INSERT INTO erp_account (name, no, remark, status, sort, default_status, creator, updater, deleted, tenant_id)
SELECT '演示-基本结算账户', 'DEMO-SETTLEMENT-001', '脱敏测试账户，不对应真实银行账户', 0, 10, b'1',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_account WHERE no = 'DEMO-SETTLEMENT-001' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_account (name, no, remark, status, sort, default_status, creator, updater, deleted, tenant_id)
SELECT '演示-项目结算账户', 'DEMO-SETTLEMENT-002', '脱敏测试账户，不对应真实银行账户', 0, 20, b'0',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_account WHERE no = 'DEMO-SETTLEMENT-002' AND tenant_id = @tenant_id AND deleted = b'0');
SET @account_default = (SELECT id FROM erp_account WHERE no = 'DEMO-SETTLEMENT-001' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @account_project = (SELECT id FROM erp_account WHERE no = 'DEMO-SETTLEMENT-002' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);

INSERT INTO erp_warehouse
    (name, address, sort, remark, principal, warehouse_price, truckage_price, status, default_status,
     creator, updater, deleted, tenant_id)
SELECT '演示-原材料仓', '脱敏-厂区东侧A库', 10, '脱敏测试仓库', '胡某（演示）', 2.5, 1.2, 0, b'1',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_warehouse WHERE name = '演示-原材料仓' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_warehouse
    (name, address, sort, remark, principal, warehouse_price, truckage_price, status, default_status,
     creator, updater, deleted, tenant_id)
SELECT '演示-成品仓', '脱敏-厂区西侧B库', 20, '脱敏测试仓库', '朱某（演示）', 3.2, 1.5, 0, b'0',
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_warehouse WHERE name = '演示-成品仓' AND tenant_id = @tenant_id AND deleted = b'0');

-- 6. 产品
INSERT INTO erp_product
    (name, bar_code, category_id, unit_id, status, standard, remark, expiry_day, weight,
     purchase_price, sale_price, min_price, creator, updater, deleted, tenant_id)
SELECT '演示-螺旋焊接钢管', 'DEMO-P-0001', @category_pipe, @unit_meter, 0, 'DN219×6mm',
       '脱敏演示产品；执行标准仅用于界面测试', 3650, 31.5, 280, 365, 330,
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product WHERE bar_code = 'DEMO-P-0001' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_product
    (name, bar_code, category_id, unit_id, status, standard, remark, expiry_day, weight,
     purchase_price, sale_price, min_price, creator, updater, deleted, tenant_id)
SELECT '演示-3PE防腐粉末', 'DEMO-P-0002', @category_anti, @unit_kg, 0, '25kg/袋',
       '脱敏演示产品', 365, 1, 18.5, 26, 22,
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product WHERE bar_code = 'DEMO-P-0002' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_product
    (name, bar_code, category_id, unit_id, status, standard, remark, expiry_day, weight,
     purchase_price, sale_price, min_price, creator, updater, deleted, tenant_id)
SELECT '演示-聚氨酯组合料', 'DEMO-P-0003', @category_insulation, @unit_kg, 0, '双组分/工业级',
       '脱敏演示产品', 180, 1, 21.8, 31, 27,
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product WHERE bar_code = 'DEMO-P-0003' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_product
    (name, bar_code, category_id, unit_id, status, standard, remark, expiry_day, weight,
     purchase_price, sale_price, min_price, creator, updater, deleted, tenant_id)
SELECT '演示-高密度聚乙烯外护管', 'DEMO-P-0004', @category_insulation, @unit_meter, 0, 'DN315mm',
       '脱敏演示产品', 3650, 8.2, 65, 92, 82,
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product WHERE bar_code = 'DEMO-P-0004' AND tenant_id = @tenant_id AND deleted = b'0');
INSERT INTO erp_product
    (name, bar_code, category_id, unit_id, status, standard, remark, expiry_day, weight,
     purchase_price, sale_price, min_price, creator, updater, deleted, tenant_id)
SELECT '演示-环氧煤沥青底漆', 'DEMO-P-0005', @category_anti, @unit_barrel, 0, '20kg/桶',
       '脱敏演示产品', 365, 20, 285, 420, 360,
       @admin_user_id, @admin_user_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_product WHERE bar_code = 'DEMO-P-0005' AND tenant_id = @tenant_id AND deleted = b'0');

SET @product_pipe = (SELECT id FROM erp_product WHERE bar_code = 'DEMO-P-0001' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @product_powder = (SELECT id FROM erp_product WHERE bar_code = 'DEMO-P-0002' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @product_pu = (SELECT id FROM erp_product WHERE bar_code = 'DEMO-P-0003' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @product_hdpe = (SELECT id FROM erp_product WHERE bar_code = 'DEMO-P-0004' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);
SET @product_paint = (SELECT id FROM erp_product WHERE bar_code = 'DEMO-P-0005' AND tenant_id = @tenant_id AND deleted = b'0' LIMIT 1);

-- 7. 采购订单：全部为草稿，供用户从审批管理页面提交
INSERT INTO erp_purchase_order
    (no, status, process_instance_id, supplier_id, account_id, order_time, total_count, total_price,
     total_product_price, total_tax_price, discount_percent, discount_price, deposit_price, remark,
     in_count, return_count, creator, updater, deleted, tenant_id)
SELECT 'CG-DEMO-20260903-001', 0, NULL, @supplier_pipe, @account_default, '2026-09-01 09:30:00', 120, 37968,
       33600, 4368, 0, 0, 5000, '脱敏演示：市政管网钢管原料采购', 0, 0,
       @purchase_creator_id, @purchase_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_purchase_order WHERE no = 'CG-DEMO-20260903-001' AND deleted = b'0');
SET @purchase_1 = (SELECT id FROM erp_purchase_order WHERE no = 'CG-DEMO-20260903-001' AND deleted = b'0' LIMIT 1);
INSERT INTO erp_purchase_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, in_count, return_count, creator, updater, deleted, tenant_id)
SELECT @purchase_1, @product_pipe, @unit_meter, 280, 120, 33600, 13, 4368,
       'DN219×6mm，演示批次', 0, 0, @purchase_creator_id, @purchase_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_purchase_order_items WHERE order_id = @purchase_1 AND product_id = @product_pipe AND deleted = b'0');

INSERT INTO erp_purchase_order
    (no, status, process_instance_id, supplier_id, account_id, order_time, total_count, total_price,
     total_product_price, total_tax_price, discount_percent, discount_price, deposit_price, remark,
     in_count, return_count, creator, updater, deleted, tenant_id)
SELECT 'CG-DEMO-20260903-002', 0, NULL, @supplier_anti, @account_default, '2026-09-02 10:20:00', 1020, 27346,
       24200, 3146, 0, 0, 3000, '脱敏演示：防腐涂层材料采购', 0, 0,
       @purchase_creator_id, @purchase_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_purchase_order WHERE no = 'CG-DEMO-20260903-002' AND deleted = b'0');
SET @purchase_2 = (SELECT id FROM erp_purchase_order WHERE no = 'CG-DEMO-20260903-002' AND deleted = b'0' LIMIT 1);
INSERT INTO erp_purchase_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, in_count, return_count, creator, updater, deleted, tenant_id)
SELECT @purchase_2, @product_powder, @unit_kg, 18.5, 1000, 18500, 13, 2405,
       '3PE粉末演示批次', 0, 0, @purchase_creator_id, @purchase_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_purchase_order_items WHERE order_id = @purchase_2 AND product_id = @product_powder AND deleted = b'0');
INSERT INTO erp_purchase_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, in_count, return_count, creator, updater, deleted, tenant_id)
SELECT @purchase_2, @product_paint, @unit_barrel, 285, 20, 5700, 13, 741,
       '环氧底漆演示批次', 0, 0, @purchase_creator_id, @purchase_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_purchase_order_items WHERE order_id = @purchase_2 AND product_id = @product_paint AND deleted = b'0');

INSERT INTO erp_purchase_order
    (no, status, process_instance_id, supplier_id, account_id, order_time, total_count, total_price,
     total_product_price, total_tax_price, discount_percent, discount_price, deposit_price, remark,
     in_count, return_count, creator, updater, deleted, tenant_id)
SELECT 'CG-DEMO-20260903-003', 0, NULL, @supplier_insulation, @account_project, '2026-09-03 08:45:00', 2800, 83620,
       74000, 9620, 0, 0, 10000, '脱敏演示：保温层组合材料采购', 0, 0,
       @purchase_creator_id, @purchase_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_purchase_order WHERE no = 'CG-DEMO-20260903-003' AND deleted = b'0');
SET @purchase_3 = (SELECT id FROM erp_purchase_order WHERE no = 'CG-DEMO-20260903-003' AND deleted = b'0' LIMIT 1);
INSERT INTO erp_purchase_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, in_count, return_count, creator, updater, deleted, tenant_id)
SELECT @purchase_3, @product_pu, @unit_kg, 21.8, 2500, 54500, 13, 7085,
       '聚氨酯组合料演示批次', 0, 0, @purchase_creator_id, @purchase_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_purchase_order_items WHERE order_id = @purchase_3 AND product_id = @product_pu AND deleted = b'0');
INSERT INTO erp_purchase_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, in_count, return_count, creator, updater, deleted, tenant_id)
SELECT @purchase_3, @product_hdpe, @unit_meter, 65, 300, 19500, 13, 2535,
       'HDPE外护管演示批次', 0, 0, @purchase_creator_id, @purchase_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_purchase_order_items WHERE order_id = @purchase_3 AND product_id = @product_hdpe AND deleted = b'0');

-- 8. 销售订单：全部为草稿，供用户从审批管理页面提交
INSERT INTO erp_sale_order
    (no, status, process_instance_id, customer_id, account_id, sale_user_id, order_time, total_count,
     total_price, total_product_price, total_tax_price, discount_percent, discount_price, deposit_price,
     remark, out_count, return_count, creator, updater, deleted, tenant_id)
SELECT 'XS-DEMO-20260903-001', 0, NULL, @customer_municipal, @account_default, @sale_creator_id,
       '2026-09-01 14:10:00', 580, 47686, 42200, 5486, 0, 0, 8000,
       '脱敏演示：市政管网防腐钢管销售', 0, 0, @sale_creator_id, @sale_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_sale_order WHERE no = 'XS-DEMO-20260903-001' AND deleted = b'0');
SET @sale_1 = (SELECT id FROM erp_sale_order WHERE no = 'XS-DEMO-20260903-001' AND deleted = b'0' LIMIT 1);
INSERT INTO erp_sale_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, out_count, return_count, creator, updater, deleted, tenant_id)
SELECT @sale_1, @product_pipe, @unit_meter, 365, 80, 29200, 13, 3796,
       '成品钢管演示订单项', 0, 0, @sale_creator_id, @sale_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_sale_order_items WHERE order_id = @sale_1 AND product_id = @product_pipe AND deleted = b'0');
INSERT INTO erp_sale_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, out_count, return_count, creator, updater, deleted, tenant_id)
SELECT @sale_1, @product_powder, @unit_kg, 26, 500, 13000, 13, 1690,
       '3PE粉末配套材料演示订单项', 0, 0, @sale_creator_id, @sale_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_sale_order_items WHERE order_id = @sale_1 AND product_id = @product_powder AND deleted = b'0');

INSERT INTO erp_sale_order
    (no, status, process_instance_id, customer_id, account_id, sale_user_id, order_time, total_count,
     total_price, total_product_price, total_tax_price, discount_percent, discount_price, deposit_price,
     remark, out_count, return_count, creator, updater, deleted, tenant_id)
SELECT 'XS-DEMO-20260903-002', 0, NULL, @customer_energy, @account_project, @sale_creator_id,
       '2026-09-02 15:30:00', 2020, 85925.2, 76040, 9885.2, 0, 0, 15000,
       '脱敏演示：能源管网聚氨酯保温材料销售', 0, 0, @sale_creator_id, @sale_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_sale_order WHERE no = 'XS-DEMO-20260903-002' AND deleted = b'0');
SET @sale_2 = (SELECT id FROM erp_sale_order WHERE no = 'XS-DEMO-20260903-002' AND deleted = b'0' LIMIT 1);
INSERT INTO erp_sale_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, out_count, return_count, creator, updater, deleted, tenant_id)
SELECT @sale_2, @product_pu, @unit_kg, 31, 1800, 55800, 13, 7254,
       '聚氨酯组合料演示订单项', 0, 0, @sale_creator_id, @sale_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_sale_order_items WHERE order_id = @sale_2 AND product_id = @product_pu AND deleted = b'0');
INSERT INTO erp_sale_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, out_count, return_count, creator, updater, deleted, tenant_id)
SELECT @sale_2, @product_hdpe, @unit_meter, 92, 220, 20240, 13, 2631.2,
       'HDPE外护管演示订单项', 0, 0, @sale_creator_id, @sale_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_sale_order_items WHERE order_id = @sale_2 AND product_id = @product_hdpe AND deleted = b'0');

INSERT INTO erp_sale_order
    (no, status, process_instance_id, customer_id, account_id, sale_user_id, order_time, total_count,
     total_price, total_product_price, total_tax_price, discount_percent, discount_price, deposit_price,
     remark, out_count, return_count, creator, updater, deleted, tenant_id)
SELECT 'XS-DEMO-20260903-003', 0, NULL, @customer_install, @account_default, @sale_creator_id,
       '2026-09-03 09:15:00', 40, 18984, 16800, 2184, 0, 0, 2000,
       '脱敏演示：防腐底漆销售', 0, 0, @sale_creator_id, @sale_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_sale_order WHERE no = 'XS-DEMO-20260903-003' AND deleted = b'0');
SET @sale_3 = (SELECT id FROM erp_sale_order WHERE no = 'XS-DEMO-20260903-003' AND deleted = b'0' LIMIT 1);
INSERT INTO erp_sale_order_items
    (order_id, product_id, product_unit_id, product_price, count, total_price, tax_percent, tax_price,
     remark, out_count, return_count, creator, updater, deleted, tenant_id)
SELECT @sale_3, @product_paint, @unit_barrel, 420, 40, 16800, 13, 2184,
       '环氧煤沥青底漆演示订单项', 0, 0, @sale_creator_id, @sale_creator_id, b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM erp_sale_order_items WHERE order_id = @sale_3 AND product_id = @product_paint AND deleted = b'0');

COMMIT;

-- 验证摘要
SELECT '供应商' AS data_type, COUNT(*) AS data_count FROM erp_supplier WHERE tenant_id = @tenant_id AND deleted = b'0'
UNION ALL SELECT '客户', COUNT(*) FROM erp_customer WHERE tenant_id = @tenant_id AND deleted = b'0'
UNION ALL SELECT '产品分类', COUNT(*) FROM erp_product_category WHERE tenant_id = @tenant_id AND deleted = b'0'
UNION ALL SELECT '计量单位', COUNT(*) FROM erp_product_unit WHERE tenant_id = @tenant_id AND deleted = b'0'
UNION ALL SELECT '产品', COUNT(*) FROM erp_product WHERE tenant_id = @tenant_id AND deleted = b'0'
UNION ALL SELECT '仓库', COUNT(*) FROM erp_warehouse WHERE tenant_id = @tenant_id AND deleted = b'0'
UNION ALL SELECT '结算账户', COUNT(*) FROM erp_account WHERE tenant_id = @tenant_id AND deleted = b'0'
UNION ALL SELECT '采购订单', COUNT(*) FROM erp_purchase_order WHERE tenant_id = @tenant_id AND deleted = b'0'
UNION ALL SELECT '销售订单', COUNT(*) FROM erp_sale_order WHERE tenant_id = @tenant_id AND deleted = b'0';
