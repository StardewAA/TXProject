-- ============================================================================
-- CRM 报价单：新增「报价类型」字段（购销报价 / 采购报价）
-- 日期：2026-09-17
--
-- 业务含义：
--   1 = 购销报价（默认）：关联客户 + 商机，审批通过后转「购销合同」（我方为乙方）
--   2 = 采购报价        ：仅可选择「上游供应商」客户，禁用客户/商机字段，
--                         审批通过后转「采购合同」（我方为甲方）
--
-- 说明：
--   1. 供应商复用 crm_customer（客户类别 customer_type = 2 上游供应商），
--      因此报价单沿用 customer_id 存储往来单位，无需新增供应商字段。
--   2. 历史数据全部视为「购销报价」（DEFAULT 1）。
--   3. 本脚本幂等，可重复执行。
--
-- 执行方式（务必带 utf8mb4，否则中文会乱码）：
--   docker cp sql/2026-09-17-crm-quotation-type.sql <mysql容器>:/tmp/
--   docker exec <mysql容器> sh -c "mysql --default-character-set=utf8mb4 -uroot -p'xxx' ruoyi-vue-pro < /tmp/2026-09-17-crm-quotation-type.sql"
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. crm_quotation 新增 quotation_type 列（幂等）
-- ---------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_quotation' AND COLUMN_NAME = 'quotation_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `crm_quotation` ADD COLUMN `quotation_type` tinyint NOT NULL DEFAULT 1 COMMENT ''报价类型：1=购销报价 2=采购报价'' AFTER `no`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 兜底：历史数据显式置为「购销报价」
UPDATE `crm_quotation` SET `quotation_type` = 1 WHERE `quotation_type` IS NULL;

-- ---------------------------------------------------------------------------
-- 2. 字典类型 crm_quotation_type（幂等）
-- ---------------------------------------------------------------------------
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `updater`)
SELECT 'CRM 报价类型', 'crm_quotation_type', 0, 'CRM 报价类型（购销报价/采购报价）', '1', '1'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_quotation_type');

-- ---------------------------------------------------------------------------
-- 3. 字典数据（幂等）
-- ---------------------------------------------------------------------------
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 1, '购销报价', '1', 'crm_quotation_type', 0, 'primary', '', '关联客户与商机，审批通过后转购销（销售）合同', '1', '1'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_quotation_type' AND `value` = '1');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 2, '采购报价', '2', 'crm_quotation_type', 0, 'warning', '', '仅可选择上游供应商，审批通过后转采购合同', '1', '1'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_quotation_type' AND `value` = '2');
