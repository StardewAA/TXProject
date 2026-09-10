-- =====================================================================
-- 云培训学院 - 模块一：资源中心（易）菜单与按钮权限
-- ID 段：41002（升级为目录） + 41020~41049（资源中心子菜单/按钮）
-- 说明：
--   1. 41002 原为占位页面菜单（type=2），现升级为目录（type=1），
--      其 component 指向的真实页面由本模块各子页面承担；
--   2. 脚本可重复执行，只新增或更新本模块菜单，不删除、覆盖其它模块菜单；
--   3. 超级管理员自动拥有全部菜单；普通角色可在“系统管理 / 角色管理”中按需授权。
-- =====================================================================

INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- 41002 资源中心升级为目录（保留原图标，path 仍为 /training/resource）
(41002, '资源中心', 'training:resource:query', 1, 20, 41000, 'resource', 'lucide:library-big', NULL, 'TrainingResource', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- ============ 资源中心子页面 ============
(41020, '文档资源库', 'training:resource-doc:query', 2, 10, 41002, 'doc', 'lucide:file-text', 'training/resource/doc-list', 'TrainingDocList', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41021, '视频资源库', 'training:resource-video:query', 2, 20, 41002, 'video', 'lucide:video', 'training/resource/video-list', 'TrainingVideoList', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41022, '考试题库', 'training:question:query', 2, 30, 41002, 'question', 'lucide:list-checks', 'training/resource/question-list', 'TrainingQuestionList', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41023, '分类与标签', 'training:resource-category:query', 2, 40, 41002, 'category', 'lucide:tags', 'training/resource/category', 'TrainingResourceCategory', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41024, '操作日志', 'training:resource-log:query', 2, 50, 41002, 'log', 'lucide:scroll-text', 'training/resource/log', 'TrainingResourceLog', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- ============ 文档库按钮 ============
(41025, '文档新增上传', 'training:resource-doc:create', 3, 1, 41020, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41026, '文档修改/状态', 'training:resource-doc:update', 3, 2, 41020, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41027, '文档删除', 'training:resource-doc:delete', 3, 3, 41020, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- ============ 视频库按钮 ============
(41028, '视频新增上传', 'training:resource-video:create', 3, 1, 41021, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41029, '视频修改/状态/学时', 'training:resource-video:update', 3, 2, 41021, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41030, '视频删除', 'training:resource-video:delete', 3, 3, 41021, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- ============ 题库按钮 ============
(41031, '试题新增', 'training:question:create', 3, 1, 41022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41032, '试题修改/状态', 'training:question:update', 3, 2, 41022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41033, '试题删除', 'training:question:delete', 3, 3, 41022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- ============ 分类与标签按钮 ============
(41034, '分类/标签新增', 'training:resource-category:create', 3, 1, 41023, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41035, '分类/标签修改', 'training:resource-category:update', 3, 2, 41023, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41036, '分类/标签删除', 'training:resource-category:delete', 3, 3, 41023, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`path` = VALUES(`path`),
`icon` = VALUES(`icon`),
`component` = VALUES(`component`),
`component_name` = VALUES(`component_name`),
`status` = VALUES(`status`),
`visible` = VALUES(`visible`),
`keep_alive` = VALUES(`keep_alive`),
`always_show` = VALUES(`always_show`),
`updater` = 'admin',
`update_time` = NOW(),
`deleted` = b'0';
