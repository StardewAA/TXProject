-- =============================================================================
-- 污染判定：现场观察到的污染特征
-- 日期：2026-09-17
-- 背景：原「判定依据」是 8 条法条复选框，判「有污染」时必填，但车间操作员用不了
--       （8 条里只有 2 条跟现场有关，其余是企业管理层义务），必填逼着随便勾一条。
--       改为：操作员录「现场观察到的污染特征」，专员复核时据此收窄法条候选。
-- 说明：field_signs 存特征 code（如 SIGN_LEAK），多条以换行 \n 分隔（法条正文含「；」，
--       不能用「；」做分隔，见 pollution-review-basis-20260916.sql 的同款处理）。
-- 注：曾有 operator_result（操作员初判意见）一列，因与 AI 初筛的「初判」定位重叠、
--     形成三个结论打架而撤销（见 pollution-drop-operator-result-20260917.sql）。
--     现场那一级的价值是留下事实，不是多下一个结论。
-- =============================================================================

ALTER TABLE `mes_set_pollution_check`
    ADD COLUMN `field_signs` varchar(500) NULL COMMENT '现场观察到的污染特征（多选，存特征 code，多条以换行分隔）' AFTER `item_spec`;

ALTER TABLE `mes_pollution_check_log`
    ADD COLUMN `field_signs` varchar(500) NULL COMMENT '当时现场观察到的污染特征快照' AFTER `item_name`;
