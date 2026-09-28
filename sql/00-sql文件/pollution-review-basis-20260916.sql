-- =====================================================================
-- MES 安全环保检测-污染判定：人工复核新增「判定依据」列
-- 背景：AI 初筛已有法规依据（ai_basis），但人工复核结论只留 review_result +
--       备注，对外拿不出判定依据；覆核一旦推翻 AI 结论，无法说明推翻理由。
--       现要求复核人从同一份法规白名单（PollutionLegalBasis.java）里勾选法条，
--       落库为「法规全称 + 条款号 + 要点全文」，多条以「；」连接。
-- 口径：仅判「有污染」时必填（错误码 1_040_818_008），无污染允许留空。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < pollution-review-basis-20260916.sql
-- =====================================================================

-- 1) 判定记录主表
ALTER TABLE `mes_set_pollution_check`
  ADD COLUMN `review_basis` varchar(1000) DEFAULT NULL COMMENT '人工复核的判定依据（复选的法规条款，多条以；连接）' AFTER `review_result`;

-- 2) 判定履历表（每行是"该次操作之后"的快照，人工依据需一并留痕）
ALTER TABLE `mes_pollution_check_log`
  ADD COLUMN `review_basis` varchar(1000) DEFAULT NULL COMMENT '人工复核的判定依据(仅 REVIEW 行)' AFTER `review_result`;
