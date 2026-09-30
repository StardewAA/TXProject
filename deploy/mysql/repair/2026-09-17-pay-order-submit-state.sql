-- Additive and rerunnable. Select the target database explicitly before execution.
-- Deploy this column BEFORE the new pay-server image. No channel/settings/business data changes.
-- Existing NULL attempts are unconfirmed: never backfill them as DISPATCHED without reconciliation.
DROP PROCEDURE IF EXISTS mall_pay_submit_state_20260917;
DELIMITER $$
CREATE PROCEDURE mall_pay_submit_state_20260917()
BEGIN
    IF DATABASE() IS NULL OR NOT EXISTS (SELECT 1 FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_name='pay_order_extension' AND table_type='BASE TABLE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select database with pay_order_extension first';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='pay_order_extension' AND column_name='submit_state') THEN
        ALTER TABLE pay_order_extension ADD COLUMN submit_state TINYINT NULL DEFAULT NULL
            COMMENT 'Submit state: 0 prepared, 1 dispatched, 2 unknown; NULL legacy unconfirmed';
    ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='pay_order_extension' AND column_name='submit_state'
        AND data_type='tinyint' AND is_nullable='YES' AND column_default IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing submit_state has incompatible definition';
    END IF;
END$$
DELIMITER ;
CALL mall_pay_submit_state_20260917();
DROP PROCEDURE mall_pay_submit_state_20260917;
