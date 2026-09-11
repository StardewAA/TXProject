-- 删除用户指定的三个测试租户及已核对的关联数据（逻辑删除）。
-- 精确匹配租户编号与名称；不处理租户 1 下同名的“123”店铺。
-- 执行前备份以下表中目标租户的记录及 system_tenant 三条记录。
SET NAMES utf8mb4;
START TRANSACTION;
CREATE TEMPORARY TABLE cleanup_tenant_ids (id BIGINT PRIMARY KEY);
INSERT INTO cleanup_tenant_ids
SELECT id FROM system_tenant
WHERE (id=111 AND name='111')
   OR (id=3001 AND name='comency')
   OR (id=3002 AND name='123');

UPDATE product_sku SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE product_spu SET deleted=b'1', status=0, updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE product_shop SET deleted=b'1', status=1, updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE product_enterprise SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE system_user_role SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE system_role_menu SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE system_users SET deleted=b'1', status=1, updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE system_role SET deleted=b'1', status=1, updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE system_oauth2_access_token SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE system_oauth2_refresh_token SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE infra_api_access_log SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE system_operate_log SET deleted=b'1', updater='tenant-cleanup', update_time=NOW()
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
UPDATE system_tenant SET deleted=b'1', status=1, updater='tenant-cleanup', update_time=NOW()
WHERE id IN (SELECT id FROM cleanup_tenant_ids) AND deleted=b'0';
DROP TEMPORARY TABLE cleanup_tenant_ids;
COMMIT;
