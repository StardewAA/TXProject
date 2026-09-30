-- ============================================================================
-- 固邦防腐材料制造有限公司（租户 tenant_id=2012）租户业务数据初始化脚本
-- ----------------------------------------------------------------------------
-- 目标：
--   1) 部门初始化：新增「客户服务部」「运营部」（市场营销部已存在，销售团队沿用）
--   2) 岗位初始化：销售经理 / 销售业务员 / 客服专员 / 运营专员 / 部门主管
--   3) 新增 5 个业务角色：销售经理、普通销售业务员、客服专员、运营专员、部门主管，
--      并为其分配菜单权限：隐藏【租户管理】菜单，仅开放 CRM 相关业务菜单
--      （CRM 完整菜单树：根 2397 及其全部 80 个子节点）
--   4) 基于 5 个角色生成对应测试用户，多租户隔离，数据归属租户 2012
-- 说明：脚本幂等，可重复执行（先清理本次创建的目标记录再重建）。
--       测试用户初始登录密码统一为 admin123。
-- 执行：
--   docker exec -i txgy-mysql mysql --default-character-set=utf8mb4 -uroot -p*** ruoyi-vue-pro < init-gubang-crm-2012.sql
-- ============================================================================

SET NAMES utf8mb4;

START TRANSACTION;

SET @tenant_id = 2012;
SET @creator   = '1';
SET @now       = NOW();
-- admin123 的 BCrypt 密文（沿用固邦租户管理员 gubang 的密文）
SET @pwd_hash  = '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG';

-- ----------------------------------------------------------------------------
-- 0) 幂等清理：删除本次脚本曾经创建的目标记录（含其关联数据）
-- ----------------------------------------------------------------------------
DELETE FROM system_user_role
 WHERE tenant_id = @tenant_id
   AND user_id IN (
     SELECT id FROM system_users
      WHERE tenant_id = @tenant_id AND username IN (
        'gubang_sales_manager','gubang_sales_rep','gubang_customer_service',
        'gubang_operations','gubang_dept_manager')
   );

DELETE FROM system_user_post
 WHERE tenant_id = @tenant_id
   AND user_id IN (
     SELECT id FROM system_users
      WHERE tenant_id = @tenant_id AND username IN (
        'gubang_sales_manager','gubang_sales_rep','gubang_customer_service',
        'gubang_operations','gubang_dept_manager')
   );

DELETE FROM system_users
 WHERE tenant_id = @tenant_id AND username IN (
   'gubang_sales_manager','gubang_sales_rep','gubang_customer_service',
   'gubang_operations','gubang_dept_manager');

DELETE FROM system_role_menu
 WHERE tenant_id = @tenant_id
   AND role_id IN (
     SELECT id FROM system_role
      WHERE tenant_id = @tenant_id AND code IN (
        'sales_manager','sales_rep','customer_service','operations_specialist','dept_manager')
   );

DELETE FROM system_role
 WHERE tenant_id = @tenant_id AND code IN (
   'sales_manager','sales_rep','customer_service','operations_specialist','dept_manager');

DELETE FROM system_post
 WHERE tenant_id = @tenant_id AND code IN (
   'gubang_sales_manager','gubang_sales_rep','gubang_customer_service',
   'gubang_operations','gubang_dept_manager');

DELETE FROM system_dept
 WHERE tenant_id = @tenant_id AND name IN ('客户服务部','运营部');

-- ----------------------------------------------------------------------------
-- 1) 部门初始化：新增客户服务部、运营部（父部门 = 总经理办公室 50077）
-- ----------------------------------------------------------------------------
INSERT INTO system_dept (name, parent_id, sort, status, creator, create_time, deleted, tenant_id)
VALUES
 ('客户服务部', 50077, 30, 0, @creator, @now, 0, @tenant_id),
 ('运营部',     50077, 40, 0, @creator, @now, 0, @tenant_id);

SET @dept_cs   = (SELECT id FROM system_dept WHERE tenant_id = @tenant_id AND name = '客户服务部' AND deleted = 0);
SET @dept_ops  = (SELECT id FROM system_dept WHERE tenant_id = @tenant_id AND name = '运营部'      AND deleted = 0);

-- ----------------------------------------------------------------------------
-- 2) 岗位初始化
-- ----------------------------------------------------------------------------
INSERT INTO system_post (code, name, sort, status, creator, create_time, deleted, tenant_id) VALUES
 ('gubang_sales_manager',     '销售经理',   1, 0, @creator, @now, 0, @tenant_id),
 ('gubang_sales_rep',         '销售业务员', 2, 0, @creator, @now, 0, @tenant_id),
 ('gubang_customer_service',  '客服专员',   3, 0, @creator, @now, 0, @tenant_id),
 ('gubang_operations',        '运营专员',   4, 0, @creator, @now, 0, @tenant_id),
 ('gubang_dept_manager',      '部门主管',   5, 0, @creator, @now, 0, @tenant_id);

