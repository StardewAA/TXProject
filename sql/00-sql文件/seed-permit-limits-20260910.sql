-- 2026-09-10 排污许可合规（设计文档 §六「按证排污」）演示数据
--
-- 目的：让租户 2010 已有的监测行真正"对着证"被判一次。
--   此前 emission_outlet.permit_limits 全为 NULL、outlet.permit_no 全为空，
--   限值比对与年总量红线都无从谈起（有表、无基准源）。
--
-- 口径说明（避免与既有演示行打架）：
--   * permit_limits 的限值刻意与既有监测行里的 limit_value 取同值
--     （SO2=50、颗粒物=30、COD=100、氨氮=8）——同一份数据两处一致，
--     自动比对重算后回填的限值不会和现有行冲突。
--   * outlet.permit_no 指向**名单里含该排放口**的证：permit_limits 的解析入口是排放口，
--     年总量要顺着排放口找到证。库里原有的 permit.outlet_codes("DA-001,DA-002") 是证侧的同一层关系，
--     两列同源，此处补齐排放口侧。
--   * 既有 permit id=4 的 annual_limits 为 [{"pollutantCode":"VOCs",...}]，本文件沿用其字段命名
--     （pollutantCode/pollutantName/annualLimitT/annualUsedT）。
--   * 演示里 DA-002 既有 COD/氨氮 废水行，故给废水限值；设计稿 §六提到的
--     "DA001 涂装 非甲烷总烃"并入 DA-001 的限值里一并维护。
--
-- ⚠️ 坑：MySQL 的 `||` 默认是**逻辑或**而非字符串连接（除非开 PIPES_AS_CONCAT）。
--    写成 'a' || 'b' 会被当数值运算，报 "Truncated incorrect DOUBLE value"。
--    这里直接用整条字符串字面量，不拼接。

-- 1) 排放口：补 permit_no + 许可浓度限值
UPDATE `mes_set_emission_outlet`
SET `permit_no` = 'P-110108-2026-0001',
    `permit_limits` = '[{"pollutantCode":"颗粒物","pollutantName":"颗粒物","limitValue":30,"unit":"mg/m3"},{"pollutantCode":"SO2","pollutantName":"二氧化硫","limitValue":50,"unit":"mg/m3"},{"pollutantCode":"VOCs","pollutantName":"非甲烷总烃","limitValue":60,"unit":"mg/m3"}]'
WHERE `tenant_id` = 2010 AND `outlet_code` = 'DA-001';

UPDATE `mes_set_emission_outlet`
SET `permit_no` = 'P-110108-2026-0002',
    `permit_limits` = '[{"pollutantCode":"COD","pollutantName":"化学需氧量","limitValue":100,"unit":"mg/L"},{"pollutantCode":"氨氮","pollutantName":"氨氮","limitValue":8,"unit":"mg/L"}]'
WHERE `tenant_id` = 2010 AND `outlet_code` = 'DA-002';

-- 2) 许可证：补许可年排放总量（驱动 80% 黄警 / 100% 红线）
UPDATE `mes_set_pollution_permit`
SET `annual_limits` = '[{"pollutantCode":"颗粒物","pollutantName":"颗粒物","annualLimitT":1.2,"annualUsedT":0},{"pollutantCode":"SO2","pollutantName":"二氧化硫","annualLimitT":3,"annualUsedT":0},{"pollutantCode":"VOCs","pollutantName":"非甲烷总烃","annualLimitT":12,"annualUsedT":0}]'
WHERE `tenant_id` = 2010 AND `permit_no` = 'P-110108-2026-0001';

UPDATE `mes_set_pollution_permit`
SET `annual_limits` = '[{"pollutantCode":"COD","pollutantName":"化学需氧量","annualLimitT":0.5,"annualUsedT":0},{"pollutantCode":"氨氮","pollutantName":"氨氮","annualLimitT":0.05,"annualUsedT":0}]'
WHERE `tenant_id` = 2010 AND `permit_no` = 'P-110108-2026-0002';
