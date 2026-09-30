-- ============================================================================
-- 个人用户（保留租户 0）的门户关注权限（2026-09-21）
--
-- 现象：个人用户在门户点「＋跟踪该项目 / 关注」→「跟踪：租户不存在（浏览不受影响）」。
--
-- 实测拆出**两个独立问题**（2026-09-21，活服务实测）：
--   ① 【部署/框架版本】bidding-server 仍未含保留租户 0 的豁免：
--        个人登录 loginType=personal + tenant-id:0        → code=0 ✅（system 服务）
--        GET /system/auth/get-permission-info（tid=0）    → code=0 ✅
--        GET /bidding/ca/seal/check（tid=0）              → code=1002015000「租户不存在」❌
--        GET /bidding/notice-follow/status（tid=0）       → 同上 ❌ ← 用户看到的
--      豁免（TenantSecurityWebFilter.PERSONAL_TENANT_ID=0，跳过 system_tenant 存在性校验）
--      由提交 90ed16bc7 引入，system 服务已含、bidding 服务没有 ⇒ **需按当前框架重新构建并重启 bidding-server**。
--   ② 【权限模型】即使 ① 修好，个人用户仍拿不到权限码：
--        system_role WHERE tenant_id=0 → **0 行**（保留租户 0 一个角色都没有）；
--        AdminUserServiceImpl.registerUser 也**不分配角色**；
--        而门户关注域要求 bidding:notice-external:query / :update ⇒ 必 403。
--
-- 本脚本解决 ②（幂等，可重复执行）：
--   1) 在租户 0 建「个人门户用户」角色（code=personal_portal，id=70004）；
--   2) 授予三个菜单：**父目录 6735「招投标」** + 两个承载菜单
--      60006260（bidding:notice-external:query）、6736（bidding:notice-external:update，
--      与「外部公告审核管理」共用同一码 —— 见 §34 的后端请求）；
--      ⚠️ 父目录**必须一起授**：MenuServiceImpl.filterDisableMenus 的 isMenuDisabled() 会沿 parentId
--      向上递归，**父菜单不在该角色的菜单集合里就判定为"禁用"并剔除该菜单**（parent == null ⇒ true）。
--      实测教训：只授两个叶子按钮时 get-permission-info 返回空权限码（角色有、码为 0），补上 6735 后正常。
--   3) 把现有全部个人用户（tenant_id=0 且未删）绑到该角色；
--   4) 自检输出。
--
-- ⚠️ 执行后必须清 Redis：menu_role_ids:* / permission_menu_ids:*
--   docker exec -i txgy-redis redis-cli --scan --pattern 'menu_role_ids:*' | xargs -r docker exec -i txgy-redis redis-cli DEL
--   docker exec -i txgy-redis redis-cli --scan --pattern 'permission_menu_ids:*' | xargs -r docker exec -i txgy-redis redis-cli DEL
--
-- ⚠️ 覆盖范围说明：本脚本只把**存量**个人用户绑上；新注册的个人用户仍没有角色
--   （注册流程不分配角色）。彻底解法是后端把 notice-follow 的关注类端点改为 isAuthenticated()，
--   或注册时自动绑定本角色 —— 见 docs/电子证照前前端契约/后端变更通知-给前端.md §34 与 §35。
-- ============================================================================

SET @personal_role_id := 70004;

-- 1) 个人门户用户角色（租户 0）
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark,
                         creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @personal_role_id, '个人门户用户', 'personal_portal', 1, 5, '', 0, 2,
       '保留租户 0 的个人用户（门户自助：关注/跟踪公告）；由 sql/history/2026-09-21-个人租户关注权限.sql 建立',
       '1', NOW(), '1', NOW(), b'0', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_role WHERE code = 'personal_portal' AND tenant_id = 0) t);

-- 2) 授予 父目录 6735 + 两个承载菜单（60006260=query、6736=update），tenant_id 必须与角色同为 0
--    （父目录不能省：filterDisableMenus 会把"父菜单不在集合里"的菜单当禁用剔除）
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT @personal_role_id, m.menu_id, 0, '1', NOW(), '1', NOW(), b'0'
FROM (SELECT 6735 AS menu_id UNION ALL SELECT 60006260 UNION ALL SELECT 6736) m
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT menu_id FROM system_role_menu WHERE role_id = @personal_role_id) x
    WHERE x.menu_id = m.menu_id
);

-- 3) 存量个人用户绑定该角色
INSERT INTO system_user_role (user_id, role_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT u.id, @personal_role_id, 0, '1', NOW(), '1', NOW(), b'0'
FROM system_users u
WHERE u.tenant_id = 0 AND u.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT user_id FROM system_user_role WHERE role_id = @personal_role_id) x
      WHERE x.user_id = u.id
  );

-- 4) 自检
SELECT '① 个人门户角色' AS item, COUNT(*) AS cnt, CASE WHEN COUNT(*) = 1 THEN 'OK' ELSE 'FAIL' END AS verdict
FROM system_role WHERE code = 'personal_portal' AND tenant_id = 0 AND deleted = b'0';
SELECT '② 该角色菜单数（应为 3 = 父目录 6735 + 两个承载）' AS item, COUNT(*) AS cnt
FROM system_role_menu WHERE role_id = @personal_role_id
UNION ALL
SELECT '③ 已绑定个人用户数', COUNT(*) FROM system_user_role WHERE role_id = @personal_role_id
UNION ALL
SELECT '④ 其中 tenant_id≠0 的错绑（应为 0）', COUNT(*) FROM system_user_role WHERE role_id = @personal_role_id AND tenant_id <> 0;
