-- =====================================================================
-- CRM 客户/线索 customer_type（客户类别）列补齐
-- 日期：2026-09-12
-- 背景：CrmCustomerDO / CrmClueDO 新增 customerType 字段（用于区分下游客户/上游供应商），
--       但部分环境（按 deploy/mysql/init 脚本初始化的库）缺少该列，
--       导致查询/创建报价单、客户、线索时报 Unknown column 'customer_type'。
-- 说明：本脚本幂等：通过 information_schema 判断列是否存在，不存在才新增。
--       值含义：1 = 下游客户（采购方），2 = 上游供应商
-- =====================================================================

USE `ruoyi-vue-pro`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. crm_customer.customer_type
-- ---------------------------------------------------------------------
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'crm_customer'
                 AND COLUMN_NAME = 'customer_type');
SET @sql := IF(@exist = 0,
    'ALTER TABLE `crm_customer` ADD COLUMN `customer_type` tinyint NOT NULL DEFAULT 1 COMMENT ''客户类别：1=下游客户 2=上游供应商'' AFTER `level`',
    'SELECT ''crm_customer.customer_type already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. crm_clue.customer_type
-- ---------------------------------------------------------------------
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'crm_clue'
                 AND COLUMN_NAME = 'customer_type');
SET @sql := IF(@exist = 0,
    'ALTER TABLE `crm_clue` ADD COLUMN `customer_type` tinyint NOT NULL DEFAULT 1 COMMENT ''客户类别：1=下游客户 2=上游供应商'' AFTER `transform_status`',
    'SELECT ''crm_clue.customer_type already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 校验
-- ---------------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('crm_customer', 'crm_clue')
  AND COLUMN_NAME = 'customer_type';
