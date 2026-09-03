SELECT name, HEX(LEFT(description, 3)) AS first3bytes, CHAR_LENGTH(description) AS len
FROM ai_tool WHERE tenant_id = 2010;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'ruoyi-vue-pro' AND TABLE_NAME = 'ai_chat_role'
ORDER BY ORDINAL_POSITION;
