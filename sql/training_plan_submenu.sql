-- ======================================================================
-- 云培训学院 - 培训管理(模块三) 子模块菜单重构
-- 背景：《赵升茹-工作台九模块细致划分》模块三含五个二级子模块
--   3-1 培训班管理（列表/创建/开班/结业）  3-2 参训人员动态维护
--   3-3 班级学习进度看板                   3-4 学习督促
--   3-5 上岗资格核查
-- 变更：原 41004「培训管理」为单页面菜单(type=2)，
--       现改为目录(type=1)，下挂 5 个三级子菜单(type=2)页面。
-- 安全：脚本可重复执行；只操作 41004 及 41011~41015 / 41200~41208 段。
-- ======================================================================
-- 1. 培训管理(41004) 由页面菜单改为目录
UPDATE system_menu
SET `type` = 1,
    `component` = NULL,
    `component_name` = NULL,
    `permission` = '',
    `visible` = b'1',
    `always_show` = b'1'
WHERE id = 41004;

-- 2. 清理旧按钮(41200~41206)与旧子菜单，避免重复插入冲突
DELETE FROM system_menu WHERE id BETWEEN 41200 AND 41206;
DELETE FROM system_menu WHERE id BETWEEN 41011 AND 41015;

-- 3. 新增 5 个三级子菜单页面(type=2)
INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT 41011 AS id, '培训班管理' AS name, 'training:plan:query' AS permission, 2 AS type, 10 AS sort,
         41004 AS parent_id, 'index' AS path, NULL AS icon, 'training/plan/index' AS component,
         'TrainingPlan' AS component_name, 0 AS status, b'1' AS visible, b'1' AS keep_alive, b'1' AS always_show,
         'admin' AS creator, NOW() AS create_time, 'admin' AS updater, NOW() AS update_time, b'0' AS deleted
  UNION ALL SELECT 41012, '参训人员维护', 'training:plan:query' AS permission, 2, 20, 41004, 'student', NULL,
         'training/plan/student', 'TrainingPlanStudent', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41013, '班级学习进度', 'training:plan:query' AS permission, 2, 30, 41004, 'progress', NULL,
         'training/plan/progress', 'TrainingPlanProgress', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41014, '学习督促', 'training:plan:query' AS permission, 2, 40, 41004, 'remind', NULL,
         'training/plan/remind', 'TrainingPlanRemind', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41015, '上岗资格核查', 'training:plan:query' AS permission, 2, 50, 41004, 'qualification', NULL,
         'training/plan/qualification', 'TrainingPlanQualification', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id IN (41011, 41012, 41013, 41014, 41015));

-- 4. 重建按钮权限(type=3)：删除旧按钮后，按模块三细分功能重建
INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT 41200 AS id, '培训班新增' AS name, 'training:plan:create' AS permission, 3 AS type, 1 AS sort, 41004 AS parent_id,
         NULL AS path, NULL AS icon, NULL AS component, NULL AS component_name, 0 AS status, b'1' AS visible, b'0' AS keep_alive, b'0' AS always_show, 'admin' AS creator, NOW() AS create_time, 'admin' AS updater, NOW() AS update_time, b'0' AS deleted
  UNION ALL SELECT 41201, '培训班修改', 'training:plan:update', 3, 2, 41004, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41202, '培训班删除', 'training:plan:delete', 3, 3, 41004, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41203, '添加学员', 'training:plan:student:add', 3, 4, 41004, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41204, '移除学员', 'training:plan:student:remove', 3, 5, 41004, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41205, '批量导入学员', 'training:plan:student:import', 3, 6, 41004, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41206, '进度登记', 'training:plan:progress:register', 3, 7, 41004, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41207, '督促发送', 'training:plan:remind:send', 3, 8, 41004, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41208, '上岗资格核查', 'training:plan:qualification:check', 3, 9, 41004, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id IN (41200, 41201, 41202, 41203, 41204, 41205, 41206, 41207, 41208));

-- ======================================================================
-- 说明：菜单路由为
--   /training/plan/index        培训班管理
--   /training/plan/student      参训人员维护
--   /training/plan/progress     班级学习进度
--   /training/plan/remind       学习督促
--   /training/plan/qualification 上岗资格核查
-- ======================================================================
