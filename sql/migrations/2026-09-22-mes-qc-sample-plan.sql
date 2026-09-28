-- ============================================================================
-- R2: 检验抽样方案（Sample Plan）
--
-- 1. mes_qc_template_item：新增抽样方案配置（按 物料 维度配置）
--      sample_type           抽样方式：1=全检 2=固定数量 3=按比例
--      sample_fixed_quantity 固定抽样数量（sample_type=2 时生效）
--      sample_ratio          抽样比例 %（sample_type=3 时生效，向上取整）
-- 2. mes_qc_iqc / mes_qc_ipqc：新增抽样方案快照（JSON 文本，建单时固化）
--      sample_plan_snapshot
-- 3. 新增字典 mes_qc_sample_type
--
-- 说明：本脚本为幂等脚本（重复执行不报错），仅做加列 / 加字典，不改动既有数据。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. mes_qc_template_item 抽样方案字段
-- ---------------------------------------------------------------------------
SET @db := DATABASE();

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @db AND table_name = 'mes_qc_template_item' AND column_name = 'sample_type') = 0,
    'ALTER TABLE `mes_qc_template_item` ADD COLUMN `sample_type` tinyint NULL DEFAULT 1 COMMENT ''抽样方式：1=全检 2=固定数量 3=按比例'' AFTER `item_id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @db AND table_name = 'mes_qc_template_item' AND column_name = 'sample_fixed_quantity') = 0,
    'ALTER TABLE `mes_qc_template_item` ADD COLUMN `sample_fixed_quantity` decimal(14,2) NULL COMMENT ''固定抽样数量（抽样方式=固定数量时生效）'' AFTER `sample_type`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @db AND table_name = 'mes_qc_template_item' AND column_name = 'sample_ratio') = 0,
    'ALTER TABLE `mes_qc_template_item` ADD COLUMN `sample_ratio` decimal(14,2) NULL COMMENT ''抽样比例 %（抽样方式=按比例时生效，向上取整）'' AFTER `sample_fixed_quantity`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量数据兜底：抽样方式为空时默认全检
UPDATE `mes_qc_template_item` SET `sample_type` = 1 WHERE `sample_type` IS NULL;

-- ---------------------------------------------------------------------------
-- 2. mes_qc_iqc 抽样方案快照
-- ---------------------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @db AND table_name = 'mes_qc_iqc' AND column_name = 'sample_plan_snapshot') = 0,
    'ALTER TABLE `mes_qc_iqc` ADD COLUMN `sample_plan_snapshot` varchar(1000) NULL COMMENT ''抽样方案快照（JSON，建单时固化）'' AFTER `item_id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3. mes_qc_ipqc 抽样方案快照
-- ---------------------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @db AND table_name = 'mes_qc_ipqc' AND column_name = 'sample_plan_snapshot') = 0,
    'ALTER TABLE `mes_qc_ipqc` ADD COLUMN `sample_plan_snapshot` varchar(1000) NULL COMMENT ''抽样方案快照（JSON，建单时固化）'' AFTER `item_id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 4. 字典 mes_qc_sample_type（抽样方式）
-- ---------------------------------------------------------------------------
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 'MES 抽样方式', 'mes_qc_sample_type', 0, '1', NOW(), '1', NOW(), 0, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'mes_qc_sample_type' AND `deleted` = 0);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '全检', '1', 'mes_qc_sample_type', 0, 'default', '', '全部数量均需检验', '1', NOW(), '1', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'mes_qc_sample_type' AND `value` = '1' AND `deleted` = 0);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '固定数量', '2', 'mes_qc_sample_type', 0, 'default', '', '按固定数量抽样，不超过基数', '1', NOW(), '1', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'mes_qc_sample_type' AND `value` = '2' AND `deleted` = 0);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '按比例', '3', 'mes_qc_sample_type', 0, 'default', '', '按比例抽样，向上取整', '1', NOW(), '1', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'mes_qc_sample_type' AND `value` = '3' AND `deleted` = 0);
