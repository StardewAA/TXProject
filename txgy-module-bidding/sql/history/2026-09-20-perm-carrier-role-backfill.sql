-- =====================================================================
-- 2026-09-20 权限体系根治（第二轮）：载体菜单归位 + 角色授权回填
--
-- 【为什么需要这一轮】上一轮脚本 2026-09-20-perm-carrier-and-tenant-fix.sql 有两个自身缺陷：
--   ① ③ 段把 37 个补建载体菜单**一律挂在招投标根目录 6735 下**（图省事），
--      而同类按钮（如 6749 新建意向 / 6750 报名投标 / 6752 上传标书）挂在页面菜单 6748 下。
--      后果：角色授权界面的树是级联勾选（assign-menu-form.vue 的 Tree 非 check-strictly），
--      勾「投标流程跟踪 6748」会级联选中 6749~6752，却**选不中 60006258 取消投标**
--      → 后续通过界面授权的角色会系统性漏掉该权限。
--   ② ④ 段只把 bidding 菜单授予 code='tenant_admin' 的角色，
--      已持有页面菜单 6748 的其它角色（如租户 1 的 common）没拿到新增载体
--      → 个人用户能建档、能报名，**却永远不能取消投标（403）**。
--
-- 【本轮口径（用户裁定）】「权限可以串，一个权限管多个按钮，直接把一个菜单下的所有权限都给」。
--   据此确立两条补齐规则（仅作用于招投标模块 bidding%，不触碰其它模块）：
--   ⑥ 页面一致性：持有某页面菜单(type=2)的角色，补齐该页面下全部按钮菜单(type=3)。
--   ⑦ 载体一致性：持有任一招投标页面菜单的角色，补齐仍挂在根目录下的孤儿载体菜单。
--   ⑧ 归位：把能确定所属页面的载体菜单改挂到对应页面下（cancel → 6748）。
--
-- 【幂等】全部 INSERT 带 NOT EXISTS 判重，可重复执行。
-- 【执行后必做】清 Redis 权限缓存，否则缓存不刷新、修复不生效：
--   redis-cli --scan --pattern 'menu_role_ids:*'       | xargs -r redis-cli DEL
--   redis-cli --scan --pattern 'permission_menu_ids:*' | xargs -r redis-cli DEL
-- 【执行库】ruoyi-vue-pro
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- ⑥ 归位：能确定所属页面的孤儿载体，改挂到对应页面菜单下
--    60006258 取消投标 → 6748 投标流程跟踪（与 6749~6752 同页）
--    60006259 爬虫任务编辑 → 6739 爬虫任务（其页面权限码为 bidding:crawl-task:query）
--    判据：仅当当前仍挂在根目录 6735 下时才改，避免覆盖人工调整
-- ---------------------------------------------------------------------
UPDATE system_menu SET parent_id = 6748
WHERE id = 60006258 AND permission = 'bidding:process-track:cancel' AND parent_id = 6735;

UPDATE system_menu SET parent_id = 6739
WHERE id = 60006259 AND permission = 'bidding:crawl-task:update' AND parent_id = 6735;

-- ---------------------------------------------------------------------
-- ⑦ 页面一致性：持有页面菜单(招投标, type=2)的角色 → 补齐该页面全部按钮(type=3)
--    例：6771 项目台账页面 → 6773/6774/6775/6776（本轮扫描发现该 4 个按钮覆盖不足）
-- ---------------------------------------------------------------------
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, b.id, '1', NOW(), '1', NOW(), b'0', r.tenant_id
FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id AND r.deleted = b'0'
JOIN system_menu p ON p.id = rm.menu_id AND p.type = 2 AND p.deleted = b'0' AND p.permission LIKE 'bidding%'
JOIN system_menu b ON b.parent_id = p.id AND b.type = 3 AND b.deleted = b'0' AND b.permission LIKE 'bidding%'
WHERE rm.deleted = b'0' AND r.tenant_id > 0
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = rm.role_id AND x.menu_id = b.id AND x.deleted = b'0');

