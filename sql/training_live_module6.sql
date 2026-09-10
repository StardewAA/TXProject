-- 模块六增量迁移：新增业务表；直播房间增量字段由 information_schema 检查后执行。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_profile (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, user_id bigint NOT NULL,
 name varchar(100) NOT NULL, organization varchar(200) NOT NULL, expertise varchar(500) NOT NULL,
 bio text, applicant_id bigint NOT NULL, status varchar(20) NOT NULL DEFAULT 'pending',
 enabled tinyint NOT NULL DEFAULT 1, review_comment varchar(1000), reviewer_id bigint,
 reviewed_at datetime, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_profile(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_booking (
 id bigint PRIMARY KEY AUTO_INCREMENT, room_id bigint NOT NULL, tenant_id bigint NOT NULL,
 user_id bigint NOT NULL, operator_id bigint NOT NULL, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_booking(room_id,tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_assignment (
 id bigint PRIMARY KEY AUTO_INCREMENT, room_id bigint NOT NULL, tenant_id bigint NOT NULL,
 title varchar(200) NOT NULL, content text NOT NULL, due_at datetime NOT NULL,
 creator_id bigint NOT NULL, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_room(room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_submission (
 id bigint PRIMARY KEY AUTO_INCREMENT, assignment_id bigint NOT NULL, tenant_id bigint NOT NULL,
 user_id bigint NOT NULL, content text NOT NULL, score int, feedback varchar(2000), reviewer_id bigint,
 reviewed_at datetime, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_submission(assignment_id,tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_notice (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, user_id bigint NOT NULL,
 room_id bigint, kind varchar(30) NOT NULL, content varchar(1000) NOT NULL, operator_id bigint,
 read_at datetime, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_inbox(tenant_id,user_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_moderation (
 id bigint PRIMARY KEY AUTO_INCREMENT, room_id bigint NOT NULL, target_tenant_id bigint NOT NULL,
 user_id bigint NOT NULL, action varchar(20) NOT NULL, reason varchar(500) NOT NULL,
 operator_id bigint NOT NULL, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_moderation(room_id,target_tenant_id,user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_quiz (
 id bigint PRIMARY KEY AUTO_INCREMENT, room_id bigint NOT NULL, tenant_id bigint NOT NULL,
 question varchar(1000) NOT NULL, options_json text NOT NULL, correct_answer int NOT NULL,
 creator_id bigint NOT NULL, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_quiz_room(room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_answer (
 id bigint PRIMARY KEY AUTO_INCREMENT, quiz_id bigint NOT NULL, tenant_id bigint NOT NULL,
 user_id bigint NOT NULL, answer int NOT NULL, correct tinyint NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_answer(quiz_id,tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_assistant (
 room_id bigint NOT NULL, tenant_id bigint NOT NULL, user_id bigint NOT NULL,
 PRIMARY KEY(room_id,tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_replay_task (
 id bigint PRIMARY KEY AUTO_INCREMENT, room_id bigint NOT NULL UNIQUE, status varchar(30) NOT NULL DEFAULT 'awaiting_recording',
 recording_url varchar(2000), issue varchar(1000), operator_id bigint,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_platform_config (
 id int PRIMARY KEY, danmaku_enabled tinyint NOT NULL DEFAULT 1,
 mic_enabled tinyint NOT NULL DEFAULT 0, updater_id bigint,
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO training_live_platform_config(id) VALUES(1);
CREATE TABLE IF NOT EXISTS training_live_audit_log (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, room_id bigint,
 target_id bigint, action varchar(40) NOT NULL, comment varchar(1000), operator_id bigint NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='training_live_room' AND column_name='lecturer_user_id')=0, 'ALTER TABLE training_live_room ADD COLUMN lecturer_user_id bigint DEFAULT NULL', 'SELECT 1');
PREPARE live_stmt FROM @ddl; EXECUTE live_stmt; DEALLOCATE PREPARE live_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='training_live_room' AND column_name='visibility')=0, 'ALTER TABLE training_live_room ADD COLUMN visibility varchar(20) NOT NULL DEFAULT ''enterprise''', 'SELECT 1');
PREPARE live_stmt FROM @ddl; EXECUTE live_stmt; DEALLOCATE PREPARE live_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='training_live_room' AND column_name='audit_status')=0, 'ALTER TABLE training_live_room ADD COLUMN audit_status varchar(20) NOT NULL DEFAULT ''pending''', 'SELECT 1');
PREPARE live_stmt FROM @ddl; EXECUTE live_stmt; DEALLOCATE PREPARE live_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='training_live_room' AND column_name='audit_comment')=0, 'ALTER TABLE training_live_room ADD COLUMN audit_comment varchar(1000) DEFAULT NULL', 'SELECT 1');
PREPARE live_stmt FROM @ddl; EXECUTE live_stmt; DEALLOCATE PREPARE live_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='training_live_room' AND column_name='replay_enabled')=0, 'ALTER TABLE training_live_room ADD COLUMN replay_enabled tinyint NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE live_stmt FROM @ddl; EXECUTE live_stmt; DEALLOCATE PREPARE live_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='training_live_room' AND column_name='replay_audit_status')=0, 'ALTER TABLE training_live_room ADD COLUMN replay_audit_status varchar(20) NOT NULL DEFAULT ''pending''', 'SELECT 1');
PREPARE live_stmt FROM @ddl; EXECUTE live_stmt; DEALLOCATE PREPARE live_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='training_live_room' AND column_name='actual_start_at')=0, 'ALTER TABLE training_live_room ADD COLUMN actual_start_at datetime DEFAULT NULL', 'SELECT 1');
PREPARE live_stmt FROM @ddl; EXECUTE live_stmt; DEALLOCATE PREPARE live_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='training_live_room' AND column_name='actual_end_at')=0, 'ALTER TABLE training_live_room ADD COLUMN actual_end_at datetime DEFAULT NULL', 'SELECT 1');
PREPARE live_stmt FROM @ddl; EXECUTE live_stmt; DEALLOCATE PREPARE live_stmt;

-- Keep request limits and existing storage lengths aligned.
ALTER TABLE training_live_room MODIFY cover_url varchar(2000), MODIFY stream_url varchar(2000),
 MODIFY replay_video_url varchar(2000), MODIFY remark varchar(2000), MODIFY lecturer_name varchar(100);
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater)
SELECT '企业直播管理','training:live:enterprise',3,1,41007,'','','',0,1,1,1,'module6','module6'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='training:live:enterprise' AND deleted=0);

CREATE TABLE IF NOT EXISTS training_live_stream (
 room_id bigint PRIMARY KEY, publish_token varchar(64) NOT NULL, client_id varchar(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS training_live_media_ticket (
 token varchar(64) PRIMARY KEY, room_id bigint NOT NULL, tenant_id bigint NOT NULL,
 user_id bigint NOT NULL, replay tinyint NOT NULL, expires_at datetime NOT NULL, KEY idx_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
