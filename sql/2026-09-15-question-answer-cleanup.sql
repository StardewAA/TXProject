-- 考试题库数据清洗：把混在题干里的 答案/解析/参考答案 提取到独立字段
-- 覆盖三类：判断题（答案：对/错 解析：…）、简答题（参考答案：…）、案例分析题（参考答案：…）

-- ① 判断题：answer ← 题干中的"答案：对/错"
UPDATE training_question
SET answer = CASE
  WHEN stem LIKE '%答案：对%' THEN '对'
  WHEN stem LIKE '%答案：错%' THEN '错'
  ELSE answer END
WHERE deleted = 0 AND (answer IS NULL OR answer = '')
  AND (stem LIKE '%答案：对%' OR stem LIKE '%答案：错%');

-- ② 判断题：analysis ← "解析：" 之后的内容（清理尾部章节残留）
UPDATE training_question
SET analysis = TRIM(REGEXP_REPLACE(
  SUBSTRING_INDEX(stem, '解析：', -1),
  '[一二三四五]、.{0,15}（共 ?[0-9]+ ?题）[[:space:]]*$', ''))
WHERE deleted = 0 AND (analysis IS NULL OR analysis = '')
  AND stem LIKE '%解析：%';

-- ③ 判断题：题干截断到"答案："之前
UPDATE training_question
SET stem = TRIM(SUBSTRING_INDEX(stem, '答案：', 1))
WHERE deleted = 0 AND stem LIKE '%答案：%'
  AND (answer IN ('对', '错'));

-- ④ 简答/案例题：analysis ← "参考答案：" 之后（清理尾部章节残留）
UPDATE training_question
SET analysis = TRIM(REGEXP_REPLACE(
  SUBSTRING_INDEX(stem, '参考答案：', -1),
  '[一二三四五]、.{0,15}（共 ?[0-9]+ ?题）[[:space:]]*$', ''))
WHERE deleted = 0 AND (analysis IS NULL OR analysis = '')
  AND stem LIKE '%参考答案：%';

-- ⑤ 简答/案例题：answer ← 参考答案全文（详情页答案区直接展示）
UPDATE training_question
SET answer = analysis
WHERE deleted = 0 AND (answer IS NULL OR answer = '')
  AND analysis IS NOT NULL AND analysis <> ''
  AND stem NOT LIKE '%答案：%' AND stem NOT LIKE '%参考答案：%';

-- ⑥ 简答/案例题：题干截断到"参考答案："之前
UPDATE training_question
SET stem = TRIM(SUBSTRING_INDEX(stem, '参考答案：', 1))
WHERE deleted = 0 AND stem LIKE '%参考答案：%';

-- ⑦ 兜底：问答类（qtype IN 4,5）答案仍为空但 analysis 有值的，用 analysis 填充
UPDATE training_question
SET answer = analysis
WHERE deleted = 0 AND (answer IS NULL OR answer = '')
  AND qtype IN (4, 5) AND analysis IS NOT NULL AND analysis <> '';
