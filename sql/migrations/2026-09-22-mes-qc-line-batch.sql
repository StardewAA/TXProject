-- ============================================================================
-- R5: 检验结论回写批次质量状态 —— IQC/OQC/RQC 检验行增加 batch_id
--
-- 方案 A：在来料/出货/退货检验行上冗余关联库存批次 mes_wm_batch.id，
-- 检验完成(finish)时按行批次回写 quality_status，驱动 R4 质量门禁。
--
-- 本脚本幂等（information_schema 兜底），可重复执行。
-- ============================================================================

-- --------------------------- mes_qc_iqc_line ---------------------------
SET @col_exists = (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'mes_qc_iqc_line'
                     AND column_name = 'batch_id');
SET @sql = IF(@col_exists IS NULL,
    'ALTER TABLE mes_qc_iqc_line ADD COLUMN batch_id bigint DEFAULT NULL COMMENT ''关联库存批次ID（mes_wm_batch）''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- --------------------------- mes_qc_oqc_line ---------------------------
SET @col_exists = (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'mes_qc_oqc_line'
                     AND column_name = 'batch_id');
SET @sql = IF(@col_exists IS NULL,
    'ALTER TABLE mes_qc_oqc_line ADD COLUMN batch_id bigint DEFAULT NULL COMMENT ''关联库存批次ID（mes_wm_batch）''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- --------------------------- mes_qc_rqc_line ---------------------------
SET @col_exists = (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'mes_qc_rqc_line'
                     AND column_name = 'batch_id');
SET @sql = IF(@col_exists IS NULL,
    'ALTER TABLE mes_qc_rqc_line ADD COLUMN batch_id bigint DEFAULT NULL COMMENT ''关联库存批次ID（mes_wm_batch）''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
