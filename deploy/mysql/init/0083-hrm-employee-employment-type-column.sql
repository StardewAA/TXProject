-- HRM 员工档案用工形式字段。

SET @schema_name := DATABASE();
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'hrm_employee'
     AND COLUMN_NAME = 'employment_type_id') = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `employment_type_id` bigint DEFAULT NULL COMMENT ''用工形式编号（hrm_employment_type.id）'' AFTER `type`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
