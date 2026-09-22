-- =====================================================================
-- 2026-09-21 菜单图标补齐 / 修正（招投标 + 云培训学院 + 电子资质 + CMS + MES 安全环保）
--
-- 背景：
--   本轮新部署/新导入的模块菜单里，有一批页面菜单（type=2，visible=1）没有图标，
--   侧边栏会渲染成空占位；另有 3 条菜单写了图标库里不存在的图标名（前端离线图标
--   兜底加载 /iconify/{prefix}.json 后仍取不到图形），同样是空白。
--
-- 做法：
--   1) 3 条非法图标名 → 同集合内的等价合法图标；
--   2) 补齐 34 条页面菜单的图标（仅改 icon 列，不动名称/路径/权限）；
--   3) 所有图标名均已对照前端本地图标集合校验存在，避免再次出现空图标。
--
-- 幂等：全部为按主键的定向 UPDATE，可重复执行。
-- =====================================================================

-- 1) 非法图标名修正
UPDATE `system_menu` SET `icon` = 'carbon:data-vis-1', `update_time` = NOW() WHERE `id` = 6737 AND `icon` = 'carbon:data-vis';
UPDATE `system_menu` SET `icon` = 'ep:first-aid-kit', `update_time` = NOW() WHERE `id` = 34000 AND `icon` = 'ep:leaf';
UPDATE `system_menu` SET `icon` = 'ep:user-filled', `update_time` = NOW() WHERE `id` = 70012 AND `icon` = 'ep:user-check';

-- 2) 页面菜单图标补齐（只补 icon 为空的行）
UPDATE `system_menu` SET `icon` = 'carbon:data-vis-1', `update_time` = NOW() WHERE `id` = 6737 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'ep:monitor', `update_time` = NOW() WHERE `id` = 6738 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'ep:trend-charts', `update_time` = NOW() WHERE `id` = 6748 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'ep:first-aid-kit', `update_time` = NOW() WHERE `id` = 34000 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:presentation', `update_time` = NOW() WHERE `id` = 41011 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:users', `update_time` = NOW() WHERE `id` = 41012 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:chart-no-axes-combined', `update_time` = NOW() WHERE `id` = 41013 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:timer', `update_time` = NOW() WHERE `id` = 41014 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:badge-check', `update_time` = NOW() WHERE `id` = 41015 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:library-big', `update_time` = NOW() WHERE `id` = 41040 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:graduation-cap', `update_time` = NOW() WHERE `id` = 41041 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:activity', `update_time` = NOW() WHERE `id` = 41042 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:chart-no-axes-column-increasing', `update_time` = NOW() WHERE `id` = 41043 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:clipboard-list', `update_time` = NOW() WHERE `id` = 41044 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:file-chart-column', `update_time` = NOW() WHERE `id` = 41045 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:presentation', `update_time` = NOW() WHERE `id` = 41046 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:library-big', `update_time` = NOW() WHERE `id` = 41047 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:layout-dashboard', `update_time` = NOW() WHERE `id` = 41700 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:list-checks', `update_time` = NOW() WHERE `id` = 41701 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:file-text', `update_time` = NOW() WHERE `id` = 41702 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:calendar-range', `update_time` = NOW() WHERE `id` = 41703 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:clipboard-check', `update_time` = NOW() WHERE `id` = 41704 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:shield-check', `update_time` = NOW() WHERE `id` = 41705 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:handshake', `update_time` = NOW() WHERE `id` = 41706 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:notebook-tabs', `update_time` = NOW() WHERE `id` = 41707 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:glasses', `update_time` = NOW() WHERE `id` = 41708 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:id-card', `update_time` = NOW() WHERE `id` = 41709 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:radio', `update_time` = NOW() WHERE `id` = 41800 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:radio-tower', `update_time` = NOW() WHERE `id` = 41801 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:boxes', `update_time` = NOW() WHERE `id` = 41802 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:user-round-cog', `update_time` = NOW() WHERE `id` = 41803 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:messages-square', `update_time` = NOW() WHERE `id` = 41804 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:bar-chart-3', `update_time` = NOW() WHERE `id` = 41805 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'lucide:book-open-check', `update_time` = NOW() WHERE `id` = 41806 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'ep:user-filled', `update_time` = NOW() WHERE `id` = 70012 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'ep:list', `update_time` = NOW() WHERE `id` = 60005100 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';
UPDATE `system_menu` SET `icon` = 'ep:office-building', `update_time` = NOW() WHERE `id` = 60005200 AND (`icon` IS NULL OR `icon` = '') AND `deleted` = b'0';

-- 校验：以下查询应返回 0 行（类型 1/2 的菜单不应再有空图标）
SELECT COUNT(*) AS '页面菜单空图标数' FROM `system_menu` WHERE `deleted` = b'0' AND `type` IN (1, 2) AND (`icon` IS NULL OR `icon` = '');
