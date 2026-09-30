-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 合同一致性比对（2026-09-28）：合同价 vs 中标结果公示金额，不一致标红
-- 依据：《电子招标投标全流程说明书》§11.3 第 2 条（合同关键字段与中标结果比对，不一致标红）、
--   §11.5（合同不一致处理：系统标红提示，招标人确认）——标红提示非硬拦截
-- ============================================================================

-- 1) bidding_contract 加一致性比对结果列
ALTER TABLE bidding_contract ADD COLUMN compare_result varchar(16) NOT NULL DEFAULT 'NO_BASELINE' COMMENT '合同一致性比对结果（MATCH 与中标结果一致 / MISMATCH 与中标结果不一致标红 / NO_BASELINE 无中标结果基准，save 登记时自动计算）' AFTER retention_warn;

-- ============================================================================
-- 校验 SELECT（执行后人工核对）
-- SHOW COLUMNS FROM bidding_contract LIKE 'compare_result';