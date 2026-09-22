-- First disable scoped-enabled, stop in-flight writes and roll back the application if needed.
-- Keep existing tables whenever any chat/FAQ/presence/read data exists. Never use --force.
DELIMITER $$
DROP PROCEDURE IF EXISTS mall_kefu_chat_rollback_guard$$
CREATE PROCEDURE mall_kefu_chat_rollback_guard()
BEGIN
    DECLARE finished INT DEFAULT 0;
    DECLARE guarded_table VARCHAR(64);
    DECLARE existing_tables CURSOR FOR
        SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()
            AND table_name IN ('promotion_kefu_chat_meta','promotion_kefu_chat_message','promotion_kefu_chat_read',
                               'promotion_kefu_chat_presence','promotion_kefu_faq_config');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=1;
    OPEN existing_tables;
    check_tables: LOOP
        FETCH existing_tables INTO guarded_table;
        IF finished=1 THEN LEAVE check_tables; END IF;
        SET @chat_used_rows=0;
        SET @chat_check_sql=CONCAT('SELECT COUNT(*) INTO @chat_used_rows FROM `',guarded_table,'`');
        PREPARE chat_check FROM @chat_check_sql;
        EXECUTE chat_check;
        DEALLOCATE PREPARE chat_check;
        IF @chat_used_rows>0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Scoped chat data exists: retain tables and fix forward';
        END IF;
    END LOOP;
    CLOSE existing_tables;
    DROP TABLE IF EXISTS promotion_kefu_chat_read,promotion_kefu_chat_message,promotion_kefu_chat_presence,
        promotion_kefu_chat_meta,promotion_kefu_faq_config;
END$$
CALL mall_kefu_chat_rollback_guard()$$
DROP PROCEDURE mall_kefu_chat_rollback_guard$$
DELIMITER ;
