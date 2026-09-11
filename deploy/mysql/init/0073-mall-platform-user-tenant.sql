-- 商城用户端公共租户：普通注册用户统一归属此租户，不使用企业租户（例如 tenant_id=1）。
-- 幂等：仅当编号不存在时创建，避免覆盖已有租户配置。
INSERT INTO system_tenant
    (id, name, contact_user_id, contact_name, contact_mobile, status, websites,
     package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted)
SELECT
    3000, '商城平台公共用户', NULL, '商城平台', NULL, 0, NULL,
    111, '2099-12-31 23:59:59', 9999999, 'system', NOW(), 'system', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM system_tenant WHERE id = 3000
);
