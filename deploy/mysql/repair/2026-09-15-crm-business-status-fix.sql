-- ============================================================================
-- CRM 商机流程配置修正（状态组 + 阶段）
-- 日期：2026-09-15
--
-- 背景：
--   业务上需要三套商机流程，「新增商机」时按所选「商机状态组」联动显示对应阶段：
--     1) 油气管道防腐销售流程：初步接洽 → 需求确认 → 方案评估 → 商务谈判 → 赢单 / 输单
--     2) 市政保温工程流程    ：初步接洽 → 方案报价 → 商务谈判 → 赢单 / 输单
--     3) 检测技术服务流程    ：需求沟通 → 方案报价 → 赢单 / 输单
--   这三个状态组已存在（id 16 / 17 / 18），但阶段配置残缺、组内顺序错乱，
--   导致「新增商机」时阶段下拉选项不全、顺序不对。
--
-- 处理原则：
--   1. 复用已有阶段的 id，只改名称与赢单率，保证已引用这些阶段的商机不会断链；
--   2. 缺失的阶段再补充，组内 sort 从 0 起顺序重排；
--   3. 脚本可重复执行（幂等）。
-- ============================================================================

USE `ruoyi-vue-pro`;

-- ---------------------------------------------------------------------------
-- 1. 油气管道防腐销售流程（type_id = 16）
--    目标：初步接洽10 / 需求确认40 / 方案评估60 / 商务谈判70 / 赢单100 / 输单0
--    复用旧阶段：35 需求调研 → 需求确认，30 报价及方案确认 → 商务谈判，31 赢单保持
-- ---------------------------------------------------------------------------
UPDATE crm_business_status SET name = '需求确认', percent = 40,  sort = 1 WHERE id = 35 AND type_id = 16;
UPDATE crm_business_status SET name = '商务谈判', percent = 70,  sort = 3 WHERE id = 30 AND type_id = 16;
UPDATE crm_business_status SET name = '赢单',     percent = 100, sort = 4 WHERE id = 31 AND type_id = 16;

INSERT INTO crm_business_status (type_id, name, percent, sort, creator, tenant_id, deleted)
SELECT 16, '初步接洽', 10, 0, '1', 2012, b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM crm_business_status WHERE type_id = 16 AND name = '初步接洽' AND deleted = b'0');

INSERT INTO crm_business_status (type_id, name, percent, sort, creator, tenant_id, deleted)
SELECT 16, '方案评估', 60, 2, '1', 2012, b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM crm_business_status WHERE type_id = 16 AND name = '方案评估' AND deleted = b'0');

INSERT INTO crm_business_status (type_id, name, percent, sort, creator, tenant_id, deleted)
SELECT 16, '输单', 0, 5, '1', 2012, b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM crm_business_status WHERE type_id = 16 AND name = '输单' AND deleted = b'0');

-- ---------------------------------------------------------------------------
-- 2. 市政保温工程流程（type_id = 17）
--    目标：初步接洽10 / 方案报价40 / 商务谈判70 / 赢单100 / 输单0
--    复用旧阶段：33 初步接洽（改赢单率），32 商务谈判保持
-- ---------------------------------------------------------------------------
UPDATE crm_business_status SET name = '初步接洽', percent = 10, sort = 0 WHERE id = 33 AND type_id = 17;
UPDATE crm_business_status SET name = '商务谈判', percent = 70, sort = 2 WHERE id = 32 AND type_id = 17;

INSERT INTO crm_business_status (type_id, name, percent, sort, creator, tenant_id, deleted)
SELECT 17, '方案报价', 40, 1, '1', 2012, b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM crm_business_status WHERE type_id = 17 AND name = '方案报价' AND deleted = b'0');

INSERT INTO crm_business_status (type_id, name, percent, sort, creator, tenant_id, deleted)
SELECT 17, '赢单', 100, 3, '1', 2012, b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM crm_business_status WHERE type_id = 17 AND name = '赢单' AND deleted = b'0');

INSERT INTO crm_business_status (type_id, name, percent, sort, creator, tenant_id, deleted)
SELECT 17, '输单', 0, 4, '1', 2012, b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM crm_business_status WHERE type_id = 17 AND name = '输单' AND deleted = b'0');

-- ---------------------------------------------------------------------------
-- 3. 检测技术服务流程（type_id = 18）
--    目标：需求沟通20 / 方案报价60 / 赢单100 / 输单0
--    复用旧阶段：34 赢单保持
-- ---------------------------------------------------------------------------
UPDATE crm_business_status SET name = '赢单', percent = 100, sort = 2 WHERE id = 34 AND type_id = 18;

INSERT INTO crm_business_status (type_id, name, percent, sort, creator, tenant_id, deleted)
SELECT 18, '需求沟通', 20, 0, '1', 2012, b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM crm_business_status WHERE type_id = 18 AND name = '需求沟通' AND deleted = b'0');

INSERT INTO crm_business_status (type_id, name, percent, sort, creator, tenant_id, deleted)
SELECT 18, '方案报价', 60, 1, '1', 2012, b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM crm_business_status WHERE type_id = 18 AND name = '方案报价' AND deleted = b'0');

INSERT INTO crm_business_status (type_id, name, percent, sort, creator, tenant_id, deleted)
SELECT 18, '输单', 0, 3, '1', 2012, b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM crm_business_status WHERE type_id = 18 AND name = '输单' AND deleted = b'0');

-- ---------------------------------------------------------------------------
-- 校验：三套流程的阶段配置
-- ---------------------------------------------------------------------------
SELECT '=== 校验：三套商机流程 ===' AS section;

SELECT s.name AS 商机流程, t.name AS 商机阶段, t.percent AS 赢单率, t.sort AS 排序
FROM crm_business_status_type s
         JOIN crm_business_status t ON t.type_id = s.id AND t.deleted = b'0'
WHERE s.id IN (16, 17, 18)
ORDER BY s.id, t.sort;
