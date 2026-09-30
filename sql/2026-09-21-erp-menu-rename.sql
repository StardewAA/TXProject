-- ============================================================================
-- ERP 菜单改名（2026-09-21）
--   一级目录：ERP 系统  →  企业资源规划
--   首页菜单：ERP 首页  →  系统首页
--
-- 可重复执行：先按 id 精确改，再按旧名兜底（用于其它环境 id 不同的情况）。
-- 只改 system_menu.name，不动 path / component / 权限，因此不影响路由与鉴权。
-- ============================================================================

-- 一级目录（/erp 下的父菜单）
UPDATE `system_menu` SET `name` = '企业资源规划'
 WHERE `id` = 2563 AND `name` <> '企业资源规划';
UPDATE `system_menu` SET `name` = '企业资源规划'
 WHERE `name` = 'ERP 系统' AND `parent_id` = 0;

-- 首页菜单（component = erp/home/index.vue）
UPDATE `system_menu` SET `name` = '系统首页'
 WHERE `id` = 2702 AND `name` <> '系统首页';
UPDATE `system_menu` SET `name` = '系统首页'
 WHERE `name` = 'ERP 首页' AND `component` = 'erp/home/index.vue';

-- 校验
SELECT `id`, `parent_id`, `name`, `path`, `component`
  FROM `system_menu`
 WHERE `id` IN (2563, 2702)
    OR `name` IN ('ERP 系统', 'ERP 首页');
