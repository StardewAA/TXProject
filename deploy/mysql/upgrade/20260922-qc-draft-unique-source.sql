-- =============================================================================
-- MES 质量管理 R1 —— IQC / IPQC 自动建单防重复（来源单据行唯一约束）
-- -----------------------------------------------------------------------------
-- 背景：到货通知进入「待质检」、报工进入「待检验」时会自动创建 IQC/IPQC 草稿单。
--       代码侧仅靠「先查后插」（selectFirstBySourceDoc）+ REQUIRES_NEW 幂等，
--       并发下两个请求可能都查不到来源单，从而各插入一条，产生重复检验单。
-- 方案：为两张检验单表补一个「来源单据行」唯一索引，从数据库层兜住并发重复建单。
--       仅新增索引，不新增/修改字段，不改业务数据模型，不清理任何历史数据。
-- 关键：source_doc_type / source_doc_id / source_line_id 三列均允许 NULL，
--       InnoDB 唯一索引不校验含 NULL 的行，因此：
--         · 人工创建的检验单（来源三列为空）不受该约束影响，可正常共存；
--         · 自动建单固定写入三列非空值，必然进入唯一性校验，防重生效。
-- 幂等：可重复执行，索引已存在时自动跳过。
-- 日期：2026-09-22
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0. 前置检查（应返回 0 行）
--    若返回结果，说明存在重复来源行，请先人工确认，不要继续执行第 2 步。
-- ---------------------------------------------------------------------------
SELECT 'mes_qc_iqc' AS tbl, tenant_id, source_doc_type, source_doc_id, source_line_id, COUNT(*) AS cnt
FROM mes_qc_iqc
WHERE source_doc_type IS NOT NULL
  AND source_doc_id IS NOT NULL
  AND source_line_id IS NOT NULL
GROUP BY tenant_id, source_doc_type, source_doc_id, source_line_id
HAVING cnt > 1;

SELECT 'mes_qc_ipqc' AS tbl, tenant_id, source_doc_type, source_doc_id, source_line_id, COUNT(*) AS cnt
FROM mes_qc_ipqc
WHERE source_doc_type IS NOT NULL
  AND source_doc_id IS NOT NULL
  AND source_line_id IS NOT NULL
GROUP BY tenant_id, source_doc_type, source_doc_id, source_line_id
HAVING cnt > 1;

-- ---------------------------------------------------------------------------
-- 1. 历史数据
--    经核查：mes_qc_iqc（10 行）、mes_qc_ipqc（4 行）均不存在重复来源行，
--    且现有记录均来自初始化种子数据。因此本迁移：
--      · 不做任何数据删除；
--      · 不修改人工创建的检验单；
--      · 不修改 source_doc_type / source_doc_id / source_line_id 的业务数据。
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 2. 创建唯一索引（幂等）
-- ---------------------------------------------------------------------------
SET @schema_name = DATABASE();

-- 2.1 mes_qc_iqc：同一租户 + 同一来源单据行，只允许存在一张来料检验单
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'mes_qc_iqc'
      AND index_name = 'uk_iqc_source');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `mes_qc_iqc` ADD UNIQUE KEY `uk_iqc_source` (`tenant_id`, `source_doc_type`, `source_doc_id`, `source_line_id`)',
    'SELECT ''uk_iqc_source 已存在，跳过'' AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2 mes_qc_ipqc：同一租户 + 同一来源单据行，只允许存在一张过程检验单
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'mes_qc_ipqc'
      AND index_name = 'uk_ipqc_source');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `mes_qc_ipqc` ADD UNIQUE KEY `uk_ipqc_source` (`tenant_id`, `source_doc_type`, `source_doc_id`, `source_line_id`)',
    'SELECT ''uk_ipqc_source 已存在，跳过'' AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3. 回滚（如需，逐条手动执行）
--    索引为纯新增约束，不含任何数据变更，回滚仅删索引、不影响数据。
-- ---------------------------------------------------------------------------
-- ALTER TABLE `mes_qc_iqc`  DROP INDEX `uk_iqc_source`;
-- ALTER TABLE `mes_qc_ipqc` DROP INDEX `uk_ipqc_source`;
