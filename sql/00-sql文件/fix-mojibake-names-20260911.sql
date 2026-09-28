-- 乱码行改名 2026-09-11
--
-- 背景：全库 387 个字符列扫下来，只有 5 个单元格含 U+FFFD，全在租户 1。HEX 证明不可还原——
-- 入库时 GBK 字节被喂给 utf8mb4 连接，多数字节当场变成 EFBFBD 丢了，只剩「业/准/知/计/确」
-- 这类零星幸存字节。能认出的碎片：STD-0001 的名字像「?业……准」、plan 是「2026……计划」、
-- env_report 是「2026……报告」。既然原文不可考，按该行自身的结构化字段重新定名，不做臆测还原。
--
-- 依据（各行其余字段）：
--   standard id=1  tenant 1    STD-0001       domain=GAS      ref=GBZ/T 189.4-2007  limits={"CO":30,"O2":19.5}
--   standard id=2  tenant 2010 STD-2010-0001  domain=EXHAUST  test_type=现场采样
--   plan     id=1  tenant 1    PLAN-0001      2026 全年  YEAR  standard_id=1（即上面那条气体标准）
--   env_report id=1 tenant 1   EP-SMOKE-1     EXHAUST_GAS     report_type=THIRD_PARTY
--
-- 每条都带 U+FFFD 守卫：只有还坏着的才改，重跑不会覆盖已经起好的名字。
-- 只能整文件喂：mysql --default-character-set=utf8mb4 < 本文件

SET NAMES utf8mb4;

UPDATE `mes_set_standard`
SET `standard_name` = '气体检测标准'
WHERE `id` = 1 AND `standard_name` LIKE CONCAT('%', _utf8mb4 X'EFBFBD', '%');

UPDATE `mes_set_standard`
SET `method` = '现场采样-实验室分析'
WHERE `id` = 1 AND `method` LIKE CONCAT('%', _utf8mb4 X'EFBFBD', '%');

UPDATE `mes_set_standard`
SET `standard_name` = '废气采样检测标准'
WHERE `id` = 2 AND `standard_name` LIKE CONCAT('%', _utf8mb4 X'EFBFBD', '%');

UPDATE `mes_set_plan`
SET `plan_name` = '2026年度气体检测计划'
WHERE `id` = 1 AND `plan_name` LIKE CONCAT('%', _utf8mb4 X'EFBFBD', '%');

UPDATE `mes_set_env_report`
SET `report_name` = '2026年度废气检测报告（第三方）'
WHERE `id` = 1 AND `report_name` LIKE CONCAT('%', _utf8mb4 X'EFBFBD', '%');
