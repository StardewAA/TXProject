-- =====================================================================
-- 2026-09-14 P2 排班×生产联动：任务关联班组
-- 背景：生产任务增加 team_id 关联排班班组（mes_cal_team），
--       打通"排班日历查看班组任务负载"（任务按班组聚合）。
-- 回滚：ALTER TABLE mes_pro_task DROP COLUMN team_id;
--       ALTER TABLE mes_pro_task DROP INDEX idx_team_id;
-- =====================================================================

ALTER TABLE mes_pro_task
    ADD COLUMN team_id BIGINT NULL COMMENT '所属班组编号（关联 mes_cal_team.id，排产时指定）' AFTER workstation_id,
    ADD INDEX idx_team_id (team_id);
