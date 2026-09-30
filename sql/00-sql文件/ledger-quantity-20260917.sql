-- =============================================================================
-- mes_pollution_ledger：加数量与来源类型，并把 source_check_id 放宽为可空
-- 日期：2026-09-17
-- 背景：本次做「治污设施换炭 → 废活性炭称重入危废台账」。
--   1) 危废台账的法定核心是"产生量"，原表没有任何数量字段 → 加 quantity / quantity_unit；
--   2) 台账原有唯一创建入口是"污染判定复核为有污染时登记"，而产废登记（换炭、换油等
--      确定性危废）没有对应的判定记录 → source_check_id 放宽为可空，并加 source_type 区分来源。
-- 注：MySQL 的 UNIQUE 索引允许多个 NULL，故 uk_ledger_source_check 无需改动、可保留。
--     source_record_no 仍是 NOT NULL——产废登记会写入作业票号，不受影响。
-- =============================================================================

ALTER TABLE `mes_pollution_ledger`
    ADD COLUMN `quantity` decimal(12,3) NULL COMMENT '数量（产废登记时填；判定驱动的行为 NULL）' AFTER `item_spec`,
    ADD COLUMN `quantity_unit` varchar(16) NULL COMMENT '数量单位（吨/千克）' AFTER `quantity`,
    ADD COLUMN `source_type` varchar(32) NULL COMMENT '来源类型：POLLUTION_CHECK(判定复核登记)/WASTE_REGISTER(产废登记)' AFTER `source_record_no`,
    MODIFY COLUMN `source_check_id` bigint NULL COMMENT '来源判定记录ID（产废登记时为空）';

-- 存量行全部是判定复核登记的，回填来源类型
UPDATE `mes_pollution_ledger` SET `source_type` = 'POLLUTION_CHECK' WHERE `source_type` IS NULL;
