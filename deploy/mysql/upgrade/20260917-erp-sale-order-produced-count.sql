-- ============================================
-- ERP 销售订单行新增「已生产数量」字段
-- 平台：ruoyi-vue-pro（txgy-module-erp）
-- 背景：MES→ERP 生产完工进度回传（对接链路 3）落地字段
-- 说明：MES 工单完工时按「销售订单号 + 物料编码」回传累计合格产量，
--       覆盖式写入该字段（非增量累加），天然幂等。
-- 注意：与 out_count（已出库）语义不同——已生产不等于已出库，故独立字段。
-- 执行环境：dev / prod 均适用
-- 幂等：使用 IF NOT EXISTS 语义（MySQL 8 不直接支持，改用存储过程判断）
-- ============================================

-- 变更 1：新增 produced_count 字段
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'erp_sale_order_items'
    AND COLUMN_NAME = 'produced_count'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE erp_sale_order_items ADD COLUMN produced_count decimal(24,6) NOT NULL DEFAULT 0.000000 COMMENT ''MES 累计完工合格数量'' AFTER out_count',
  'SELECT ''produced_count 已存在，跳过'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 变更 2：校验
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'erp_sale_order_items'
  AND COLUMN_NAME = 'produced_count';
