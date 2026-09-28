-- mes_set_* 表注释乱码修复（2026-09-10）
--
-- 前三轮（fix- / rebuild- / recover-comments-20260910.sql）只扫了
-- INFORMATION_SCHEMA.COLUMNS.COLUMN_COMMENT —— 表注释 TABLE_COMMENT 不在扫描面里，
-- 于是"498 列全净"是真的，但这 7 张表的表注释一直是花的。同一类损坏，漏了另一个面。
--
-- 损坏机理与前几轮同源（UTF-8 字节被按 GBK 两两成对解码）：
--   安全环保检测 = E5AE89 E585A8 E78EAF E4BF9D E6A380 E6B58B 2D
--   两两成对后   = (E5AE)(89E5)(85A8)(E78E)(AFE4)(BF9D)(E6A3)(80E6)(B58B)(2D)
--   (AFE4)/(80E6) 无 GBK 对应 → 各塌成一个 '?'，末尾孤字节直接丢。
-- 故 **不能**用 "原串.encode('utf-8').decode('gbk')" 反推比对：'?' 是丢字节的痕迹，
-- 用 gbk 的 'replace' 反解会得到 U+FFFD 而不是 '?'，两边永远对不上。
-- 正确做法是把**表里存的乱码**逆回字节流（'?' → 0x3F 占位），读那些还完好的字节段。
--
-- 还原依据（逐字节比对，非猜测）：前半段完好段解出 安全环保检测- ；
-- 后半段完好段解出 危化品安全检测记录 / 消防设施检测记录 / 作业环境气体检测记录 /
-- 危废台账 / 检测计划 / 环保污染判定记录 / 检测标准，
-- 每个被 '?' 吃掉的位置都恰好是 "环保"→"保" 这类三字节里的一对，无歧义。
-- 生成方式：人工逐表比对，非脚本。

ALTER TABLE `mes_set_chemical_safety` COMMENT '安全环保检测-危化品安全检测记录';
ALTER TABLE `mes_set_fire_check`      COMMENT '安全环保检测-消防设施检测记录';
ALTER TABLE `mes_set_gas_record`      COMMENT '安全环保检测-作业环境气体检测记录';
ALTER TABLE `mes_set_hazardous_waste` COMMENT '安全环保检测-危废台账';
ALTER TABLE `mes_set_plan`            COMMENT '安全环保检测-检测计划';
ALTER TABLE `mes_set_pollution_check` COMMENT '安全环保检测-环保污染判定记录';
ALTER TABLE `mes_set_standard`        COMMENT '安全环保检测-检测标准';
