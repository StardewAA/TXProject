-- =====================================================================
-- MES 安全环保检测（SET）模块 - 排污许可证 菜单+按钮 SQL
-- 结构：目录「安全环保检测」id=34000（20260902 已建，勿重建）> 功能菜单 34350 > 按钮 34360+
-- ID 段：34350~34399 为排污许可保留（34301 污染判定/34302 污染台账 已占用，34311~34315 为其按钮）
-- 说明：超级管理员(super_admin)自动拥有全部菜单，无需绑定 system_role_menu；
--       租户普通角色如需授权，另跑 system_role_menu 绑定（本文件不含）。
-- 注意：component 与 txgy-ui-admin-vben 页面文件一致后方可应用；
--       应用后请清 Redis 菜单/权限缓存（menu_role_ids:* / permission_menu_ids:* / role:*）并重新登录。
-- 列：id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted
-- 执行：mysql -uroot -proot ruoyi-vue-pro < set-pollution-permit-20260909-menu.sql
-- =====================================================================

-- 防重清理（若需重跑本文件）
DELETE FROM system_menu WHERE id BETWEEN 34350 AND 34399;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 34350 AND 34399;

INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34350, '排污许可证', '', 2, 192, 34000, 'pollutionPermit', 'ep:document', 'mes/safetyEnv/pollutionPermit/index', 'MesSetPollutionPermit', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34360, '查询', 'mes:set-pollution-permit:query', 3, 1, 34350, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34361, '新增', 'mes:set-pollution-permit:create', 3, 2, 34350, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34362, '修改', 'mes:set-pollution-permit:update', 3, 3, 34350, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34363, '删除', 'mes:set-pollution-permit:delete', 3, 4, 34350, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
