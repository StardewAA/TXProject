SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'hrm_recruit_post'
    AND column_name = 'channel_id'
);
SET @statement := IF(
  @column_exists = 0,
  'ALTER TABLE hrm_recruit_post ADD COLUMN channel_id bigint DEFAULT NULL COMMENT ''招聘渠道'' AFTER post_type_id',
  'SELECT 1'
);
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
