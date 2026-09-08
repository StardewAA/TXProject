-- ======================================================================
-- 云培训学院 - 资源中心：补挂侧边栏子菜单 + 父菜单修正
-- 说明：
--   模块一（资源中心）的 5 个真实子页面（文档资源库 / 视频资源库 / 考试题库 /
--   分类与标签 / 操作日志）此前未挂菜单，导致"资源中心"父菜单（41002）点击
--   无响应、页面空白。本脚本：
--     1) 将 41002 改回 type=2 并绑定 ModuleOverview 占位页（保留模块介绍/建设进度）
--     2) 新增 5 个子菜单（type=2，parent_id=41002），component 指向现有真实页面
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_resource_submenu.sql
-- ======================================================================

-- 1. 父菜单改为叶子菜单，指向资源中心介绍页（ModuleOverview 占位）
UPDATE `system_menu`
SET `type` = 2,
    `component` = 'training/resource/index',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `id` = 41002;

-- 2. 新增 5 个子菜单（分类与标签、操作日志 暂合入分类与标签与操作日志共用页）
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41060, '文档资源库', 'training:resource:query', 2, 10, 41002, 'doc-list',     'lucide:file-text',        'training/resource/doc-list/index',     'TrainingResourceDocList',     0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41061, '视频资源库', 'training:resource:query', 2, 20, 41002, 'video-list',   'lucide:video',            'training/resource/video-list/index',   'TrainingResourceVideoList',   0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41062, '考试题库',   'training:resource:query', 2, 30, 41002, 'question',     'lucide:list-checks',      'training/resource/question-list/index', 'TrainingResourceQuestion',    0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41063, '分类与标签', 'training:resource:query', 2, 40, 41002, 'category',     'lucide:tags',             'training/resource/category/index',     'TrainingResourceCategory',    0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41064, '操作日志',   'training:resource:query', 2, 50, 41002, 'log',          'lucide:activity',         'training/resource/log/index',          'TrainingResourceLog',         0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
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