-- =====================================================================
-- 2026-09-28 电子证照域菜单授权（补《招投标模块现状扫描报告-2026-09-20》P0-1 缺口）
--
-- 报告原文：「60005100~60005650 段 42 个菜单仅 1 个有角色授权（页面父菜单 60005100/200/300/400/500 全零授权）
--            → 非超管角色菜单不可见、权限码 403」。经本机库复核：域根 60005000 与 5 个页面菜单 system_role_menu 均为空，
--            确认属实（授权行数 2，且不在本域页面树上）。
-- 口径（依据）：电子证照域是**投标人/企业侧**能力，租户自有角色应可见；
--           故授予租户 1 的「超级管理员(role_id=1)」与「普通角色(role_id=2)」；
--           测试角色 70001 招标方 / 70002 平台方 / 70003 政府审批方属外部视角，本脚本不授予（如需，由产品决定后另加）。
-- 幂等：INSERT ... SELECT ... WHERE NOT EXISTS（本表无业务唯一键，故用存在性判重，不依赖 IGNORE）。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

-- ① 域根 + 5 个页面菜单 → 角色 1、2
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.role_id, m.id, '1', NOW(), '1', NOW(), b'0', 0
FROM system_menu m
CROSS JOIN (SELECT 1 AS role_id UNION ALL SELECT 2) r
WHERE m.id IN (60005000, 60005100, 60005200, 60005300, 60005400, 60005500)
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = r.role_id AND x.menu_id = m.id AND x.deleted = b'0');

-- ② 域内其余子菜单（按钮/页面，含本次新增的 60005106 / 60005205-206 / 60005310 / 60005404 / 60005509-512）→ 角色 1、2
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.role_id, m.id, '1', NOW(), '1', NOW(), b'0', 0
FROM system_menu m
CROSS JOIN (SELECT 1 AS role_id UNION ALL SELECT 2) r
WHERE (m.id IN (60005106, 60005205, 60005206, 60005310, 60005404, 60005509, 60005510, 60005511, 60005512)
       OR m.parent_id BETWEEN 60005000 AND 60005650)
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = r.role_id AND x.menu_id = m.id AND x.deleted = b'0');

-- ③ 跟随「父菜单已授权」的角色（整树授权先例）：本域将来授给更多角色时，新按钮不会漏授
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT rm.role_id, m.id, '1', NOW(), '1', NOW(), b'0', 0
FROM system_menu m
CROSS JOIN (SELECT DISTINCT role_id FROM system_role_menu
            WHERE menu_id IN (60005000, 60005100, 60005200, 60005300, 60005400, 60005500) AND deleted = b'0') rm
WHERE m.id IN (60005106, 60005205, 60005206, 60005310, 60005404, 60005509, 60005510, 60005511, 60005512)
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = rm.role_id AND x.menu_id = m.id AND x.deleted = b'0');

-- 校验：应 ≥ 2*43（域内菜单 43 个 × 2 角色，去重后略多/少视历史授权）
SELECT COUNT(*) AS credential_grants FROM system_role_menu WHERE menu_id BETWEEN 60005000 AND 60005650;
