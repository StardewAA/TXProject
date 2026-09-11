-- =====================================================================
-- MES 安全环保检测（SET）模块 - 环保污染监控 菜单+按钮 SQL
-- 结构：目录「安全环保检测」id=34000（20260902 已建，勿重建）> 功能菜单 34301 > 按钮 34311+
-- 预留 ID 段 34300~34400（34000~34200 已被 SET 其它功能占用）
-- 说明：超级管理员(super_admin)自动拥有全部菜单，无需绑定 system_role_menu；
--       租户普通角色如需授权，另跑 system_role_menu 绑定（本文件不含）。
-- 注意：component 与 txgy-ui-admin-vben 页面文件一致后方可应用；
--       应用后请清 Redis 菜单/权限缓存（menu_role_ids:* / permission_menu_ids:* / role:*）并重新登录。
-- 列：id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted
-- 执行：mysql -uroot -proot ruoyi-vue-pro < set-pollution-check-20260903-menu.sql
-- =====================================================================

-- 防重清理（若需重跑本文件）
DELETE FROM system_menu WHERE id BETWEEN 34300 AND 34400;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 34300 AND 34400;

INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34301, '污染判定', '', 2, 190, 34000, 'pollutionCheck', 'ep:files', 'mes/safetyEnv/pollutionCheck/index', 'MesSetPollutionCheck', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34311, '查询', 'mes:set-pollution-check:query', 3, 1, 34301, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34312, '新增', 'mes:set-pollution-check:create', 3, 2, 34301, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34313, '复核', 'mes:set-pollution-check:review', 3, 3, 34301, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34314, '修改', 'mes:set-pollution-check:update', 3, 4, 34301, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34315, '删除', 'mes:set-pollution-check:delete', 3, 5, 34301, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
