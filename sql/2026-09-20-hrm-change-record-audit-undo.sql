-- =============================================================
-- HRM 异动台账补全：审计留痕（谁办的、谁批的）+ 未生效异动撤销
-- 幂等：可重复执行
-- =============================================================

SET @db := DATABASE();

-- ---------- 1) 审计字段 ----------
SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `source` tinyint NOT NULL DEFAULT 1 COMMENT ''来源：1后台办理 2审批通过 3系统生效 4再入职 5取消离职''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'source');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `operator_user_id` bigint NULL COMMENT ''经办人（系统用户编号）''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'operator_user_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''经办人姓名''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'operator_name');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `approval_id` bigint NULL COMMENT ''异动审批单编号''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'approval_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `approver_user_id` bigint NULL COMMENT ''审批人（系统用户编号）''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'approver_user_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `approver_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''审批人姓名''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'approver_name');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `approve_time` datetime NULL COMMENT ''审批通过时间''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'approve_time');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2) 撤销字段 ----------
SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `cancel_status` tinyint NOT NULL DEFAULT 0 COMMENT ''撤销状态：0正常 1已撤销''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'cancel_status');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `cancel_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''撤销原因''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'cancel_reason');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `cancel_time` datetime NULL COMMENT ''撤销时间''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'cancel_time');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `cancel_user_id` bigint NULL COMMENT ''撤销人（系统用户编号）''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'cancel_user_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_change_record` ADD COLUMN `cancel_user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''撤销人姓名''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_change_record' AND column_name = 'cancel_user_name');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史数据：已生效的异动统一标记为「系统生效」，未生效的标记为「后台办理」
UPDATE `hrm_employee_change_record` SET `source` = 3 WHERE `applied_time` IS NOT NULL;
