-- ==================================================================================
-- 云培训学院 - 模块六「直播与讲师体系」子菜单 SQL
--
-- 功能结构（页面组件统一位于 views/training/live/<sub>/index.vue）：
--   61 直播大厅          hall
--   62 直播管理          room
--   63 企业学习管理      enterprise
--   64 讲师入驻          profile
--   65 助教与场控        assist
--   66 运营数据          stats
--   67 平台规则          config
--
-- 挂载点：41007「直播与讲师体系」，由单页(type=2)改为目录(type=1)，
--         原来的页面内横向页签改为左侧主菜单的二级菜单（与 41005 / 41008 / 41009 目录改造一致）。
--
-- 安全：脚本可重复执行；只操作 41007 / 41800~41806 段，绝不触碰其它菜单。
-- 依赖：必须在 training_menu.sql（含 41007 原始定义）之后执行。
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_module6_live_menu.sql
-- ==================================================================================

-- 1. 将 41007「直播与讲师体系」由单页(type=2)改为目录(type=1)，用于挂载模块六子功能
UPDATE system_menu
SET `type` = 1,
    `component` = NULL,
    `component_name` = NULL,
    `permission` = 'training:live:query',
    `visible` = b'1',
    `always_show` = b'1'
WHERE id = 41007;

-- 2. 清理模块六旧子菜单（重跑安全），避免重复插入冲突
DELETE FROM system_menu WHERE id BETWEEN 41800 AND 41806;

-- 3. 新增 7 个二级子菜单(type=2)，父级统一为 41007
INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41800, '直播大厅', 'training:live:hall:query', 2, 10, 41007, 'hall', NULL,
 'training/live/hall/index', 'TrainingLiveHall', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41801, '直播管理', 'training:live:room:query', 2, 20, 41007, 'room', NULL,
 'training/live/room/index', 'TrainingLiveRoom', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41802, '企业学习管理', 'training:live:enterprise:query', 2, 30, 41007, 'enterprise', NULL,
 'training/live/enterprise/index', 'TrainingLiveEnterprise', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41803, '讲师入驻', 'training:live:profile:query', 2, 40, 41007, 'profile', NULL,
 'training/live/profile/index', 'TrainingLiveProfile', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41804, '助教与场控', 'training:live:assist:query', 2, 50, 41007, 'assist', NULL,
 'training/live/assist/index', 'TrainingLiveAssist', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41805, '运营数据', 'training:live:stats:query', 2, 60, 41007, 'stats', NULL,
 'training/live/stats/index', 'TrainingLiveStats', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41806, '平台规则', 'training:live:config:query', 2, 70, 41007, 'config', NULL,
 'training/live/config/index', 'TrainingLiveConfig', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ==================================================================================
-- 菜单路由（在 41007「直播与讲师体系」目录下）：
--   /training/live/hall          直播大厅（61）
--   /training/live/room          直播管理（62）
--   /training/live/enterprise    企业学习管理（63）
--   /training/live/profile       讲师入驻（64）
--   /training/live/assist        助教与场控（65）
--   /training/live/stats         运营数据（66）
--   /training/live/config        平台规则（67）
-- 说明：各子页面复用同一个工作区组件（views/training/live/live-workspace.vue），
--       通过 tab 属性只渲染对应功能区块，导航完全由左侧主菜单驱动。
-- ==================================================================================
