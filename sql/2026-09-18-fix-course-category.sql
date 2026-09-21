-- 修复：课程未绑定分类，导致门户课程中心按分类筛选时查不到课程
-- 说明：training_course.category_id 全为 NULL，而课程 code 与 training_course_category.code 一一对应
--       （如课程 H0202 → 分类 H0202），据此回填分类 ID，使一级/二级/三级分类筛选均可命中

UPDATE training_course c
    JOIN training_course_category t ON t.code = c.code AND t.deleted = 0
SET c.category_id = t.id
WHERE c.deleted = 0
  AND c.category_id IS NULL;

-- 校验：按分类统计（应覆盖全部课程）
-- SELECT t.code, t.name, COUNT(c.id) cnt
-- FROM training_course c
--          JOIN training_course_category t ON t.id = c.category_id
-- WHERE c.deleted = 0
-- GROUP BY t.code, t.name
-- ORDER BY t.code;
