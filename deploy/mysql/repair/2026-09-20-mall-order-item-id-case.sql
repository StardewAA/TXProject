-- 2026-09-20：mall_202609201111.sql（Navicat dump）将列名写成混合大小写 order_item_Id，
-- 而 Linux 容器内 MySQL 列名区分大小写，MyBatis 查询 order_item_id 会报 Unknown column。
-- 统一改为小写，保持与 AfterSaleDO 字段/snake_case 一致。可重复执行。
SET @s = (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `trade_after_sale` CHANGE COLUMN `order_item_Id` `order_item_id` bigint NULL',
  'SELECT "trade_after_sale.order_item_id already normalized" AS note')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_after_sale' AND COLUMN_NAME = 'order_item_Id');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;