-- =============================================================================
-- 撤销 operator_result（操作员初判意见）
-- 日期：2026-09-17
-- 起因：设计评审发现该字段累赘——「初判」这个位置已经被 AI 初筛占了，
--       再加一个操作员初判就形成【三个结论】（AI / 操作员 / 专员），
--       三方不一致时无法呈现，且操作员实际上是在替专员下结论。
-- 改为：现场那一级只留「现场观察到的污染特征」（事实），不做结论；
--       结论只有两个来源——AI 参考建议 + 专员复核终局。
-- 影响：有效数据中该列全为空（仅本会话的 3 条测试记录有值，已一并物理清理）。
-- =============================================================================

ALTER TABLE `mes_set_pollution_check` DROP COLUMN `operator_result`;
ALTER TABLE `mes_pollution_check_log` DROP COLUMN `operator_result`;

-- 顺带物理清理本会话建的 3 条测试记录（此前只做了逻辑删除）
DELETE FROM `mes_set_pollution_check` WHERE id IN (920442, 920443, 920444);
DELETE FROM `mes_pollution_check_log` WHERE check_id IN (920442, 920443, 920444);
