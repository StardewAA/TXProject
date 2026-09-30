-- =====================================================================
-- MES 安全环保检测（SET）模块 - 治污设施 菜单+按钮+权限 SQL
-- 结构：目录「安全环保检测」id=34000（勿重建）> 功能菜单 34463 > 按钮 34464~34468
-- ID 段：34463~34468（34400~34454 已被环保报告/排放口/废气/废水/碳/危化品检查占用；
--        34460~34462 危化品链；**34455~34499 其余为空段，本片取其中连续 6 个**）
-- 权限：mes:set-facility:query / :create / :update / :delete / :operate
--       operate 一个键管"换炭 / 停运申报 / 停运审批 / 复运"四个动作 ——
--       它们都是"对设施动手"，没有谁该被单独拆出来的理由，拆了只是多四个键要配。
-- 角色：60185(tenant_admin) 绑全部；60466 环保专员 绑全部（治污设施本就归环保专员）。
-- ⚠ 前车之鉴：危化品链曾误占 34400~34402（环保报告的父菜单/查询/新增），
--   见 chemical-20260910-menu-fix.sql。排菜单 id 前**必须先查 system_menu 实际占用**。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < facility-20260910-menu.sql
-- 应用后必须**重启 mes-server**（内存权限缓存），见 docs/开发说明-2026-09-08.md §13.2 / §18.7。
-- =====================================================================

-- 防重清理（若需重跑本文件）
DELETE FROM system_menu      WHERE id BETWEEN 34463 AND 34468;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 34463 AND 34468;

-- 1. 功能菜单 + 按钮
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
  (34463, '治污设施', '', 2, 197, 34000, 'facility', 'ep:set-up', 'mes/safetyEnv/facility/index', 'MesFacility', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34464, '查询',     'mes:set-facility:query',   3, 1, 34463, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34465, '创建',     'mes:set-facility:create',  3, 2, 34463, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34466, '更新',     'mes:set-facility:update',  3, 3, 34463, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34467, '删除',     'mes:set-facility:delete',  3, 4, 34463, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34468, '运行操作', 'mes:set-facility:operate', 3, 5, 34463, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 2. 管理员角色 60185 绑全部
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `tenant_id`) VALUES
  (60185, 34463, 'system', 'system', 2010),
  (60185, 34464, 'system', 'system', 2010),
  (60185, 34465, 'system', 'system', 2010),
  (60185, 34466, 'system', 'system', 2010),
  (60185, 34467, 'system', 'system', 2010),
  (60185, 34468, 'system', 'system', 2010);

-- 3. 环保专员 60466 绑全部（治污设施运行管控是环保专员的本职）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `tenant_id`) VALUES
  (60466, 34463, 'system', 'system', 2010),
  (60466, 34464, 'system', 'system', 2010),
  (60466, 34465, 'system', 'system', 2010),
  (60466, 34466, 'system', 'system', 2010),
  (60466, 34467, 'system', 'system', 2010),
  (60466, 34468, 'system', 'system', 2010);

-- 4. 校验
SELECT '菜单34463-34468' k, COUNT(*) v FROM `system_menu` WHERE `id` BETWEEN 34463 AND 34468 AND `deleted` = 0
UNION ALL SELECT '管理员60185绑定', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60185 AND `menu_id` BETWEEN 34463 AND 34468 AND `deleted` = 0
UNION ALL SELECT '环保专员60466绑定', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60466 AND `menu_id` BETWEEN 34463 AND 34468 AND `deleted` = 0
UNION ALL SELECT '撞车检查(应为0)', COUNT(*) FROM `system_menu` WHERE `id` BETWEEN 34455 AND 34499 AND `deleted` = 0 AND `id` NOT BETWEEN 34460 AND 34468;
