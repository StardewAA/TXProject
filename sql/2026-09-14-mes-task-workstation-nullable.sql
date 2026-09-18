-- =====================================================================
-- 2026-09-14 P1 项目管理联动：任务自动拆分功能配套 DDL
-- 背景：新增"按工艺路线自动拆分生成任务"能力（POST /mes/pro/task/auto-generate-by-route），
--       自动拆分时工位尚未确定，要求 mes_pro_task.workstation_id 允许为空；
--       原建表为 NOT NULL 且无默认值，导致自动拆分插入失败
--       （SQLException: Field 'workstation_id' doesn't have a default value）。
-- 影响：任务工位允许暂空，报工前通过"修改任务"或排产界面指定；
--       报工仍强制校验工位（mes_pro_feedback.workstation_id 必填），不受影响。
-- 回滚：ALTER TABLE mes_pro_task
--         MODIFY COLUMN workstation_id BIGINT NOT NULL COMMENT '工作站编号';
--       （回滚前需先为 workstation_id 为空的任务补齐工位）
-- =====================================================================

ALTER TABLE mes_pro_task
    MODIFY COLUMN workstation_id BIGINT NULL COMMENT '工作站编号（自动拆分任务可暂空，报工前指定）';
