-- ============================================================
--  MES 环保：业务编号唯一性收敛为「租户内唯一」
--
--  背景：manifest_no / facility_no / event_no 三列原本没有数据库唯一约束，
--        唯一性只靠应用层查重；而应用层查询带 MyBatis-Plus 租户插件过滤，
--        并发场景下仍可能落库两条同号记录。
--  口径：这三个编号由各租户自行编排，**跨租户允许同号**，因此约束建成
--        (tenant_id, xxx_no) 联合唯一 —— 租户内唯一、跨租户互不干扰。
--        permit_no 不在此列：它是排污许可证号（统一社会信用代码格式），
--        各租户天然不同，保持原有 uk_permit_no 全局唯一。
--
--  ⚠️ 执行前置（以下三项均应为 0，否则建索引直接失败，须先清理）：
--     1) 租户内重复编号   2) 编号为空串（NULL 不冲突，空串会冲突）
--     2026-09-24 在 ruoyi-vue-pro 实测：三项均为 0，可直接执行。
--
--  ⚠️ 语义：唯一键不含 deleted，因此**逻辑删除的行仍占用编号**
--     （与原 uk_permit_no 保持一致的口径）。业务上编号不复用，属预期行为。
--
--  可重复执行：索引已存在则跳过。
-- ============================================================
SET NAMES utf8mb4;
USE `ruoyi-vue-pro`;

-- ---------- 执行前校验（应返回 0 行）----------
-- SELECT tenant_id, manifest_no, COUNT(*) FROM mes_set_hazwaste_manifest
--   WHERE deleted = 0 GROUP BY tenant_id, manifest_no HAVING COUNT(*) > 1;

-- 1) 危废转移联单号（联单主表）
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_set_hazwaste_manifest'
    AND INDEX_NAME = 'uk_tenant_manifest_no');
SET @sql := IF(@exist = 0,
  'ALTER TABLE mes_set_hazwaste_manifest ADD UNIQUE KEY uk_tenant_manifest_no (tenant_id, manifest_no)',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 治污设施编号
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_set_treatment_facility'
    AND INDEX_NAME = 'uk_tenant_facility_no');
SET @sql := IF(@exist = 0,
  'ALTER TABLE mes_set_treatment_facility ADD UNIQUE KEY uk_tenant_facility_no (tenant_id, facility_no)',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 应急事件编号
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_set_emergency_event'
    AND INDEX_NAME = 'uk_tenant_event_no');
SET @sql := IF(@exist = 0,
  'ALTER TABLE mes_set_emergency_event ADD UNIQUE KEY uk_tenant_event_no (tenant_id, event_no)',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 执行后校验 ----------
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0
  AND INDEX_NAME IN ('uk_tenant_manifest_no', 'uk_tenant_facility_no', 'uk_tenant_event_no')
GROUP BY TABLE_NAME, INDEX_NAME;
