-- 用户追加删除旧“商城平台公共用户”租户（3000）。
-- 当前 PC/H5/注册使用租户 1；执行前已备份目标租户与关联记录。
SET NAMES utf8mb4;
START TRANSACTION;
CREATE TEMPORARY TABLE cleanup_public_tenant (id BIGINT PRIMARY KEY);
INSERT INTO cleanup_public_tenant
SELECT id FROM system_tenant WHERE id=3000 AND name='商城平台公共用户';

UPDATE promotion_diy_page SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_public_tenant) AND deleted=b'0';
UPDATE promotion_diy_template SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_public_tenant) AND deleted=b'0';
UPDATE infra_api_access_log SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_public_tenant) AND deleted=b'0';
UPDATE system_tenant SET deleted=b'1', status=1, updater='tenant-cleanup', update_time=NOW()
WHERE id IN (SELECT id FROM cleanup_public_tenant) AND deleted=b'0';
DROP TEMPORARY TABLE cleanup_public_tenant;
COMMIT;
