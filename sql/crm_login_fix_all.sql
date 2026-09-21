-- ============================================================
--  CRM 测试用户登录彻底修复（兜底版）
--  - 自检当前固邦租户 / 用户 / 密码头
--  - 若固邦租户不存在则一键创建（默认 package_id=0、2099 到期、9999 账号）
--  - 把部门 + 5 角色 + 5 用户 + 5 关联 的 tenant_id 全部对齐到固邦
--  - 末尾验证：每行 tenant_name 必须 = 固邦防腐材料制造有限公司
--
--  执行方式（Navicat / DataGrip 直接打开运行，或 mysql 客户端 source）
-- ============================================================

-- ------------------------------------------------------------
-- 0) 拿固邦的 id（若不存在则先创建）
-- ------------------------------------------------------------
SELECT @gubang_id := id
FROM   system_tenant
WHERE  name = '固邦防腐材料制造有限公司' AND deleted = 0
LIMIT 1;

-- 若没有则建一个（参照 OPENLAB BS 默认值，package_id=0、2099 到期、9999 账号）
INSERT INTO `system_tenant`
  (`id`, `name`, `contact_user_id`, `contact_name`, `contact_mobile`,
   `status`, `websites`, `package_id`, `expire_time`, `account_count`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT
  IFNULL(MAX(id), 0) + 1,
  '固邦防腐材料制造有限公司',
  NULL, '固邦管理员', '15888888888',
  0, '', 0, '2099-12-31 00:00:00', 9999,
  'admin', NOW(), 'admin', NOW(), b'0'
FROM `system_tenant`
WHERE NOT EXISTS (
  SELECT 1 FROM `system_tenant`
  WHERE name = '固邦防腐材料制造有限公司' AND deleted = 0
);

-- 重新拿到刚创建的 id
SELECT @gubang_id := id
FROM   system_tenant
WHERE  name = '固邦防腐材料制造有限公司' AND deleted = 0
LIMIT 1;

SELECT 'STEP 0 / 固邦公司租户 id（若 gubang_tenant_id 仍为 NULL 请检查上面 INSERT）' AS step;
SELECT @gubang_id AS gubang_tenant_id;

-- ------------------------------------------------------------
-- 1) 把部门 CRM部(id=200) 的 tenant_id 改为固邦
-- ------------------------------------------------------------
UPDATE `system_dept`
SET    `tenant_id` = @gubang_id
WHERE  `id` = 200;

-- ------------------------------------------------------------
-- 2) 把 5 角色的 tenant_id 改为固邦（不存在则由用户自己删了重跑）
-- ------------------------------------------------------------
UPDATE `system_role`
SET    `tenant_id` = @gubang_id
WHERE  `code` IN (
        'gubang_sales_manager',
        'gubang_sales_rep',
        'gubang_customer_service',
        'gubang_operations',
        'gubang_dept_manager'
      );

-- ------------------------------------------------------------
-- 3) 把 5 用户的 tenant_id 改为固邦
--    顺手把状态都修正为 status=0, deleted=0（防止脏数据）
-- ------------------------------------------------------------
UPDATE `system_users`
SET    `tenant_id` = @gubang_id,
       `status`    = 0,
       `deleted`   = b'0'
WHERE  `username` IN (
        'gubang_sales_manager',
        'gubang_sales_rep',
        'gubang_customer_service',
        'gubang_operations',
        'gubang_dept_manager'
      );

-- ------------------------------------------------------------
-- 4) 把 5 用户-角色关联的 tenant_id 改为固邦
-- ------------------------------------------------------------
UPDATE `system_user_role`
SET    `tenant_id` = @gubang_id,
       `deleted`   = b'0'
WHERE  `user_id` BETWEEN 250 AND 254;

-- ------------------------------------------------------------
-- 5) 验证：每行的 tenant_name 应为 "固邦防腐材料制造有限公司"
--    密码头应以 $2a$04$ 开头
-- ------------------------------------------------------------
SELECT 'STEP 5 / 验证修复结果' AS step;
SELECT u.id,
       u.username,
       u.tenant_id,
       t.name       AS tenant_name,
       u.status,
       u.deleted,
       LEFT(u.password, 25) AS password_head
FROM   `system_users` u
LEFT   JOIN `system_tenant` t ON t.id = u.tenant_id
WHERE  u.username LIKE 'gubang_%'
ORDER  BY u.id;

-- 角色验证
SELECT r.id, r.name, r.code, r.tenant_id, t.name AS tenant_name
FROM   `system_role` r
LEFT   JOIN `system_tenant` t ON t.id = r.tenant_id
WHERE  r.code LIKE 'gubang_%'
ORDER  BY r.id;

-- 用户-角色关联验证
SELECT ur.user_id, u.username, ur.role_id, r.name AS role_name, ur.tenant_id, t.name AS tenant_name
FROM   `system_user_role` ur
JOIN   `system_users`     u  ON u.id  = ur.user_id
JOIN   `system_role`      r  ON r.id  = ur.role_id
LEFT   JOIN `system_tenant` t ON t.id = ur.tenant_id
WHERE  u.username LIKE 'gubang_%'
ORDER  BY ur.user_id;

-- 部门验证
SELECT id, name, tenant_id FROM `system_dept` WHERE id = 200;

-- ============================================================
-- 期望输出（每张表的 tenant_name 都是"固邦防腐材料制造有限公司"）：
--   用户：status=0 deleted=0 password_head=$2a$04$...
--   角色：status=0 deleted=0
--   关联：tenant_id 与上面一致
--   部门：tenant_id 与上面一致
--
-- 验证完后再去登录页：
--   租户 = 固邦防腐材料制造有限公司
--   账号 = gubang_sales_rep
--   密码 = admin123
-- ============================================================