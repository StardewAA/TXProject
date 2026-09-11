-- ======================================================================
-- 云培训学院 - 模块二：课程管理（课程全生命周期）重构补充脚本
-- 依据：《模块二课程管理（核心-难）初步搭建方案》二级子模块 21~26
-- 覆盖：
--   21 课程分类：按文档补齐 13 类行业分类种子
--   21~26 菜单：课程管理拆分为侧边栏二级子菜单（课程列表/分类管理/审核管理/评论管理/版本管理）
--   按钮权限：修正父菜单（原误挂 41001 工作台，应为 41003 课程管理）
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_course_module2.sql
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 课程分类：按文档 21 补齐 13 类行业分类（已存在同名分类则跳过，不覆盖历史数据）
--    文档要求分类：行业新规政策、国标规范解读、防腐工艺教程、保温施工工法、
--    材料选型知识、生产操作规范、质检检验教程、安全环保实操、高危作业培训、
--    招投标合规、项目管理、职业素养、持证上岗专项课程
-- ----------------------------------------------------------------------
INSERT INTO `training_course_category` (`name`, `code`, `sort`, `remark`, `creator`)
SELECT * FROM (
  SELECT '行业新规政策' AS name, 'industry-policy' AS code, 101 AS sort, '行业最新法规、政策解读类课程' AS remark, 'seed' AS creator
  UNION ALL SELECT '国标规范解读', 'national-standard', 102, '国家标准、行业规范条文解读课程', 'seed'
  UNION ALL SELECT '防腐工艺教程', 'anticorrosion-tech', 103, '防腐施工工艺与质量控制课程', 'seed'
  UNION ALL SELECT '保温施工工法', 'insulation-method', 104, '保温层施工工法与验收课程', 'seed'
  UNION ALL SELECT '材料选型知识', 'material-selection', 105, '防腐保温材料选型与性能课程', 'seed'
  UNION ALL SELECT '生产操作规范', 'production-sop', 106, '生产现场标准化操作规范课程', 'seed'
  UNION ALL SELECT '质检检验教程', 'quality-inspection', 107, '质量检验、检测方法与判定课程', 'seed'
  UNION ALL SELECT '安全环保实操', 'safety-env-practice', 108, '安全与环保现场实操课程', 'seed'
  UNION ALL SELECT '高危作业培训', 'high-risk-work', 109, '高处、动火、受限空间等高危作业培训', 'seed'
  UNION ALL SELECT '招投标合规', 'bidding-compliance', 110, '招投标流程与合规管理课程', 'seed'
  UNION ALL SELECT '项目管理', 'project-mgmt', 111, '工程项目进度、成本、合同管理课程', 'seed'
  UNION ALL SELECT '职业素养', 'professional-quality', 112, '职业道德、沟通协作等素养课程', 'seed'
  UNION ALL SELECT '持证上岗专项课程', 'certification-special', 113, '特种作业、岗位持证专项培训', 'seed'
) AS t
WHERE NOT EXISTS (
  SELECT 1 FROM `training_course_category` c
  WHERE c.`name` = t.`name` AND c.`deleted` = b'0'
);

-- ----------------------------------------------------------------------
-- 2. 课程管理侧边栏二级子菜单（父菜单 41003 课程管理）
-- ----------------------------------------------------------------------
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41020, '课程列表',   'training:course:query',   2, 10, 41003, 'list',     'lucide:notebook-tabs',      'training/course/list/index',     'TrainingCourseList',     0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41021, '分类管理',   'training:course:query',   2, 20, 41003, 'category', 'lucide:tags',                'training/course/category/index', 'TrainingCourseCategory', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41022, '审核管理',   'training:course:audit',   2, 30, 41003, 'audit',    'lucide:clipboard-check',     'training/course/audit/index',    'TrainingCourseAudit',    0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41023, '评论管理',   'training:course:comment', 2, 40, 41003, 'comment',  'lucide:message-square',      'training/course/comment/index',  'TrainingCourseComment',  0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41024, '版本管理',   'training:course:query',   2, 50, 41003, 'version',  'lucide:git-branch-plus',     'training/course/version/index',  'TrainingCourseVersion',  0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
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

-- ----------------------------------------------------------------------
-- 3. 修正按钮权限父菜单：41100~41105 原误挂 41001（工作台），应挂 41003（课程管理）
-- ----------------------------------------------------------------------
UPDATE `system_menu`
SET `parent_id` = 41003, `updater` = 'admin', `update_time` = NOW()
WHERE `id` BETWEEN 41100 AND 41105
  AND `parent_id` = 41001;
