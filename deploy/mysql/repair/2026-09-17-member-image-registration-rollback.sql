-- Prefer rolling back the application while retaining these nullable columns.
-- Destructive schema rollback is allowed ONLY before any named/contact accounts have been stored.
-- Run after stopping member writes and backing up member_user. Never run with --force.
DELIMITER $$
DROP PROCEDURE IF EXISTS mall_image_registration_rollback_guard$$
CREATE PROCEDURE mall_image_registration_rollback_guard()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='member_user' AND column_name='login_name') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM member_user WHERE login_name IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Named accounts exist: keep schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='member_user' AND column_name='contact_mobile') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM member_user WHERE contact_mobile IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Contact data exists: keep schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='member_user' AND index_name='uk_tenant_login_name') THEN
        ALTER TABLE member_user DROP INDEX uk_tenant_login_name;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='member_user' AND column_name='login_name') THEN
        ALTER TABLE member_user DROP COLUMN login_name;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='member_user' AND column_name='contact_mobile') THEN
        ALTER TABLE member_user DROP COLUMN contact_mobile;
    END IF;
END$$
CALL mall_image_registration_rollback_guard()$$
DROP PROCEDURE mall_image_registration_rollback_guard$$
DELIMITER ;
