-- ======================================================================
-- 云培训学院 - 课程表新增「授课目标」字段
-- 背景：学员门户课程详情页「课程简介」需要展示三个模块：
--   课程概述（复用已有 summary）/ 适合学习人员（复用已有 applicable_posts）/ 授课目标（新增）
-- 幂等：仅当列不存在时添加（兼容 MySQL 5.7/8.0，用 information_schema 判断）
-- ======================================================================
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'training_course'
      AND COLUMN_NAME = 'objectives'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `training_course` ADD COLUMN `objectives` text COMMENT ''授课目标'' AFTER `summary`',
    'SELECT ''objectives already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
