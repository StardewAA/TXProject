-- =====================================================================
-- 2026-09-22 HRM 部门级数据权限（阶段二 2.1 · 方案 1）
--
-- 产品口径：
--   HR 与董事长（角色数据范围＝全部）看全部员工；
--   各部门管理（自定义/本部门）只看本部门员工及其过程数据；
--   普通员工（仅本人）只看本人。
--
-- 背景：框架的 DeptDataPermissionRule 只能按本表已有列过滤（dept_id / user_id），
--       而以下"按人"的表只有 employee_id，没有 dept_id，因此先冗余一列再注册规则。
--
-- 做法（每张表三步）：
--   1) 新增 dept_id 列 + idx_dept_id 索引；
--   2) 用 hrm_employee.dept_id 回填历史数据；
--   3) 建 BEFORE INSERT 触发器，新增记录自动从员工带出 dept_id
--      （员工调岗不追溯历史：与框架注释一致——历史数据保留原部门）。
--
-- 不含：薪资（hrm_salary_*）、社保（hrm_insurance_*）、招聘（hrm_recruit_*）、
--       个人简历（hrm_personal_resume*）、黑名单（hrm_employee_blacklist，属全公司风控清单，刻意不隔离）。
--
-- 回滚：对每张表执行
--   DROP TRIGGER IF EXISTS trg_<表名>_dept_id;
--   ALTER TABLE <表名> DROP INDEX idx_dept_id, DROP COLUMN dept_id;
-- =====================================================================

-- ---------- 考勤 ----------
ALTER TABLE hrm_attendance_clock
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_attendance_clock t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_attendance_clock_dept_id;
CREATE TRIGGER trg_hrm_attendance_clock_dept_id BEFORE INSERT ON hrm_attendance_clock FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_attendance_leave
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_attendance_leave t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_attendance_leave_dept_id;
CREATE TRIGGER trg_hrm_attendance_leave_dept_id BEFORE INSERT ON hrm_attendance_leave FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

-- ---------- 绩效 ----------
ALTER TABLE hrm_performance_assessment
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_performance_assessment t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_performance_assessment_dept_id;
CREATE TRIGGER trg_hrm_performance_assessment_dept_id BEFORE INSERT ON hrm_performance_assessment FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_performance_assessment_action_record
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_performance_assessment_action_record t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_performance_assessment_action_record_dept_id;
CREATE TRIGGER trg_hrm_performance_assessment_action_record_dept_id BEFORE INSERT ON hrm_performance_assessment_action_record FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

-- 绩效申诉只有 assessment_id：经考核主表回填 / 带出
ALTER TABLE hrm_performance_assessment_appeal_record
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（经 hrm_performance_assessment 关联员工，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_performance_assessment_appeal_record t
    JOIN hrm_performance_assessment pa ON pa.id = t.assessment_id
    JOIN hrm_employee e ON e.id = pa.employee_id
    SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_performance_assessment_appeal_record_dept_id;
CREATE TRIGGER trg_hrm_performance_assessment_appeal_record_dept_id BEFORE INSERT ON hrm_performance_assessment_appeal_record FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_performance_assessment pa
        JOIN hrm_employee e ON e.id = pa.employee_id WHERE pa.id = NEW.assessment_id));

-- ---------- 合同 / 证件 / 安全 / 健康 ----------
ALTER TABLE hrm_employee_contract
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_contract t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_contract_dept_id;
CREATE TRIGGER trg_hrm_employee_contract_dept_id BEFORE INSERT ON hrm_employee_contract FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_contract_sign_record
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_contract_sign_record t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_contract_sign_record_dept_id;
CREATE TRIGGER trg_hrm_contract_sign_record_dept_id BEFORE INSERT ON hrm_contract_sign_record FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_certificate
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_certificate t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_certificate_dept_id;
CREATE TRIGGER trg_hrm_employee_certificate_dept_id BEFORE INSERT ON hrm_employee_certificate FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_certificate_warning
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_certificate_warning t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_certificate_warning_dept_id;
CREATE TRIGGER trg_hrm_certificate_warning_dept_id BEFORE INSERT ON hrm_certificate_warning FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_certificate_renewal
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_certificate_renewal t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_certificate_renewal_dept_id;
CREATE TRIGGER trg_hrm_certificate_renewal_dept_id BEFORE INSERT ON hrm_certificate_renewal FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_safety_education
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_safety_education t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_safety_education_dept_id;
CREATE TRIGGER trg_hrm_safety_education_dept_id BEFORE INSERT ON hrm_safety_education FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_health_check
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_health_check t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_health_check_dept_id;
CREATE TRIGGER trg_hrm_health_check_dept_id BEFORE INSERT ON hrm_health_check FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

