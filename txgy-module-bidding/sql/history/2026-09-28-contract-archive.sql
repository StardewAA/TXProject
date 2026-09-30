-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 存档 15 年归档锁（2026-09-28）：归档只读锁定 + 保存期限自动计算与提醒
-- 依据：《电子招标投标全流程说明书》§11.3 第 5 条（归档后只读锁定，日志只追加不覆盖）、
--   §12.2 第 3 条（存档等法定时限自动计算与提醒未系统落地 → 本轮补齐）、§14 附录（存档 ≥15 年）
--   《电子招标投标办法》技术规范（存档备查 ≥15 年）
-- ============================================================================

-- 1) bidding_contract 加归档锁列
ALTER TABLE bidding_contract ADD COLUMN archive_time datetime DEFAULT NULL COMMENT '归档时间（SIGNED→ARCHIVED 时点）' AFTER status;
ALTER TABLE bidding_contract ADD COLUMN retention_until datetime DEFAULT NULL COMMENT '保存到期时间（归档 + 15 年，自动计算落库；电子办法技术规范 存档≥15年）' AFTER archive_time;
ALTER TABLE bidding_contract ADD COLUMN retention_warn varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '保存期限提醒标记（NORMAL 正常 / EXPIRING_SOON 临期（到期前90天内） / EXPIRED 已到期，自动计算）' AFTER retention_until;

-- ============================================================================
-- 校验 SELECT（执行后人工核对）
-- SHOW COLUMNS FROM bidding_contract LIKE 'retention%';
-- SHOW COLUMNS FROM bidding_contract LIKE 'archive_time';
