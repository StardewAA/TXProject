-- HRM：hrm_employee_contract.employee_id 应为可空
--
-- 背景：发放 Offer 时自动生成劳务合同草稿，此时员工尚未建档，employee_id 由
--   HrmEmployeeContractServiceImpl.createDraftForOffer 留空，入职建档后回写。
--   本地库该列却是 NOT NULL 且无默认值（严格模式下即报错），于是发 Offer 走到
--   建草稿这一步就炸：
--
--     java.sql.SQLException: Field 'employee_id' doesn't have a default value
--     INSERT INTO hrm_employee_contract (no, template_id, source_offer_id, ...)
--       -- 注意列清单里没有 employee_id，正是因为它按设计为空
--
-- 项目权威快照 sql/hrm_202609201427.sql 写得很明确：
--   `employee_id` bigint DEFAULT NULL COMMENT '员工编号；Offer 阶段草稿可为空，入职建档后回写'
--
-- 这是 hrm_company_profile 装错结构之后同一类问题的第二例（本地表结构与代码意图不符）。
-- 为免再有第三例，已把全部 hrm 表（1400 列）的可空性与权威快照逐列比对，仅 4 处不一致：
--   hrm_employee_contract.employee_id   快照可空 / 本地 NOT NULL   ← 本脚本修的就是它
--   hrm_certificate_type.creator        快照可空 / 本地 NOT NULL   ← 无害，creator/updater 由框架填，不动
--   hrm_certificate_type.updater        快照可空 / 本地 NOT NULL   ← 同上
--   hrm_certificate_type.sort           快照 NOT NULL / 本地可空   ← 更宽松，不影响写入，不动
--
-- 幂等：MODIFY COLUMN 可重复执行。两个既有索引（idx_..._employee_id
-- 与 idx_..._status_end_time）在 MODIFY 后均保留，已核对。

SET NAMES utf8mb4;

ALTER TABLE `hrm_employee_contract`
    MODIFY COLUMN `employee_id` bigint DEFAULT NULL
    COMMENT '员工编号；Offer 阶段草稿可为空，入职建档后回写';

-- 核对：IS_NULLABLE 应为 YES，且索引仍在
-- SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA='ruoyi-vue-pro' AND TABLE_NAME='hrm_employee_contract'
--     AND COLUMN_NAME='employee_id';
-- SHOW INDEX FROM hrm_employee_contract WHERE Column_name='employee_id';   -- 应有 2 条
