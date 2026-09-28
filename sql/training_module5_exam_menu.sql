-- ==================================================================================
-- 云培训学院 - 模块五「考试与认证」子菜单 SQL
--
-- 功能结构（页面组件统一位于 views/training/exam/<sub>/index.vue）：
--   51 总览            overview
--   52 题库与审核      questions
--   53 试卷管理        papers
--   54 考试安排        schedules
--   55 阅卷与成绩      grading
--   56 证书管理        certificates
--   57 联合认证        issuers
--   58 我的考试        myexams
--   59 错题复盘        wrong
--   60 我的证书        mycerts
--
-- 挂载点：41006「考试与认证」，由单页(type=2)改为目录(type=1)，
--         原来的页面内功能导航改为左侧主菜单的二级菜单（与 41005 / 41008 / 41009 目录改造一致）。
--
-- 安全：脚本可重复执行；只操作 41006 / 41700~41709 段，绝不触碰其它菜单。
-- 依赖：必须在 training_menu.sql（含 41006 原始定义）之后执行。
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_module5_exam_menu.sql
-- ==================================================================================

-- 1. 将 41006「考试与认证」由单页(type=2)改为目录(type=1)，用于挂载模块五子功能
UPDATE system_menu
SET `type` = 1,
    `component` = NULL,
    `component_name` = NULL,
    `permission` = 'training:exam:query',
    `visible` = b'1',
    `always_show` = b'1'
WHERE id = 41006;

-- 2. 清理模块五旧子菜单（重跑安全），避免重复插入冲突
DELETE FROM system_menu WHERE id BETWEEN 41700 AND 41709;

-- 3. 新增 10 个二级子菜单(type=2)，父级统一为 41006
INSERT INTO system_menu
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(41700, '总览', 'training:exam:overview:query', 2, 10, 41006, 'overview', NULL,
 'training/exam/overview/index', 'TrainingExamOverview', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41701, '题库与审核', 'training:exam:question:query', 2, 20, 41006, 'questions', NULL,
 'training/exam/questions/index', 'TrainingExamQuestions', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41702, '试卷管理', 'training:exam:paper:query', 2, 30, 41006, 'papers', NULL,
 'training/exam/papers/index', 'TrainingExamPapers', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41703, '考试安排', 'training:exam:schedule:query', 2, 40, 41006, 'schedules', NULL,
 'training/exam/schedules/index', 'TrainingExamSchedules', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41704, '阅卷与成绩', 'training:exam:grading:query', 2, 50, 41006, 'grading', NULL,
 'training/exam/grading/index', 'TrainingExamGrading', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41705, '证书管理', 'training:exam:certificate:query', 2, 60, 41006, 'certificates', NULL,
 'training/exam/certificates/index', 'TrainingExamCertificates', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41706, '联合认证', 'training:exam:issuer:query', 2, 70, 41006, 'issuers', NULL,
 'training/exam/issuers/index', 'TrainingExamIssuers', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41707, '我的考试', 'training:exam:mine:query', 2, 80, 41006, 'myexams', NULL,
 'training/exam/myexams/index', 'TrainingExamMyExams', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41708, '错题复盘', 'training:exam:wrong:query', 2, 90, 41006, 'wrong', NULL,
 'training/exam/wrong/index', 'TrainingExamWrong', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(41709, '我的证书', 'training:exam:mycert:query', 2, 100, 41006, 'mycerts', NULL,
 'training/exam/mycerts/index', 'TrainingExamMyCerts', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ==================================================================================
-- 菜单路由（在 41006「考试与认证」目录下）：
--   /training/exam/overview        总览（51）
--   /training/exam/questions       题库与审核（52）
--   /training/exam/papers          试卷管理（53）
--   /training/exam/schedules       考试安排（54）
--   /training/exam/grading         阅卷与成绩（55）
--   /training/exam/certificates    证书管理（56）
--   /training/exam/issuers         联合认证（57）
--   /training/exam/myexams         我的考试（58）
--   /training/exam/wrong           错题复盘（59）
--   /training/exam/mycerts         我的证书（60）
-- 说明：各子页面复用同一个 iframe 工作区组件（views/training/components/exam-workspace.vue），
--       embedded 模式下 iframe 内部不再渲染自己的功能菜单，导航完全由左侧主菜单驱动。
-- ==================================================================================
