-- 环保看板并到在线监控看板 + 隐藏无关顶层菜单（2026-09-21）
--
-- 背景：库里有两个看板菜单——
--   34700「环保看板」 挂 34014「治污设施与报告」，指向 mes/safetyEnv/envBoard/index
--                     （许可余量 / 证载有效期 / 治污设施 / 危废暂存 / 联单 / 应急物资）
--   34720「在线监控看板」挂 34012「排放监测」，指向 mes/safetyEnv/monitorBoard/index
--                     （污染源在线监控日监管板 + 实时监控大屏）
-- 现要求「环保看板」就是后者。两者合一，留下 34720，前端 envBoard 页面与其 api 客户端已删除。
--
-- 为什么留 34720 而不是留 34700 —— 已查全量授权，结论是留 34720 才无损：
--   34720~34723 授权 = 2010{60185,60466} + 2025{61000~61008}
--   34700/34701 授权 = 2010{60185,60466} + 2025{61000,61003,61004,61006,61007,61008}
--   即「凡是拿到 34700 的角色，都同时拿到了 34720 及三个按钮」。
--   反过来做（留 34700）会让 61001/61002/61005 三个角色连看板带派单/闭环权限一起丢掉。
--
-- 34701 不删、改挂到 34720 下：它的权限是 `mes:set-env-board:query`，而
--   /mes/safety-env/env-board/summary 这个聚合接口仍在跑（envReport、排污许可合规判定
--   都依赖同一批表）。删掉它 = 所有角色失去该权限 → 接口全员 403，
--   e2e/run_e2e_env_board.py 的接口用例由 49 passed 直接转红。
--   按钮（type=3）本来就不渲染在侧边栏，留着的代价只是菜单管理页里多一个勾选项。
--
-- ⚠️ 隐藏菜单用 visible，不是 status：前端从不读 status（guard.ts / store/auth.ts /
--    generate-menus.ts / generate-routes-backend.ts 全无引用），
--    visible=0 才会让 generate-menus 的 hideInMenu 生效、filterTree 连整棵子树一起摘掉。
-- ⚠️ system_menu 是全局表（没有 tenant_id 列），第 4 步对**所有租户**生效，这正是想要的。
-- ⚠️ system_role_menu 必须带 tenant_id。本文件不新增授权行，只删 34700 的旧行。
--
-- 生效三步，缺一步就得到「库里明明有、接口却 403」的假象：
--   1) 重启 system-server（权限判定的正主）
--   2) 重启 mes-server（它有一层 1 分钟的 Guava hasAnyPermissions 缓存）
--   3) 删掉 Redis 里的旧判定结果：`DEL menu_role_ids:<tenantId>:<menuId>`
--      本次涉及 34720~34723 × {2010, 2025} 共 8 个键。
--      第 3 步最关键也最容易漏：改动前若有人访问过该菜单，角色未授权时的**空集**
--      已被 @Cacheable 缓存进 Redis，重启 JVM 带不走它。

SET NAMES utf8mb4;

-- 1. 34720 改名为「环保看板」。component / component_name / parent_id 全不动：
--    路由 name 取自 component_name（MesSetMonitorBoard），改名不会影响任何 router.push。
UPDATE system_menu SET name = '环保看板', updater = '1', update_time = NOW() WHERE id = 34720;

-- 2. 34701 改挂到 34720 下（授权行原样保留，任何角色都不会因此掉权限）
UPDATE system_menu SET parent_id = 34720, updater = '1', update_time = NOW() WHERE id = 34701;

-- 3. 删 34700 及其授权行（先删授权再删菜单，避免留下指向不存在菜单的悬空授权）
DELETE FROM system_role_menu WHERE menu_id = 34700;
DELETE FROM system_menu WHERE id = 34700;

-- 4. 隐藏与 MES 交付无关的顶层菜单。
--    支付管理 1117 / 报表管理 1281 / 工作流程 1185 / 会员中心 2262 / 商城系统 2362 /
--    公众号管理 2084 / CRM 2397 / ERP 2563 / WMS 6400 / AI 大模型 2758 / IM 即时通讯 6500
--    保留：系统管理(1) / 基础设施(2) / MES 系统(5100)
UPDATE system_menu SET visible = b'0', updater = '1', update_time = NOW()
WHERE id IN (1117, 1185, 1281, 2084, 2262, 2362, 2397, 2563, 2758, 6400, 6500);

-- 复核：六项依次应为 1 / 0 / 1 / 8 / 11 / 3
SELECT '34720应为1且名为环保看板' AS k, COUNT(*) AS v FROM system_menu WHERE id = 34720 AND name = '环保看板'
UNION ALL SELECT '34700应为0', COUNT(*) FROM system_menu WHERE id = 34700
UNION ALL SELECT '34701应挂在34720下', COUNT(*) FROM system_menu WHERE id = 34701 AND parent_id = 34720
UNION ALL SELECT '34701授权应保留8行', COUNT(*) FROM system_role_menu WHERE menu_id = 34701
UNION ALL SELECT '已隐藏顶层应11个', COUNT(*) FROM system_menu WHERE parent_id = 0 AND visible = b'0'
UNION ALL SELECT '仍可见顶层应3个', COUNT(*) FROM system_menu WHERE parent_id = 0 AND visible = b'1';
