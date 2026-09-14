-- ======================================================================
-- 模块二：课程管理「新增课程」表单改造配套增量脚本（2026-09-14）
--   1) 适用岗位工种联动：新增「学习域/课程组」learn_domain_codes
--      - 按岗位工种表 training_job.learn_domain_codes 自动匹配回填
--      - 支持一级域（如 A）+ 二级课程组（如 B01）编码，* 表示全域
--   2) 结课考试对接模块三「考试与认证」：新增考试时间窗
--      - exam_start_time / exam_end_time 与既有 exam_id（试卷 ID）配套
--
-- 幂等：重复执行不会报错（MySQL 不支持 ADD COLUMN IF NOT EXISTS，用存储过程判断）
-- 涉及表：training_course
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 课程表：新增 learn_domain_codes（学习域/课程组）
-- ----------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_add_course_learn_domain`;
DELIMITER $$
CREATE PROCEDURE `sp_add_course_learn_domain`()
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'training_course'
                     AND COLUMN_NAME = 'learn_domain_codes') THEN
        ALTER TABLE `training_course`
            ADD COLUMN `learn_domain_codes` varchar(255) DEFAULT NULL
                COMMENT '学习域/课程组编码（按适用岗位工种自动匹配，逗号分隔，* 表示全域）'
                AFTER `applicable_posts`;
    END IF;
END$$
DELIMITER ;
CALL `sp_add_course_learn_domain`();
DROP PROCEDURE IF EXISTS `sp_add_course_learn_domain`;

-- ----------------------------------------------------------------------
-- 2. 课程表：新增结课考试时间窗 exam_start_time / exam_end_time
-- ----------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_add_course_exam_time`;
DELIMITER $$
CREATE PROCEDURE `sp_add_course_exam_time`()
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'training_course'
                     AND COLUMN_NAME = 'exam_start_time') THEN
        ALTER TABLE `training_course`
            ADD COLUMN `exam_start_time` datetime DEFAULT NULL
                COMMENT '结课考试开始时间（模块三考试与认证联动）'
                AFTER `exam_id`;
    END IF;
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'training_course'
                     AND COLUMN_NAME = 'exam_end_time') THEN
        ALTER TABLE `training_course`
            ADD COLUMN `exam_end_time` datetime DEFAULT NULL
                COMMENT '结课考试结束时间（模块三考试与认证联动）'
                AFTER `exam_start_time`;
    END IF;
END$$
DELIMITER ;
CALL `sp_add_course_exam_time`();
DROP PROCEDURE IF EXISTS `sp_add_course_exam_time`;

-- ----------------------------------------------------------------------
-- 3. 菜单名称：「课程列表」改为「课程名称」（需求 1 统一页面与菜单文案）
-- ----------------------------------------------------------------------
UPDATE `system_menu`
SET `name` = '课程名称'
WHERE `id` = 41020;
