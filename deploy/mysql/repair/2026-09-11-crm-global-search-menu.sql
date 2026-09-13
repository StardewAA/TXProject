-- =============================================================
-- CRM 全局检索菜单补配
--
-- 背景：
--   提交 6ec5dff9「客户上下游分类打通 + 一键引用回填 + 相似客户推荐 + 全局检索」
--   新增了全局检索页面（txgy-ui-admin-vben/.../views/crm/search/index.vue，
--   路由名 CrmGlobalSearch），但仓库未附带对应的 system_menu 配置，
--   导致页面做出来却没有任何入口。
--
-- 对应后端接口：GET /crm/global-search/all
--   权限标识：复用 crm:customer:query
--   （见 CrmGlobalSearchController#searchAll 的 @PreAuthorize）
--
-- 适用库：ruoyi-vue-pro
-- 说明：超管（super_admin）自动拥有全部菜单，无需额外插 system_role_menu；
--       其他角色需要在「角色管理 - 菜单权限」中勾选本菜单。
-- =============================================================

USE `ruoyi-vue-pro`;

-- 幂等：先清理同路径的旧记录，避免重复插入
DELETE FROM `system_menu`
WHERE `parent_id` = 2397 AND `path` = 'search' AND `component` = 'crm/search/index';

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
   `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  ('全局检索', '', 2, 5, 2397, 'search', 'ep:search',
   'crm/search/index', 'CrmGlobalSearch', 0, b'1', b'1', b'0',
   '1', NOW(), '1', NOW(), b'0');

-- 校验
SELECT `id`, `name`, `path`, `component`, `component_name`, `sort`, `visible`
FROM `system_menu`
WHERE `parent_id` = 2397 AND `path` = 'search' AND `deleted` = 0;
