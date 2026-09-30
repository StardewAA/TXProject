-- 演示数据：将 3 门课程置为「待审核」（status=1），并在审核日志中留痕 SUBMIT
-- 使「审核管理 → 待审核队列」打开即有真实数据可审
SET NAMES utf8mb4;

UPDATE training_course SET status = 1, update_time = NOW()
WHERE deleted = 0 AND id IN (12, 13, 14) AND status = 0;

INSERT INTO training_course_audit_log
  (course_id, action, from_status, to_status, operator_id, operator_name, reason, creator, tenant_id)
SELECT id, 'SUBMIT', 0, 1, 1, 'admin', '提交审核（演示数据初始化）', 'admin', 1
FROM training_course
WHERE deleted = 0 AND id IN (12, 13, 14);
