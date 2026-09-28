-- ============================================================
--  CRM 测试用户 - 修复租户ID 脚本
--  问题现象：登录提示 "账号密码不正确"，但用户已写入 system_users
--  根因    ：原脚本写入 tenant_id=1（默认租户"OPENLAB BS"），
--            而登录页选中的租户是"固邦防腐材料制造有限公司"，
--            yudao 多租户登录按 tenant_id + username 查询用户，固邦租户下无此用户。
--  解决方法    ：把 5 用户 / 5 角色 / 5 用户角色关联的 tenant_id 改为固邦公司的真实租户号
-- ============================================================

-- 0) 先查出固邦防腐的真实租户ID（手动执行 SELECT 看结果，确认后再执行下面 UPDATE）
--    请把下面 SELECT 单独跑一次，确认返回的就是登录页那个"固邦防腐材料制造有限公司"：
-- SELECT id, name FROM system_tenant WHERE name LIKE '%固邦%' AND deleted = 0;
-- 例如返回 id=123，则后续 UPDATE 实际就把 tenant_id 改为 123。

-- 1) 修复 5 个用户的租户ID
UPDATE `system_users`
SET    `tenant_id` = (SELECT `id` FROM `system_tenant`
                      WHERE `name` LIKE '%固邦%' AND `deleted` = 0
                      LIMIT 1)
WHERE  `username` IN (
        'gubang_sales_manager',
        'gubang_sales_rep',
        'gubang_customer_service',
        'gubang_operations',
        'gubang_dept_manager'
      );

-- 2) 修复 5 个角色的租户ID
UPDATE `system_role`
SET    `tenant_id` = (SELECT `id` FROM `system_tenant`
                      WHERE `name` LIKE '%固邦%' AND `deleted` = 0
                      LIMIT 1)
WHERE  `code` IN (
        'gubang_sales_manager',
        'gubang_sales_rep',
        'gubang_customer_service',
        'gubang_operations',
        'gubang_dept_manager'
      );

-- 3) 修复 5 条用户-角色关联的租户ID
UPDATE `system_user_role`
SET    `tenant_id` = (SELECT `id` FROM `system_tenant`
                      WHERE `name` LIKE '%固邦%' AND `deleted` = 0
                      LIMIT 1)
WHERE  `user_id` IN (250, 251, 252, 253, 254);

-- 4) 验证修复结果：每个用户的 tenant_id 现在应指向"固邦防腐材料制造有限公司"
SELECT u.id        AS user_id,
       u.username  AS username,
       u.tenant_id AS tenant_id,
       t.name      AS tenant_name
FROM   `system_users` u
LEFT   JOIN `system_tenant` t ON t.id = u.tenant_id
WHERE  u.username LIKE 'gubang_%'
ORDER  BY u.id;

-- 5) 验证关联：每个用户都应有 1 条角色关联
SELECT ur.user_id, u.username, ur.role_id, r.name AS role_name, ur.tenant_id
FROM   `system_user_role` ur
JOIN   `system_users`     u  ON u.id  = ur.user_id
JOIN   `system_role`      r  ON r.id  = ur.role_id
WHERE  u.username LIKE 'gubang_%'
ORDER  BY ur.user_id;

-- ============================================================
-- 跑完以上后，再去登录页用 gubang_sales_rep / admin123 登录
-- （确认第 0 步查出的"固邦防腐材料制造有限公司"id 已被正确写入）
-- ============================================================