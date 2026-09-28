-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- =====================================================================
-- 主体信息与授权域 权限点菜单注册脚本 —— txgy-module-bidding 小模块
-- 依据：《招投标模块开发规范》4.4（权限码同步 sql/ 菜单脚本）；docs/法规依据/00-索引与引用说明.md
-- 用法：在 system 库执行（平台只加菜单行）。菜单 id 用 060_xxx 号段；冲突自行改 id。
-- 目录：60003000 主体信息与授权管理（type=1 目录，新目录）；
--      企业侧（档案自维护）用 bidding:subject:query/update；
--      平台侧（授权/经办用户/资格查询）用 bidding:subject-auth:query/update。
-- 注意：投标资格门只校验“授权+档案完善”，不校验菜单权限——菜单仅控制入口显隐。
-- =====================================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (60003000, '主体信息与授权管理', '', 1, 3000, 0, 'subject-auth', 'ep:user', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60003100, '主体档案查看（企业自维护）', 'bidding:subject:query', 3, 1, 60003000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60003101, '主体档案维护（企业自维护）', 'bidding:subject:update', 3, 2, 60003000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60003200, '已授权主体查看（平台）', 'bidding:subject-auth:query', 3, 3, 60003000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60003201, '主体授权/经办用户管理（平台）', 'bidding:subject-auth:update', 3, 4, 60003000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
