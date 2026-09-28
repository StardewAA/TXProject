-- 污染源在线监控看板：菜单与授权（2026-09-21）
-- 菜单 id 段 34720（34720 看板，34721 查询 / 34722 派单 / 34723 闭环）。
--   挂在 34012「排放监测」下（排放口/废气/废水/碳排放同一段），sort 5 —— 它读的就是这几张读数表。
--   实时监控大屏不另开菜单：它是同一个页面里的整屏 Modal，同一份数据、同一批渲染，
--   单开一个路由只会多出一个"点进去和看板长得一样"的入口。
-- 权限只设三个动作：查询 / 派单 / 闭环。看板本身是只读聚合，没有增删改，
--   不为了凑齐五个按钮造出后端根本不校验的权限。
--
-- 授权面（按各租户「排放监测」现有授权对齐，不扩大）：
--   2010 华瀚基地  60185 企业超级管理员、60466 环保专员
--   2025 天信管业  61000~61008 全部角色（该租户的环保菜单本就整段授权）
--
-- 幂等：先按 id 段删除再插入。
-- ⚠️ system_role_menu 必须带 tenant_id。漏了会落成默认 0，而租户插件按租户号过滤，
--    结果是「授权行明明在库里、用户却看不到菜单、接口 403」。
--    删除按 id 段全租户删：34720~34723 只属于本菜单，这样才能顺带修掉漏写 tenant_id 的行。
--
-- ⚠️ 菜单/角色改动生效三步，缺一步就得到「库里明明有、接口却 403」的假象：
--    1) 重启 system-server（权限判定的正主）；
--    2) 重启 mes-server（它有一层 1 分钟的 Guava hasAnyPermissions 缓存）；
--    3) 删掉 Redis 里的旧判定结果：`DEL menu_role_ids:<tenantId>:<menuId>`。
--    第 3 步最关键也最容易漏：改动前若有人访问过该菜单，角色未授权时的**空集**已被 @Cacheable
--    缓存进 Redis，重启 JVM 也带不走它。本次要删的是 34720~34723 × {2010, 2025} 共 8 个键。

SET NAMES utf8mb4;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 34720 AND 34723;
DELETE FROM system_menu WHERE id BETWEEN 34720 AND 34723;

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater)
VALUES (34720,'在线监控看板','',2,5,34012,'monitor-board','mes/safetyEnv/monitorBoard/index','MesSetMonitorBoard',0,b'1',b'0',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater)
VALUES (34721,'查询','mes:set-monitor:query',3,1,34720,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater)
VALUES (34722,'派单','mes:set-monitor:dispatch',3,2,34720,'',0,b'1',b'1',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater)
VALUES (34723,'闭环','mes:set-monitor:close',3,3,34720,'',0,b'1',b'1',b'1','1','1');

-- 授权 2010：60185 企业超级管理员
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id)
SELECT 60185, id, '1', '1', 2010 FROM system_menu WHERE id BETWEEN 34720 AND 34723;

-- 授权 2010：60466 环保专员
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id)
SELECT 60466, id, '1', '1', 2010 FROM system_menu WHERE id BETWEEN 34720 AND 34723;

-- 授权 2025：61000~61008 全部角色
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id)
SELECT r.id, m.id, '1', '1', 2025
FROM system_role r, system_menu m
WHERE r.tenant_id = 2025 AND r.id BETWEEN 61000 AND 61008 AND m.id BETWEEN 34720 AND 34723;

-- 复核：应为 4 条菜单、2010 8 行授权、2025 36 行授权
SELECT '菜单' k, COUNT(*) v FROM system_menu WHERE id BETWEEN 34720 AND 34723
UNION ALL SELECT '2010授权', COUNT(*) FROM system_role_menu WHERE menu_id BETWEEN 34720 AND 34723 AND tenant_id = 2010
UNION ALL SELECT '2025授权', COUNT(*) FROM system_role_menu WHERE menu_id BETWEEN 34720 AND 34723 AND tenant_id = 2025;
