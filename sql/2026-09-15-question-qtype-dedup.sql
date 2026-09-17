-- 题库数据修复 2：
-- A. 简答/案例题 qtype 错标为 1(单选) → 改为 4(实操问答)/5(案例分析)
-- B. 三批重复导入的题目去重（软删，保留每组最小 id）

-- A1. 简答类 → 4 实操问答
UPDATE training_question
SET qtype = 4, update_time = NOW()
WHERE deleted = 0 AND qtype = 1 AND LENGTH(answer) > 50
  AND (stem LIKE '%简述%' OR stem LIKE '%列举%' OR stem LIKE '%为什么现场人员%' OR stem LIKE '%需要向客户确认%' OR stem LIKE '%需要收集%');

-- A2. 案例类 → 5 案例分析
UPDATE training_question
SET qtype = 5, update_time = NOW()
WHERE deleted = 0 AND qtype = 1 AND LENGTH(answer) > 50
  AND (stem LIKE '%请分析%' OR stem LIKE '%客户问%' OR stem LIKE '%现场新员工%' OR stem LIKE '%某市政供热%');

-- B. 重复题去重：同题干保留最小 id，其余软删
UPDATE training_question q
JOIN (
  SELECT MIN(id) AS keep_id, stem
  FROM training_question
  WHERE deleted = 0
  GROUP BY stem
  HAVING COUNT(*) > 1
) dup ON q.stem = dup.stem AND q.id > dup.keep_id
SET q.deleted = 1, q.update_time = NOW()
WHERE q.deleted = 0;
