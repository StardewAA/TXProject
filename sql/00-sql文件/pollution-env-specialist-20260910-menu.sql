-- =====================================================================
-- 环保专员角色 + 标记品终审（需求 5.6：环保专员「标记品管理（终审）」）
-- 2026-09-10
--
-- 口径：标记/解除标记只由环保专员定夺（独立权限点 mes:set-pollution-mark:update），
--       企业超级管理员(60185)不授此权限，形成「复核归质检、标记终审归环保专员」的隔离。
--       复核/流转权限沿用 mes:set-pollution-check:review（台账接口复用该权限），不发环保专员。
-- =====================================================================

-- 1. 按钮菜单：标记终审（挂在 34302 污染暂存台账 下）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES (34382, '标记终审', 'mes:set-pollution-mark:update', 3, 1, 34302, '', '', '', '', 0, b'1', b'1', b'1', 'system', 'system');

-- 2. 角色：环保专员（租户 2010）
INSERT INTO `system_role` (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `updater`, `tenant_id`)
VALUES (60466, '环保专员', 'env_specialist', 20, 1, '[]', 0, 2, '环保专员：台账标记品终审；不做污染复核、不做台账流转', 'system', 'system', 2010);

-- 3. 演示账号：huahan_huanbao / admin123（密码 hash 直接取 huahan，保证同密码）
INSERT INTO `system_users` (`username`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`, `sex`, `avatar`, `status`, `login_ip`, `login_date`, `creator`, `updater`, `tenant_id`, `password`)
SELECT 'huahan_huanbao', '郑洁', '环保专员（标记品终审）演示账号', `dept_id`, `post_ids`, 'huanbao@example.com', '13910018099', `sex`, `avatar`, 0, '', NULL, 'system', 'system', 2010, `password`
FROM `system_users` WHERE `id` = 80370;

-- 4. 绑定用户 → 角色
INSERT INTO `system_user_role` (`user_id`, `role_id`, `creator`, `updater`, `tenant_id`)
SELECT u.`id`, 60466, 'system', 'system', 2010 FROM `system_users` u
WHERE u.`username` = 'huahan_huanbao' AND u.`tenant_id` = 2010;

-- 5. 角色 → 菜单：仅阅览类页面 + 终审按钮（不含复核/新增/修改/删除）
--
-- 必须先绑祖先目录 5100(MES 系统) / 34010(污染管控)：后端 filterDisableMenus 是
-- 按父链递归剪枝的，链子断在哪一节，那一节的整棵子树都会被判成"无权"→
-- 解出来就是 0 个权限码，页面上按钮全被前端 auth 过滤隐藏。
-- 5100/34010 都是 type=1 目录且 permission 为空，绑它们不发任何权限码，只是把链子接通。
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `tenant_id`) VALUES
    (60466, 5100, 'system', 'system', 2010),   -- MES 系统（根目录）
    (60466, 34000, 'system', 'system', 2010),  -- 安全环保检测（父分组）
    (60466, 34010, 'system', 'system', 2010),  -- 污染管控（父分组）
    (60466, 34301, 'system', 'system', 2010),  -- 污染判定（只读：判定查询权限是台账页的后端权限）
    (60466, 34302, 'system', 'system', 2010),  -- 污染暂存台账
    (60466, 34370, 'system', 'system', 2010),  -- 污染追溯
    (60466, 34380, 'system', 'system', 2010),  -- 排放合规流水
    (60466, 34311, 'system', 'system', 2010),  -- 判定查询按钮
    (60466, 34371, 'system', 'system', 2010),  -- 追溯查询按钮
    (60466, 34381, 'system', 'system', 2010),  -- 排放流水查询按钮
    (60466, 34382, 'system', 'system', 2010);  -- 标记终审按钮

-- 6. 校验
SELECT '菜单34382' k, COUNT(*) v FROM `system_menu` WHERE `id` = 34382 AND `deleted` = 0
UNION ALL SELECT '角色60466', COUNT(*) FROM `system_role` WHERE `id` = 60466 AND `deleted` = 0
UNION ALL SELECT '账号huahan_huanbao', COUNT(*) FROM `system_users` WHERE `username` = 'huahan_huanbao' AND `deleted` = 0
UNION ALL SELECT '角色菜单绑定', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60466 AND `deleted` = 0;
