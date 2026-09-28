-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-24 登记 bidding:crawl-task:query 存活承载（本地库）
--
-- 背景：CrawlTaskController 的 page/get 接口 @PreAuthorize('bidding:crawl-task:query') 仍在用，
--   但该码的两个承载 6739「任务查询」/ 6745「爬取任务查询」均为软删行 → 权限查询（deleted=0）
--   拿不到该码 → 非超管调爬取任务接口 403，且角色授权界面无法管理该权限点。
--   update 码已有存活承载 60006259（2026-09-20-perm-carrier-role-backfill.sql 登记）。
--
-- 处理：
--   ① 在 6738「爬取任务工作台」（存活页面）下登记 type=3 按钮 60006261「任务查询」，
--      承载 bidding:crawl-task:query（sort=4，紧随采集源新增/修改/删除 1/2/3）；
--   ② 把软删行 6745 上的 4 个角色绑定（60042/60021/162/161）按原 tenant 转绑到 60006261；
--   ③ 删除 6745 的死绑定行。
--
-- 号段登记（先登记再占用）：60006261 = 流程子域批次·采集域（crawl）新增登记，2026-09-24；
--   与 60006259「采集任务更新」同批紧邻。禁止再向 60006261 登记其他语义。
--
-- 幂等：INSERT IGNORE / NOT EXISTS 防重；DELETE 二次执行无副作用。
-- 执行后需重跑初始包重导（包内菜单 97 → 98）。
-- ============================================================================

-- 0) 前置检查（期望：60006261 空闲；6738 存活；6745 软删且绑 4 角色）
SELECT id, name, permission, parent_id, type, sort FROM system_menu WHERE id IN (6738, 6745, 60006261) ORDER BY id;
SELECT menu_id, GROUP_CONCAT(role_id ORDER BY role_id) AS roles FROM system_role_menu WHERE menu_id = 6745 AND deleted = b'0' GROUP BY menu_id;

-- ① 登记存活承载按钮
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (60006261, '任务查询', 'bidding:crawl-task:query', 3, 4, 6738, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 6745 的角色绑定按原 tenant 转绑到 60006261（同角色已绑则不重复）
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT rm.role_id, 60006261, rm.tenant_id, '1', NOW(), '1', NOW(), b'0'
FROM system_role_menu rm
WHERE rm.menu_id = 6745
  AND rm.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.menu_id = 60006261 AND x.role_id = rm.role_id AND x.deleted = b'0');

-- ③ 清理 6745 死绑定（菜单已软删，绑定行无意义）
DELETE FROM system_role_menu WHERE menu_id = 6745;

-- ========== 校验 ==========
-- 期望①：60006261 存在且挂 6738，承载 crawl-task:query
SELECT id, name, permission, parent_id, type, sort FROM system_menu WHERE id = 60006261 AND deleted = b'0';

-- 期望②：转绑后 60006261 绑 4 角色（60042/60021/162/161）；6745 绑定清零
SELECT menu_id, COUNT(*) AS role_cnt FROM system_role_menu WHERE menu_id IN (60006261, 6745) AND deleted = b'0' GROUP BY menu_id ORDER BY menu_id;

-- 期望③：crawl-task:query 存活承载 = 1（60006261）；update 存活承载 = 1（60006259）
SELECT permission, SUM(deleted = b'0') AS live_carriers, SUM(deleted = b'1') AS del_carriers
  FROM system_menu WHERE permission IN ('bidding:crawl-task:query', 'bidding:crawl-task:update')
 GROUP BY permission;
