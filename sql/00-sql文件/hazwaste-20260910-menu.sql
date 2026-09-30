-- =====================================================================
-- MES 安全环保检测（SET）模块 - 危废链 菜单+按钮+门卫角色 SQL
-- 结构：目录「安全环保检测」id=34000（勿重建）> 功能菜单 34390 > 按钮 34391~34395
-- ID 段：34390~34395 危废链（34380/34381 排放流水、34382 标记终审已占用；34400+ 留给设计稿 34401~34413 的 dg_* 新页）
-- 权限：mes:set-hazwaste:query / :create / :update / :delete / :gate
-- 角色：租户 2010 角色 60185(tenant_admin) 绑全部；另建 60467 门卫(hazwaste_guard) 只绑 查询+门卫放行
-- 说明：设计稿《危险品全生命周期管理》§12 把新页排在 34400+、权限前缀 mes:dg-*；
--       本片沿用本仓已落地的 343xx + mes:set-* 口径（与 pollutionDischarge/pollutionTrace 一致），
--       34400+ 段留给尚未落地的处置任务/危化品档案/衡算等页，避免一段里两套命名。
-- 注意：component 与 txgy-ui-admin-vben 页面文件一致后方可应用；
--       应用后请清 Redis 菜单/权限缓存并重新登录（见 docs/开发说明-2026-09-08.md §13.2）。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < hazwaste-20260910-menu.sql
-- =====================================================================

-- 防重清理（若需重跑本文件）
DELETE FROM system_menu WHERE id BETWEEN 34390 AND 34395;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 34390 AND 34395;

-- 1. 功能菜单 + 按钮
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
  (34390, '危废管理', '', 2, 195, 34000, 'hazwaste', 'ep:document-delete', 'mes/safetyEnv/hazwaste/index', 'MesHazwaste', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34391, '查询',     'mes:set-hazwaste:query',  3, 1, 34390, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34392, '创建',     'mes:set-hazwaste:create', 3, 2, 34390, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34393, '更新',     'mes:set-hazwaste:update', 3, 3, 34390, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34394, '删除',     'mes:set-hazwaste:delete', 3, 4, 34390, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34395, '门卫放行', 'mes:set-hazwaste:gate',   3, 5, 34390, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 2. 租户 2010 超级管理员角色 60185 绑全部
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', r.`tenant_id` FROM `system_role` r
JOIN (SELECT 34390 AS id UNION ALL SELECT 34391 UNION ALL SELECT 34392 UNION ALL SELECT 34393 UNION ALL SELECT 34394 UNION ALL SELECT 34395) m
WHERE r.`id` = 60185;

-- 3. 门卫角色（只读 + 放行，刻意不给建/改/删：门卫不该能改联单）
INSERT INTO `system_role` (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `updater`, `tenant_id`)
VALUES (60467, '门卫', 'hazwaste_guard', 21, 1, '[]', 0, 2, '门卫：凭生效联单+四方签字放行危废出厂；不可新建/修改/删除联单', 'system', 'system', 2010);

-- 4. 演示账号：huahan_menwei / admin123（密码 hash 取 huahan，保证同密码）
INSERT INTO `system_users` (`username`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`, `sex`, `avatar`, `status`, `login_ip`, `login_date`, `creator`, `updater`, `tenant_id`, `password`)
SELECT 'huahan_menwei', '王强', '门卫（危废出厂硬放行）演示账号', `dept_id`, `post_ids`, 'menwei@example.com', '13910018097', `sex`, `avatar`, 0, '', NULL, 'system', 'system', 2010, `password`
FROM `system_users` WHERE `id` = 80370;

INSERT INTO `system_user_role` (`user_id`, `role_id`, `creator`, `updater`, `tenant_id`)
SELECT u.`id`, 60467, 'system', 'system', 2010 FROM `system_users` u
WHERE u.`username` = 'huahan_menwei' AND u.`tenant_id` = 2010;

-- 祖先目录必须一起绑：后端 filterDisableMenus 按父链递归剪枝，链子断一节整棵子树
-- 都被判成"无权"→ 解出 0 个权限码。5100/34011 都是空 permission 的目录，不发权限码。
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `tenant_id`) VALUES
    (60467, 5100, 'system', 'system', 2010),   -- MES 系统（根目录）
    (60467, 34000, 'system', 'system', 2010),  -- 安全环保检测（父分组）
    (60467, 34011, 'system', 'system', 2010),  -- 危废与化学品（父分组）
    (60467, 34390, 'system', 'system', 2010),  -- 危废管理
    (60467, 34391, 'system', 'system', 2010),  -- 查询
    (60467, 34395, 'system', 'system', 2010);  -- 门卫放行

-- 5. 校验
SELECT '菜单34390-34395' k, COUNT(*) v FROM `system_menu` WHERE `id` BETWEEN 34390 AND 34395 AND `deleted` = 0
UNION ALL SELECT '角色60467门卫', COUNT(*) FROM `system_role` WHERE `id` = 60467 AND `deleted` = 0
UNION ALL SELECT '账号huahan_menwei', COUNT(*) FROM `system_users` WHERE `username` = 'huahan_menwei' AND `deleted` = 0
UNION ALL SELECT '管理员60185绑定', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60185 AND `menu_id` BETWEEN 34390 AND 34395 AND `deleted` = 0
UNION ALL SELECT '门卫60467绑定', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60467 AND `deleted` = 0;
