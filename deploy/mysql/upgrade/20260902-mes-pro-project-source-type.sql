-- ============================================================================
-- MES 项目管理：补充「项目来源类型」
-- ----------------------------------------------------------------------------
-- 背景：业务流程图要求项目可来源于「客户订单」或「库存备库」，原表仅有来源单号
--       order_source_code（纯文本），缺少来源类型，无法区分与统计。
-- 内容：
--   1. mes_pro_project 增加 source_type 列
--   2. 新增字典 mes_pro_project_source_type
-- 重复执行：幂等设计，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

-- 1. 增加来源类型列（幂等：不存在才加列） ------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_pro_project' AND COLUMN_NAME = 'source_type');
SET @col_ddl := IF(@col_exists = 0,
    'ALTER TABLE `mes_pro_project` ADD COLUMN `source_type` tinyint NULL DEFAULT NULL COMMENT ''项目来源类型：1销售订单 2库存备库 3其他'' AFTER `order_source_code`',
    'SELECT 1');
PREPARE col_stmt FROM @col_ddl;
EXECUTE col_stmt;
DEALLOCATE PREPARE col_stmt;

-- 2. 项目来源类型字典 --------------------------------------------------------
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_pro_project_source_type';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_pro_project_source_type';

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES ('MES 项目来源类型', 'mes_pro_project_source_type', 0, NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (1, '销售订单', '1', 'mes_pro_project_source_type', 0, 'primary', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (2, '库存备库', '2', 'mes_pro_project_source_type', 0, 'warning', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (3, '其他', '3', 'mes_pro_project_source_type', 0, 'default', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');