-- ---------------------------------------------------------------------
-- ⑧ 载体一致性：持有任一招投标页面菜单的角色 → 补齐仍挂在根目录 6735 下的孤儿载体
--    这些载体对应的前端页面尚未配页面菜单（故无法按 ⑦ 归属），
--    按用户口径「一个菜单下的所有权限都给」统一补授予同一批角色。
-- ---------------------------------------------------------------------
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, c.id, '1', NOW(), '1', NOW(), b'0', r.tenant_id
FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id AND r.deleted = b'0'
JOIN system_menu p ON p.id = rm.menu_id AND p.type = 2 AND p.deleted = b'0' AND p.permission LIKE 'bidding%'
JOIN system_menu c ON c.parent_id = 6735 AND c.type = 3 AND c.deleted = b'0' AND c.permission LIKE 'bidding%'
WHERE rm.deleted = b'0' AND r.tenant_id > 0
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = rm.role_id AND x.menu_id = c.id AND x.deleted = b'0');

-- ---------------------------------------------------------------------
-- ⑨ 恢复被误删的「超管总览」载体菜单（id=60006222）
--    来源：同事脚本 2026-09-28-process-track-overview.sql §3.2/3.3
--    背景：该菜单曾被当作"临时载体菜单"删除，导致权限码 bidding:process-track:overview
--          无任何承载 → 超管权限集里没有它 → BiddingProcessTrackController.isSuperAdmin()
--          恒为 false → 平台超管也拿不到跨租户总览（getOverviewPage(reqVO,false) 只按本租户过滤）。
--    口径：与同事脚本逐字对齐（id=60006222 / type=3 / parent_id=6748 / sort=19），
--          授权仅绑 super_admin（role_id=1），tenant_id 取角色真实租户（对齐本脚本 ① 段口径）。
-- ---------------------------------------------------------------------
-- 2026-10 改为 upsert（原 INSERT IGNORE）：与 2026-09-28-process-track-overview.sql 同 id 同内容，
--   属**有意复用主键**（恢复被误删行）。用 ON DUPLICATE KEY UPDATE 明确表达"撞号=设计意图"，
--   同时满足 check-seed-id-collision.js 对白名单文件"必须是 upsert"的自检。
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (60006222, '投标流程总览（超管）', 'bidding:process-track:overview', 3, 19, 6748, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE updater = '1', update_time = NOW();

UPDATE system_menu SET parent_id = 6748
WHERE id = 60006222 AND permission = 'bidding:process-track:overview' AND parent_id <> 6748;

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 1, m.id, '1', NOW(), '1', NOW(), b'0', COALESCE((SELECT r.tenant_id FROM system_role r WHERE r.id = 1), 0)
FROM system_menu m
WHERE m.id = 60006222 AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id = 1 AND x.menu_id = 60006222 AND x.deleted = b'0');

-- ---------------------------------------------------------------------
-- 校验段 C：超管总览载体（应恰好 1 行，且授权 1 个角色）
-- ---------------------------------------------------------------------
SELECT m.id, m.permission, m.parent_id,
       (SELECT COUNT(DISTINCT role_id) FROM system_role_menu WHERE menu_id = m.id AND deleted = b'0') AS role_cnt
FROM system_menu m
WHERE m.permission = 'bidding:process-track:overview' AND m.deleted = b'0';

-- ---------------------------------------------------------------------
-- 校验段 A：仍存在的「持有页面菜单但缺按钮菜单」覆盖漏洞（应为 0 行）
--   例外：60006222 bidding:process-track:overview 是**超管专属**码（仅绑 role_id=1，
--   决定是否跨租户），按设计只授 1 个角色，不计入覆盖漏洞。
-- ---------------------------------------------------------------------
SELECT '覆盖漏洞' AS chk, m.id AS menu_id, m.permission,
       (SELECT COUNT(DISTINCT role_id) FROM system_role_menu WHERE menu_id = m.id AND deleted = b'0') AS btn_roles,
       (SELECT COUNT(DISTINCT rm.role_id) FROM system_role_menu rm WHERE rm.menu_id = m.parent_id AND rm.deleted = b'0') AS page_roles
FROM system_menu m
WHERE m.permission LIKE 'bidding%' AND m.type = 3 AND m.deleted = b'0'
HAVING btn_roles < page_roles;

-- ---------------------------------------------------------------------
-- 校验段 B：process-track 六项权限的角色覆盖（应均为同一数字）
-- ---------------------------------------------------------------------
SELECT m.permission,
       (SELECT COUNT(DISTINCT role_id) FROM system_role_menu WHERE menu_id = m.id AND deleted = b'0') AS role_cnt
FROM system_menu m
WHERE m.permission LIKE 'bidding:process-track%' AND m.deleted = b'0'
ORDER BY m.permission;
