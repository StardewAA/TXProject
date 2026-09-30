-- ONLY after scoped-enabled=false and no in-flight scoped writes.
-- Refuses to discard sessions, grants or audit history. With data, keep tables and roll back the image only.
-- Legacy promotion_kefu_conversation / promotion_kefu_message are NEVER changed.
DELIMITER $$
CREATE PROCEDURE mall_kefu_scope_rollback_20260917()
BEGIN
    DECLARE records BIGINT;
    SELECT (SELECT COUNT(*) FROM promotion_kefu_agent)
         + (SELECT COUNT(*) FROM promotion_kefu_session)
         + (SELECT COUNT(*) FROM promotion_kefu_scope_audit) INTO records;
    IF records <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Refusing rollback: customer-service data exists; retain tables';
    END IF;
    DROP TABLE promotion_kefu_scope_audit;
    DROP TABLE promotion_kefu_session;
    DROP TABLE promotion_kefu_agent;
END$$
DELIMITER ;
CALL mall_kefu_scope_rollback_20260917();
DROP PROCEDURE mall_kefu_scope_rollback_20260917;
