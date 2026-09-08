-- 2026-09-10 补管理页菜单行（type=2）：外部公告管理页 + 采集源管理页
-- 背景：2026-09-08-notice-external-menu.sql 只注册了目录(60001000/60002000)与按钮权限(type=3)，
--       没有任何带 component 的页面菜单行；前端 accessMode=backend 下 noticeExternal/crawlExternal
--       两个页面无入口不可达。本脚本幂等可重复执行（固定 ID + INSERT IGNORE）。
-- 惯例：component 无前导斜杠、不带 .vue（对齐 2026-09-03-notice-menu.sql 6753 行写法）。

-- ① 外部公告管理页（挂在 60001000 外部公告目录下；管理列表 views/bidding/noticeExternal/index.vue）
INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60001110, '外部公告管理', 'bidding:notice-external:query', 2, 1, 60001000, 'notice-external', 'ep:document', 'bidding/noticeExternal/index', 'BiddingNoticeExternal', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 采集源管理页（挂在 60002000 采集管理目录下；views/bidding/crawlExternal/source/index.vue）
--    注：采集任务/采集批次管理页（crawlExternal/task|job）前端尚未建，建页后再补对应 type=2 行。
INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60002110, '采集源管理', 'bidding:crawl-source:query', 2, 1, 60002000, 'source', 'ep:collection', 'bidding/crawlExternal/source/index', 'BiddingCrawlExternalSource', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ③ 超管(role_id=1)授权两个页面行（对齐 2026-09-09-notice-menu-under-bidding.sql 的整树授权先例）
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
SELECT 1, id, 'admin', NOW(), 'admin', NOW(), 0 FROM system_menu
WHERE deleted = 0 AND id IN (60001110, 60002110);

-- ④（可选）租户默认角色/成员角色放行：按职能取舍后参照上行改 role_id 执行；
--    门户侧关注域放行（bidding:notice-external:query/update 给租户默认角色）仍见《后续后端配合说明》N8，未在本脚本处理。
