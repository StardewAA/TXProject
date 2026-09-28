-- 租户 2010 仓库测试数据（生成自 e2e/seed_wm_tenant2010.py）
-- 开头先按 id 段清掉上一次的种子数据，所以本文件可重复执行。
-- ⚠️ 整文件 UTF-8 喂 mysql：mysql --default-character-set=utf8mb4 < 本文件

SET NAMES utf8mb4;

DELETE FROM `mes_md_vendor` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_item_receipt` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_item_receipt_line` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_item_receipt_detail` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_misc_receipt` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_misc_receipt_line` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_misc_receipt_detail` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_misc_issue` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_misc_issue_line` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_misc_issue_detail` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_transfer` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_transfer_line` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_transfer_detail` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_material_stock` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;
DELETE FROM `mes_wm_transaction` WHERE id BETWEEN 9200000 AND 9299999 AND tenant_id=2010;

INSERT INTO `mes_md_vendor` (`id`, `code`, `name`, `level`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9200000, 'V-HZ-001', '北京华翰金属材料有限公司', 'A', 0, '仓库测试数据', '80370', '2026-09-10 09:00:00', '80370', '2026-09-10 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt` (`id`, `code`, `name`, `purchase_order_code`, `vendor_id`, `receipt_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9201000, 'IR-HZ-20260512001', '采购入库单', 'PO-HZ-20260512', 9200000, '2026-05-12 09:00:00', 4, '仓库测试数据', '80370', '2026-05-12 09:00:00', '80370', '2026-05-12 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt` (`id`, `code`, `name`, `purchase_order_code`, `vendor_id`, `receipt_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9201001, 'IR-HZ-20260520001', '采购入库单', 'PO-HZ-20260520', 9200000, '2026-05-20 09:00:00', 4, '仓库测试数据', '80370', '2026-05-20 09:00:00', '80370', '2026-05-20 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt` (`id`, `code`, `name`, `purchase_order_code`, `vendor_id`, `receipt_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9201002, 'IR-HZ-20260603001', '采购入库单', 'PO-HZ-20260603', 9200000, '2026-06-03 09:00:00', 4, '仓库测试数据', '80370', '2026-06-03 09:00:00', '80370', '2026-06-03 09:00:00', 2010);
INSERT INTO `mes_wm_misc_receipt` (`id`, `code`, `name`, `type`, `receipt_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9204000, 'MR-HZ-20260618001', '其他入库单', 1, '2026-06-18 09:00:00', 4, '仓库测试数据', '80370', '2026-06-18 09:00:00', '80370', '2026-06-18 09:00:00', 2010);
INSERT INTO `mes_wm_transfer` (`id`, `code`, `name`, `type`, `confirm_flag`, `transfer_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9210000, 'TR-HZ-20260708001', '库内调拨单', 1, 1, '2026-07-08 09:00:00', 4, '仓库测试数据', '80370', '2026-07-08 09:00:00', '80370', '2026-07-08 09:00:00', 2010);
INSERT INTO `mes_wm_transfer` (`id`, `code`, `name`, `type`, `confirm_flag`, `transfer_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9210001, 'TR-HZ-20260715001', '库内调拨单', 1, 1, '2026-07-15 09:00:00', 4, '仓库测试数据', '80370', '2026-07-15 09:00:00', '80370', '2026-07-15 09:00:00', 2010);
INSERT INTO `mes_wm_misc_issue` (`id`, `code`, `name`, `type`, `issue_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9207000, 'MI-HZ-20260805001', '其他出库单', 1, '2026-08-05 09:00:00', 4, '仓库测试数据', '80370', '2026-08-05 09:00:00', '80370', '2026-08-05 09:00:00', 2010);
INSERT INTO `mes_wm_misc_issue` (`id`, `code`, `name`, `type`, `issue_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9207001, 'MI-HZ-20260820001', '其他出库单', 1, '2026-08-20 09:00:00', 4, '仓库测试数据', '80370', '2026-08-20 09:00:00', '80370', '2026-08-20 09:00:00', 2010);
INSERT INTO `mes_wm_misc_issue` (`id`, `code`, `name`, `type`, `issue_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9207002, 'MI-HZ-20260902001', '其他出库单', 1, '2026-09-02 09:00:00', 4, '仓库测试数据', '80370', '2026-09-02 09:00:00', '80370', '2026-09-02 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt_line` (`id`, `receipt_id`, `item_id`, `received_quantity`, `batch_id`, `batch_code`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9202000, 9201000, 1100, 120, 1001, 'PC', '80370', '2026-05-12 09:00:00', '80370', '2026-05-12 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt_line` (`id`, `receipt_id`, `item_id`, `received_quantity`, `batch_id`, `batch_code`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9202001, 9201001, 1075, 300, 1003, 'PC202600001', '80370', '2026-05-20 09:00:00', '80370', '2026-05-20 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt_line` (`id`, `receipt_id`, `item_id`, `received_quantity`, `batch_id`, `batch_code`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9202002, 9201002, 1094, 500, 1007, 'BATCH_ITEM_94', '80370', '2026-06-03 09:00:00', '80370', '2026-06-03 09:00:00', 2010);
INSERT INTO `mes_wm_misc_receipt_line` (`id`, `receipt_id`, `item_id`, `quantity`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9205000, 9204000, 1072, 80, NULL, 704, 716, 727, '80370', '2026-06-18 09:00:00', '80370', '2026-06-18 09:00:00', 2010);
INSERT INTO `mes_wm_transfer_line` (`id`, `transfer_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `from_warehouse_id`, `from_location_id`, `from_area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9211000, 9210000, 9213002, 1094, 120, 1007, 704, 716, 727, '80370', '2026-07-08 09:00:00', '80370', '2026-07-08 09:00:00', 2010);
INSERT INTO `mes_wm_transfer_line` (`id`, `transfer_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `from_warehouse_id`, `from_location_id`, `from_area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9211001, 9210001, 9213000, 1100, 40, 1001, 704, 716, 727, '80370', '2026-07-15 09:00:00', '80370', '2026-07-15 09:00:00', 2010);
INSERT INTO `mes_wm_misc_issue_line` (`id`, `issue_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9208000, 9207000, 9213002, 1094, 150, 1007, 'BATCH_ITEM_94', 704, 716, 727, '80370', '2026-08-05 09:00:00', '80370', '2026-08-05 09:00:00', 2010);
INSERT INTO `mes_wm_misc_issue_line` (`id`, `issue_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9208001, 9207001, 9213005, 1094, 50, 1007, 'BATCH_ITEM_94', 705, 717, 728, '80370', '2026-08-20 09:00:00', '80370', '2026-08-20 09:00:00', 2010);
INSERT INTO `mes_wm_misc_issue_line` (`id`, `issue_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9208002, 9207002, 9213000, 1100, 30, 1001, 'PC', 704, 716, 727, '80370', '2026-09-02 09:00:00', '80370', '2026-09-02 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt_detail` (`id`, `line_id`, `receipt_id`, `item_id`, `quantity`, `batch_id`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9203000, 9202000, 9201000, 1100, 120, 1001, 704, 716, 727, '80370', '2026-05-12 09:00:00', '80370', '2026-05-12 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt_detail` (`id`, `line_id`, `receipt_id`, `item_id`, `quantity`, `batch_id`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9203001, 9202001, 9201001, 1075, 300, 1003, 704, 716, 727, '80370', '2026-05-20 09:00:00', '80370', '2026-05-20 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt_detail` (`id`, `line_id`, `receipt_id`, `item_id`, `quantity`, `batch_id`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9203002, 9202002, 9201002, 1094, 500, 1007, 704, 716, 727, '80370', '2026-06-03 09:00:00', '80370', '2026-06-03 09:00:00', 2010);
INSERT INTO `mes_wm_misc_receipt_detail` (`id`, `receipt_id`, `line_id`, `item_id`, `quantity`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9206000, 9204000, 9205000, 1072, 80, 704, 716, 727, '80370', '2026-06-18 09:00:00', '80370', '2026-06-18 09:00:00', 2010);
INSERT INTO `mes_wm_transfer_detail` (`id`, `line_id`, `transfer_id`, `item_id`, `quantity`, `to_warehouse_id`, `to_location_id`, `to_area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9212000, 9211000, 9210000, 1094, 120, 705, 717, 728, '80370', '2026-07-08 09:00:00', '80370', '2026-07-08 09:00:00', 2010);
INSERT INTO `mes_wm_transfer_detail` (`id`, `line_id`, `transfer_id`, `item_id`, `quantity`, `to_warehouse_id`, `to_location_id`, `to_area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9212001, 9211001, 9210001, 1100, 40, 705, 717, 729, '80370', '2026-07-15 09:00:00', '80370', '2026-07-15 09:00:00', 2010);
INSERT INTO `mes_wm_misc_issue_detail` (`id`, `issue_id`, `line_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9209000, 9207000, 9208000, 9213002, 1094, 150, 1007, 'BATCH_ITEM_94', 704, 716, 727, '80370', '2026-08-05 09:00:00', '80370', '2026-08-05 09:00:00', 2010);
INSERT INTO `mes_wm_misc_issue_detail` (`id`, `issue_id`, `line_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9209001, 9207001, 9208001, 9213005, 1094, 50, 1007, 'BATCH_ITEM_94', 705, 717, 728, '80370', '2026-08-20 09:00:00', '80370', '2026-08-20 09:00:00', 2010);
INSERT INTO `mes_wm_misc_issue_detail` (`id`, `issue_id`, `line_id`, `material_stock_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9209002, 9207002, 9208002, 9213000, 1100, 30, 1001, 'PC', 704, 716, 727, '80370', '2026-09-02 09:00:00', '80370', '2026-09-02 09:00:00', 2010);
INSERT INTO `mes_wm_material_stock` (`id`, `item_type_id`, `item_id`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `vendor_id`, `quantity`, `receipt_time`, `frozen`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9213000, 272, 1100, 1001, 'PC', 704, 716, 727, 9200000, 60, '2026-05-12 09:00:00', 0, '80370', '2026-09-10 09:00:00', '80370', '2026-09-10 09:00:00', 2010),
  (9213001, 277, 1075, 1003, 'PC202600001', 704, 716, 727, 9200000, 300, '2026-05-20 09:00:00', 0, '80370', '2026-09-10 09:00:00', '80370', '2026-09-10 09:00:00', 2010),
  (9213002, 282, 1094, 1007, 'BATCH_ITEM_94', 704, 716, 727, 9200000, 230, '2026-06-03 09:00:00', 0, '80370', '2026-09-10 09:00:00', '80370', '2026-09-10 09:00:00', 2010),
  (9213003, 274, 1072, NULL, NULL, 704, 716, 727, NULL, 80, '2026-06-18 09:00:00', 0, '80370', '2026-09-10 09:00:00', '80370', '2026-09-10 09:00:00', 2010),
  (9213005, 282, 1094, 1007, 'BATCH_ITEM_94', 705, 717, 728, NULL, 70, '2026-07-08 09:00:00', 0, '80370', '2026-09-10 09:00:00', '80370', '2026-09-10 09:00:00', 2010),
  (9213007, 272, 1100, 1001, 'PC', 705, 717, 729, NULL, 40, '2026-07-15 09:00:00', 0, '80370', '2026-09-10 09:00:00', '80370', '2026-09-10 09:00:00', 2010);
INSERT INTO `mes_wm_transaction` (`id`, `type`, `biz_type`, `biz_id`, `biz_code`, `biz_line_id`, `material_stock_id`, `related_transaction_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `transaction_time`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9214000, 1, 110, 9201000, 'IR-HZ-20260512001', 9202000, 9213000, NULL, 1100, 120, 1001, 'PC', 704, 716, 727, '2026-05-12 09:00:00', '80370', '2026-05-12 09:00:00', '80370', '2026-05-12 09:00:00', 2010),
  (9214001, 1, 110, 9201001, 'IR-HZ-20260520001', 9202001, 9213001, NULL, 1075, 300, 1003, 'PC202600001', 704, 716, 727, '2026-05-20 09:00:00', '80370', '2026-05-20 09:00:00', '80370', '2026-05-20 09:00:00', 2010),
  (9214002, 1, 110, 9201002, 'IR-HZ-20260603001', 9202002, 9213002, NULL, 1094, 500, 1007, 'BATCH_ITEM_94', 704, 716, 727, '2026-06-03 09:00:00', '80370', '2026-06-03 09:00:00', '80370', '2026-06-03 09:00:00', 2010),
  (9214003, 1, 114, 9204000, 'MR-HZ-20260618001', 9205000, 9213003, NULL, 1072, 80, NULL, NULL, 704, 716, 727, '2026-06-18 09:00:00', '80370', '2026-06-18 09:00:00', '80370', '2026-06-18 09:00:00', 2010),
  (9214004, 3, 111, 9210000, 'TR-HZ-20260708001', 9211000, 9213002, NULL, 1094, -120, 1007, 'BATCH_ITEM_94', 704, 716, 727, '2026-07-08 09:00:00', '80370', '2026-07-08 09:00:00', '80370', '2026-07-08 09:00:00', 2010),
  (9214005, 4, 112, 9210000, 'TR-HZ-20260708001', 9211000, 9213005, 9214004, 1094, 120, 1007, 'BATCH_ITEM_94', 705, 717, 728, '2026-07-08 09:00:00', '80370', '2026-07-08 09:00:00', '80370', '2026-07-08 09:00:00', 2010),
  (9214006, 3, 111, 9210001, 'TR-HZ-20260715001', 9211001, 9213000, NULL, 1100, -40, 1001, 'PC', 704, 716, 727, '2026-07-15 09:00:00', '80370', '2026-07-15 09:00:00', '80370', '2026-07-15 09:00:00', 2010),
  (9214007, 4, 112, 9210001, 'TR-HZ-20260715001', 9211001, 9213007, 9214006, 1100, 40, 1001, 'PC', 705, 717, 729, '2026-07-15 09:00:00', '80370', '2026-07-15 09:00:00', '80370', '2026-07-15 09:00:00', 2010),
  (9214008, 2, 113, 9207000, 'MI-HZ-20260805001', 9208000, 9213002, NULL, 1094, -150, 1007, 'BATCH_ITEM_94', 704, 716, 727, '2026-08-05 09:00:00', '80370', '2026-08-05 09:00:00', '80370', '2026-08-05 09:00:00', 2010),
  (9214009, 2, 113, 9207001, 'MI-HZ-20260820001', 9208001, 9213005, NULL, 1094, -50, 1007, 'BATCH_ITEM_94', 705, 717, 728, '2026-08-20 09:00:00', '80370', '2026-08-20 09:00:00', '80370', '2026-08-20 09:00:00', 2010),
  (9214010, 2, 113, 9207002, 'MI-HZ-20260902001', 9208002, 9213000, NULL, 1100, -30, 1001, 'PC', 704, 716, 727, '2026-09-02 09:00:00', '80370', '2026-09-02 09:00:00', '80370', '2026-09-02 09:00:00', 2010);

-- ==================== 孤儿库存行归位 ====================
-- mes_wm_material_stock 130/131：有库存、无单据、无流水，且指向租户 1 的仓库/物料。
-- 130 直接归位到本租户 item 1094；131 并入同名库存行后软删。
DELETE FROM `mes_wm_item_receipt` WHERE id BETWEEN 9190000 AND 9199999 AND tenant_id=2010;
DELETE FROM `mes_wm_item_receipt_line` WHERE id BETWEEN 9190000 AND 9199999 AND tenant_id=2010;
DELETE FROM `mes_wm_item_receipt_detail` WHERE id BETWEEN 9190000 AND 9199999 AND tenant_id=2010;
DELETE FROM `mes_wm_transaction` WHERE id BETWEEN 9190000 AND 9199999 AND tenant_id=2010;

INSERT INTO `mes_wm_item_receipt` (`id`, `code`, `name`, `vendor_id`, `receipt_date`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9190000, 'IR-HZ-20260430001', '期初结存归位', 9200000, '2026-04-30 09:00:00', 4, '补 mes_wm_material_stock 130/131 的来源单据', '80370', '2026-04-30 09:00:00', '80370', '2026-04-30 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt_line` (`id`, `receipt_id`, `item_id`, `received_quantity`, `batch_id`, `batch_code`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9190100, 9190000, 1094, 10, 1009, 'RAW-BATCH-001', '80370', '2026-04-30 09:00:00', '80370', '2026-04-30 09:00:00', 2010),
  (9190101, 9190000, 1100, 10, 1001, 'PC', '80370', '2026-04-30 09:00:00', '80370', '2026-04-30 09:00:00', 2010);
INSERT INTO `mes_wm_item_receipt_detail` (`id`, `line_id`, `receipt_id`, `item_id`, `quantity`, `batch_id`, `warehouse_id`, `location_id`, `area_id`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9190200, 9190100, 9190000, 1094, 10, 1009, 704, 716, 727, '80370', '2026-04-30 09:00:00', '80370', '2026-04-30 09:00:00', 2010),
  (9190201, 9190101, 9190000, 1100, 10, 1001, 704, 716, 727, '80370', '2026-04-30 09:00:00', '80370', '2026-04-30 09:00:00', 2010);
INSERT INTO `mes_wm_transaction` (`id`, `type`, `biz_type`, `biz_id`, `biz_code`, `biz_line_id`, `material_stock_id`, `related_transaction_id`, `item_id`, `quantity`, `batch_id`, `batch_code`, `warehouse_id`, `location_id`, `area_id`, `transaction_time`, `creator`, `create_time`, `updater`, `update_time`, `tenant_id`) VALUES
  (9190300, 1, 110, 9190000, 'IR-HZ-20260430001', 9190100, 130, NULL, 1094, 10, 1009, 'RAW-BATCH-001', 704, 716, 727, '2026-04-30 09:00:00', '80370', '2026-04-30 09:00:00', '80370', '2026-04-30 09:00:00', 2010),
  (9190301, 1, 110, 9190000, 'IR-HZ-20260430001', 9190101, 9213000, NULL, 1100, 10, 1001, 'PC', 704, 716, 727, '2026-04-30 09:00:00', '80370', '2026-04-30 09:00:00', '80370', '2026-04-30 09:00:00', 2010);
UPDATE `mes_wm_material_stock` SET `item_type_id`=282, `item_id`=1094, `batch_id`=1009, `batch_code`='RAW-BATCH-001', `warehouse_id`=704, `location_id`=716, `area_id`=727, `vendor_id`=9200000, `quantity`=10, `receipt_time`='2026-04-30 09:00:00', `frozen`=0, `updater`='80370', `update_time`='2026-09-10 09:00:00' WHERE `id`=130 AND `tenant_id`=2010;
-- 131 与上面 item1100/批次PC 那张库存行同 key，数量已并入，这里只置软删留痕
UPDATE `mes_wm_material_stock` SET `quantity`=0, `deleted`=1, `updater`='80370', `update_time`='2026-09-10 09:00:00' WHERE `id`=131 AND `tenant_id`=2010;
