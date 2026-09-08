-- =====================================================================
-- 2026-09-08 模块九「数据统计与档案归档」菜单编号迁移
-- 背景：旧编号 41037~41054 与模块八「校企合作与创新大赛」(41040~41047/41300~41326)
--       及「学员学习中心」(41050~41053) 冲突，导致模块九子菜单在库中被覆盖。
-- 方案：整体迁移至空闲段 41601~41620，目录仍为 41010。
-- 说明：幂等，可重复执行；只操作 training:analytics 相关行，不影响 coop/learning。
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- 1. 迁移角色授权引用（旧菜单 id -> 新菜单 id），避免授权丢失
UPDATE `system_role_menu` rm
JOIN `system_menu` m ON m.id = rm.menu_id AND m.deleted = b'0' AND m.permission LIKE 'training:analytics%'
SET rm.menu_id = 41601 WHERE rm.menu_id = 41037;
UPDATE `system_role_menu` rm
JOIN `system_menu` m ON m.id = rm.menu_id AND m.deleted = b'0' AND m.permission LIKE 'training:analytics%'
SET rm.menu_id = 41602 WHERE rm.menu_id = 41038;
UPDATE `system_role_menu` rm
JOIN `system_menu` m ON m.id = rm.menu_id AND m.deleted = b'0' AND m.permission LIKE 'training:analytics%'
SET rm.menu_id = 41603 WHERE rm.menu_id = 41039;
UPDATE `system_role_menu` rm
JOIN `system_menu` m ON m.id = rm.menu_id AND m.deleted = b'0' AND m.permission LIKE 'training:analytics%'
SET rm.menu_id = 41614 WHERE rm.menu_id = 41048;
UPDATE `system_role_menu` rm
JOIN `system_menu` m ON m.id = rm.menu_id AND m.deleted = b'0' AND m.permission LIKE 'training:analytics%'
SET rm.menu_id = 41615 WHERE rm.menu_id = 41049;
UPDATE `system_role_menu` rm
JOIN `system_menu` m ON m.id = rm.menu_id AND m.deleted = b'0' AND m.permission LIKE 'training:analytics%'
SET rm.menu_id = 41620 WHERE rm.menu_id = 41054;

-- 2. 确保 41010 目录定义正确（type=1，作为子菜单挂载点）
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41010, '数据统计与档案归档', 'training:analytics:query', 1, 100, 41000, 'analytics', 'lucide:chart-no-axes-combined', NULL, 'TrainingAnalytics', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `type` = VALUES(`type`), `component` = VALUES(`component`),
`component_name` = VALUES(`component_name`), `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

-- 3. 插入 8 个子菜单（41601~41608，父级 41010）
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

-- 4. 插入 10 个按钮权限（41611~41620）
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

-- 5. 自愈按钮 type
UPDATE `system_menu`
SET `type` = 3, `updater` = 'admin', `update_time` = NOW()
WHERE `id` BETWEEN 41611 AND 41620 AND `type` <> 3;

-- 6. 清理旧段遗留的模块九行（仅限 training:analytics% 权限，避免误伤 coop/learning）
DELETE FROM `system_menu`
WHERE `deleted` = b'0'
  AND `id` IN (41037, 41038, 41039, 41048, 41049, 41054)
  AND `permission` LIKE 'training:analytics%';

COMMIT;
