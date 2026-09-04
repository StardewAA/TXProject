-- ==================================================================================
-- 云培训学院 - 模块八「校企合作与创新大赛」子菜单 SQL
-- 文档：《模块八校企合作与创新大赛初步搭建方案.docx》
--
-- 功能结构：
--   81 校企合作管理
--     81.1 合作院校管理
--     81.2 校企定向培训班
--     81.3 校企学员档案互通
--     81.4 院校人才输出（校企人才库）
--   82 创新大赛专区
--     82.1 大赛基础管理
--     82.2 报名与作品
--     82.3 评审与奖励（含评审管理 / 公示与奖励）
--     82.4 大赛成果库
--
-- 挂载点：41009「校企合作与创新大赛」，由单页(type=2)改为目录(type=1)。
-- 页面组件统一位于：views/training/cooperation/<sub>/index.vue
--
-- 安全：脚本可重复执行；只操作 41008 / 41009 / 41040~41047 / 41300~41326 段。
-- 依赖：必须在 training_menu.sql（含 41008/41009 原始定义）之后执行。
-- ==================================================================================

-- 1. 还原 41008「VR 虚拟仿真实操训练」为单页面菜单，避免此前被错误目录化
UPDATE system_menu
SET `type` = 2,
    `component` = 'training/vr/index',
    `component_name` = 'TrainingVr',
    `permission` = 'training:vr:query',
    `visible` = b'1',
    `always_show` = b'1'
WHERE id = 41008;

-- 2. 将 41009「校企合作与创新大赛」改为目录，用于挂载模块八子功能
UPDATE system_menu
SET `type` = 1,
    `component` = NULL,
    `component_name` = NULL,
    `permission` = 'training:cooperation:query',
    `visible` = b'1',
    `always_show` = b'1'
WHERE id = 41009;

-- 3. 清理旧子菜单与按钮，避免重复插入冲突
DELETE FROM system_menu WHERE id BETWEEN 41040 AND 41047;
DELETE FROM system_menu WHERE id BETWEEN 41300 AND 41326;

-- 4. 新增 8 个三级子菜单页面(type=2)，父级统一为 41009
INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT 41040 AS id, '合作院校管理' AS name, 'training:coop:school:query' AS permission, 2 AS type, 10 AS sort,
         41009 AS parent_id, 'school' AS path, NULL AS icon, 'training/cooperation/school/index' AS component,
         'TrainingCoopSchool' AS component_name, 0 AS status, b'1' AS visible, b'1' AS keep_alive, b'1' AS always_show,
         'admin' AS creator, NOW() AS create_time, 'admin' AS updater, NOW() AS update_time, b'0' AS deleted
  UNION ALL SELECT 41041, '校企定向培训班', 'training:coop:train-class:query', 2, 20, 41009, 'train-class', NULL,
         'training/cooperation/train-class/index', 'TrainingCoopTrainClass', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41042, '校企学员档案互通', 'training:coop:archive:query', 2, 30, 41009, 'archive', NULL,
         'training/cooperation/archive/index', 'TrainingCoopArchive', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41043, '院校人才输出', 'training:coop:talent:query', 2, 40, 41009, 'talent', NULL,
         'training/cooperation/talent/index', 'TrainingCoopTalent', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41044, '大赛基础管理', 'training:coop:contest:query', 2, 50, 41009, 'contest', NULL,
         'training/cooperation/contest/index', 'TrainingCoopContest', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41045, '报名与作品', 'training:coop:submit:query', 2, 60, 41009, 'submit', NULL,
         'training/cooperation/submit/index', 'TrainingCoopSubmit', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41046, '评审与奖励', 'training:coop:award:query', 2, 70, 41009, 'award', NULL,
         'training/cooperation/award/index', 'TrainingCoopAward', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41047, '大赛成果库', 'training:coop:works:query', 2, 80, 41009, 'works', NULL,
         'training/cooperation/works/index', 'TrainingCoopWorks', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id BETWEEN 41040 AND 41047);

