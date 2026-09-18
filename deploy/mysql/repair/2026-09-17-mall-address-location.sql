-- Additive / rerunnable. Back up member_address and trade_order, select the mall database first.
-- Existing rows remain NULL; no tenant, account, written address or order fact is changed.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='member_address' AND column_name='longitude')=0,
    'ALTER TABLE member_address ADD COLUMN longitude DECIMAL(10,6) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='member_address' AND column_name='latitude')=0,
    'ALTER TABLE member_address ADD COLUMN latitude DECIMAL(9,6) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='member_address' AND column_name='coord_system')=0,
    'ALTER TABLE member_address ADD COLUMN coord_system VARCHAR(16) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='member_address' AND column_name='location_source')=0,
    'ALTER TABLE member_address ADD COLUMN location_source VARCHAR(32) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='member_address' AND column_name='location_precision')=0,
    'ALTER TABLE member_address ADD COLUMN location_precision VARCHAR(32) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='member_address' AND column_name='location_confirmed_at')=0,
    'ALTER TABLE member_address ADD COLUMN location_confirmed_at DATETIME NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='trade_order' AND column_name='receiver_longitude')=0,
    'ALTER TABLE trade_order ADD COLUMN receiver_longitude DECIMAL(10,6) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='trade_order' AND column_name='receiver_latitude')=0,
    'ALTER TABLE trade_order ADD COLUMN receiver_latitude DECIMAL(9,6) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='trade_order' AND column_name='receiver_coord_system')=0,
    'ALTER TABLE trade_order ADD COLUMN receiver_coord_system VARCHAR(16) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='trade_order' AND column_name='receiver_location_source')=0,
    'ALTER TABLE trade_order ADD COLUMN receiver_location_source VARCHAR(32) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='trade_order' AND column_name='receiver_location_precision')=0,
    'ALTER TABLE trade_order ADD COLUMN receiver_location_precision VARCHAR(32) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='trade_order' AND column_name='receiver_location_confirmed_at')=0,
    'ALTER TABLE trade_order ADD COLUMN receiver_location_confirmed_at DATETIME NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
