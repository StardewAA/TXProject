-- Prefer retaining nullable columns when reverting the application. Stop member/trade writes and back up first.
-- Do not use --force. This guard refuses ANY coordinate metadata loss, including soft-deleted records.
DELIMITER $$
DROP PROCEDURE IF EXISTS mall_address_location_rollback_guard$$
CREATE PROCEDURE mall_address_location_rollback_guard()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='longitude') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM member_address WHERE longitude IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='latitude') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM member_address WHERE latitude IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='coord_system') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM member_address WHERE coord_system IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='location_source') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM member_address WHERE location_source IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='location_precision') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM member_address WHERE location_precision IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='location_confirmed_at') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM member_address WHERE location_confirmed_at IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_longitude') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM trade_order WHERE receiver_longitude IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_latitude') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM trade_order WHERE receiver_latitude IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_coord_system') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM trade_order WHERE receiver_coord_system IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_location_source') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM trade_order WHERE receiver_location_source IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_location_precision') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM trade_order WHERE receiver_location_precision IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_location_confirmed_at') THEN
        SET @used = 0;
        SET @q = 'SELECT COUNT(*) INTO @used FROM trade_order WHERE receiver_location_confirmed_at IS NOT NULL';
        PREPARE rollback_check FROM @q; EXECUTE rollback_check; DEALLOCATE PREPARE rollback_check;
        IF @used > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Address location data exists: retain schema and fix forward'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='longitude') THEN
        ALTER TABLE member_address DROP COLUMN longitude;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='latitude') THEN
        ALTER TABLE member_address DROP COLUMN latitude;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='coord_system') THEN
        ALTER TABLE member_address DROP COLUMN coord_system;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='location_source') THEN
        ALTER TABLE member_address DROP COLUMN location_source;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='location_precision') THEN
        ALTER TABLE member_address DROP COLUMN location_precision;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_address' AND column_name='location_confirmed_at') THEN
        ALTER TABLE member_address DROP COLUMN location_confirmed_at;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_longitude') THEN
        ALTER TABLE trade_order DROP COLUMN receiver_longitude;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_latitude') THEN
        ALTER TABLE trade_order DROP COLUMN receiver_latitude;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_coord_system') THEN
        ALTER TABLE trade_order DROP COLUMN receiver_coord_system;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_location_source') THEN
        ALTER TABLE trade_order DROP COLUMN receiver_location_source;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_location_precision') THEN
        ALTER TABLE trade_order DROP COLUMN receiver_location_precision;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='receiver_location_confirmed_at') THEN
        ALTER TABLE trade_order DROP COLUMN receiver_location_confirmed_at;
    END IF;
END$$
CALL mall_address_location_rollback_guard()$$
DROP PROCEDURE mall_address_location_rollback_guard$$
DELIMITER ;
