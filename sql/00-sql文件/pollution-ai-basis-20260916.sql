-- =====================================================================
-- MES 安全环保检测-污染判定：AI 初筛新增「法规依据」列
-- 背景：AI 初筛的判定结论原先无法规出处（ai_reason 仅为自由文本描述），
--       现要求判定援引现行法规，并把「法规名 + 条款号/名录编号 + 要点」
--       作为独立字段落库、在界面展示。
-- 法条来源：PollutionLegalBasis.java（条款号已核对固废法 2020 修订版公布文本）
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < pollution-ai-basis-20260916.sql
-- =====================================================================

-- 1) 判定记录主表
ALTER TABLE `mes_set_pollution_check`
  ADD COLUMN `ai_basis` varchar(500) DEFAULT NULL COMMENT 'AI 判定的法规依据（法规名+条款号/名录编号+要点）' AFTER `ai_reason`;

-- 2) 判定履历表（每行是"该次操作之后"的 AI 快照，需同步留存当时引用的法条）
ALTER TABLE `mes_pollution_check_log`
  ADD COLUMN `ai_basis` varchar(500) DEFAULT NULL COMMENT '当时 AI 判定的法规依据' AFTER `ai_reason`;
