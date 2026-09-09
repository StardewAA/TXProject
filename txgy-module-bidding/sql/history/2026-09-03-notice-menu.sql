-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- =====================================================================
-- 公示公告域（notice）菜单/权限注册脚本 —— txgy-module-bidding 小模块
-- 依据：《招投标模块开发规范》4.4（权限码同步 sql/ 菜单脚本）、06/07 对齐稿
-- 用法：在 system 库执行（平台只加菜单行，不触碰既有模块源码/表结构）。
-- 说明：菜单 id 用 060_xxx_xxx 手工号段，与本模块错误码号段同源便于对照；
--       与既有 system_menu 冲突时自行改 id（parent_id 同步）后再执行。
-- =====================================================================

-- 1. 目录：公告/公示管理（挂在系统菜单根下；type=1 目录，component 留空）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (60001000, '公告/公示管理', '', 1, 1000, 0, 'notice', 'ep:document', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 2. 页面与按钮（type=2 菜单页 / type=3 按钮；permission 即 bidding:notice:*）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(60001100, '公告列表', 'bidding:notice:query', 2, 1, 60001000, 'list', NULL, 'bidding/notice/index', 'NoticeIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60001101, '公告新建', 'bidding:notice:create', 3, 2, 60001100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60001102, '公告修改', 'bidding:notice:update', 3, 3, 60001100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60001103, '公告发布', 'bidding:notice:publish', 3, 4, 60001100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60001104, '公告删除', 'bidding:notice:delete', 3, 5, 60001100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 3. 大厅公开读不注册权限（@PermitAll，见 bidding SecurityConfiguration 白名单）
