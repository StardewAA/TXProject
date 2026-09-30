-- ============================================================
-- HRM 异动审批「审批通过后真正落地」所需的目标字段
--
-- 背景（测试报告 H-04 / H-05）：审批单只存了类型/原因/期望生效时间，
-- 审批通过后只有「转正」会生效，调岗/晋升与离职分支什么都不做。
--
-- 本脚本为审批单补充目标值：调岗/晋升的目标任职信息、离职的离职类型与结算信息。
-- 幂等：可重复执行
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'hrm_employee_approval' AND column_name = 'target_dept_id');
SET @sql := IF(@c = 0,
    'ALTER TABLE `hrm_employee_approval`
       ADD COLUMN `target_dept_id` bigint NULL COMMENT ''目标部门编号（调岗/晋升）'' AFTER `remark`,
       ADD COLUMN `target_post_name` varchar(128) NULL COMMENT ''目标岗位名称'' AFTER `target_dept_id`,
       ADD COLUMN `target_post_level` varchar(64) NULL COMMENT ''目标岗位职级'' AFTER `target_post_name`,
       ADD COLUMN `target_leader_employee_id` bigint NULL COMMENT ''目标直属上级员工编号'' AFTER `target_post_level`,
       ADD COLUMN `target_work_address` varchar(255) NULL COMMENT ''目标工作地址'' AFTER `target_leader_employee_id`,
       ADD COLUMN `quit_type` tinyint NULL COMMENT ''离职类型（对齐 HrmEmployeeQuitTypeEnum）'' AFTER `target_work_address`,
       ADD COLUMN `quit_reason` tinyint NULL COMMENT ''离职原因（对齐 HrmEmployeeQuitReasonEnum）'' AFTER `quit_type`,
       ADD COLUMN `salary_settlement_time` datetime NULL COMMENT ''薪资结算时间（离职）'' AFTER `quit_reason`',
    'SELECT ''审批目标字段已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 晋升/调岗自动套薪所需的目标薪资（M-04）
SET @c2 := (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'hrm_employee_approval' AND column_name = 'target_basic_salary');
SET @sql := IF(@c2 = 0,
    'ALTER TABLE `hrm_employee_approval`
       ADD COLUMN `target_basic_salary` decimal(12,2) NULL COMMENT ''目标基本工资（填写后审批通过自动套薪）'' AFTER `salary_settlement_time`,
       ADD COLUMN `target_performance_salary` decimal(12,2) NULL COMMENT ''目标绩效工资'' AFTER `target_basic_salary`',
    'SELECT ''目标薪资字段已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