SET @post_sales_manager    = (SELECT id FROM system_post WHERE tenant_id = @tenant_id AND code = 'gubang_sales_manager'    AND deleted = 0);
SET @post_sales_rep        = (SELECT id FROM system_post WHERE tenant_id = @tenant_id AND code = 'gubang_sales_rep'        AND deleted = 0);
SET @post_cs               = (SELECT id FROM system_post WHERE tenant_id = @tenant_id AND code = 'gubang_customer_service' AND deleted = 0);
SET @post_ops              = (SELECT id FROM system_post WHERE tenant_id = @tenant_id AND code = 'gubang_operations'       AND deleted = 0);
SET @post_dept_manager     = (SELECT id FROM system_post WHERE tenant_id = @tenant_id AND code = 'gubang_dept_manager'     AND deleted = 0);

-- ----------------------------------------------------------------------------
-- 3) 业务角色创建（type=2 自定义角色，data_scope=1 全部数据权限）
-- ----------------------------------------------------------------------------
INSERT INTO system_role (name, code, sort, data_scope, status, type, creator, create_time, deleted, tenant_id) VALUES
 ('销售经理',       'sales_manager',          1, 1, 0, 2, @creator, @now, 0, @tenant_id),
 ('普通销售业务员', 'sales_rep',              2, 1, 0, 2, @creator, @now, 0, @tenant_id),
 ('客服专员',       'customer_service',       3, 1, 0, 2, @creator, @now, 0, @tenant_id),
 ('运营专员',       'operations_specialist',  4, 1, 0, 2, @creator, @now, 0, @tenant_id),
 ('部门主管',       'dept_manager',           5, 1, 0, 2, @creator, @now, 0, @tenant_id);

SET @role_sales_manager   = (SELECT id FROM system_role WHERE tenant_id = @tenant_id AND code = 'sales_manager'          AND deleted = 0);
SET @role_sales_rep       = (SELECT id FROM system_role WHERE tenant_id = @tenant_id AND code = 'sales_rep'              AND deleted = 0);
SET @role_cs              = (SELECT id FROM system_role WHERE tenant_id = @tenant_id AND code = 'customer_service'       AND deleted = 0);
SET @role_ops             = (SELECT id FROM system_role WHERE tenant_id = @tenant_id AND code = 'operations_specialist'  AND deleted = 0);
SET @role_dept_manager    = (SELECT id FROM system_role WHERE tenant_id = @tenant_id AND code = 'dept_manager'           AND deleted = 0);

-- ----------------------------------------------------------------------------
-- 4) 角色 - 菜单权限分配
--    仅开放 CRM 菜单树（根 2397 及其 80 个子节点），
--    不分配【租户管理】(1224/1138) 等任何其他菜单
-- ----------------------------------------------------------------------------
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, deleted, tenant_id)
SELECT r.role_id, m.mid, @creator, @now, 0, @tenant_id
FROM (
    SELECT @role_sales_manager AS role_id UNION ALL
    SELECT @role_sales_rep     UNION ALL
    SELECT @role_cs            UNION ALL
    SELECT @role_ops           UNION ALL
    SELECT @role_dept_manager
) r
CROSS JOIN (
    SELECT 2391 AS mid UNION ALL SELECT 2392 UNION ALL SELECT 2393 UNION ALL SELECT 2394 UNION ALL
    SELECT 2395 UNION ALL SELECT 2396 UNION ALL SELECT 2397 UNION ALL SELECT 2398 UNION ALL SELECT 2399 UNION ALL
    SELECT 2400 UNION ALL SELECT 2401 UNION ALL SELECT 2402 UNION ALL SELECT 2403 UNION ALL SELECT 2404 UNION ALL
    SELECT 2405 UNION ALL SELECT 2406 UNION ALL SELECT 2407 UNION ALL SELECT 2408 UNION ALL SELECT 2409 UNION ALL
    SELECT 2410 UNION ALL SELECT 2411 UNION ALL SELECT 2412 UNION ALL SELECT 2413 UNION ALL SELECT 2414 UNION ALL
    SELECT 2415 UNION ALL SELECT 2416 UNION ALL SELECT 2417 UNION ALL SELECT 2418 UNION ALL SELECT 2419 UNION ALL
    SELECT 2420 UNION ALL SELECT 2421 UNION ALL SELECT 2422 UNION ALL SELECT 2423 UNION ALL SELECT 2424 UNION ALL
    SELECT 2425 UNION ALL SELECT 2426 UNION ALL SELECT 2427 UNION ALL SELECT 2428 UNION ALL SELECT 2429 UNION ALL
    SELECT 2430 UNION ALL SELECT 2431 UNION ALL SELECT 2432 UNION ALL SELECT 2433 UNION ALL SELECT 2516 UNION ALL
    SELECT 2517 UNION ALL SELECT 2518 UNION ALL SELECT 2519 UNION ALL SELECT 2520 UNION ALL SELECT 2521 UNION ALL
    SELECT 2522 UNION ALL SELECT 2523 UNION ALL SELECT 2524 UNION ALL SELECT 2526 UNION ALL SELECT 2527 UNION ALL
    SELECT 2528 UNION ALL SELECT 2529 UNION ALL SELECT 2530 UNION ALL SELECT 2531 UNION ALL SELECT 2532 UNION ALL
    SELECT 2533 UNION ALL SELECT 2534 UNION ALL SELECT 2535 UNION ALL SELECT 2536 UNION ALL SELECT 2543 UNION ALL
    SELECT 2544 UNION ALL SELECT 2546 UNION ALL SELECT 2560 UNION ALL SELECT 2561 UNION ALL SELECT 2562 UNION ALL
    SELECT 2701 UNION ALL SELECT 2703 UNION ALL SELECT 2704 UNION ALL SELECT 2705 UNION ALL SELECT 2706 UNION ALL
    SELECT 2707 UNION ALL SELECT 2708 UNION ALL SELECT 2709 UNION ALL SELECT 2710 UNION ALL SELECT 2711 UNION ALL
    SELECT 2712 UNION ALL SELECT 2736 UNION ALL SELECT 2737 UNION ALL SELECT 2738 UNION ALL SELECT 2741 UNION ALL
    SELECT 2742
) m;

