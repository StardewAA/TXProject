-- ============================================================
--  CRM 5 测试用户【确定性】脚本（重置版）
--  目标：把 5 用户/5 角色/部门 全部建到"固邦防腐材料制造有限公司"租户下
--  效果：登录页租户选固邦公司 + 账号 gubang_sales_rep + 密码 admin123 → 可登录
--
--  使用方法：复制整段到 MySQL 客户端执行；可重复执行
-- ============================================================

-- ============================================================
-- STEP 0 / 确认连对了库（防止改错数据库）
-- ============================================================
SELECT 'STEP 0 / 当前连接信息（请确认这个就是登录页连的库）' AS step;
SELECT DATABASE() AS db_name, @@hostname AS mysql_host, @@port AS port, CURRENT_USER() AS login_user;

-- 字符集检查（system_users.password 必须是 utf8mb4，避免 $ 被转义成 ?）
SELECT 'STEP 0.1 / system_users 表字符集' AS step;
SELECT TABLE_NAME, TABLE_COLLATION
FROM   information_schema.TABLES
WHERE  TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_users';
SELECT COLUMN_NAME, CHARACTER_SET_NAME, COLLATION_NAME
FROM   information_schema.COLUMNS
WHERE  TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'system_users'
  AND COLUMN_NAME  IN ('username','password','nickname','email');

-- ============================================================
-- STEP 1 / 找到（或创建）"固邦防腐材料制造有限公司"租户
-- ============================================================

-- 1.1 先按 name 精确查
SELECT @gubang_id := id
FROM   system_tenant
WHERE  name = '固邦防腐材料制造有限公司' AND deleted = 0
LIMIT 1;

-- 1.2 如果没找到，按 id = 123（固邦常用的占位 id）查一次
SELECT @gubang_id := IFNULL(@gubang_id, (
         SELECT id FROM system_tenant
         WHERE (id = 123 OR name LIKE '%固邦%') AND deleted = 0
         ORDER BY (id = 123) DESC LIMIT 1
       ));

-- 1.3 如果还是没找到，自动建一条（参照 OPENLAB BS 默认）
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
WHERE @gubang_id IS NULL;

-- 1.4 重新拿到固邦的 id
SELECT @gubang_id := id
FROM   system_tenant
WHERE  name = '固邦防腐材料制造有限公司' AND deleted = 0
LIMIT 1;

-- 1.5 验证固邦确实存在
SELECT 'STEP 1.5 / 固邦公司租户（若仍为 NULL，请检查 system_tenant 表是否能写入）' AS step;
SELECT @gubang_id AS gubang_tenant_id, (SELECT name FROM system_tenant WHERE id = @gubang_id) AS gubang_tenant_name;

-- ============================================================
-- STEP 2 / 清理所有历史 gubang_* 残留（无论之前怎么建的、什么 id，全部清掉重来）
-- ============================================================
DELETE FROM `system_user_role` WHERE `user_id` IN (SELECT id FROM `system_users` WHERE `username` LIKE 'gubang_%');
DELETE FROM `system_users`     WHERE `username` LIKE 'gubang_%';
DELETE FROM `system_role`      WHERE `code`    LIKE 'gubang_%';

