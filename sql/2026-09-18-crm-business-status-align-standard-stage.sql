-- ============================================================================
-- 商机状态配置对齐商机管理（标准化 5 阶段）→ 租户 2012
-- 日期：2026-09-18
--
-- 背景：商机管理已改为「固定阶段 + 赢单/输单按钮」：
--   阶段 = 商机待评估(5%) / 初步接洽(10%) / 需求确认(40%) / 方案评估(60%) / 商务谈判(70%)
--   赢单、输单不再作为阶段，改由「赢单」「输单」按钮驱动（只置 business_state）
--
-- 本脚本把「系统配置 → 商机状态配置」里的数据改成与新方案一致：
--   1. 每个状态组的阶段统一规范化为标准 5 阶段，名称/赢单率/排序与后端 CrmBusinessStageEnum 完全一致；
--   2. 移除「赢单」「输单」阶段行（其用途已由 win/lose 接口承担）；
--   3. 复用原有行的 id（尽量少改动），并把商机历史字段 status_id 按 stage 重新指向对应阶段行，
--      保证历史数据不悬空、旧统计口径一致；
--   4. 未设置状态组的历史商机统一归入「油气管道防腐销售流程」(id=16)。
--
-- 幂等，可重复执行。
-- 执行方式：
--   docker cp sql/2026-09-18-crm-business-status-align-standard-stage.sql <mysql容器>:/tmp/
--   docker exec <mysql容器> sh -c "mysql --default-character-set=utf8mb4 -uroot -p'xxx' ruoyi-vue-pro < /tmp/2026-09-18-crm-business-status-align-standard-stage.sql"
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 状态组 16「油气管道防腐销售流程」→ 标准 5 阶段
--    36初步接洽 35需求确认 37方案评估 30商务谈判 31赢单 38输单
-- ---------------------------------------------------------------------------
UPDATE `crm_business_status` SET name = '商机待评估', percent = 5,  sort = 0 WHERE id = 31 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '初步接洽',   percent = 10, sort = 1 WHERE id = 36 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '需求确认',   percent = 40, sort = 2 WHERE id = 35 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '方案评估',   percent = 60, sort = 3 WHERE id = 37 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '商务谈判',   percent = 70, sort = 4 WHERE id = 30 AND tenant_id = 2012;
DELETE FROM `crm_business_status` WHERE id = 38 AND tenant_id = 2012; -- 输单行（赢单行 id=31 已复用为「商机待评估」）

-- ---------------------------------------------------------------------------
-- 2. 状态组 17「市政保温工程流程」→ 标准 5 阶段
--    33初步接洽 39方案报价 32商务谈判 40赢单 41输单
-- ---------------------------------------------------------------------------
UPDATE `crm_business_status` SET name = '商机待评估', percent = 5,  sort = 0 WHERE id = 40 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '初步接洽',   percent = 10, sort = 1 WHERE id = 33 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '需求确认',   percent = 40, sort = 2 WHERE id = 39 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '方案评估',   percent = 60, sort = 3 WHERE id = 41 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '商务谈判',   percent = 70, sort = 4 WHERE id = 32 AND tenant_id = 2012;

-- ---------------------------------------------------------------------------
-- 3. 状态组 18「检测技术服务流程」→ 标准 5 阶段
--    42需求沟通 43方案报价 34赢单 44输单（原缺「商务谈判」，需补一行）
-- ---------------------------------------------------------------------------
UPDATE `crm_business_status` SET name = '商机待评估', percent = 5,  sort = 0 WHERE id = 34 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '初步接洽',   percent = 10, sort = 1 WHERE id = 42 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '需求确认',   percent = 40, sort = 2 WHERE id = 43 AND tenant_id = 2012;
UPDATE `crm_business_status` SET name = '方案评估',   percent = 60, sort = 3 WHERE id = 44 AND tenant_id = 2012;
INSERT INTO `crm_business_status` (type_id, name, percent, sort, tenant_id)
SELECT 18, '商务谈判', 70, 4, 2012 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `crm_business_status` WHERE type_id = 18 AND name = '商务谈判' AND tenant_id = 2012);

-- ---------------------------------------------------------------------------
-- 4. 商机历史字段 status_id 按 stage 重新指向对应阶段行（不悬空）
-- ---------------------------------------------------------------------------
UPDATE `crm_business` b
JOIN `crm_business_status` s ON s.type_id = b.status_type_id AND s.tenant_id = b.tenant_id
SET b.status_id = s.id
WHERE b.tenant_id = 2012 AND b.status_type_id IS NOT NULL
  AND s.name = CASE b.stage
        WHEN 1 THEN '商机待评估' WHEN 2 THEN '初步接洽' WHEN 3 THEN '需求确认'
        WHEN 4 THEN '方案评估' WHEN 5 THEN '商务谈判' ELSE '商机待评估' END;

-- ---------------------------------------------------------------------------
-- 5. 未设置状态组的历史商机统一归入「油气管道防腐销售流程」(id=16)，并同步 status_id
-- ---------------------------------------------------------------------------
UPDATE `crm_business` SET status_type_id = 16 WHERE tenant_id = 2012 AND status_type_id IS NULL;
UPDATE `crm_business` b
JOIN `crm_business_status` s ON s.type_id = 16 AND s.tenant_id = 2012
SET b.status_id = s.id
WHERE b.tenant_id = 2012
  AND s.name = CASE b.stage
        WHEN 1 THEN '商机待评估' WHEN 2 THEN '初步接洽' WHEN 3 THEN '需求确认'
        WHEN 4 THEN '方案评估' WHEN 5 THEN '商务谈判' ELSE '商机待评估' END;

-- ---------------------------------------------------------------------------
-- 6. 结果自查
-- ---------------------------------------------------------------------------
SELECT '=== 规范化后：状态组及阶段（租户2012） ===' AS info;
SELECT t.id AS 状态组id, t.name AS 状态组, s.id AS 阶段id, s.name AS 阶段, s.percent AS 赢单率, s.sort AS 排序
FROM crm_business_status_type t
JOIN crm_business_status s ON s.type_id = t.id AND s.tenant_id = t.tenant_id
WHERE t.tenant_id = 2012 AND t.deleted = 0 AND s.deleted = 0
ORDER BY t.id, s.sort;

SELECT '=== 自查：仍残留“赢单/输单”阶段（应为空） ===' AS info;
SELECT t.name AS 状态组, s.name AS 阶段 FROM crm_business_status s
JOIN crm_business_status_type t ON t.id = s.type_id
WHERE s.tenant_id = 2012 AND s.name IN ('赢单', '输单');

SELECT '=== 自查：阶段数不等于 5 的状态组（应为空） ===' AS info;
SELECT t.id, t.name, COUNT(s.id) AS 阶段数 FROM crm_business_status_type t
LEFT JOIN crm_business_status s ON s.type_id = t.id AND s.deleted = 0
WHERE t.tenant_id = 2012 AND t.deleted = 0 GROUP BY t.id, t.name HAVING COUNT(s.id) <> 5;

SELECT '=== 自查：商机 status_id 悬空（应为空） ===' AS info;
SELECT b.id, b.name, b.status_type_id, b.status_id FROM crm_business b
LEFT JOIN crm_business_status s ON s.id = b.status_id AND s.tenant_id = b.tenant_id
WHERE b.tenant_id = 2012 AND b.status_id IS NOT NULL AND s.id IS NULL;
