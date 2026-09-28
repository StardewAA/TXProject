-- =====================================================================
-- 文件版本登记按钮权限注册（M10 增量）—— bidding:certificate:file
-- 依据：《招投标模块开发规范》§4.4；父节点 60002100 证照台账（见 2026-09-08-certificate-menu.sql）。
-- =====================================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(60002105, '文件版本登记', 'bidding:certificate:file', 3, 6, 60002100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `deleted` = b'0';
