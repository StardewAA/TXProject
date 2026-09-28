-- =====================================================================
-- MES 安全环保检测（SET）模块 - 污染追溯 菜单+按钮 SQL
-- 结构：目录「安全环保检测」id=34000（20260902 已建，勿重建）> 功能菜单 34370 > 按钮 34371
-- ID 段：34370~34399 为污染追溯保留（34301 判定/34302 台账/34350 排污许可 已占用）
-- 权限：单一查询按钮 mes:set-pollution-trace:query（三只读 page/get 共用；页面无新增/编辑/删除）
-- 角色：租户 2010 角色 60185(tenant_admin) 绑定（super_admin 自动拥有免绑）
-- 注意：component 与 txgy-ui-admin-vben 页面文件一致后方可应用；
--       应用后请清 Redis 菜单/权限缓存（menu_role_ids:* / permission_menu_ids:* / role:*）并重新登录。
-- 列：id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < pollution-trace-20260909-menu.sql
-- =====================================================================

-- 防重清理（若需重跑本文件）
DELETE FROM system_menu WHERE id BETWEEN 34370 AND 34399;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 34370 AND 34399;

INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34370, '污染追溯', '', 2, 193, 34000, 'pollutionTrace', 'ep:set-up', 'mes/safetyEnv/pollutionTrace/index', 'MesPollutionTrace', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34371, '查询', 'mes:set-pollution-trace:query', 3, 1, 34370, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 绑定租户 2010 角色 60185(tenant_admin)
INSERT INTO system_role_menu (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `id`, 34370, '1', NOW(), '1', NOW(), b'0', `tenant_id` FROM system_role WHERE `id` = 60185;
INSERT INTO system_role_menu (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `id`, 34371, '1', NOW(), '1', NOW(), b'0', `tenant_id` FROM system_role WHERE `id` = 60185;
