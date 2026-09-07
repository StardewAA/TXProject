-- ==================================================================================
-- 云培训学院 - 模块七「VR 虚拟仿真实操训练」子菜单 SQL
-- 文档：《模块七 VR 虚拟仿真实操训练初步搭建方案(1).docx》
--
-- 功能结构：
--   71 VR 资源项目管理（项目上传/多版本管理/上下架归档、分类标签、学时与评分规则）
--   72 VR 任务管理（选项目建任务、绑定课程/培训班、完成次数/合格线/截止时间）
--   73 学员 VR 实操训练（任务分组：待完成/已完成/已逾期、仿真实操练习、学时统计）
--   74 VR 实操考核（自动评分、复杂场景人工复核留痕、不合格重练重考）
--   75 VR 实操数据看板（管理员/讲师看板、学员个人历史与学时档案同步）
--
-- 挂载点：41008「VR 虚拟仿真实操训练」，由单页(type=2)改为目录(type=1)，
--         挂载 5 个二级子模块菜单（与 41005 学习中心 / 41009 校企合作目录改造一致）。
-- 页面组件统一位于：views/training/vr/<sub>/index.vue，父壳 views/training/vr/index.vue。
--
-- 安全：脚本可重复执行；只操作 41008 / 41500~41540 段，绝不触碰其它菜单。
-- 依赖：必须在 training_menu.sql（含 41008 原始定义）之后执行。
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_module7_vr_menu.sql
-- ==================================================================================

-- 1. 将 41008「VR 虚拟仿真实操训练」由单页(type=2)改为目录(type=1)，用于挂载模块七子功能
UPDATE system_menu
SET `type` = 1,
    `component` = NULL,
    `component_name` = NULL,
    `permission` = 'training:vr:query',
    `visible` = b'1',
    `always_show` = b'1'
WHERE id = 41008;

-- 2. 清理模块七旧子菜单与按钮（重跑安全），避免重复插入冲突
DELETE FROM system_menu WHERE id BETWEEN 41500 AND 41504;
DELETE FROM system_menu WHERE id BETWEEN 41510 AND 41540;

-- 3. 新增 5 个二级子模块菜单(type=2)，父级统一为 41008
INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT 41500 AS id, 'VR 资源项目管理' AS name, 'training:vr-project:query' AS permission, 2 AS type, 10 AS sort,
         41008 AS parent_id, 'project' AS path, 'lucide:boxes' AS icon, 'training/vr/project/index' AS component,
         'TrainingVrProject' AS component_name, 0 AS status, b'1' AS visible, b'1' AS keep_alive, b'1' AS always_show,
         'admin' AS creator, NOW() AS create_time, 'admin' AS updater, NOW() AS update_time, b'0' AS deleted
  UNION ALL SELECT 41501, 'VR 任务管理', 'training:vr-task:query', 2, 20, 41008, 'task', 'lucide:clipboard-list',
         'training/vr/task/index', 'TrainingVrTask', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41502, '学员 VR 实操训练', 'training:vr-practice:query', 2, 30, 41008, 'practice', 'lucide:glasses',
         'training/vr/practice/index', 'TrainingVrPractice', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41503, 'VR 实操考核', 'training:vr-assess:query', 2, 40, 41008, 'assess', 'lucide:clipboard-check',
         'training/vr/assess/index', 'TrainingVrAssess', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41504, 'VR 实操数据看板', 'training:vr-dashboard:query', 2, 50, 41008, 'dashboard', 'lucide:bar-chart-3',
         'training/vr/dashboard/index', 'TrainingVrDashboard', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id BETWEEN 41500 AND 41504);

-- 4. 按钮权限(type=3)，按页面挂载
INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  -- 41500 VR 资源项目管理
  SELECT 41510 AS id, '项目新增' AS name, 'training:vr-project:create' AS permission, 3 AS type, 1 AS sort, 41500 AS parent_id,
         NULL AS path, NULL AS icon, NULL AS component, NULL AS component_name, 0 AS status, b'1' AS visible, b'0' AS keep_alive, b'0' AS always_show, 'admin' AS creator, NOW() AS create_time, 'admin' AS updater, NOW() AS update_time, b'0' AS deleted
  UNION ALL SELECT 41511, '项目修改', 'training:vr-project:update', 3, 2, 41500, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41512, '项目删除', 'training:vr-project:delete', 3, 3, 41500, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41513, '上下架/归档', 'training:vr-project:transition', 3, 4, 41500, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41514, '版本维护', 'training:vr-project:version', 3, 5, 41500, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41515, '分类标签管理', 'training:vr-tag:manage', 3, 6, 41500, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41501 VR 任务管理
  UNION ALL SELECT 41520, '任务新增', 'training:vr-task:create', 3, 1, 41501, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41521, '任务停用', 'training:vr-task:toggle', 3, 2, 41501, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41522, '任务删除', 'training:vr-task:delete', 3, 3, 41501, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41502 学员 VR 实操训练
  UNION ALL SELECT 41530, '开始实操', 'training:vr-practice:start', 3, 1, 41502, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41531, '提交成绩', 'training:vr-practice:submit', 3, 2, 41502, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41503 VR 实操考核
  UNION ALL SELECT 41532, '人工复核', 'training:vr-assess:review', 3, 1, 41503, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41533, '重练重考', 'training:vr-assess:retry', 3, 2, 41503, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41504 VR 实操数据看板
  UNION ALL SELECT 41540, '历史导出', 'training:vr-dashboard:export', 3, 1, 41504, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id BETWEEN 41510 AND 41540);

-- ==================================================================================
-- 菜单路由（在 41008「VR 虚拟仿真实操训练」目录下）：
--   /training/vr/project        VR 资源项目管理（71）
--   /training/vr/task           VR 任务管理（72）
--   /training/vr/practice       学员 VR 实操训练（73）
--   /training/vr/assess         VR 实操考核（74）
--   /training/vr/dashboard      VR 实操数据看板（75）
-- 注意：41008 由单页改为目录后，父路径 /training/vr 由前端父壳
--       views/training/vr/index.vue（redirect 到 /training/vr/project）兜底。
-- ==================================================================================
