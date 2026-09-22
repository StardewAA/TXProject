-- Allow persisted AI客服 messages. Safe to rerun on MySQL 8.
SET @constraint_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'promotion_kefu_chat_message'
      AND CONSTRAINT_NAME = 'ck_kefu_chat_role'
      AND CONSTRAINT_TYPE = 'CHECK'
);
SET @drop_sql = IF(@constraint_exists > 0,
    'ALTER TABLE promotion_kefu_chat_message DROP CHECK ck_kefu_chat_role',
    'SELECT 1');
PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

ALTER TABLE promotion_kefu_chat_message
    ADD CONSTRAINT ck_kefu_chat_role
    CHECK (sender_role IN ('MEMBER','AGENT','FAQ','AI','SYSTEM'));
