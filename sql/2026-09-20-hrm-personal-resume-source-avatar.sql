-- HRM personal resume attachment sources and preview avatars.
-- Safe to run repeatedly on an existing tenant database.
SET @hrm_personal_resume_source_type_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'hrm_personal_resume' AND column_name = 'source_type'
);
SET @hrm_personal_resume_source_type_sql := IF(
  @hrm_personal_resume_source_type_exists = 0,
  'ALTER TABLE hrm_personal_resume ADD COLUMN source_type tinyint NOT NULL DEFAULT 1 COMMENT ''附件来源：1用户上传，2制作生成'' AFTER resume_type',
  'SELECT 1'
);
PREPARE hrm_personal_resume_source_type_stmt FROM @hrm_personal_resume_source_type_sql;
EXECUTE hrm_personal_resume_source_type_stmt;
DEALLOCATE PREPARE hrm_personal_resume_source_type_stmt;

SET @hrm_personal_resume_avatar_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'hrm_personal_resume' AND column_name = 'avatar_url'
);
SET @hrm_personal_resume_avatar_sql := IF(
  @hrm_personal_resume_avatar_exists = 0,
  'ALTER TABLE hrm_personal_resume ADD COLUMN avatar_url varchar(1024) DEFAULT NULL COMMENT ''简历头像地址，仅用于页面展示'' AFTER file_url',
  'SELECT 1'
);
PREPARE hrm_personal_resume_avatar_stmt FROM @hrm_personal_resume_avatar_sql;
EXECUTE hrm_personal_resume_avatar_stmt;
DEALLOCATE PREPARE hrm_personal_resume_avatar_stmt;

ALTER TABLE hrm_personal_resume MODIFY COLUMN content mediumtext NULL COMMENT '在线简历内容或附件提取纯文本';
