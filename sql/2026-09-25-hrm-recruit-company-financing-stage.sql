-- HRM 招聘企业融资阶段。可重复执行，适用于已有招聘企业档案表的环境。
SET @schema_name := DATABASE();
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'hrm_recruit_company_profile' AND COLUMN_NAME = 'financing_stage') = 0,
  'ALTER TABLE `hrm_recruit_company_profile` ADD COLUMN `financing_stage` varchar(64) DEFAULT NULL COMMENT ''融资阶段：不融资、股权融资、上市融资'' AFTER `scale`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