-- ============================================================
-- STEP 3 / 创建部门 CRM部（id=200，挂在"深圳总公司"=101 下，租户=固邦）
-- ============================================================
DELETE FROM `system_dept` WHERE `id` = 200;
INSERT INTO `system_dept`
  (`id`, `name`, `parent_id`, `sort`, `leader_user_id`, `phone`, `email`, `status`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (200, 'CRM部', 101, 99, NULL, NULL, 'crm@txgy.cn', 0,
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id);

-- ============================================================
-- STEP 4 / 创建 5 角色（id=170~174，code=用户名，name=角色名，租户=固邦）
-- ============================================================
INSERT INTO `system_role`
  (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (170, '销售经理',       'gubang_sales_manager',     1, 1, '', 0, 2, 'CRM测试-销售经理',
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (171, '普通销售业务员', 'gubang_sales_rep',         2, 1, '', 0, 2, 'CRM测试-普通销售业务员',
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (172, '客服专员',       'gubang_customer_service',  3, 1, '', 0, 2, 'CRM测试-客服专员',
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (173, '运营专员',       'gubang_operations',        4, 1, '', 0, 2, 'CRM测试-运营专员',
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (174, '部门主管',       'gubang_dept_manager',      5, 1, '', 0, 2, 'CRM测试-部门主管',
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id);

-- ============================================================
-- STEP 5 / 创建 5 用户（id=250~254，密码=admin123 的 $2a$04$ BCrypt 哈希，租户=固邦）
--   哈希均已用 Python bcrypt 实测 checkpw('admin123', hash) = True
--   与项目 SecurityProperties.passwordEncoderLength=4 完全兼容
-- ============================================================
INSERT INTO `system_users`
  (`id`, `username`, `password`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`,
   `sex`, `avatar`, `status`, `login_ip`, `login_date`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (250, 'gubang_sales_manager',
   '$2a$04$9lrHJMFwjJyg8.SeCwsOze.1Y47UlTJG3X/s.80Hl9dO6CQYXKJs6',
   '销售经理', 'CRM测试-销售经理', 200, NULL,
   'gubang_sales_manager@txgy.cn', '', 0, '', 0, '', NULL,
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (251, 'gubang_sales_rep',
   '$2a$04$996zoIBsFim60BttkN9LLu8U5Vj7TbV0skx7WLmFYUho8LEMseaZO',
   '普通销售业务员', 'CRM测试-普通销售业务员', 200, NULL,
   'gubang_sales_rep@txgy.cn', '', 0, '', 0, '', NULL,
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (252, 'gubang_customer_service',
   '$2a$04$x..fq5jBWwqNnzXCaUi31uFDMXojeUg8D1QYxiagGCZgdf5jqAizG',
   '客服专员', 'CRM测试-客服专员', 200, NULL,
   'gubang_customer_service@txgy.cn', '', 0, '', 0, '', NULL,
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (253, 'gubang_operations',
   '$2a$04$ULGUF5ccMsQ4joRmT8qLh.qj440cVzCdiRC9zpR5.VmHw/gL3tSzO',
   '运营专员', 'CRM测试-运营专员', 200, NULL,
   'gubang_operations@txgy.cn', '', 0, '', 0, '', NULL,
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (254, 'gubang_dept_manager',
   '$2a$04$vkTEmWsp3.wshXRszFl1O.NC3I0OV6d3.RiSjVMeK3xnPQGyBBFea',
   '部门主管', 'CRM测试-部门主管', 200, NULL,
   'gubang_dept_manager@txgy.cn', '', 0, '', 0, '', NULL,
   'admin', NOW(), 'admin', NOW(), b'0', @gubang_id);

-- ============================================================
-- STEP 6 / 创建 5 用户-角色关联
-- ============================================================
INSERT INTO `system_user_role`
  (`id`, `user_id`, `role_id`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (100, 250, 170, 'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (101, 251, 171, 'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (102, 252, 172, 'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (103, 253, 173, 'admin', NOW(), 'admin', NOW(), b'0', @gubang_id),
  (104, 254, 174, 'admin', NOW(), 'admin', NOW(), b'0', @gubang_id);

-- ============================================================
-- STEP 7 / 模拟"登录时的查询"（这是关键验证 —— 跟 AdminAuthServiceImpl 用的完全一致）
-- ============================================================
SELECT 'STEP 7 / 模拟登录查询：username=gubang_sales_rep + tenant=固邦' AS step;
SELECT id, username, tenant_id, status, deleted, LEFT(password, 25) AS password_head
FROM   system_users
WHERE  username   = 'gubang_sales_rep'
  AND  tenant_id  = @gubang_id
  AND  deleted    = 0;

-- 期望返回 1 行；password_head 必须是 $2a$04$... 开头

-- ============================================================
-- STEP 8 / 最终全量验证
-- ============================================================
SELECT 'STEP 8.1 / 5 用户完整状态' AS step;
SELECT u.id, u.username, u.tenant_id, t.name AS tenant_name,
       u.status, u.deleted, LEFT(u.password, 25) AS password_head
FROM   system_users u
LEFT   JOIN system_tenant t ON t.id = u.tenant_id
WHERE  u.username LIKE 'gubang_%' ORDER BY u.id;

SELECT 'STEP 8.2 / 5 角色' AS step;
SELECT r.id, r.name, r.code, r.tenant_id, t.name AS tenant_name, r.status, r.deleted
FROM   system_role r
LEFT   JOIN system_tenant t ON t.id = r.tenant_id
WHERE  r.code LIKE 'gubang_%' ORDER BY r.id;

SELECT 'STEP 8.3 / 5 用户-角色关联' AS step;
SELECT ur.user_id, u.username, ur.role_id, r.name AS role_name,
       ur.tenant_id, t.name AS tenant_name, ur.deleted
FROM   system_user_role ur
JOIN   system_users u ON u.id = ur.user_id
JOIN   system_role  r ON r.id = ur.role_id
LEFT   JOIN system_tenant t ON t.id = ur.tenant_id
WHERE  u.username LIKE 'gubang_%' ORDER BY ur.user_id;

SELECT 'STEP 8.4 / 部门 CRM部' AS step;
SELECT id, name, tenant_id, (SELECT name FROM system_tenant WHERE id = d.tenant_id) AS tenant_name, deleted
FROM   system_dept d WHERE id = 200;

-- ============================================================
-- 期望：所有 tenant_name 都是 "固邦防腐材料制造有限公司"，
--       status=0、deleted=b'0'、password_head 以 $2a$04$ 开头
-- ============================================================

-- ============================================================
-- STEP 9 / 【必做】清浏览器 localStorage 后再登录（前端缓存的旧 tenant-id 是最常见挡板）
-- ============================================================
-- 1) 登录页按 F12
-- 2) Application → Local Storage → 选 origin
-- 3) 全选所有 key → Delete
-- 4) Ctrl+Shift+R 强刷
-- 5) 下拉重新选 "固邦防腐材料制造有限公司"
-- 6) 账号 gubang_sales_rep  密码 admin123  → 登录