-- 5. 按钮权限(type=3)，按页面挂载
INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  -- 41040 合作院校管理
  SELECT 41300 AS id, '院校新增' AS name, 'training:coop:school:create' AS permission, 3 AS type, 1 AS sort, 41040 AS parent_id,
         NULL AS path, NULL AS icon, NULL AS component, NULL AS component_name, 0 AS status, b'1' AS visible, b'0' AS keep_alive, b'0' AS always_show, 'admin' AS creator, NOW() AS create_time, 'admin' AS updater, NOW() AS update_time, b'0' AS deleted
  UNION ALL SELECT 41301, '院校修改', 'training:coop:school:update', 3, 2, 41040, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41302, '院校删除', 'training:coop:school:delete', 3, 3, 41040, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41303, '入驻审核', 'training:coop:school:audit', 3, 4, 41040, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41304, '合作启停', 'training:coop:school:toggle', 3, 5, 41040, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41041 校企定向培训班
  UNION ALL SELECT 41305, '定向班新增', 'training:coop:train-class:create', 3, 1, 41041, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41306, '定向班修改', 'training:coop:train-class:update', 3, 2, 41041, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41307, '定向班删除', 'training:coop:train-class:delete', 3, 3, 41041, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41308, '开班下发任务', 'training:coop:train-class:start', 3, 4, 41041, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41309, '结业归档', 'training:coop:train-class:finish', 3, 5, 41041, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41042 校企学员档案互通
  UNION ALL SELECT 41310, '档案查询', 'training:coop:archive:query', 3, 1, 41042, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41311, '档案导出', 'training:coop:archive:export', 3, 2, 41042, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41043 院校人才输出
  UNION ALL SELECT 41312, '人才查询', 'training:coop:talent:query', 3, 1, 41043, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41313, '人才导出', 'training:coop:talent:export', 3, 2, 41043, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41314, '对接状态更新', 'training:coop:talent:update', 3, 3, 41043, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41044 大赛基础管理
  UNION ALL SELECT 41315, '大赛新增', 'training:coop:contest:create', 3, 1, 41044, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41316, '大赛修改', 'training:coop:contest:update', 3, 2, 41044, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41317, '大赛删除', 'training:coop:contest:delete', 3, 3, 41044, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41318, '赛程状态流转', 'training:coop:contest:transition', 3, 4, 41044, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41045 报名与作品
  UNION ALL SELECT 41319, '入围审核', 'training:coop:submit:audit', 3, 1, 41045, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41320, '驳回报名', 'training:coop:submit:reject', 3, 2, 41045, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41046 评审与奖励
  UNION ALL SELECT 41321, '评审打分', 'training:coop:award:score', 3, 1, 41046, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41322, '获奖公示', 'training:coop:award:publish', 3, 2, 41046, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41323, '证书发放', 'training:coop:award:cert', 3, 3, 41046, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41324, '评审数据删除', 'training:coop:award:delete', 3, 4, 41046, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  -- 41047 大赛成果库
  UNION ALL SELECT 41325, '成果查询', 'training:coop:works:query', 3, 1, 41047, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
  UNION ALL SELECT 41326, '成果导出', 'training:coop:works:export', 3, 2, 41047, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id BETWEEN 41300 AND 41326);

-- ==================================================================================
-- 菜单路由（在 41009「校企合作与创新大赛」目录下）：
--   /training/cooperation/school        合作院校管理
--   /training/cooperation/train-class   校企定向培训班
--   /training/cooperation/archive      校企学员档案互通
--   /training/cooperation/talent        院校人才输出
--   /training/cooperation/contest       大赛基础管理
--   /training/cooperation/submit        报名与作品
--   /training/cooperation/award         评审与奖励
--   /training/cooperation/works         大赛成果库
-- ==================================================================================
