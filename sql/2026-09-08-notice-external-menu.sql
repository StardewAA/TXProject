-- =====================================================================
-- 外部公告域 管理/关注权限 菜单注册脚本 —— txgy-module-bidding 小模块
-- 依据：《招投标模块开发规范》4.4（权限码同步 sql/ 菜单脚本）；docs/bidding-notice-external/外部公告-前端对接-后续后端配合说明.md
--       N1（管理分页=update）、N8（门户/关注=query+update）、爬虫管理面（bidding:crawl-*）
-- 用法：在 system 库执行（平台只加菜单行，不触碰既有模块源码/表结构）。
-- 说明：菜单 id 用 060_xxx_xxx 手工号段（延续 2026-09-03-notice-menu.sql 惯例）；
--       与既有 system_menu 冲突时自行改 id（parent_id 同步）后再执行。
--       仅注册菜单行（先例同 notice-menu.sql）；给租户默认角色批量授权 = 运维按 system_role_menu 关联放行。
-- 目录现状：60001000 公告/公示管理（notice-menu.sql 已建，type=1 目录）。本脚本追加：
--   A) 60001200~ 按钮（visible=0 隐藏）：外部公告 query/update —— N1/N8 依赖的权限点；
--   B) 60002000 新目录“外部公告采集管理” + 60002100~ 按钮：bidding:crawl-source/task/job —— 爬虫管理面权限点。
-- =====================================================================

-- A) 外部公告域权限点（挂在既有目录 60001000 下；隐藏按钮只承载 permission）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60001200, '外部公告查看', 'bidding:notice-external:query', 3, 20, 60001000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60001201, '外部公告管理', 'bidding:notice-external:update', 3, 21, 60001000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- B) 采集管理目录（type=1 目录）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (60002000, '外部公告采集管理', '', 1, 2000, 0, 'crawl-external', 'ep:monitor', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- B) 采集源/任务/批次权限点（隐藏按钮；采集执行器(GENERIC 通道)接入后可配真实菜单页）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60002100, '采集源查看', 'bidding:crawl-source:query', 3, 1, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002101, '采集源新建', 'bidding:crawl-source:create', 3, 2, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002102, '采集源修改/启停', 'bidding:crawl-source:update', 3, 3, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002103, '采集源删除', 'bidding:crawl-source:delete', 3, 4, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002104, '采集任务查看', 'bidding:crawl-task:query', 3, 5, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002105, '采集任务人工重试', 'bidding:crawl-task:update', 3, 6, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002106, '采集批次查看', 'bidding:crawl-job:query', 3, 7, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
