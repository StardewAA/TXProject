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

INSERT INTO system_role_menu (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT source.`role_id`,1646,'admin',NOW(),'admin',NOW(),b'0',source.`tenant_id`
FROM system_role_menu source
JOIN system_role role ON role.id=source.role_id
WHERE source.menu_id=1641 AND source.deleted=b'0' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu existing
    WHERE existing.role_id=source.role_id AND existing.menu_id=1646 AND existing.deleted=b'0'
  )
GROUP BY source.`role_id`,source.`tenant_id`;
