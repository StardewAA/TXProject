-- 招聘沟通会话只有在求职者首次发消息后才进入等待回复。
SET @db = DATABASE();
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = @db AND table_name = 'hrm_recruit_chat_conversation' AND column_name = 'status') > 0,
  'ALTER TABLE hrm_recruit_chat_conversation MODIFY COLUMN status tinyint NOT NULL DEFAULT 0 COMMENT ''会话状态：0 待发起，1 等待回复，2 招聘方已回复，3 沟通失败''',
  'SELECT 1'
);
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

UPDATE hrm_recruit_chat_conversation
SET status = 0
WHERE status = 1 AND last_time IS NULL AND hr_reply_time IS NULL;
