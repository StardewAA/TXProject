-- Additive / rerunnable. Select the mall database first. Does not create/move users or enable tenants.
-- Preserve idx_mobile and all existing mobile/password/member/tenant values.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='member_user' AND column_name='login_name')=0,
    'ALTER TABLE member_user ADD COLUMN login_name VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''Normalized account name''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='member_user' AND column_name='contact_mobile')=0,
    'ALTER TABLE member_user ADD COLUMN contact_mobile VARCHAR(11) NULL COMMENT ''Unverified contact, never identity''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='member_user' AND index_name='uk_tenant_login_name')=0,
    'ALTER TABLE member_user ADD UNIQUE KEY uk_tenant_login_name (tenant_id, login_name)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
