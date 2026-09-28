-- 环保看板：菜单与授权（2026-09-10）
-- 菜单 id 段 34700（34700 看板，34701 查询按钮）。
--   看板是只读聚合，没有新增/修改/删除动作，故只有 query 一个按钮权限——
--   不为了"凑齐五个按钮"造出四个后端根本不校验的权限。
-- 授权：60185 企业超级管理员、60466 环保专员。看板就是环保专员的日常首页，不给它等于让人瞎干。
--       60468 危化品安全员不授：其职责在库位校验与五双签字，看板里的许可/联单/暂存与它无关。
-- 幂等：先按 id 段删除再插入。
-- ⚠️ system_role_menu 必须带 tenant_id=2010。漏了会落成默认 0，而租户插件按 2010 过滤，
--    结果是「授权行明明在库里、用户却看不到菜单、接口 403」——排查起来极费时间（已踩过一次）。
-- 删除按 id 段全租户删：34700/34701 只属于本菜单，这样才能修掉早先漏写 tenant_id 的行。
-- ⚠️ 菜单/角色改动生效三步，缺一步就得到「库里明明有、接口却 403」的假象：
--    1) 重启 system-server（权限判定的正主）；
--    2) 重启 mes-server（它有一层 1 分钟的 Guava hasAnyPermissions 缓存）；
--    3) 删掉 Redis 里的旧判定结果：`DEL menu_role_ids:<tenantId>:<menuId>`。
--    第 3 步最关键也最容易漏：`getMenuRoleIdListByMenuIdFromCache` 是 @Cacheable 到 Redis 的，
--    一旦某次判定在角色未授权时跑过，就把**空集**缓存下来了，重启 JVM 也带不走它。
--    本菜单要删的是 menu_role_ids:2010:34701。

SET NAMES utf8mb4;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 34700 AND 34701;
DELETE FROM system_menu WHERE id BETWEEN 34700 AND 34701;

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,component,component_name,status,visible,keep_alive,always_show,creator,updater) VALUES (34700,'环保看板','',2,220,34000,'env-board','mes/safetyEnv/envBoard/index','MesSetEnvBoard',0,b'1',b'0',b'1','1','1');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater) VALUES (34701,'查询','mes:set-env-board:query',3,1,34700,'',0,b'1',b'1',b'1','1','1');

-- 授权 60185（企业超级管理员）
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id)
SELECT 60185, id, '1', '1', 2010 FROM system_menu WHERE id BETWEEN 34700 AND 34701;

-- 授权 60466（环保专员）
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id)
SELECT 60466, id, '1', '1', 2010 FROM system_menu WHERE id BETWEEN 34700 AND 34701;
