-- 修复：wms_receipt_order_detail 缺少质检相关列，导致 WMS 入库单创建失败
--
-- 现象：供应链协同 → 推送 WMS（采购入库申请）时，WMS 端
--   WmsReceiptOrderDetailMapper.insert → BatchUpdateException: Unknown column 'quality_images' in 'field list'
--   → 接口返回 500「系统异常」，ERP 侧 erp_purchase_in.wms_status 回写 FAILED。
--
-- 原因：部分环境 wms_receipt_order_detail 为旧版本结构，缺 WmsReceiptOrderDetailDO 已声明的 6 个列。
--
-- ⚠️ 本脚本做的是「结构收敛」，不是单纯的「缺列就补」：
--    历史上这 6 个列存在两套互相冲突的定义，只判断列是否存在会导致不同环境结构不一致
--    （已执行过旧脚本的库保持旧宽度，新库用新宽度，静默漂移）。因此这里对每个列：
--      1) 不存在      → ADD COLUMN（用下面约定的规范定义）
--      2) 存在但类型不同 → MODIFY COLUMN 收敛到规范定义
--      3) 已一致      → 不动
--
-- 两套历史定义（差异已逐一核对）：
--   列名                       sql/wms_202609201121.sql(基线)   deploy/mysql/repair/2026-09-12-wms-receipt-acceptance.sql(旧修复)
--   quality_images             text                             varchar(8000)
--   quality_remark             varchar(255)                     varchar(1000)
--   quality_required_snapshot  bit(1)                           boolean(tinyint(1))
--   actual/qualified/unqualified_quantity  decimal(20,2)         decimal(20,2)  ← 一致
--
-- 规范取值与理由：
--   quality_images            text          —— 与基线一致；存放 JSON 数组，text 容量足够，varchar(8000) 放宽到 text 安全。
--   quality_remark            varchar(1000) —— 取两者中较宽者：加宽安全，收窄可能截断已有数据。
--   quality_required_snapshot bit(1)        —— 与基线及本表其他布尔列（deleted）一致；tinyint(1)→bit(1) 保持 0/1 语义。
--   三个数量列                 decimal(20,2) —— 两套定义本就一致。
--   （Java 侧 WmsReceiptOrderDetailDO 三个字段均为 String/Boolean，上述类型都能正确映射，无需改代码。）
--
-- 可重复执行。

SET @tbl := 'wms_receipt_order_detail';
SET @db := DATABASE();

-- ---------- 数量列：decimal(20,2) ----------
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE wms_receipt_order_detail ADD COLUMN `actual_quantity` decimal(20,2) DEFAULT NULL COMMENT ''实际入库数量''',
  IF(MAX(COLUMN_TYPE) <> 'decimal(20,2)',
     'ALTER TABLE wms_receipt_order_detail MODIFY COLUMN `actual_quantity` decimal(20,2) DEFAULT NULL COMMENT ''实际入库数量''',
     'DO 0'))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'actual_quantity');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE wms_receipt_order_detail ADD COLUMN `qualified_quantity` decimal(20,2) DEFAULT NULL COMMENT ''合格数量''',
  IF(MAX(COLUMN_TYPE) <> 'decimal(20,2)',
     'ALTER TABLE wms_receipt_order_detail MODIFY COLUMN `qualified_quantity` decimal(20,2) DEFAULT NULL COMMENT ''合格数量''',
     'DO 0'))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'qualified_quantity');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE wms_receipt_order_detail ADD COLUMN `unqualified_quantity` decimal(20,2) DEFAULT NULL COMMENT ''不合格数量''',
  IF(MAX(COLUMN_TYPE) <> 'decimal(20,2)',
     'ALTER TABLE wms_receipt_order_detail MODIFY COLUMN `unqualified_quantity` decimal(20,2) DEFAULT NULL COMMENT ''不合格数量''',
     'DO 0'))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'unqualified_quantity');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- quality_images：text ----------
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE wms_receipt_order_detail ADD COLUMN `quality_images` text COLLATE utf8mb4_unicode_ci COMMENT ''质检图片''',
  IF(MAX(COLUMN_TYPE) <> 'text',
     'ALTER TABLE wms_receipt_order_detail MODIFY COLUMN `quality_images` text COLLATE utf8mb4_unicode_ci COMMENT ''质检图片''',
     'DO 0'))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'quality_images');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- quality_remark：varchar(1000)（取较宽者，避免收窄截断） ----------
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE wms_receipt_order_detail ADD COLUMN `quality_remark` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''质检备注''',
  IF(MAX(COLUMN_TYPE) <> 'varchar(1000)',
     'ALTER TABLE wms_receipt_order_detail MODIFY COLUMN `quality_remark` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''质检备注''',
     'DO 0'))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'quality_remark');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- quality_required_snapshot：bit(1) ----------
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE wms_receipt_order_detail ADD COLUMN `quality_required_snapshot` bit(1) DEFAULT NULL COMMENT ''是否必检快照''',
  IF(MAX(COLUMN_TYPE) <> 'bit(1)',
     'ALTER TABLE wms_receipt_order_detail MODIFY COLUMN `quality_required_snapshot` bit(1) DEFAULT NULL COMMENT ''是否必检快照''',
     'DO 0'))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'quality_required_snapshot');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
