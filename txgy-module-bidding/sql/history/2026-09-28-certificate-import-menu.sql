-- =====================================================================
-- 2026-09-28 证照 Excel 导入权限点（五页改造缺接口契约 Q1 配套）
--
-- 规范依据：Q1 契约——POST /bidding/certificate/import（Excel、行级回执、模板下载）；
--           权限点 bidding:certificate:import。
-- 配套端点：GET /bidding/certificate/import-template（模板下载，无需授权）。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1）默认放行全部权限，无需授权（先例 60005105 同口径）。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005106, '证照导入', 'bidding:certificate:import', 3, 7, 60005100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
