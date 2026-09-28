-- =====================================================================
-- 2026-09-21 P2 软删除唯一键收敛：把逻辑删除列 deleted 纳入两处历史唯一键
--
-- 背景：这两处唯一键未包含 deleted，而表上都有 deleted 逻辑删除列：
--       - hrm_recruit_offer.uk_offer_application(application_id)
--       - hrm_salary_employee_info.uk_hrm_salary_employee_info_employee_id(employee_id)
--       逻辑删除旧记录后，再对同一 application_id / employee_id 插入新记录会命中
--       唯一键冲突（对 HR 表现为 500），库内其余唯一键均已带 deleted，这里对齐。
--
-- 处理：
--   uk_offer_application (application_id)                  -> (application_id, deleted)
--   uk_hrm_salary_employee_info_employee_id (employee_id)  -> (employee_id, deleted)
--
-- 前置：执行前确认不存在 (业务键, deleted) 重复数据（本库 2026-09-21 核对为 0 条，
--       校验 SQL 见下方「0. 前置校验」）。
-- 幂等：一次性迁移脚本。重复执行会因索引已变更报 Duplicate key name，
--       如需重复执行请先核对索引列（见「2. 执行后校验」）。
-- 回滚：
--   ALTER TABLE hrm_recruit_offer DROP INDEX uk_offer_application,
--       ADD UNIQUE KEY uk_offer_application (application_id);
--   ALTER TABLE hrm_salary_employee_info DROP INDEX uk_hrm_salary_employee_info_employee_id,
--       ADD UNIQUE KEY uk_hrm_salary_employee_info_employee_id (employee_id);
-- =====================================================================

-- 0. 前置校验：以下两条查询都应返回 0 行，否则先清理重复数据再执行
-- select application_id, deleted from hrm_recruit_offer group by application_id, deleted having count(*) > 1;
-- select employee_id, deleted from hrm_salary_employee_info group by employee_id, deleted having count(*) > 1;

-- 1. 收敛唯一键：纳入 deleted
ALTER TABLE hrm_recruit_offer
    DROP INDEX uk_offer_application,
    ADD UNIQUE KEY uk_offer_application (application_id, deleted);

ALTER TABLE hrm_salary_employee_info
    DROP INDEX uk_hrm_salary_employee_info_employee_id,
    ADD UNIQUE KEY uk_hrm_salary_employee_info_employee_id (employee_id, deleted);

-- 2. 执行后校验：应分别返回 application_id,deleted / employee_id,deleted 两列
-- show index from hrm_recruit_offer where key_name = 'uk_offer_application';
-- show index from hrm_salary_employee_info where key_name = 'uk_hrm_salary_employee_info_employee_id';
