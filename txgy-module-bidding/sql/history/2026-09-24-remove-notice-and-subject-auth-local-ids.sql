-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-24 删除「公告/公示管理」+「主体信息与授权管理」空壳（本地库 id 版）
--
-- 背景：队友评审三条意见的 ②③ 收尾。dev 库对应脚本为
--   · history/2026-09-16-remove-notice-and-subject-auth-menus.sql（dev id 6753~6758/6762/6763）
--   · history/2026-09-20-subject-profile-relocate.sql（dev id 6760/6761 → 60005000）
--   · history/2026-09-23-remove-empty-subject-auth-dir.sql（dev id 6759 空壳目录）
--   但本地库菜单 id 与 dev 号段错位（本地 6753~6763 属 MES 项目管理按钮，**严禁在本库执行上述 dev 脚本**），
--   本地实际目标：公告模块 6783/6784/6785~6788；主体域 6789/6790/6791/6792/6793。
--
-- 关键口径（2026-09-24 实测核对）：
--   · 公告五个权限码双承载：旧菜单 6784~6788 + 新按钮 60006223~60006227（挂 6735 下）。
--     旧菜单绑定在角色 1/2/60346/60352 上，新按钮当时**零绑定**——直接删旧菜单会让公告工作台
--     （/bidding/notice/* 的 @PreAuthorize）对这 4 个角色全部 403。故必须先按权限码 1:1 转绑再删。
--   · subject-auth 两个码：6792(query)/6793(update) 删后仍有 60006300/60006301 承载，
--     沿用 dev 09-16 先例不转移角色 1/2 的绑定（模块已退役、页面文件已删）。
--   · 主体档案 6790/6791 迁入 60005000「资质与证书管理」首位（对齐 dev 09-20 归位口径）。
--   · 60006100/60006101/60006300/60006301 四行此前挂 6760（本地 6760 = MES「项目成员删除」），
--     一并归位到迁置后的主体档案页 6790 下。
--
-- 前置检查：执行前确认 0) 段输出与本注释一致（6783~6788 为公告模块、6789~6793 为主体域、
--   60006223~27 为转绑目标、60005000 为资质与证书管理目录），不一致立即中止，不得执行。
-- 幂等性：INSERT 转绑带 NOT EXISTS 防重；DELETE/UPDATE 可重复执行。
-- 回滚：script/backup/2026-09-24-menu-backup.sql（本次调整前的 system_menu/system_role_menu 快照）。
-- ============================================================================

-- 0) 前置检查（期望输出与上方注释一致）
SELECT id, name, type, parent_id, permission FROM system_menu WHERE id IN (6783,6784,6785,6786,6787,6788,6789,6790,6791,6792,6793) AND deleted=b'0' ORDER BY id;
SELECT id, name, permission, type, parent_id FROM system_menu WHERE id IN (60006223,60006224,60006225,60006226,60006227,60005000) AND deleted=b'0' ORDER BY id;

-- ========== ① 公告模块：先按权限码 1:1 转绑角色，再自底向上删除 ==========
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT rm.role_id, m2.id, rm.tenant_id, '1', NOW(), '1', NOW(), b'0'
FROM system_role_menu rm
JOIN system_menu m1 ON m1.id = rm.menu_id AND m1.deleted = b'0' AND m1.id IN (6784, 6785, 6786, 6787, 6788)
JOIN system_menu m2 ON m2.permission = m1.permission AND m2.deleted = b'0' AND m2.id IN (60006223, 60006224, 60006225, 60006226, 60006227)
WHERE rm.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.menu_id = m2.id AND x.role_id = rm.role_id AND x.deleted = b'0');

-- 删除公告模块：先角色绑定，再按钮 6785~6788，再页面 6784，再目录 6783
DELETE FROM system_role_menu WHERE menu_id IN (6785, 6786, 6787, 6788, 6784, 6783);
DELETE FROM system_menu WHERE id IN (6785, 6786, 6787, 6788) AND deleted = b'0';
DELETE FROM system_menu WHERE id = 6784 AND deleted = b'0';
DELETE FROM system_menu WHERE id = 6783 AND deleted = b'0';

-- ========== ② 主体域：删平台授权 6792/6793，迁主体档案，删空壳 6789 ==========
-- 2a) 平台授权管理（页面文件已删，模块退役；绑定角色 1/2 不转移，沿用 dev 09-16 先例）
DELETE FROM system_role_menu WHERE menu_id IN (6792, 6793);
DELETE FROM system_menu WHERE id = 6793 AND deleted = b'0';
DELETE FROM system_menu WHERE id = 6792 AND deleted = b'0';

-- 2b) 主体档案 6790 迁入「资质与证书管理」60005000 首位（sort=0），维护按钮 6791 跟随
UPDATE system_menu SET parent_id = 60005000, sort = 0, updater = '1', update_time = NOW() WHERE id = 6790 AND deleted = b'0';
UPDATE system_menu SET sort = 1, updater = '1', update_time = NOW() WHERE id = 6791 AND deleted = b'0';

-- 2c) 删空壳目录 6789（此时子项应为 0：6790/6791 已迁走，6792/6793 已删）
DELETE FROM system_role_menu WHERE menu_id = 6789;
DELETE FROM system_menu WHERE id = 6789 AND deleted = b'0';

-- 2d) 归位修复：四行此前误挂 MES 6760 的菜单改挂主体档案页 6790
UPDATE system_menu SET parent_id = 6790, updater = '1', update_time = NOW()
 WHERE id IN (60006100, 60006101, 60006300, 60006301) AND deleted = b'0';

-- ========== 校验 ==========
-- 期望①：6783~6788/6789/6792/6793 全部 0 行
SELECT COUNT(*) AS retired_notice_subject FROM system_menu WHERE id IN (6783,6784,6785,6786,6787,6788,6789,6792,6793) AND deleted = b'0';

-- 期望②：6790 主体档案挂 60005000 首位、6791 跟随
SELECT id, name, parent_id, sort, type FROM system_menu WHERE id IN (6790, 6791) AND deleted = b'0';

-- 期望③：四行归位挂 6790
SELECT id, name, parent_id FROM system_menu WHERE id IN (60006100, 60006101, 60006300, 60006301) AND deleted = b'0';

-- 期望④：招投标 6735 直挂子项不含 6783/6789，且 6771 已是一级
SELECT id, name, type, sort, parent_id FROM system_menu WHERE parent_id = 6735 AND deleted = b'0' ORDER BY sort, id;

-- 期望⑤：九个权限码仍有承载（全部 ≥1）
SELECT permission, COUNT(*) AS carriers FROM system_menu
 WHERE permission IN ('bidding:notice:query','bidding:notice:create','bidding:notice:update','bidding:notice:delete','bidding:notice:publish',
                      'bidding:subject:query','bidding:subject:update','bidding:subject-auth:query','bidding:subject-auth:update')
   AND deleted = b'0' GROUP BY permission;

-- 期望⑥：转绑后 60006223~27 各绑 4 个角色（1/2/60346/60352），且旧菜单 6784~6788 绑定清零
SELECT menu_id, COUNT(*) AS role_cnt FROM system_role_menu WHERE menu_id IN (60006223,60006224,60006225,60006226,60006227) AND deleted = b'0' GROUP BY menu_id ORDER BY menu_id;
SELECT COUNT(*) AS old_notice_bindings FROM system_role_menu WHERE menu_id IN (6784,6785,6786,6787,6788);
