-- ============================================================================
-- 门户「关注 / 跟踪公告」权限承载修复（2026-09-21）
--
-- 现象：门户公告详情页「＋关注 / 跟踪该项目」报
--   「无操作权限（需要 bidding:notice-external:query/update），请联系管理员开通」，
--   关注按钮恒显示「需登录」——任何普通租户用户都复现。
--
-- 实测根因（本机库 ruoyi-vue-pro，2026-09-21，**以实测为准**）：
--   ① bidding:notice-external:query 在整个 system_menu 里**没有任何菜单承载**（只有 :update 有
--      6736「外部公告审核管理」）。本框架的权限码只能经菜单授予（system_role_menu），
--      没有承载菜单 ⇒ 任何角色都授不了 ⇒ 除 super_admin（绕过校验）外全员 403。
--   ② 后端把"查不到承载菜单"的结果也缓存了 —— Redis 里已有
--      permission_menu_ids:bidding:notice-external:query = ["java.util.ArrayList",[]]，
--      **只补菜单不清缓存仍然 403**。
--   ③ 症状因此不对称（实测 songyonglin / 角色 common / 租户 1）：
--        POST /bidding/notice-follow/follow    → code=0          （:update 有承载 6736）
--        GET  /bidding/notice-follow/status    → code=403 没有该操作权限
--        GET  /bidding/notice-follow/item/page → code=403 没有该操作权限
--
-- 本脚本（幂等，可重复执行）：
--   1) 补承载菜单 60006260「外部公告关注查询」= bidding:notice-external:query
--      （type=3 按钮，挂在 6735「招投标」下；字段照抄既有承载 60006223「公告查询」）；
--   2) 把新承载授予**所有已持有 :update 承载（6736）的角色** ——
--      "能关注却看不了自己的关注列表"本身就是错的，两者必须同批授予；
--      ⚠️ tenant_id 必须跟随来源行复制：写成 0 会让租户侧查不到授权（2026-09-20 踩过这个坑）。
--   3) 把新承载并入含 6736 的租户套餐 menu_ids（今后新建租户自动带上）；
--   4) 自检输出（承载数 / 授予角色数 / 套餐 / 是否有 tenant_id=0 的错授权）。
--
-- ⚠️ 执行后**必须清 Redis**（菜单 / 权限缓存），否则 ② 的旧缓存会继续返回 403：
--      docker exec -i txgy-redis redis-cli --scan --pattern 'permission_menu_ids:*' | xargs -r docker exec -i txgy-redis redis-cli DEL
--      docker exec -i txgy-redis redis-cli --scan --pattern 'menu_role_ids:*'         | xargs -r docker exec -i txgy-redis redis-cli DEL
--
-- 遗留（需后端改，前端/DB 侧无法两全）：
--   门户关注域现在与「外部公告审核管理」**共用** bidding:notice-external:update 这个权限码，
--   而该码的承载菜单正是审核工作台（/manage/page、/approve、/reject）。
--   ⇒ 想让"普通门户用户能关注"，就只能把审核工作台一起给出去 —— 不合理。
--   建议后端把 notice-follow 的关注类端点改为 isAuthenticated()（门户自助能力），
--   或拆出独立门户权限码并默认授予。详见
--   docs/电子证照前前端契约/后端变更通知-给前端.md §34。
-- ============================================================================

SET @menu_id := 60006260;

-- 1) 承载菜单（幂等：按 permission 判重，system_menu 无 permission 唯一索引，故显式 NOT EXISTS）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @menu_id, '外部公告关注查询', 'bidding:notice-external:query', 3, 100, 6735, '', '', NULL, NULL,
       0, b'0', b'0', b'0', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM system_menu WHERE permission = 'bidding:notice-external:query') t
);

-- 2) 授予：与 :update 承载（6736）同批角色，tenant_id 跟随来源行
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT DISTINCT rm.role_id, @menu_id, rm.tenant_id, '1', NOW(), '1', NOW(), b'0'
FROM system_role_menu rm
WHERE rm.menu_id = 6736
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT role_id FROM system_role_menu WHERE menu_id = @menu_id) x
      WHERE x.role_id = rm.role_id
  );

-- 3) 租户套餐：含 6736 的并入新承载（menu_ids 为 text 的 JSON 数组，末尾必是 ']'）
UPDATE system_tenant_package
SET menu_ids    = CONCAT(LEFT(menu_ids, CHAR_LENGTH(menu_ids) - 1), ',', @menu_id, ']'),
    update_time = NOW()
WHERE id > 0
  AND menu_ids LIKE '%6736%'
  AND menu_ids NOT LIKE CONCAT('%', @menu_id, '%');

-- 4) 自检
SELECT '① 承载菜单数（应为 1）' AS item, COUNT(*) AS cnt,
       CASE WHEN COUNT(*) = 1 THEN 'OK' ELSE 'FAIL' END AS verdict
FROM system_menu WHERE permission = 'bidding:notice-external:query' AND deleted = b'0';
SELECT '② 新承载已授角色数' AS item, COUNT(DISTINCT role_id) AS cnt FROM system_role_menu WHERE menu_id = @menu_id
UNION ALL
SELECT '②b 对照：:update(6736) 已授角色数', COUNT(DISTINCT role_id) FROM system_role_menu WHERE menu_id = 6736;
SELECT '③ 含新承载的套餐' AS item, GROUP_CONCAT(id) AS cnt FROM system_tenant_package WHERE menu_ids LIKE CONCAT('%', @menu_id, '%');
SELECT '④ 新承载里 tenant_id=0 的错授权（应为 0）' AS item, COUNT(*) AS cnt FROM system_role_menu WHERE menu_id = @menu_id AND tenant_id = 0;
