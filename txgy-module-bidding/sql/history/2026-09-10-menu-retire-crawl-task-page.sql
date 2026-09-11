-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-10 退役「采集任务」独立页（= 前端页标题「信息获取任务管理」）
-- 规范依据：《招投标模块开发规范》4.4（菜单/权限码与 sql/ 脚本同步）；
--   用户口径 2026-09-11：「信息获取任务管理其实就是爬虫管理，改个名就行，把信息获取任务管理删了」。
-- 业务目标：该独立页（bidding/crawlExternal/task/index）与工作台 6738 的「任务」页签功能重复，
--   前端页面文件已删除；菜单 6739 必须同步退役，否则侧边栏点进去 404。
--
-- ⚠️ 为什么不是直接删 6739：工作台「任务」页签调 /bidding/crawl-task/page、任务详情调 /crawl-task/get，
--   后端 @PreAuthorize 要求 bidding:crawl-task:query；而该权限正是由菜单 6739 承载的
--   （重试按钮 6744 承载 bidding:crawl-task:update）。直接删菜单会连带回收角色授权 → 工作台任务页签 403。
--   因此把 6739 **由菜单(type=2) 转为按钮(type=3)** 并挂到工作台 6738 下：id 不变 → system_role_menu 授权行继续有效，
--   侧边栏不再出现该页（页面已删），任务查询权限完整保留。
--
-- 幂等：目标态 UPDATE / 逻辑删除，可重复执行。
-- ============================================================================

-- ① 6739「采集任务」→ 工作台下的按钮「任务查询」（保留 permission=bidding:crawl-task:query）
UPDATE system_menu
   SET name = '任务查询', type = 3, sort = 4, parent_id = 6738,
       path = '', icon = '', component = '', component_name = '',
       updater = '1', update_time = NOW()
 WHERE id = 6739
   AND deleted = b'0';

-- ② 6744「采集任务重试」由 6739 重挂到工作台 6738（permission=bidding:crawl-task:update 不变）
UPDATE system_menu
   SET parent_id = 6738, sort = 5, updater = '1', update_time = NOW()
 WHERE id = 6744
   AND deleted = b'0';

-- ③ 6745「爬取任务查询」与 ① 的权限重复（同为 bidding:crawl-task:query）→ 逻辑删除并清理角色绑定
UPDATE system_menu
   SET deleted = b'1', updater = '1', update_time = NOW()
 WHERE id = 6745
   AND deleted = b'0';
DELETE FROM system_role_menu WHERE menu_id = 6745;

-- ========== 校验 ==========
-- 期望①：6739 为 type=3、parent_id=6738、permission=bidding:crawl-task:query、component 为空
SELECT id, name, permission, type, sort, parent_id, path, component, component_name, deleted
  FROM system_menu WHERE id IN (6739, 6744, 6745) ORDER BY id;

-- 期望②：工作台 6738 下按钮 = 6741 采集源新增 / 6742 采集源修改 / 6743 采集源删除 / 6739 任务查询 / 6744 采集任务重试
SELECT id, name, permission, type, sort, parent_id
  FROM system_menu WHERE parent_id = 6738 AND deleted = b'0' ORDER BY sort, id;

-- 期望③：目录 6737 下只剩「爬取任务工作台 6738」「采集批次 6740」两个页面菜单
SELECT id, name, type, path, component, component_name
  FROM system_menu WHERE parent_id = 6737 AND deleted = b'0' ORDER BY sort, id;

-- 期望④：仍持有 crawl-task 权限的角色数（迁移前后应不变）
SELECT m.permission, COUNT(rm.role_id) AS role_cnt
  FROM system_menu m LEFT JOIN system_role_menu rm ON rm.menu_id = m.id
 WHERE m.permission IN ('bidding:crawl-task:query', 'bidding:crawl-task:update')
   AND m.deleted = b'0'
 GROUP BY m.permission;
