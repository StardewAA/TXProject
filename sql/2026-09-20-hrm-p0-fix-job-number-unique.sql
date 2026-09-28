-- ============================================================
-- HRM P0 修复：hrm_employee.job_number 增加租户内唯一约束
--
-- 背景：job_number 只有普通索引 idx_hrm_employee_job_number，手动填写或并发写入
--       可产生重复工号（考勤、薪资、合同都按工号对账，重复会导致数据串号）。
--
-- 执行前处理：先消除已存在的重复工号（历史测试数据），否则唯一索引会创建失败。
-- 幂等：可重复执行。
-- ============================================================

-- 1. 处理已存在的重复工号：保留最早的一条，其余按「QC + 6 位新序号」重编号
--    （仅影响重复行；当前环境重复项为已软删的测试档案，重编号不影响业务）
SET @newSeq := (SELECT MAX(CAST(SUBSTRING(job_number, 3) AS UNSIGNED))
                FROM hrm_employee
                WHERE job_number REGEXP '^QC[0-9]+$');

UPDATE hrm_employee e
JOIN (
    SELECT id
    FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id, job_number ORDER BY id) rn
        FROM hrm_employee
        WHERE job_number IS NOT NULL AND job_number <> ''
    ) t
    WHERE t.rn > 1
) dup ON dup.id = e.id
SET e.job_number = CONCAT('QC', LPAD(@newSeq := @newSeq + 1, 6, '0')),
    e.updater = 'p0-fix-20260920',
    e.update_time = NOW();

-- 2. 建立租户内唯一约束（MySQL 唯一索引对 NULL 不去重，工号为空不受影响）
SET @idxExists := (SELECT COUNT(*) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'hrm_employee'
                     AND index_name = 'uk_hrm_employee_job_number');
SET @sql := IF(@idxExists = 0,
    'ALTER TABLE `hrm_employee` ADD UNIQUE KEY `uk_hrm_employee_job_number` (`tenant_id`, `job_number`)',
    'SELECT ''uk_hrm_employee_job_number 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 核对：应返回 0 行（无重复工号）与 1 行唯一索引
-- SELECT tenant_id, job_number, COUNT(*) c FROM hrm_employee GROUP BY tenant_id, job_number HAVING c > 1;
-- SHOW INDEX FROM hrm_employee WHERE Key_name = 'uk_hrm_employee_job_number';
