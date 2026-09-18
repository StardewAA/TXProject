-- ============================================================
--  CRM 测试用户登录诊断 + 自动修复
--  适用项目：txgycloud (txgy-module-system)
--
--  运行前确保连接的数据库就是登录页对应的业务库（use xxx_db;）
--  脚本会分 6 步执行，每步都会打印结果，请按顺序看输出
-- ============================================================

-- 准备一个调试变量：固邦公司的目标 tenant_id
SELECT @gubang_id := id, @gubang_name := name
FROM   system_tenant
WHERE  name LIKE '%固邦%' AND deleted = 0
ORDER  BY id ASC LIMIT 1;

-- 打印：STEP 1 固邦公司候选租户
SELECT 'STEP 1 / 固邦公司候选租户（如果为空，说明 DB 里没有这个租户）' AS step;
SELECT IFNULL(@gubang_id, '⚠️ 未找到固邦公司，请先去 Admin 后台【租户管理】新建一个名为「固邦防腐材料制造有限公司」的租户') AS gubang_tenant_id,
       @gubang_name AS gubang_tenant_name;

-- 打印：STEP 2 gubang_* 用户当前状态
SELECT 'STEP 2 / gubang_* 用户当前状态' AS step;
SELECT id,
       username,
       tenant_id,
       (SELECT name FROM system_tenant WHERE id = u.tenant_id) AS tenant_name,
       status,
       deleted,
       LEFT(password, 25) AS password_head
FROM   system_users u
WHERE  username IN (
         'gubang_sales_manager',
         'gubang_sales_rep',
         'gubang_customer_service',
         'gubang_operations',
         'gubang_dept_manager'
       );

-- 打印：STEP 3 gubang_* 角色当前状态
SELECT 'STEP 3 / gubang_* 角色当前状态' AS step;
SELECT id,
       name,
       code,
       tenant_id,
       (SELECT name FROM system_tenant WHERE id = r.tenant_id) AS tenant_name,
       status,
       deleted
FROM   system_role r
WHERE  code IN (
         'gubang_sales_manager',
         'gubang_sales_rep',
         'gubang_customer_service',
         'gubang_operations',
         'gubang_dept_manager'
       );

-- 打印：STEP 4 gubang_* 用户-角色关联当前状态
SELECT 'STEP 4 / gubang_* 用户-角色关联当前状态' AS step;
SELECT ur.id,
       ur.user_id,
       u.username,
       ur.role_id,
       r.name AS role_name,
       ur.tenant_id,
       (SELECT name FROM system_tenant WHERE id = ur.tenant_id) AS tenant_name
FROM   system_user_role ur
JOIN   system_users u ON u.id = ur.user_id
JOIN   system_role  r ON r.id = ur.role_id
WHERE  u.username LIKE 'gubang_%';

-- ============================================================
-- STEP 5 / 修复：将这 5 个用户 / 5 个角色 / 5 条关联的 tenant_id
--               统一改为 @gubang_id（固邦公司的真实租户号）
--               若 @gubang_id 为空，则以下 UPDATE 会把所有目标行的 tenant_id 设为 NULL，
--               不会报错，但请回到 STEP 1 先确认固邦租户已存在。
-- ============================================================

-- 5.1 修复 5 个用户的 tenant_id
UPDATE system_users
SET    tenant_id = @gubang_id
WHERE  username IN (
         'gubang_sales_manager',
         'gubang_sales_rep',
         'gubang_customer_service',
         'gubang_operations',
         'gubang_dept_manager'
       );

-- 5.2 修复 5 个角色的 tenant_id
UPDATE system_role
SET    tenant_id = @gubang_id
WHERE  code IN (
         'gubang_sales_manager',
         'gubang_sales_rep',
         'gubang_customer_service',
         'gubang_operations',
         'gubang_dept_manager'
       );

-- 5.3 修复 5 条用户-角色关联的 tenant_id
UPDATE system_user_role
SET    tenant_id = @gubang_id
WHERE  user_id BETWEEN 250 AND 254;

-- 5.4 顺手把 CRM 部(部门 id=200) 的 tenant_id 也改成固邦
UPDATE system_dept
SET    tenant_id = @gubang_id
WHERE  id = 200;

-- ============================================================
-- STEP 6 / 修复后再次确认：每个用户的 tenant_id 现在必须指向固邦公司
-- ============================================================
SELECT 'STEP 6 / 修复后再次确认' AS step;
SELECT u.id,
       u.username,
       u.tenant_id,
       t.name AS tenant_name,
       u.status,
       u.deleted
FROM   system_users u
LEFT   JOIN system_tenant t ON t.id = u.tenant_id
WHERE  u.username LIKE 'gubang_%'
ORDER  BY u.id;

-- 期望：每行的 tenant_name 都是「固邦防腐材料制造有限公司」；
--       status=0、deleted=0、password 以 $2a$04$ 开头。

-- ============================================================
-- 修复完成后再去登录页登录：
--   租户 = 固邦防腐材料制造有限公司
--   账号 = gubang_sales_rep  （或另外 4 个）
--   密码 = admin123
-- ============================================================