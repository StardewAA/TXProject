-- =====================================================================
-- CRM 售后【回访记录 crm_after_sale_visit_record】补充 status 列
-- 日期：2026-09-14
-- 背景：提交 77306e98「回访任务完成后自动生成待填写回访记录」为
--       CrmAfterSaleVisitRecordDO 新增了 status 字段
--       （1=待填写 2=已填写，见 CrmAfterSaleVisitRecordStatusEnum），
--       但建表脚本 2026-09-07-crm-after-sale.sql 未同步该列，
--       导致回访记录列表查询/新增报 Unknown column 'status'。
-- 说明：本脚本幂等：通过 information_schema 判断列是否存在，不存在才新增。
--       默认值 2（已填写）为兜底语义：手动登记即为已完成填写的记录，
--       自动生成场景由 CrmAfterSaleVisitTaskServiceImpl 显式写入 1（待填写）。
-- 应用范围：ruoyi-vue-pro 业务库
-- =====================================================================

USE `ruoyi-vue-pro`;

SET NAMES utf8mb4;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'crm_after_sale_visit_record'
                 AND COLUMN_NAME = 'status');
SET @sql := IF(@exist = 0,
    'ALTER TABLE `crm_after_sale_visit_record` ADD COLUMN `status` tinyint NOT NULL DEFAULT 2 COMMENT ''记录状态：1=待填写 2=已填写'' AFTER `task_id`',
    'SELECT ''crm_after_sale_visit_record.status already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'crm_after_sale_visit_record'
  AND COLUMN_NAME = 'status';
