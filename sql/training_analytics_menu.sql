-- 模块九：数据统计与档案归档 - 菜单与按钮权限（幂等脚本）
-- 目录 41010；子菜单 41601~41608；按钮 41611~41620。
-- 41606 全平台看板为平台级入口：仅系统租户超管可见（租户套餐未勾选即不可见）。
-- 97 证书公开验真公共接口免登录（@PermitAll），菜单入口保留在登录内。
--
-- 版本说明（2026-09-08）：
--  旧规划使用 41037~41054 段，其中 41040~41047 与模块八「校企合作与创新大赛」
--  （training_module8_cooperation_menu.sql）重叠、41050~41053 与「学员学习中心」
--  （training_learner_submenu.sql）重叠，导致同段互相覆盖、模块九子菜单缺失。
--  本版本整体迁移到空闲段 41601~41620，与上述模块彻底隔离。

SET NAMES utf8mb4;

-- 1. 41010 升级为目录（原统计看板页面下沉为 41601）
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41010, '数据统计与档案归档', 'training:analytics:query', 1, 100, 41000, 'analytics', 'lucide:chart-no-axes-combined', NULL, 'TrainingAnalytics', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `type` = VALUES(`type`), `component` = VALUES(`component`),
`component_name` = VALUES(`component_name`), `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

-- 2. 子菜单
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41601, '统计看板', 'training:analytics:query', 2, 1, 41010, 'dashboard', 'lucide:gauge', 'training/analytics/index', 'TrainingAnalytics', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41602, '学时管控与学习监管', 'training:analytics:study-monitor:query', 2, 10, 41010, 'study-monitor', 'lucide:timer', 'training/analytics/study-monitor/index', 'TrainingStudyMonitor', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41603, '个人学习档案', 'training:analytics:personal-archive:query', 2, 20, 41010, 'personal-archive', 'lucide:id-card', 'training/analytics/personal-archive/index', 'TrainingPersonalArchive', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41604, '讲师数据权限', 'training:analytics:lecturer:query', 2, 30, 41010, 'lecturer', 'lucide:presentation', 'training/analytics/lecturer/index', 'TrainingLecturerData', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41605, '企业培训报表与档案', 'training:analytics:enterprise-report:query', 2, 40, 41010, 'enterprise-report', 'lucide:file-chart-column', 'training/analytics/enterprise-report/index', 'TrainingEnterpriseReport', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41606, '全平台看板与治理', 'training:analytics:platform', 2, 50, 41010, 'platform', 'lucide:layout-dashboard', 'training/analytics/platform/index', 'TrainingPlatformDashboard', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41607, '外部系统对接', 'training:analytics:external-api:query', 2, 60, 41010, 'external-api', 'lucide:plug-zap', 'training/analytics/external-api/index', 'TrainingExternalApi', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41608, '证书公开验真', 'training:analytics:cert-verify:query', 2, 70, 41010, 'cert-verify', 'lucide:badge-check', 'training/analytics/cert-verify/index', 'TrainingCertVerify', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
`type` = VALUES(`type`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
`component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
`updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

-- 3. 按钮权限（type=3）
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41611, '学习督促批量推送', 'training:analytics:study-monitor:remind', 3, 1, 41602, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41612, '企业报表导出', 'training:analytics:enterprise-report:export', 3, 1, 41605, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41613, '平台看板导出', 'training:analytics:platform:export', 3, 1, 41606, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41614, '档案人工变更', 'training:analytics:archive-manual:update', 3, 2, 41606, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41615, '证书/学时规则配置', 'training:analytics:rule-config:update', 3, 3, 41606, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41616, '外部API管理', 'training:analytics:external-config:update', 3, 4, 41606, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41617, '租户隔离配置', 'training:analytics:tenant-isolation:update', 3, 5, 41606, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41618, '外部任务下发', 'training:analytics:external-api:dispatch', 3, 1, 41607, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41619, '外部档案获取', 'training:analytics:external-api:archive', 3, 2, 41607, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41620, '证书免登录验真', 'training:analytics:cert-verify:public', 3, 1, 41608, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
`type` = VALUES(`type`), `parent_id` = VALUES(`parent_id`),
`updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

-- 4. 自愈：早期环境曾把部分按钮权限错建为页面菜单（type=2），
--    导致侧边栏出现空白菜单项。此语句保证重复执行后按钮类型始终正确。
UPDATE `system_menu`
SET `type` = 3, `updater` = 'admin', `update_time` = NOW()
WHERE `id` BETWEEN 41611 AND 41620 AND `type` <> 3;

-- 5. 清理遗留：迁移前旧段（41037/41038/41039/41048/41049/41054）残留的模块九菜单行。
--    仅删除权限确认为 training:analytics% 的旧行，绝不触碰模块八 coop / 学习中心等行。
DELETE FROM `system_menu`
WHERE `deleted` = b'0'
  AND `id` IN (41037, 41038, 41039, 41048, 41049, 41054)
  AND `permission` LIKE 'training:analytics%';
