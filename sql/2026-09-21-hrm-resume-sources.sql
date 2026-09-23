-- 招聘附件来源、头像及全文提取内容。可重复执行。
SET @db_name = DATABASE();
SET @has_source = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='hrm_personal_resume' AND column_name='source_type');
SET @sql = IF(@has_source=0, 'ALTER TABLE hrm_personal_resume ADD COLUMN source_type tinyint NOT NULL DEFAULT 1 COMMENT ''附件来源：1上传，2制作生成'' AFTER resume_type', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_work_address = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='hrm_recruit_post' AND column_name='work_address');
SET @sql = IF(@has_work_address=0, 'ALTER TABLE hrm_recruit_post ADD COLUMN work_address varchar(255) DEFAULT NULL COMMENT ''具体工作地址'' AFTER area_id, ADD COLUMN longitude decimal(10,6) DEFAULT NULL COMMENT ''工作地址经度'' AFTER work_address, ADD COLUMN latitude decimal(10,6) DEFAULT NULL COMMENT ''工作地址纬度'' AFTER longitude', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_avatar = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='hrm_personal_resume' AND column_name='avatar_url');
SET @sql = IF(@has_avatar=0, 'ALTER TABLE hrm_personal_resume ADD COLUMN avatar_url varchar(1024) DEFAULT NULL COMMENT ''简历头像地址'' AFTER file_url', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = 'ALTER TABLE hrm_personal_resume MODIFY COLUMN content mediumtext NULL';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
