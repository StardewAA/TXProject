-- =====================================================================
-- 云培训学院菜单
-- 独立 ID 段：41000 ~ 41010
-- 说明：脚本可重复执行，只新增或更新本模块菜单，不删除、覆盖其它模块菜单。
-- 超级管理员自动拥有全部菜单；普通角色可在“系统管理 / 角色管理”中按需授权。
-- =====================================================================

INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41000, '云培训学院', '', 1, 360, 0, '/training', 'lucide:graduation-cap', NULL, 'TrainingCenter', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41001, '工作台', 'training:workbench:query', 2, 10, 41000, 'workbench', 'lucide:layout-dashboard', 'training/workbench/index', 'TrainingWorkbench', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41002, '资源中心', 'training:resource:query', 2, 20, 41000, 'resource', 'lucide:library-big', 'training/resource/index', 'TrainingResource', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41003, '课程管理', 'training:course:query', 2, 30, 41000, 'course', 'lucide:notebook-tabs', 'training/course/index', 'TrainingCourse', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41004, '培训管理', 'training:plan:query', 2, 40, 41000, 'plan', 'lucide:calendar-range', 'training/plan/index', 'TrainingPlan', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41005, '学员学习中心', 'training:learning:query', 2, 50, 41000, 'learning', 'lucide:book-open-check', 'training/learning/index', 'TrainingLearning', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41006, '考试与认证', 'training:exam:query', 2, 60, 41000, 'exam', 'lucide:badge-check', 'training/exam/index', 'TrainingExam', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41007, '直播与讲师体系', 'training:live:query', 2, 70, 41000, 'live', 'lucide:radio-tower', 'training/live/index', 'TrainingLive', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41008, 'VR 虚拟仿真实操训练', 'training:vr:query', 2, 80, 41000, 'vr', 'lucide:glasses', 'training/vr/index', 'TrainingVr', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41009, '校企合作与创新大赛', 'training:cooperation:query', 2, 90, 41000, 'cooperation', 'lucide:handshake', 'training/cooperation/index', 'TrainingCooperation', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41010, '数据统计与档案归档', 'training:analytics:query', 2, 100, 41000, 'analytics', 'lucide:chart-no-axes-combined', 'training/analytics/index', 'TrainingAnalytics', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
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
