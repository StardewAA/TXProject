-- 2026-09-10：第二批 10 张空壳表落成模块前，先拆业务编号唯一键。
--
-- 与 drop-code-unique-20260910.sql（第一批 6 张表）同一根因、同一口径：
--   唯一键只建在业务编号列上，而删除是逻辑删除（deleted 0→1）。
--   服务层查重按 deleted=0 过滤 → 看不见软删行 → 放行 → INSERT 撞唯一键 → 500。
--   "建-删-再建同一编号"稳定复现，且 e2e cleanup 也看不见软删行，无法自愈。
--
-- 不改成 (编号, deleted, tenant_id)：deleted 只有 0/1，删第二次仍相撞，只是把故障推迟一轮。
-- 采用方案同第一批：去掉业务编号唯一键，查重交给服务层的 <模块>_NO_DUPLICATE 校验
--   （validateBase 里按 deleted=0 查，命中返回业务错误码而不是 500）。
--
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < drop-code-unique-batch2-20260910.sql

SET NAMES utf8mb4;

ALTER TABLE `mes_set_dust_record`         DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_noise_record`        DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_gas_record`          DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_electrical_record`   DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_fire_check`          DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_ppe_check`           DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_pressure_vessel`     DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_occupational_hazard` DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_plan`                DROP INDEX `uk_plan_no`;
ALTER TABLE `mes_set_standard`            DROP INDEX `uk_standard_no`;

-- 补普通索引：唯一键拆掉后，按编号筛选仍然走索引
ALTER TABLE `mes_set_dust_record`         ADD INDEX `idx_record_no` (`record_no`);
ALTER TABLE `mes_set_noise_record`        ADD INDEX `idx_record_no` (`record_no`);
ALTER TABLE `mes_set_gas_record`          ADD INDEX `idx_record_no` (`record_no`);
ALTER TABLE `mes_set_electrical_record`   ADD INDEX `idx_record_no` (`record_no`);
ALTER TABLE `mes_set_fire_check`          ADD INDEX `idx_record_no` (`record_no`);
ALTER TABLE `mes_set_ppe_check`           ADD INDEX `idx_record_no` (`record_no`);
ALTER TABLE `mes_set_pressure_vessel`     ADD INDEX `idx_record_no` (`record_no`);
ALTER TABLE `mes_set_occupational_hazard` ADD INDEX `idx_record_no` (`record_no`);
ALTER TABLE `mes_set_plan`                ADD INDEX `idx_plan_no` (`plan_no`);
ALTER TABLE `mes_set_standard`            ADD INDEX `idx_standard_no` (`standard_no`);

-- 校验：应输出 0
SELECT '剩余业务编号唯一键(应为0)' k, COUNT(*) v
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('mes_set_dust_record','mes_set_noise_record','mes_set_gas_record',
                     'mes_set_electrical_record','mes_set_fire_check','mes_set_ppe_check',
                     'mes_set_pressure_vessel','mes_set_occupational_hazard',
                     'mes_set_plan','mes_set_standard')
  AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY';
