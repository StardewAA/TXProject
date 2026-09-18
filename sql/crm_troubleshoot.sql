-- ============================================================
--  CRM 登录失败 - 终极诊断脚本
--  请把以下所有 SELECT 的输出截图/复制贴给我
-- ============================================================

-- 0) 当前连的库和主机（确认你连的是登录页连的同一个 MySQL）
SELECT 'A / 当前连接信息' AS section;
SELECT DATABASE()  AS current_db,
       @@hostname  AS mysql_host,
       @@port      AS mysql_port,
       CURRENT_USER() AS login_user;

-- 1) system_tenant 里"固邦"是否存在、id 是几
SELECT 'B / 固邦公司是否存在' AS section;
SELECT id, name, status, deleted, package_id, expire_time, account_count, create_time
FROM   system_tenant
WHERE  name LIKE '%固邦%' OR id = (
         SELECT tenant_id FROM system_users
         WHERE username LIKE 'gubang_%' LIMIT 1
       );

-- 2) 所有启用租户（前端下拉数据源），看固邦在不在
SELECT 'C / 所有启用租户（前端下拉数据源）' AS section;
SELECT id, name, status, deleted
FROM   system_tenant
WHERE  status = 0 AND deleted = 0
ORDER  BY id;

-- 3) gubang_* 5 个用户完整状态（包含密码前 30 位 + 状态 + 删除标记）
SELECT 'D / 5 用户完整状态（密码前 30 位非常关键）' AS section;
SELECT id,
       username,
       tenant_id,
       (SELECT name FROM system_tenant WHERE id = u.tenant_id) AS tenant_name,
       status,
       deleted,
       LEFT(password, 30) AS password_head,
       LENGTH(password)   AS pwd_len
FROM   system_users u
WHERE  username LIKE 'gubang_%'
ORDER  BY id;

-- 4) gubang_* 5 角色
SELECT 'E / 5 角色状态' AS section;
SELECT id, name, code, tenant_id, status, deleted
FROM   system_role
WHERE  code LIKE 'gubang_%' OR id BETWEEN 170 AND 174
ORDER  BY id;

-- 5) 5 用户-角色关联
SELECT 'F / 5 用户-角色关联' AS section;
SELECT ur.id, ur.user_id, u.username, ur.role_id, r.name AS role_name,
       ur.tenant_id, ur.deleted
FROM   system_user_role ur
JOIN   system_users u ON u.id = ur.user_id
LEFT   JOIN system_role  r ON r.id = ur.role_id
WHERE  u.username LIKE 'gubang_%'
ORDER  BY ur.user_id;

-- 6) system_users 表的列字符集（看 password 字段是不是 utf8mb4，
--    避免插入时 $ 被转义)
SELECT 'G / system_users 表字符集' AS section;
SELECT TABLE_NAME, TABLE_COLLATION
FROM   information_schema.TABLES
WHERE  TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_users';

SELECT COLUMN_NAME, CHARACTER_SET_NAME, COLLATION_NAME
FROM   information_schema.COLUMNS
WHERE  TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_users' AND COLUMN_NAME = 'password';

-- 7) MySQL 全局字符集（看连接是不是 utf8mb4）
SELECT 'H / 当前连接字符集' AS section;
SHOW VARIABLES LIKE 'character_set%';
SHOW VARIABLES LIKE 'collation%';

-- 8) 直接验证：把 gubang_sales_rep 当前 password 头 30 拉出来，跟我提供的比对
SELECT 'I / 期望的 password 头（用于跟 D 段比对）' AS section;
SELECT '$2a$04$j/y1FeRJDcG' AS expected_gubang_sales_manager,
       '$2a$04$noV.jHHQtU1' AS expected_gubang_sales_rep,
       '$2a$04$EUKCEDeiV0' AS expected_gubang_customer_service,
       '$2a$04$Ywr4VRKNoJs' AS expected_gubang_operations,
       '$2a$04$jLTyqmTqHi' AS expected_gubang_dept_manager;

-- ============================================================
-- 把你得到的 A ~ I 段输出直接贴给我，我立刻告诉你哪一步的问题
-- ============================================================