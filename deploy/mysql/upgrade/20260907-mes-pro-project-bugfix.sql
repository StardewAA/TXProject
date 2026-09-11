-- ============================================================================
-- MES「项目管理」P0 Bug 修复 升级脚本
-- ----------------------------------------------------------------------------
-- 内容：
--   1. mes_pro_project 增加 owner_user_id（负责人用户编号）列
--      配合「负责人必须为系统用户」校验，负责人姓名由后端按用户自动回填
-- 说明：
--   1. 本脚本为幂等脚本，可重复执行
-- ============================================================================

SET NAMES utf8mb4;

-- 1. 项目负责人用户编号
SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'mes_pro_project'
                     AND COLUMN_NAME = 'owner_user_id');
SET @ddl = IF(@col_exists = 0,
              'ALTER TABLE `mes_pro_project` ADD COLUMN `owner_user_id` bigint NULL COMMENT ''负责人用户编号'' AFTER `owner`',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
