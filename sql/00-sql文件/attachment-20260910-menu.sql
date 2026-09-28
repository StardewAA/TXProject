-- =====================================================================
-- MES 安全环保检测（SET）模块 - 通用业务附件 权限 SQL
-- 结构：按钮挂在「污染追溯」34370 之下（附件是跨单据的审计面，不单独占一个侧边栏菜单，
--       type=3 的按钮本就不渲染成路由）
-- ID 段：34450~34459 为业务附件保留（34370~34399 已被污染追溯占用）
-- 权限：mes:set-attachment:query  查询附件列表
--       mes:set-attachment:update 登记/删除附件（文件走 /infra/file/upload 前端直传，这里只登记地址）
-- 角色：租户 2010 角色 60185(tenant_admin) 绑定（super_admin 自动拥有免绑）
-- 注意：应用后请清 Redis 菜单/权限缓存（menu_role_ids:* / permission_menu_ids:* / role:*）并重新登录。
-- 列：id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < attachment-20260910-menu.sql
-- =====================================================================

-- 防重清理（若需重跑本文件）
DELETE FROM system_menu WHERE id BETWEEN 34450 AND 34459;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 34450 AND 34459;

INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34450, '附件查看', 'mes:set-attachment:query', 3, 10, 34370, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34451, '附件维护', 'mes:set-attachment:update', 3, 11, 34370, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 绑定租户 2010 角色 60185(tenant_admin)
INSERT INTO system_role_menu (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `id`, 34450, '1', NOW(), '1', NOW(), b'0', `tenant_id` FROM system_role WHERE `id` = 60185;
INSERT INTO system_role_menu (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `id`, 34451, '1', NOW(), '1', NOW(), b'0', `tenant_id` FROM system_role WHERE `id` = 60185;