-- ----------------------------------------------------------------------------
-- 5) 测试用户创建
--    销售经理 / 业务员 / 部门主管 归属 市场营销部(50082)；客服专员->客户服务部；运营专员->运营部
-- ----------------------------------------------------------------------------
INSERT INTO system_users (username, password, nickname, dept_id, post_ids, email, status, creator, create_time, deleted, tenant_id) VALUES
 ('gubang_sales_manager',    @pwd_hash, '销售经理(测试)',   50082, CONCAT('[', @post_sales_manager, ']'), 'gubang_sales_manager@example.com',   0, @creator, @now, 0, @tenant_id),
 ('gubang_sales_rep',        @pwd_hash, '销售业务员(测试)', 50082, CONCAT('[', @post_sales_rep, ']'),     'gubang_sales_rep@example.com',       0, @creator, @now, 0, @tenant_id),
 ('gubang_customer_service', @pwd_hash, '客服专员(测试)',   @dept_cs,  CONCAT('[', @post_cs, ']'),         'gubang_customer_service@example.com',0, @creator, @now, 0, @tenant_id),
 ('gubang_operations',       @pwd_hash, '运营专员(测试)',   @dept_ops, CONCAT('[', @post_ops, ']'),       'gubang_operations@example.com',      0, @creator, @now, 0, @tenant_id),
 ('gubang_dept_manager',     @pwd_hash, '部门主管(测试)',   50082, CONCAT('[', @post_dept_manager, ']'), 'gubang_dept_manager@example.com',    0, @creator, @now, 0, @tenant_id);

SET @user_sales_manager   = (SELECT id FROM system_users WHERE tenant_id = @tenant_id AND username = 'gubang_sales_manager'   AND deleted = 0);
SET @user_sales_rep       = (SELECT id FROM system_users WHERE tenant_id = @tenant_id AND username = 'gubang_sales_rep'       AND deleted = 0);
SET @user_cs              = (SELECT id FROM system_users WHERE tenant_id = @tenant_id AND username = 'gubang_customer_service' AND deleted = 0);
SET @user_ops             = (SELECT id FROM system_users WHERE tenant_id = @tenant_id AND username = 'gubang_operations'      AND deleted = 0);
SET @user_dept_manager    = (SELECT id FROM system_users WHERE tenant_id = @tenant_id AND username = 'gubang_dept_manager'    AND deleted = 0);

-- 用户 - 角色 关联
INSERT INTO system_user_role (user_id, role_id, creator, create_time, deleted, tenant_id) VALUES
 (@user_sales_manager, @role_sales_manager, @creator, @now, 0, @tenant_id),
 (@user_sales_rep,     @role_sales_rep,     @creator, @now, 0, @tenant_id),
 (@user_cs,            @role_cs,            @creator, @now, 0, @tenant_id),
 (@user_ops,           @role_ops,           @creator, @now, 0, @tenant_id),
 (@user_dept_manager,  @role_dept_manager,  @creator, @now, 0, @tenant_id);

-- 用户 - 岗位 关联
INSERT INTO system_user_post (user_id, post_id, creator, create_time, deleted, tenant_id) VALUES
 (@user_sales_manager, @post_sales_manager, @creator, @now, 0, @tenant_id),
 (@user_sales_rep,     @post_sales_rep,     @creator, @now, 0, @tenant_id),
 (@user_cs,            @post_cs,            @creator, @now, 0, @tenant_id),
 (@user_ops,           @post_ops,           @creator, @now, 0, @tenant_id),
 (@user_dept_manager,  @post_dept_manager,  @creator, @now, 0, @tenant_id);

COMMIT;
