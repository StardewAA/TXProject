-- 个人用户租户（tenant_id = 0），让个人注册账号可以登录。
-- MySQL 默认会把自增列的 0 当成“生成下一个编号”，因此本迁移显式启用
-- NO_AUTO_VALUE_ON_ZERO，确保代码中保留的 PERSONAL_TENANT_ID=0 与数据库一致。
SET @OLD_SQL_MODE = @@SESSION.sql_mode;
SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'NO_AUTO_VALUE_ON_ZERO');

INSERT INTO system_tenant
(id, name, contact_user_id, contact_name, contact_mobile, status, websites, package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted)
VALUES
(0, '个人用户', NULL, '个人用户', NULL, 0, NULL, 0, '2099-12-31 23:59:59', 999999, '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name), status=0, deleted=b'0', update_time=NOW();

SET SESSION sql_mode = @OLD_SQL_MODE;