-- ---------- 异动 / 离职 ----------
ALTER TABLE hrm_employee_change_record
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_change_record t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_change_record_dept_id;
CREATE TRIGGER trg_hrm_employee_change_record_dept_id BEFORE INSERT ON hrm_employee_change_record FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_quit_info
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_quit_info t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_quit_info_dept_id;
CREATE TRIGGER trg_hrm_employee_quit_info_dept_id BEFORE INSERT ON hrm_employee_quit_info FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_quit_handover_item
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_quit_handover_item t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_quit_handover_item_dept_id;
CREATE TRIGGER trg_hrm_employee_quit_handover_item_dept_id BEFORE INSERT ON hrm_employee_quit_handover_item FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_approval
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_approval t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_approval_dept_id;
CREATE TRIGGER trg_hrm_employee_approval_dept_id BEFORE INSERT ON hrm_employee_approval FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

-- ---------- 员工子档案 ----------
ALTER TABLE hrm_employee_contact
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_contact t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_contact_dept_id;
CREATE TRIGGER trg_hrm_employee_contact_dept_id BEFORE INSERT ON hrm_employee_contact FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_education_experience
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_education_experience t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_education_experience_dept_id;
CREATE TRIGGER trg_hrm_employee_education_experience_dept_id BEFORE INSERT ON hrm_employee_education_experience FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_work_experience
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_work_experience t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_work_experience_dept_id;
CREATE TRIGGER trg_hrm_employee_work_experience_dept_id BEFORE INSERT ON hrm_employee_work_experience FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_training_experience
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_training_experience t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_training_experience_dept_id;
CREATE TRIGGER trg_hrm_employee_training_experience_dept_id BEFORE INSERT ON hrm_employee_training_experience FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_file
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_file t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_file_dept_id;
CREATE TRIGGER trg_hrm_employee_file_dept_id BEFORE INSERT ON hrm_employee_file FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_personal_note
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_personal_note t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_personal_note_dept_id;
CREATE TRIGGER trg_hrm_employee_personal_note_dept_id BEFORE INSERT ON hrm_employee_personal_note FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

ALTER TABLE hrm_employee_salary_card
    ADD COLUMN dept_id BIGINT NULL COMMENT '所属部门编号（冗余自 hrm_employee.dept_id，用于部门数据权限）',
    ADD INDEX idx_dept_id (dept_id);
UPDATE hrm_employee_salary_card t JOIN hrm_employee e ON e.id = t.employee_id SET t.dept_id = e.dept_id;
DROP TRIGGER IF EXISTS trg_hrm_employee_salary_card_dept_id;
CREATE TRIGGER trg_hrm_employee_salary_card_dept_id BEFORE INSERT ON hrm_employee_salary_card FOR EACH ROW
    SET NEW.dept_id = IFNULL(NEW.dept_id, (SELECT e.dept_id FROM hrm_employee e WHERE e.id = NEW.employee_id));

-- 3. 执行后校验：以下查询应返回 0 行（即所有历史数据都已回填）
-- select 'hrm_attendance_clock' t, count(*) from hrm_attendance_clock where dept_id is null and employee_id is not null
-- union all select 'hrm_performance_assessment', count(*) from hrm_performance_assessment where dept_id is null and employee_id is not null
-- union all select 'hrm_employee_contract', count(*) from hrm_employee_contract where dept_id is null and employee_id is not null;
