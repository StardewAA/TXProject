-- =====================================================================
-- 2026-09-24 HRM 月度工资表「同租户同年月唯一」（QA 报告 Q-012 / I-004）
--
-- 背景：hrm_salary_month_record 只有 (year, month, status) 普通索引，
--       服务端仅做 selectByYearMonth 的「先查后插」校验，并发/重复点击可创建两张同年月工资表，
--       后续核算、发放、归档都会出现两份同名数据。
--
-- 处理：用 STORED 生成列把「未删除行」的 (tenant_id, year, month) 抽出来做唯一键：
--       已删除行的生成列为 NULL，不参与唯一约束（因此历史多条已删除记录不会冲突）。
--
-- 前置校验（应返回 0 行）：
--   SELECT tenant_id, year, month, COUNT(*) c FROM hrm_salary_month_record
--   WHERE deleted = 0 GROUP BY tenant_id, year, month HAVING c > 1;
--   本库 2026-09-24 核对为 0 行（9 月 2 条、10 月 3 条重复记录均为已删除）。
--
-- 回滚：
--   ALTER TABLE hrm_salary_month_record DROP INDEX uk_hrm_salary_month_record_active_year_month;
--   ALTER TABLE hrm_salary_month_record DROP COLUMN active_year_month_key;
-- =====================================================================

-- 1. 生成列：未删除行 = tenant-year-month，已删除行 = NULL
ALTER TABLE `hrm_salary_month_record`
    ADD COLUMN `active_year_month_key` varchar(64)
        GENERATED ALWAYS AS (IF(`deleted` = b'0',
            CONCAT(`tenant_id`, '-', `year`, '-', `month`), NULL)) STORED
        COMMENT '未删除行的同租户同年月唯一键（Q-012）';

-- 2. 唯一键
ALTER TABLE `hrm_salary_month_record`
    ADD UNIQUE KEY `uk_hrm_salary_month_record_active_year_month` (`active_year_month_key`);

-- 3. 执行后校验：应返回 uk_hrm_salary_month_record_active_year_month 一行
-- SHOW INDEX FROM hrm_salary_month_record WHERE Key_name = 'uk_hrm_salary_month_record_active_year_month';
