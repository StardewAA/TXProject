-- 回滚 envboard-merge-20260921-menu.sql（2026-09-21）
-- 把 2026-09-21 那次「环保看板改造」对 system_menu / system_role_menu 的改动逐条反做。
-- 依据：
--   34700 的原始定义取自同目录 envboard-20260910-menu.sql，其中 parent_id/sort 由
--     safetyEnv-menu-merge-20260910.sql:53 改成 (34014, 3) —— 这里直接写终值。
--   34700 原来的 8 行授权取自改造前对 system_role_menu 的实际查询（2010: 60185/60466；
--     2025: 61000/61003/61004/61006/61007/61008）。system_role_menu.id 是自增，重建行会拿到新 id，
--     这不影响判定（判定只看 role_id + menu_id + tenant_id）。
--   34720 当初只被改了 name（path/component/component_name/sort/status/visible 都没动），故只改回 name。
--   34701 的授权行从未被改动，只需改回 parent_id。
--   11 个顶层菜单当初被 visible=0 隐藏，全部恢复 visible=1。
-- 幂等：34700 与它的授权行都先按 menu_id 删再插。
-- 生效三步：重启 system-server → 重启 mes-server（1 分钟 Guava 权限缓存）→ Redis 权限缓存
--   （key 形如 menu_role_ids:<menuId>，无租户段；为空则说明从未写过，无需清）。

SET NAMES utf8mb4;

-- 1. 恢复 34700「环保看板」菜单行本身
DELETE FROM system_role_menu WHERE menu_id = 34700;
DELETE FROM system_menu WHERE id = 34700;
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater)
VALUES (34700,'环保看板','',2,3,34014,'env-board','mes/safetyEnv/envBoard/index','MesSetEnvBoard',0,b'1',b'0',b'1','1','1');

-- 2. 恢复 34700 的 8 行授权
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id) VALUES
 (60185,34700,'1','1',2010),
 (60466,34700,'1','1',2010),
 (61000,34700,'1','1',2025),
 (61003,34700,'1','1',2025),
 (61004,34700,'1','1',2025),
 (61006,34700,'1','1',2025),
 (61007,34700,'1','1',2025),
 (61008,34700,'1','1',2025);

-- 3. 34701「查询」按钮改挂回 34700 下（授权行不动）
UPDATE system_menu SET parent_id = 34700, updater = '1', update_time = NOW() WHERE id = 34701;

-- 4. 34720 名字改回「在线监控看板」
UPDATE system_menu SET name = '在线监控看板', updater = '1', update_time = NOW() WHERE id = 34720;

-- 5. 11 个顶层菜单恢复可见
UPDATE system_menu SET visible = b'1', updater = '1', update_time = NOW()
WHERE id IN (1117, 1185, 1281, 2084, 2262, 2362, 2397, 2563, 2758, 6400, 6500);

-- 复核：依次应为 1 / 8 / 1 / 1 / 14 / 0 / 14
SELECT '34700恢复且挂34014'    AS k, COUNT(*) AS v FROM system_menu WHERE id = 34700 AND parent_id = 34014
UNION ALL SELECT '34700授权恢复8行',   COUNT(*) FROM system_role_menu WHERE menu_id = 34700
UNION ALL SELECT '34701挂回34700下',   COUNT(*) FROM system_menu WHERE id = 34701 AND parent_id = 34700
UNION ALL SELECT '34720名回到在线监控看板', COUNT(*) FROM system_menu WHERE id = 34720 AND name = '在线监控看板'
UNION ALL SELECT '顶层可见应14个',     COUNT(*) FROM system_menu WHERE parent_id = 0 AND visible = b'1' AND deleted = 0
UNION ALL SELECT '顶层隐藏应0个',      COUNT(*) FROM system_menu WHERE parent_id = 0 AND visible = b'0' AND deleted = 0
UNION ALL SELECT '顶层合计应14个',     COUNT(*) FROM system_menu WHERE parent_id = 0 AND deleted = 0;
