-- =============================================================
-- CRM 全局检索菜单移除
--
-- 背景：
--   全局检索功能（前端 views/crm/search/index.vue + api/crm/global-search，
--   后端 CrmGlobalSearchController/Service/RespVO，接口 /crm/global-search/all）
--   已从代码中移除，本脚本清理其遗留的菜单与角色授权数据，
--   否则侧边栏仍会显示「全局检索」入口，点击后因页面不存在而空白。
--
-- 对应数据：
--   菜单    system_menu.id = 32661
--           （parent_id = 2397「CRM 系统」/ path = search / component = crm/search/index
--             / component_name = CrmGlobalSearch）
--   授权    system_role_menu.menu_id = 32661（54 条，含租户管理员/销售角色等）
--
-- 幂等：可重复执行。
-- 回滚：见 deploy/mysql/backup/2026-09-21-global-search-menu-rollback.sql
--       （菜单行 + 角色授权行的完整 mysqldump，可直接 source 恢复）
--
-- 适用库：ruoyi-vue-pro
-- =============================================================

USE `ruoyi-vue-pro`;

-- 1) 解除角色绑定
--    system_role_menu 为纯关系表（无 deleted 字段），只能物理删除
DELETE FROM `system_role_menu`
WHERE `menu_id` = 32661;

-- 2) 逻辑删除菜单
--    保留数据行（deleted 标记），如需恢复只需把 deleted 改回 b'0'
UPDATE `system_menu`
SET `deleted`     = b'1',
    `visible`     = b'0',
    `updater`     = '1',
    `update_time` = NOW()
WHERE `id` = 32661
   OR (`parent_id` = 2397 AND `path` = 'search' AND `component` = 'crm/search/index');

-- 3) 校验：两个结果都应为 0
SELECT COUNT(*) AS `仍可见的全局检索菜单` FROM `system_menu`
WHERE `deleted` = 0
  AND (`parent_id` = 2397 AND `path` = 'search' AND `component` = 'crm/search/index');

SELECT COUNT(*) AS `仍绑定的角色授权` FROM `system_role_menu` WHERE `menu_id` = 32661;
