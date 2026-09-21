-- ============================================================================
-- 云培训学院：课程版本链 root_id 回填修复
--
-- 问题现象：「课程管理 → 版本管理」选择任意课程，查询不到任何版本
-- 根本原因：training_course.root_id 为空，版本管理按 root_id 聚合版本族时匹配不到记录
--   1) 78 门学习地图种子课程（training_learning_map_courses.sql 插入）未写 root_id
--   2) 由上述课程「发布新版本」生成的 v2 也未继承到 root_id（原值本为空）
-- 影响范围：凡是 root_id 为空的课程族，其版本历史无法查询
-- 脚本幂等：已修复的行不会被重复更新，可安全重复执行
-- ============================================================================

-- 1. 首版课程（version = 1）：版本族根指向自身
UPDATE training_course
SET root_id = id
WHERE deleted = 0
  AND root_id IS NULL
  AND version = 1;

-- 2. 高版本课程（version > 1）：按课程编码关联同族首版
UPDATE training_course c
JOIN (
    SELECT code, MIN(id) AS root_id
    FROM training_course
    WHERE deleted = 0 AND version = 1
    GROUP BY code
) t ON t.code = c.code
SET c.root_id = t.root_id
WHERE c.deleted = 0
  AND c.root_id IS NULL
  AND c.version > 1;

-- 3. 校验：仍为空的数量（应为 0）
SELECT COUNT(*) AS still_null_root_id
FROM training_course
WHERE deleted = 0 AND root_id IS NULL;
