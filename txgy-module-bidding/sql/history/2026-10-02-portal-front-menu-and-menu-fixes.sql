-- =====================================================================
-- 2026-10-02 菜单修正 + 前台门户跳转入口
-- 1) 6736「外部公告审核管理」菜单 permission 由 :update 修正为 :query
--    （菜单可见性应挂查询权限；按钮级操作权限不变，见 60001201/页面内 can()）
-- 2) 6759「主体信息与授权管理」空目录菜单删除（type=1、无 component、无子菜单，不应存在）
-- 3) 新增 60006604「前台门户」菜单（parent 6735 招投标，跳转壳组件 bidding/portalRedirect/index，
--    同 SPA router.replace 保留 accessToken，门户接口不丢鉴权）
-- =====================================================================

USE `ruoyi-vue-pro`;

-- 1) 6736 菜单权限码修正
UPDATE `system_menu` SET `permission` = 'bidding:notice-external:query', `updater` = '1', `update_time` = NOW() WHERE `id` = 6736 AND `deleted` = b'0';

-- 2) 删除 6759 空目录（软删菜单 + 角色授权关联）
UPDATE `system_menu` SET `deleted` = b'1', `updater` = '1', `update_time` = NOW() WHERE `id` = 6759 AND `deleted` = b'0';
UPDATE `system_role_menu` SET `deleted` = b'1', `updater` = '1', `update_time` = NOW() WHERE `menu_id` = 6759 AND `deleted` = b'0';

-- 3) 前台门户跳转菜单（空 permission：门户公开（ignoreAccess），入口不设权限门槛，父目录可见即可见）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(60006604, '前台门户', '', 2, 1, 6735, 'portal-front', 'lucide:globe', 'bidding/portalRedirect/index', 'BiddingPortalRedirect', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 复核（全部应为 1 行 / 1 行 / 1 行）
SELECT id, name, permission, deleted FROM system_menu WHERE id IN (6736, 6759, 60006604);