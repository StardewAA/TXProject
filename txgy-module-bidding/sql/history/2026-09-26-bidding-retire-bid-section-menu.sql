-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-26 退役「标段台账」独立菜单（6772）——功能并入「项目台账」抽屉三级下钻
-- 口径来源：用户 2026-09-14 拍板（招投标模块开发·项目中期汇报 + 项目台账三级下钻方案）：
--   · “标段登记入口直接删除”：不再有独立的标段台账页（views/bidding/bidSection/index.vue 随菜单退场）；
--   · “标段信息关联的是内部公告”：标段数据由内部公告的标段字段带出，不再单独登记；
--   · 标段新建/修改改在**通用实体详情页**（/bidding/entity/BID_SECTION/...，静态路由不挂菜单）做，
--     沿用同一 saveBidSection 接口与 bidding:bid-section:create/update 权限点。
--
-- 决策（对照方案第五节”待确认②菜单硬删还是 status=1 停用”）：
--   采用**硬删 6772** + **保留权限点**（团队既有先例：提交 73872dfb「退役采集任务独立页菜单，
--   页面已删、权限保留」；及 2026-09-10-menu-retire-crawl-task-page.sql）：
--   · 6775/6776（create/update）不能被删除——NoticeWorkbench 公告编辑器选标段、门户标段登记仍调用
--     saveBidSection（后端 @PreAuthorize 断言 bidding:bid-section:create/update），
--     直接删会连带回收角色授权 → 那些链路 403。故保留并改挂 6771（新宿主）。
--   · bidding:bid-section:query（原由 6772 承载）在 6771 下以按钮 **6777** 重建并**原样转绑**角色，
--     保证任何复用该查询码的调用方不受影响（权限点数迁移前后不变）。
--   · 标段详情页登记/编辑（entityDetail）持同一 create/update 码，权限点数不变。
--
-- 幂等/顺序注意：**必须先建 6777 并复制角色绑定、再删 6772**（若先删 6772 的 system_role_menu，
--   “FROM system_role_menu WHERE menu_id=6772”就取不到行，query 权限会变成无人持有）。
--   本脚本已按正确顺序编写，可重复执行（用 NOT EXISTS 防重）。
--
-- 回滚：重跑 history/2026-09-10-project-domain.sql（建 6772/6775/6776 及其角色绑定）；并删除 6777 行与其绑定。
-- ============================================================================

-- ① 6775「标段新建」/ 6776「标段修改」改挂 6771（项目台账）——从将被删除的 6772 迁出，保住鉴权点，不留孤儿菜单
UPDATE system_menu
   SET parent_id = 6771, updater = '1', update_time = NOW()
 WHERE id IN (6775, 6776)
   AND deleted = b'0';

-- ② 在 6771 下重建 bidding:bid-section:query 按钮 6777（先于删除 6772，否则失去角色绑定来源）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6777, '标段查询', 'bidding:bid-section:query', 3, 3, 6771, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE updater = '1', update_time = NOW();

-- ③ 把原绑定给 6772（query）的角色，转绑到 6777（此时 6772 的 system_role_menu 尚未删除）
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT rm.role_id, 6777, rm.tenant_id, '1', NOW(), '1', NOW(), b'0'
FROM system_role_menu rm
WHERE rm.menu_id = 6772
  AND rm.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.menu_id = 6777 AND x.role_id = rm.role_id AND x.deleted = b'0');

-- ④ 硬删 6772「标段台账」独立页菜单及全部角色绑定
DELETE FROM system_role_menu WHERE menu_id = 6772;
DELETE FROM system_menu WHERE id = 6772 AND deleted = b'0';

-- ========== 校验 ==========
-- 期望①：6772 已删（无行）
SELECT COUNT(*) AS retired_6772 FROM system_menu WHERE id = 6772 AND deleted = b'0';

-- 期望②：6771 项目台账下按钮 = 6773 项目新建 / 6774 项目修改 / 6775 标段新建 / 6776 标段修改 / 6777 标段查询
SELECT id, name, permission, type, sort, parent_id
  FROM system_menu WHERE parent_id = 6771 AND deleted = b'0' ORDER BY sort, id;

-- 期望③：bid-section 三个权限点仍存在且已绑定角色（数量迁移前后应不变）
SELECT m.permission, COUNT(rm.role_id) AS role_cnt
  FROM system_menu m LEFT JOIN system_role_menu rm ON rm.menu_id = m.id AND rm.deleted = b'0'
 WHERE m.permission IN ('bidding:bid-section:query', 'bidding:bid-section:create', 'bidding:bid-section:update')
   AND m.deleted = b'0'
 GROUP BY m.permission;