-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- ⚠️⚠️⚠️ 2026-09-24 追加警示：本脚本按 **dev 库** id 号段编写（dev 6759 = 主体信息与授权管理空壳目录）。
-- 本地库 id 号段已错位：本地 6759 = MES「项目成员更新」按钮（挂 6750 项目管理下，带 5 个角色绑定），
-- 前置检查（children=0）对按钮必然通过，直接执行会**误删 MES 菜单**。
-- 本地库请改跑 history/2026-09-24-remove-notice-and-subject-auth-local-ids.sql（本地 6789 空壳目录）。
-- 执行前务必核对 SELECT id,name,type,parent_id FROM system_menu WHERE id=6759 的输出与本脚本目标一致。
-- ============================================================================
-- 2026-09-23 删除空壳目录 6759「主体信息与授权管理」
--
-- 背景（评审口径）：「主体信息与授权管理没有子菜单」——其子项已全部迁出/删除：
--   · 6762/6763 平台授权管理：已于 2026-09-16 物理删除
--     （history/2026-09-16-remove-notice-and-subject-auth-menus.sql）；
--   · 6760/6761 主体档案（企业自维护）：已于 2026-09-20 迁入「资质与证书管理」60005000
--     （history/2026-09-20-subject-profile-relocate.sql）。
--   6759 自此成为无子项、无权限码（permission=''）的纯分组空壳，评审要求移除。
--
-- 处理方式：**物理删除（DELETE）**，与 6770「项目与标段」退役先例一致——
--   初始包 sql/master-bidding-sync.sql 由 sql/tools/build-init-from-db.js 按库导出，
--   软删行仍会被导出，物理删除才能保证重生成的初始包不再含 6759。
--
-- 幂等性：UPDATE / DELETE 可重复执行，二次执行无副作用。
-- 前置确认：执行前应确认 6759 下已无任何子菜单（见下方前置检查），有子项则不得执行。
--
-- 回滚：如需恢复，参照 history/2026-09-10-project-domain.sql 的目录插入写法重建 6759。
-- ============================================================================

-- 0) 前置检查：期望 children = 0，否则中止本脚本
SELECT COUNT(*) AS children_of_6759
  FROM system_menu WHERE parent_id = 6759 AND deleted = b'0';

-- 1) 先删该目录在 system_role_menu 中的绑定行，避免悬挂引用
DELETE FROM system_role_menu WHERE menu_id = 6759;

-- 2) 再删菜单本体（物理删除）
DELETE FROM system_menu WHERE id = 6759 AND deleted = b'0';

-- ========== 复核 ==========
-- 期望①：6759 已不存在（0 行）
SELECT COUNT(*) AS retired_6759 FROM system_menu WHERE id = 6759 AND deleted = b'0';

-- 期望②：招投标（6735）直挂子项不再包含 6759
-- 期望清单：6736 外部公告审核管理 / 6737 信息聚合任务管理 / 6748 投标流程跟踪 /
--           6771 项目台账 / 6790 标书智能体 / 6800 招标全流程 / 60007000 智慧标书审查 等
SELECT id, name, type, sort, parent_id
  FROM system_menu WHERE parent_id = 6735 AND deleted = b'0' ORDER BY sort, id;

-- 期望③：6759 的角色绑定已清零
SELECT COUNT(*) AS role_menu_left FROM system_role_menu WHERE menu_id = 6759;
