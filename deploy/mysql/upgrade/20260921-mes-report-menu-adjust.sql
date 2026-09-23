-- =====================================================================
-- 2026-09-21 MES 报表菜单调整
-- 背景：
--   「报表数据集」（配置 SQL 的页面）属于管理向功能，不直接暴露在左侧菜单；
--   业务人员从「生产执行报表」页面进入，通过页面顶部「+ 新增报表」按钮跳转过去。
-- 变更：
--   1) 6900「报表中心」重命名为「生产执行报表」；
--   2) 6800「报表数据集」隐藏左侧菜单入口（visible = 0）。
--      注意：仅隐藏菜单显示，菜单记录、路由（/mes/report-dataset）、
--            权限与页面代码全部保留，仍可通过 URL 直接访问。
-- 说明：幂等，可重复执行。
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- 1. 菜单改名：报表中心 -> 生产执行报表
UPDATE `system_menu`
SET `name` = '生产执行报表', `updater` = 'admin', `update_time` = NOW()
WHERE `id` = 6900;

-- 2. 隐藏「报表数据集」的左侧菜单入口（保留路由与权限）
UPDATE `system_menu`
SET `visible` = b'0', `updater` = 'admin', `update_time` = NOW()
WHERE `id` = 6800;

COMMIT;

-- 校验：
-- SELECT id, name, path, component, visible + 0 AS visible_val, status
-- FROM system_menu WHERE id IN (6800, 6900);
-- 期望：6900 name = 生产执行报表；6800 visible_val = 0
