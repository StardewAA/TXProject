-- ============================================================================
-- CRM 商机状态组「归一」脚本（对齐 2026-09-15-crm-business-status-fix.sql 的最终版）
-- 日期：2026-09-16
--
-- 背景：
--   本库历史上先后出现过三套同名的商机状态组，导致「新增商机」的状态组下拉出现重复项、
--   且部分流程阶段残缺：
--     10 / 11 / 12 ：早期按测试数据导入时生成（阶段名与业务最终版不一致，现有商机引用它）
--     201 / 202 / 203：另一份导出脚本生成
--     16 / 17 / 18 ：团队规范版（最新提交 a52236b0 以它为准，但本库缺 type 行、阶段残缺）
--
-- 处理原则：
--   1. 以 16 / 17 / 18 为唯一保留的三套流程，阶段按业务确认的顺序重建；
--   2. 现有商机先按语义重定向到新阶段，避免引用断链；
--   3. 其余重复状态组及其阶段一并清理；
--   4. 仅影响租户 2012，可重复执行。
-- ============================================================================

USE `ruoyi-vue-pro`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 现有商机重定向到规范后的阶段（按语义对应）
--    5001 报价及方案确认 → 16-方案评估(1603)
--    5002 赢单           → 16-赢单(1605)
--    5006 需求调研       → 16-需求确认(1602)
--    5003 商务谈判       → 17-商务谈判(1703)
--    5004 初步接洽       → 17-初步接洽(1701)
--    5005 赢单           → 18-赢单(1803)
-- ---------------------------------------------------------------------------
UPDATE crm_business SET status_id = 1603 WHERE id = 5001 AND tenant_id = 2012;
UPDATE crm_business SET status_id = 1605 WHERE id = 5002 AND tenant_id = 2012;
UPDATE crm_business SET status_id = 1703 WHERE id = 5003 AND tenant_id = 2012;
UPDATE crm_business SET status_id = 1701 WHERE id = 5004 AND tenant_id = 2012;
UPDATE crm_business SET status_id = 1803 WHERE id = 5005 AND tenant_id = 2012;
UPDATE crm_business SET status_id = 1602 WHERE id = 5006 AND tenant_id = 2012;

UPDATE crm_business SET status_type_id = 16 WHERE tenant_id = 2012 AND id IN (5001, 5002, 5006);
UPDATE crm_business SET status_type_id = 17 WHERE tenant_id = 2012 AND id IN (5003, 5004);
UPDATE crm_business SET status_type_id = 18 WHERE tenant_id = 2012 AND id = 5005;

-- ---------------------------------------------------------------------------
-- 2. 清理重复/残留的状态组与阶段（仅租户 2012）
-- ---------------------------------------------------------------------------
DELETE FROM crm_business_status WHERE tenant_id = 2012 AND type_id IN (10, 11, 12, 201, 202, 203);
DELETE FROM crm_business_status_type WHERE tenant_id = 2012 AND id IN (10, 11, 12, 201, 202, 203);
-- 16 / 17 / 18 的阶段整体重建，保证顺序与赢单率完全一致
DELETE FROM crm_business_status WHERE tenant_id = 2012 AND type_id IN (16, 17, 18);
DELETE FROM crm_business_status_type WHERE tenant_id = 2012 AND id IN (16, 17, 18);

-- ---------------------------------------------------------------------------
-- 3. 重建三套商机流程（业务确认的最终版）
-- ---------------------------------------------------------------------------
INSERT INTO crm_business_status_type (id, name, dept_ids, creator, create_time, updater, update_time, tenant_id, deleted)
VALUES (16, '油气管道防腐销售流程', '', '1', NOW(), '1', NOW(), 2012, b'0'),
       (17, '市政保温工程流程', '', '1', NOW(), '1', NOW(), 2012, b'0'),
       (18, '检测技术服务流程', '', '1', NOW(), '1', NOW(), 2012, b'0');

INSERT INTO crm_business_status (id, type_id, name, percent, sort, creator, create_time, updater, update_time, tenant_id, deleted)
VALUES
    -- 油气管道防腐销售流程
    (1601, 16, '初步接洽', 10, 0, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1602, 16, '需求确认', 40, 1, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1603, 16, '方案评估', 60, 2, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1604, 16, '商务谈判', 70, 3, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1605, 16, '赢单', 100, 4, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1606, 16, '输单', 0, 5, '1', NOW(), '1', NOW(), 2012, b'0'),
    -- 市政保温工程流程
    (1701, 17, '初步接洽', 10, 0, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1702, 17, '方案报价', 40, 1, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1703, 17, '商务谈判', 70, 2, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1704, 17, '赢单', 100, 3, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1705, 17, '输单', 0, 4, '1', NOW(), '1', NOW(), 2012, b'0'),
    -- 检测技术服务流程
    (1801, 18, '需求沟通', 20, 0, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1802, 18, '方案报价', 60, 1, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1803, 18, '赢单', 100, 2, '1', NOW(), '1', NOW(), 2012, b'0'),
    (1804, 18, '输单', 0, 3, '1', NOW(), '1', NOW(), 2012, b'0');

-- ---------------------------------------------------------------------------
-- 4. 校验
-- ---------------------------------------------------------------------------
SELECT s.id AS 状态组ID, s.name AS 商机流程, t.name AS 商机阶段, t.percent AS 赢单率, t.sort AS 排序
FROM crm_business_status_type s
         JOIN crm_business_status t ON t.type_id = s.id AND t.deleted = b'0'
WHERE s.tenant_id = 2012 AND s.deleted = b'0'
ORDER BY s.id, t.sort;
