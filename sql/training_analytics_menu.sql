-- 模块九：数据统计与档案归档 - 菜单与按钮权限（幂等脚本）
-- 41010 升级目录；41037~41044 子菜单；41045~41054 按钮。
-- 41042 全平台看板为平台级入口：仅系统租户超管可见（租户套餐未勾选即不可见）。
-- 97 证书公开验真公共接口免登录（@PermitAll），菜单入口保留在登录内。

-- 1. 41010 升级为目录（原统计看板页面下沉为 41037）
UPDATE `system_menu`
SET `type` = 1, `component` = NULL, `component_name` = 'TrainingAnalytics',
    `icon` = 'lucide:chart-no-axes-combined', `updater` = 'admin', `update_time` = NOW()
WHERE `id` = 41010;

-- 2. 子菜单
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41037, '统计看板', 'training:analytics:query', 2, 1, 41010, 'dashboard', 'lucide:gauge', 'training/analytics/index', 'TrainingAnalytics', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41038, '学时管控与学习监管', 'training:analytics:study-monitor:query', 2, 10, 41010, 'study-monitor', 'lucide:timer', 'training/analytics/study-monitor/index', 'TrainingStudyMonitor', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41039, '个人学习档案', 'training:analytics:personal-archive:query', 2, 20, 41010, 'personal-archive', 'lucide:id-card', 'training/analytics/personal-archive/index', 'TrainingPersonalArchive', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41040, '讲师数据权限', 'training:analytics:lecturer:query', 2, 30, 41010, 'lecturer', 'lucide:presentation', 'training/analytics/lecturer/index', 'TrainingLecturerData', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41041, '企业培训报表与档案', 'training:analytics:enterprise-report:query', 2, 40, 41010, 'enterprise-report', 'lucide:file-chart-column', 'training/analytics/enterprise-report/index', 'TrainingEnterpriseReport', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41042, '全平台看板与治理', 'training:analytics:platform', 2, 50, 41010, 'platform', 'lucide:layout-dashboard', 'training/analytics/platform/index', 'TrainingPlatformDashboard', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41043, '外部系统对接', 'training:analytics:external-api:query', 2, 60, 41010, 'external-api', 'lucide:plug-zap', 'training/analytics/external-api/index', 'TrainingExternalApi', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41044, '证书公开验真', 'training:analytics:cert-verify:query', 2, 70, 41010, 'cert-verify', 'lucide:badge-check', 'training/analytics/cert-verify/index', 'TrainingCertVerify', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
`type` = VALUES(`type`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
`component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
`updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

-- 3. 按钮权限（type=3）
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41045, '学习督促批量推送', 'training:analytics:study-monitor:remind', 3, 1, 41038, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41046, '企业报表导出', 'training:analytics:enterprise-report:export', 3, 1, 41041, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41047, '平台看板导出', 'training:analytics:platform:export', 3, 1, 41042, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41048, '档案人工变更', 'training:analytics:archive-manual:update', 3, 2, 41042, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41049, '证书/学时规则配置', 'training:analytics:rule-config:update', 3, 3, 41042, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41050, '外部API管理', 'training:analytics:external-config:update', 3, 4, 41042, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41051, '租户隔离配置', 'training:analytics:tenant-isolation:update', 3, 5, 41042, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41052, '外部任务下发', 'training:analytics:external-api:dispatch', 3, 1, 41043, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41053, '外部档案获取', 'training:analytics:external-api:archive', 3, 2, 41043, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41054, '证书免登录验真', 'training:analytics:cert-verify:public', 3, 1, 41044, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
`type` = VALUES(`type`), `parent_id` = VALUES(`parent_id`),
`updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

-- 4. 自愈：早期环境曾把部分按钮权限错建为页面菜单（type=2），
--    导致侧边栏出现空白菜单项。此语句保证重复执行后按钮类型始终正确。
UPDATE `system_menu`
SET `type` = 3, `updater` = 'admin', `update_time` = NOW()
WHERE `id` BETWEEN 41045 AND 41054 AND `type` <> 3;
