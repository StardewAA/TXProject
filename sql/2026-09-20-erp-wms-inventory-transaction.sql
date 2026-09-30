-- ============================================================================
-- 方案A（WMS 作为实物唯一数据源）—— ERP 侧改造
-- 2026-09-20
--
-- 背景（来自答辩参考文档「方案A」）：
--   1. 所有实物动作（MES 生产入库、商城出库、ERP 销售出库）全部在 WMS 内执行；
--   2. WMS 每发生一笔实物异动（入库、出库、退货、盘点调整），通过 API 把「库存事务单据」推给 ERP；
--   3. ERP 收到推送后，自动生成对应的入库单 / 销售出库单 / 盘点调整单，并自动更新账面库存。
--
-- 本脚本做两件事：
--   1. 给 ERP 的「其它入库单」「其它出库单」加上来源标记字段，让 WMS 自动生成单据可溯源；
--   2. （可选）演示数据见文件末尾。
--
-- 可重复执行：ALTER 前先查 information_schema，存在则跳过。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、其它入库单 erp_stock_in 增加来源字段
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'erp_stock_in' AND column_name = 'source_system');
SET @s := IF(@c = 0,
  'ALTER TABLE `erp_stock_in` ADD COLUMN `source_system` varchar(32) DEFAULT NULL COMMENT ''来源系统：ERP 手工制单 / WMS 推送''',
  'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'erp_stock_in' AND column_name = 'source_ref_no');
SET @s := IF(@c = 0,
  'ALTER TABLE `erp_stock_in` ADD COLUMN `source_ref_no` varchar(64) DEFAULT NULL COMMENT ''来源单号：WMS 库存事务号''',
  'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------------------------------------------------------------------------
-- 二、其它出库单 erp_stock_out 增加来源字段
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'erp_stock_out' AND column_name = 'source_system');
SET @s := IF(@c = 0,
  'ALTER TABLE `erp_stock_out` ADD COLUMN `source_system` varchar(32) DEFAULT NULL COMMENT ''来源系统：ERP 手工制单 / WMS 推送''',
  'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'erp_stock_out' AND column_name = 'source_ref_no');
SET @s := IF(@c = 0,
  'ALTER TABLE `erp_stock_out` ADD COLUMN `source_ref_no` varchar(64) DEFAULT NULL COMMENT ''来源单号：WMS 库存事务号''',
  'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------------------------------------------------------------------------
-- 三、校验
-- ---------------------------------------------------------------------------
SELECT table_name, column_name, column_type, column_comment
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name IN ('erp_stock_in', 'erp_stock_out')
   AND column_name IN ('source_system', 'source_ref_no')
 ORDER BY table_name, column_name;
