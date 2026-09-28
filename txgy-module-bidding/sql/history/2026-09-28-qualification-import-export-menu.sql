-- =====================================================================
-- 2026-09-28 资质导入/导出权限点（五页改造缺接口契约 Q6 配套）
--
-- 规范依据：Q6 契约——资质导入/导出，权限 bidding:qualification:import/export。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1）默认放行全部权限，无需授权（先例 60005204 同口径）。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005205, '资质导入', 'bidding:qualification:import', 3, 10, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005206, '资质导出', 'bidding:qualification:export', 3, 10, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
