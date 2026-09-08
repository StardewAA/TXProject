-- ======================================================================
-- 云培训学院 - 模块四：学员学习中心（侧边栏二级菜单）
-- 说明：
--   将 41005 "学员学习中心" 改造为带 4 个子菜单的父菜单（学习任务/直播房间/
--   学习统计与轨迹/防挂机配置），与现有"资源中心"二级菜单样式一致。
--   前端文件：
--     views/training/learning/index.vue            父 容器（router-view）
--     views/training/learning/task/index.vue       学习任务
--     views/training/learning/room/index.vue       直播房间
--     views/training/learning/track/index.vue      学习统计与轨迹
--     views/training/learning/config/index.vue     防挂机配置
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_learner_submenu.sql
-- ======================================================================

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41050, '学习任务',       'training:learning:query', 2, 10, 41005, 'task',   'lucide:list-checks',     'training/learning/task/index',   'TrainingLearningTask',   0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41051, '直播房间',       'training:learning:query', 2, 20, 41005, 'room',   'lucide:radio',            'training/learning/room/index',   'TrainingLearningRoom',   0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41052, '学习统计/轨迹',  'training:learning:query', 2, 30, 41005, 'track',  'lucide:line-chart',       'training/learning/track/index',  'TrainingLearningTrack',  0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41053, '防挂机配置',     'training:learning:query', 2, 40, 41005, 'config', 'lucide:shield-check',     'training/learning/config/index', 'TrainingLearningConfig', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
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