-- =====================================================================
-- MES 安全环保检测（SET）模块 - 危化品链 菜单+按钮+安全员角色 SQL
-- 结构：目录「安全环保检测」id=34000（勿重建）> 功能菜单 34396 > 按钮 34397~34399 + 34460~34462
-- ⚠ 34400~34402 是「环保检测报告」的父菜单/查询/新增，**不可占用**（2026-09-10 撞过一次，见 chemical-20260910-menu-fix.sql）
-- ID 段：34396~34399 + 34460~34462 危化品链（34390~34395 危废链、34400~34454 环保报告等已占用；34455~34499 为空段）
-- 权限：mes:set-chemical:query / :create / :update / :delete / :check / :sign
-- 角色：租户 2010 角色 60185(tenant_admin) 绑全部；
--       另建 60468 危化品安全员(safety_officer) 只绑 查询+库位校验+五双签字
--       —— 五双要求"双账号各签一次"，所以必须有第二个能签字的账号，且它**不该**能改档案。
-- 说明：设计稿《危险品全生命周期管理》§12 把新页排在 34400+、权限前缀 mes:dg-*；
--       本片沿用本仓已落地的 343xx + mes:set-* 口径（与 pollutionDischarge/pollutionTrace/hazwaste 一致）。
-- 注意：component 与 txgy-ui-admin-vben 页面文件一致后方可应用；
--       应用后请清 Redis 菜单/权限缓存并重新登录（见 docs/开发说明-2026-09-08.md §13.2）。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < chemical-20260910-menu.sql
-- =====================================================================

-- 防重清理（若需重跑本文件）
DELETE FROM system_menu WHERE id BETWEEN 34396 AND 34399 OR id BETWEEN 34460 AND 34462;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 34396 AND 34399 OR menu_id BETWEEN 34460 AND 34462;

-- 1. 功能菜单 + 按钮
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
  (34396, '危化品档案', '', 2, 196, 34000, 'chemical', 'ep:warning', 'mes/safetyEnv/chemical/index', 'MesChemical', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34397, '查询',     'mes:set-chemical:query',  3, 1, 34396, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34398, '创建',     'mes:set-chemical:create', 3, 2, 34396, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34399, '更新',     'mes:set-chemical:update', 3, 3, 34396, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34460, '删除',     'mes:set-chemical:delete', 3, 4, 34396, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34461, '库位校验', 'mes:set-chemical:check',  3, 5, 34396, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34462, '五双签字', 'mes:set-chemical:sign',   3, 6, 34396, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 2. 租户 2010 超级管理员角色 60185 绑全部
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', r.`tenant_id` FROM `system_role` r
JOIN (SELECT 34396 AS id UNION ALL SELECT 34397 UNION ALL SELECT 34398 UNION ALL SELECT 34399
      UNION ALL SELECT 34460 UNION ALL SELECT 34461 UNION ALL SELECT 34462) m
WHERE r.`id` = 60185;

-- 3. 安全员角色（查询 + 校验 + 签字，刻意不给建/改/删：安全员不该能改档案本身）
INSERT INTO `system_role` (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `updater`, `tenant_id`)
VALUES (60468, '危化品安全员', 'safety_officer', 22, 1, '[]', 0, 2, '危化品安全员：库位校验 + 五双双人签字（双人制的第二人）；不可新建/修改/删除档案', 'system', 'system', 2010);

-- 4. 演示账号：huahan_anquan / admin123（密码 hash 取 huahan，保证同密码）
INSERT INTO `system_users` (`username`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`, `sex`, `avatar`, `status`, `login_ip`, `login_date`, `creator`, `updater`, `tenant_id`, `password`)
SELECT 'huahan_anquan', '李敏', '危化品安全员（五双双人签字第二人）演示账号', `dept_id`, `post_ids`, 'anquan@example.com', '13910018098', `sex`, `avatar`, 0, '', NULL, 'system', 'system', 2010, `password`
FROM `system_users` WHERE `id` = 80370;

INSERT INTO `system_user_role` (`user_id`, `role_id`, `creator`, `updater`, `tenant_id`)
SELECT u.`id`, 60468, 'system', 'system', 2010 FROM `system_users` u
WHERE u.`username` = 'huahan_anquan' AND u.`tenant_id` = 2010;

-- 祖先目录必须一起绑：后端 filterDisableMenus 按父链递归剪枝，链子断一节整棵子树
-- 都被判成"无权"→ 解出 0 个权限码。5100/34011 都是空 permission 的目录，不发权限码。
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `tenant_id`) VALUES
    (60468, 5100, 'system', 'system', 2010),   -- MES 系统（根目录）
    (60468, 34000, 'system', 'system', 2010),  -- 安全环保检测（父分组）
    (60468, 34011, 'system', 'system', 2010),  -- 危废与化学品（父分组）
    (60468, 34396, 'system', 'system', 2010),  -- 危化品档案
    (60468, 34397, 'system', 'system', 2010),  -- 查询
    (60468, 34461, 'system', 'system', 2010),  -- 库位校验
    (60468, 34462, 'system', 'system', 2010);  -- 五双签字

-- 5. 校验
SELECT '菜单34396-34399+34460-34462' k, COUNT(*) v FROM `system_menu` WHERE (`id` BETWEEN 34396 AND 34399 OR `id` BETWEEN 34460 AND 34462) AND `deleted` = 0
UNION ALL SELECT '角色60468安全员', COUNT(*) FROM `system_role` WHERE `id` = 60468 AND `deleted` = 0
UNION ALL SELECT '账号huahan_anquan', COUNT(*) FROM `system_users` WHERE `username` = 'huahan_anquan' AND `deleted` = 0
UNION ALL SELECT '管理员60185绑定', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60185 AND (`menu_id` BETWEEN 34396 AND 34399 OR `menu_id` BETWEEN 34460 AND 34462) AND `deleted` = 0
UNION ALL SELECT '安全员60468绑定', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60468 AND `deleted` = 0;
