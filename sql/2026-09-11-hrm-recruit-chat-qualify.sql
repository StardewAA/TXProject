-- 招聘沟通增加投递资格：HR 回复后才可正式投递；任一方可结束沟通。
-- MySQL 8.4 可重复执行：通过 information_schema 判断缺列后再动态执行。
SET @db = DATABASE();
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='hrm_recruit_chat_conversation' AND column_name='status')=0, 'ALTER TABLE hrm_recruit_chat_conversation ADD COLUMN status tinyint NOT NULL DEFAULT 0 COMMENT ''会话状态：0 待发起，1 等待回复，2 招聘方已回复，3 沟通失败'' AFTER hr_unread', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='hrm_recruit_chat_conversation' AND column_name='hr_reply_time')=0, 'ALTER TABLE hrm_recruit_chat_conversation ADD COLUMN hr_reply_time datetime DEFAULT NULL AFTER status', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='hrm_recruit_chat_conversation' AND column_name='end_time')=0, 'ALTER TABLE hrm_recruit_chat_conversation ADD COLUMN end_time datetime DEFAULT NULL AFTER hr_reply_time', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='hrm_recruit_chat_conversation' AND column_name='end_reason')=0, 'ALTER TABLE hrm_recruit_chat_conversation ADD COLUMN end_reason varchar(255) DEFAULT NULL AFTER end_time', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='hrm_recruit_chat_conversation' AND column_name='active_status')=0, 'ALTER TABLE hrm_recruit_chat_conversation ADD COLUMN active_status tinyint GENERATED ALWAYS AS (IF(status = 3, NULL, 1)) STORED COMMENT ''未结束会话唯一键标记'' AFTER end_reason', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='hrm_recruit_chat_conversation' AND column_name='candidate_id')=0, 'ALTER TABLE hrm_recruit_chat_conversation ADD COLUMN candidate_id bigint DEFAULT NULL COMMENT ''正式投递后绑定的候选人编号'' AFTER candidate_user_id', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='hrm_recruit_chat_conversation' AND column_name='application_status')=0, 'ALTER TABLE hrm_recruit_chat_conversation ADD COLUMN application_status varchar(32) DEFAULT NULL COMMENT ''投递状态：READY_TO_SUBMIT 待投递，SUBMITTED 已投递'' AFTER status', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='hrm_recruit_chat_conversation' AND column_name='application_id')=0, 'ALTER TABLE hrm_recruit_chat_conversation ADD COLUMN application_id bigint DEFAULT NULL COMMENT ''正式应聘记录编号'' AFTER application_status', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db AND table_name='hrm_recruit_chat_conversation' AND index_name='uk_recruit_chat_active')>0, 'ALTER TABLE hrm_recruit_chat_conversation DROP INDEX uk_recruit_chat_active', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
ALTER TABLE hrm_recruit_chat_conversation ADD UNIQUE INDEX uk_recruit_chat_active (post_id, candidate_user_id, hr_user_id, active_status, deleted);

-- 历史上已完成门户投递的会话补齐候选人和投递状态。
UPDATE hrm_recruit_chat_conversation c
JOIN hrm_recruit_application a
  ON a.post_id = c.post_id
 AND a.applicant_user_id = c.candidate_user_id
 AND a.status NOT IN ('WITHDRAWN', 'ELIMINATED')
SET c.candidate_id = a.candidate_id,
    c.application_id = a.id,
    c.application_status = 'SUBMITTED'
WHERE c.application_status IS NULL;

UPDATE hrm_recruit_chat_conversation
SET application_status = 'READY_TO_SUBMIT'
WHERE application_status IS NULL
  AND hr_reply_time IS NOT NULL
  AND status <> 3;
