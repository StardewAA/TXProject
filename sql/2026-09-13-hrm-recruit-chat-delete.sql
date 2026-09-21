-- HRM 招聘沟通支持企业端删除会话，并允许删除后重新发起同岗位沟通。
SET @db = DATABASE();
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = @db AND table_name = 'hrm_recruit_chat_conversation'
     AND index_name = 'uk_recruit_chat_active') > 0,
  'ALTER TABLE hrm_recruit_chat_conversation DROP INDEX uk_recruit_chat_active',
  'SELECT 1'
);
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = @db AND table_name = 'hrm_recruit_chat_conversation'
     AND column_name = 'deleted') > 0,
  'ALTER TABLE hrm_recruit_chat_conversation ADD UNIQUE INDEX uk_recruit_chat_active (post_id, candidate_user_id, hr_user_id, active_status, deleted)',
  'SELECT 1'
);
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

INSERT IGNORE INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1646,CONVERT(0xE68B9BE88198E6B29FE9809AE588A0E999A4 USING utf8mb4),'hrm:recruit:chat:delete',3,4,1641,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');
