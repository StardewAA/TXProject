-- =====================================================================
-- 资质与证书域菜单/权限注册脚本 —— txgy-module-bidding 小模块（证照台账/验真/预警运行/企业资质）
-- 依据：《招投标模块开发规范》§4.4（权限码随 sql/ 菜单脚本登记，与 Controller @PreAuthorize 一一对应）
-- 用法：在 system 库执行（平台只加菜单行，不触碰既有模块源码/表结构）。
-- 说明：菜单 id 用 060_xxx_xxx 手工号段（与错误码 1-060-xxx 同源便于对照），冲突时自行改 id；
--       组件 component 仅台账/资质列表页占位（前端工程未建时 NULL 亦可，先保证权限码可用）。
-- =====================================================================

-- 1. 目录：资质与证书管理（type=1）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (60002000, '资质与证书管理', '', 1, 1100, 0, 'certificate', 'ep:medal', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 2. 页面/按钮（type=2 菜单页 / type=3 按钮；permission 与 Controller 权限码一致）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- 证照台账（M2 Controller 权限）
(60002100, '证照台账', 'bidding:certificate:query', 2, 1, 60002000, 'list', NULL, 'bidding/certificate/index', 'CertificateIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002101, '证照新建', 'bidding:certificate:create', 3, 2, 60002100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002102, '证照修改/状态流转/文件版本', 'bidding:certificate:update', 3, 3, 60002100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002103, '证照验真', 'bidding:certificate:verify', 3, 4, 60002100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002104, '预警扫描/派发', 'bidding:certificate:alert-run', 3, 5, 60002100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 企业资质（M5/M6 权限）
(60002200, '企业资质', 'bidding:qualification:query', 2, 6, 60002000, 'qualification', NULL, 'bidding/qualification/index', 'QualificationIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002201, '资质建档', 'bidding:qualification:create', 3, 7, 60002200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002202, '延续申报', 'bidding:qualification:renew', 3, 8, 60002200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002203, '延续审批通过', 'bidding:qualification:approve', 3, 9, 60002200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002204, '延续审批退回', 'bidding:qualification:reject', 3, 10, 60002200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
