-- HRM 招聘企业主页公司相册。

SET @schema_name := DATABASE();
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'hrm_recruit_company_profile'
     AND COLUMN_NAME = 'album') = 0,
  'ALTER TABLE `hrm_recruit_company_profile` ADD COLUMN `album` text DEFAULT NULL COMMENT ''公司相册图片 URL JSON 数组'' AFTER `products`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
