-- =====================================================================
-- R5 质量模块：OQC / IQC / RQC 表补充关联库存批次字段 batch_id
-- 背景：检验结果需关联库存批次（mes_wm_batch.id），检验完成(finish)时按本单批次
--       回写质量状态，驱动 R4 入库/出库门禁。R5 代码已引用该列，但缺少 DB 迁移。
-- 涉及表：mes_qc_oqc、mes_qc_oqc_line、mes_qc_iqc、mes_qc_iqc_line、
--         mes_qc_rqc、mes_qc_rqc_line
-- 幂等：可重复执行（information_schema 判断 + PREPARE）
-- =====================================================================

-- 出货检验表头
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mes_qc_oqc'
      AND COLUMN_NAME = 'batch_id') = 0,
  'ALTER TABLE mes_qc_oqc ADD COLUMN batch_id BIGINT NULL COMMENT ''关联库存批次ID(mes_wm_batch.id)；检验完成时按本单批次回写质量状态'' AFTER batch_code',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 出货检验行
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mes_qc_oqc_line'
      AND COLUMN_NAME = 'batch_id') = 0,
  'ALTER TABLE mes_qc_oqc_line ADD COLUMN batch_id BIGINT NULL COMMENT ''关联库存批次ID(mes_wm_batch.id)；检验完成时按本单批次回写质量状态'' AFTER indicator_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 来料检验表头
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mes_qc_iqc'
      AND COLUMN_NAME = 'batch_id') = 0,
  'ALTER TABLE mes_qc_iqc ADD COLUMN batch_id BIGINT NULL COMMENT ''关联库存批次ID(mes_wm_batch.id)；检验完成时按本单批次回写质量状态'' AFTER item_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 来料检验行
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mes_qc_iqc_line'
      AND COLUMN_NAME = 'batch_id') = 0,
  'ALTER TABLE mes_qc_iqc_line ADD COLUMN batch_id BIGINT NULL COMMENT ''关联库存批次ID(mes_wm_batch.id)；检验完成时按本单批次回写质量状态'' AFTER indicator_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 退货检验表头
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mes_qc_rqc'
      AND COLUMN_NAME = 'batch_id') = 0,
  'ALTER TABLE mes_qc_rqc ADD COLUMN batch_id BIGINT NULL COMMENT ''关联库存批次ID(mes_wm_batch.id)；检验完成时按本单批次回写质量状态'' AFTER batch_code',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 退货检验行
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mes_qc_rqc_line'
      AND COLUMN_NAME = 'batch_id') = 0,
  'ALTER TABLE mes_qc_rqc_line ADD COLUMN batch_id BIGINT NULL COMMENT ''关联库存批次ID(mes_wm_batch.id)；检验完成时按本单批次回写质量状态'' AFTER indicator_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
