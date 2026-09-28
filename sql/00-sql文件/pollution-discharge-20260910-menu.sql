-- =====================================================================
-- MES 安全环保检测（SET）模块 - 排放合规流水 菜单+按钮 SQL
-- 结构：目录「安全环保检测」id=34000（勿重建）> 功能菜单 34380 > 按钮 34381
-- ID 段：34380~34381 排放流水（34301/34302/34350/34370 已占用，34372~34399 余量）
-- 权限：只读查询按钮 mes:set-pollution-discharge:query
-- 角色：租户 2010 角色 60185(tenant_admin) 绑定（super_admin 自动拥有免绑）
-- 注意：component 与 txgy-ui-admin-vben 页面文件一致后方可应用；
--       应用后请清 Redis 菜单/权限缓存（menu_role_ids:* / permission_menu_ids:* / role:*）并重新登录。
-- 列：id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < pollution-discharge-20260910-menu.sql
-- =====================================================================

-- 防重清理（若需重跑本文件）
DELETE FROM system_menu WHERE id BETWEEN 34380 AND 34381;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 34380 AND 34381;

INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34380, '排放合规流水', '', 2, 194, 34000, 'pollutionDischarge', 'ep:histogram', 'mes/safetyEnv/pollutionDischarge/index', 'MesPollutionDischarge', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34381, '查询', 'mes:set-pollution-discharge:query', 3, 1, 34380, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 绑定租户 2010 角色 60185(tenant_admin)
INSERT INTO system_role_menu (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `id`, 34380, '1', NOW(), '1', NOW(), b'0', `tenant_id` FROM system_role WHERE `id` = 60185;
INSERT INTO system_role_menu (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `id`, 34381, '1', NOW(), '1', NOW(), b'0', `tenant_id` FROM system_role WHERE `id` = 60185;
