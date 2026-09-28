-- =====================================================================
-- CRM 客户上下游分类：补 crm_customer / crm_clue 的 customer_type 列 + 相关字典
-- 日期：2026-09-11
-- 背景：commit 6ec5dff9（客户上下游分类打通）在 DO/VO 中新增了 customer_type 字段，
--       但未随代码提交建表与字典脚本，会导致 CRM 客户/线索列表查询报
--       Unknown column 'customer_type'。本脚本补齐缺失部分。
-- 说明：
--   1. 本脚本幂等，可重复执行。
--   2. 字典取值沿用仓库既有方案（见 sql/00-sql文件/crm_industry_category_dict.sql）：
--        crm_customer_category           1=下游客户 / 2=上游供应商
--        crm_customer_industry_downstream 101-110（下游行业，级联下拉）
--        crm_customer_industry_upstream   201-206（上游行业，级联下拉）
--        crm_customer_industry            列表/详情展示用合并字典
--   3. 为不破坏历史数据，合并字典 crm_customer_industry 采用「保留旧值 + 追加新值」，
--      不执行仓库脚本中的 DELETE，历史客户的 industry_id(1-8) 仍可正常展示。
-- 应用范围：ruoyi-vue-pro 业务库
-- =====================================================================

USE `ruoyi-vue-pro`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. crm_customer.customer_type
-- ---------------------------------------------------------------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'ruoyi-vue-pro' AND TABLE_NAME = 'crm_customer'
                   AND COLUMN_NAME = 'customer_type');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE `crm_customer` ADD COLUMN `customer_type` tinyint NULL DEFAULT 1 COMMENT ''客户类别：1=下游客户(采购方)；2=上游供应商'' AFTER `source`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. crm_clue.customer_type
-- ---------------------------------------------------------------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'ruoyi-vue-pro' AND TABLE_NAME = 'crm_clue'
                   AND COLUMN_NAME = 'customer_type');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE `crm_clue` ADD COLUMN `customer_type` tinyint NULL DEFAULT 1 COMMENT ''客户类别：1=下游客户(采购方)；2=上游供应商'' AFTER `source`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 字典类型
-- ---------------------------------------------------------------------
INSERT INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 99001, 'CRM 客户类别', 'crm_customer_category', 0, 'CRM 客户类别（下游客户/上游供应商）', '1', NOW(), '1', NOW(), b'0', NULL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_customer_category');

INSERT INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 99002, 'CRM 下游客户行业', 'crm_customer_industry_downstream', 0, 'CRM 下游客户行业', '1', NOW(), '1', NOW(), b'0', NULL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_customer_industry_downstream');

INSERT INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 99003, 'CRM 上游供应商行业', 'crm_customer_industry_upstream', 0, 'CRM 上游供应商行业', '1', NOW(), '1', NOW(), b'0', NULL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_customer_industry_upstream');

-- ---------------------------------------------------------------------
-- 4. 字典数据：下游客户行业（101-110）
-- ---------------------------------------------------------------------
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99101, 1, '石油化工', '101', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '101');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99102, 2, '电力热力', '102', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '102');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99103, 3, '冶金钢铁', '103', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '103');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99104, 4, '新能源(风电/光伏/储能)', '104', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '104');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99105, 5, '建筑工程总包', '105', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '105');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99106, 6, '海工船舶', '106', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '106');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99107, 7, '轨道交通', '107', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '107');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99108, 8, '医药食品', '108', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '108');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99109, 9, '渠道经销商', '109', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '109');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99110, 10, '工程施工单位', '110', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_downstream' AND `value` = '110');

-- ---------------------------------------------------------------------
-- 5. 字典数据：上游供应商行业（201-206）
-- ---------------------------------------------------------------------
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99201, 1, '化工原料厂', '201', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_upstream' AND `value` = '201');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99202, 2, '保温材料厂', '202', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_upstream' AND `value` = '202');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99203, 3, '金属材料厂商', '203', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_upstream' AND `value` = '203');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99204, 4, '橡塑塑胶厂商', '204', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_upstream' AND `value` = '204');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99205, 5, '填料辅料厂商', '205', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_upstream' AND `value` = '205');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99206, 6, '包装材料供应商', '206', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry_upstream' AND `value` = '206');

-- ---------------------------------------------------------------------
-- 6. 字典数据：客户类别（1=下游客户 / 2=上游供应商）
-- ---------------------------------------------------------------------
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99401, 1, '下游客户', '1', 'crm_customer_category', 0, 'default', '', '向我方采购', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_category' AND `value` = '1');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99402, 2, '上游供应商', '2', 'crm_customer_category', 0, 'default', '', '向我方供货', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_category' AND `value` = '2');

-- ---------------------------------------------------------------------
-- 7. 合并展示字典 crm_customer_industry：追加新值 101-206（保留历史 1-8）
-- ---------------------------------------------------------------------
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99301, 101, '石油化工', '101', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '101');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99302, 102, '电力热力', '102', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '102');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99303, 103, '冶金钢铁', '103', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '103');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99304, 104, '新能源(风电/光伏/储能)', '104', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '104');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99305, 105, '建筑工程总包', '105', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '105');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99306, 106, '海工船舶', '106', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '106');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99307, 107, '轨道交通', '107', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '107');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99308, 108, '医药食品', '108', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '108');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99309, 109, '渠道经销商', '109', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '109');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99310, 110, '工程施工单位', '110', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '110');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99311, 201, '化工原料厂', '201', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '201');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99312, 202, '保温材料厂', '202', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '202');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99313, 203, '金属材料厂商', '203', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '203');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99314, 204, '橡塑塑胶厂商', '204', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '204');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99315, 205, '填料辅料厂商', '205', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '205');
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 99316, 206, '包装材料供应商', '206', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_industry' AND `value` = '206');
