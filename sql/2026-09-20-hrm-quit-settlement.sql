-- =============================================================
-- HRM 离职闭环（二）：计薪截止日 + 工资结清确认
-- 说明：计薪截止日默认等于离职生效日，可被 HR 提前（按班组结算周期封账）；
--      考勤与工资计算按截止日封账，避免离职后继续生成缺勤与工资。
-- 幂等：可重复执行
-- =============================================================

SET @db := DATABASE();

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_quit_info` ADD COLUMN `salary_cutoff_date` date NULL COMMENT ''计薪截止日（默认=离职生效日）''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_quit_info'
      AND column_name = 'salary_cutoff_date');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_quit_info` ADD COLUMN `salary_settled` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''工资是否已结清''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_quit_info'
      AND column_name = 'salary_settled');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_quit_info` ADD COLUMN `salary_settle_time` datetime NULL COMMENT ''工资结清时间''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_quit_info'
      AND column_name = 'salary_settle_time');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_quit_info` ADD COLUMN `salary_settle_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''结算说明''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_quit_info'
      AND column_name = 'salary_settle_remark');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史数据：已有离职单的计薪截止日回填为计划离职日期（按天）
UPDATE `hrm_employee_quit_info`
SET `salary_cutoff_date` = DATE(`plan_quit_time`)
WHERE `salary_cutoff_date` IS NULL AND `plan_quit_time` IS NOT NULL;
