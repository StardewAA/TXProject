-- =====================================================================
-- 企业资质演示操作按钮权限注册（M9 增量）—— txgy-module-bidding 小模块
-- 依据：《招投标模块开发规范》§4.4；与 Controller @PreAuthorize 权限码一一对应；沿用 060_xxx 号段。
-- 用法：在 system 库执行；页面父节点 = 企业资质(60002200，见 2026-09-08-certificate-menu.sql)。
-- =====================================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(60002210, '资质标准登记', 'bidding:qualification:standard', 3, 11, 60002200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002211, '要素台账录入', 'bidding:qualification:element', 3, 12, 60002200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002212, '达标核查执行', 'bidding:qualification:review', 3, 13, 60002200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002213, '安许证管理', 'bidding:qualification:permit', 3, 14, 60002200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002214, '资质到期预警扫描', 'bidding:qualification:alert-run', 3, 15, 60002200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
