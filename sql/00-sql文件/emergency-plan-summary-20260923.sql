-- =====================================================================
-- 应急预案新增「预案简介」字段
-- =====================================================================
-- 背景：原表只有 remark(备注, 255 字) 一个长文本，装的是"依据 GB/T 29639-2020 编制"
--       这类编制说明，不是一个能写一段话的简介。此前详情页看到的长文本就是 remark。
-- 用途：列表/详情里给人一眼看懂的预案概述（适用范围、主要风险、响应要点）。
--
-- 执行（本脚本不含 USE，必须显式指定库）：
--   mysql -uroot -proot --default-character-set=utf8mb4 -D ruoyi-vue-pro < emergency-plan-summary-20260923.sql
--
-- ⚠️ 加列后**必须重新编译 txgy-module-mes-server 并重启 mes 服务**，
--    否则 DO 里没有这个属性，MyBatis-Plus 不会读写它（库里有值也传不到前端）。
-- 可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mes_set_emergency_plan'
    AND COLUMN_NAME = 'summary'
);
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `mes_set_emergency_plan` ADD COLUMN `summary` varchar(1000) DEFAULT NULL COMMENT ''预案简介'' AFTER `plan_name`',
  'SELECT ''mes_set_emergency_plan.summary 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT, ORDINAL_POSITION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'mes_set_emergency_plan'
  AND COLUMN_NAME IN ('plan_name', 'summary', 'remark')
ORDER BY ORDINAL_POSITION;
