-- P0 报工进度回写：项目表增加生产进度字段
-- 口径：SUM(工单已生产数量) / SUM(工单计划数量，不含已取消)，与 selectProjectStatistics 一致
-- 由报工审批通过 / 工单完工 / 工单取消 / 挂接解绑工单时自动回写

ALTER TABLE `mes_pro_project`
    ADD COLUMN `progress` decimal(5, 2) DEFAULT NULL COMMENT '生产进度百分比（0-100，自动计算回写）' AFTER `status`;
