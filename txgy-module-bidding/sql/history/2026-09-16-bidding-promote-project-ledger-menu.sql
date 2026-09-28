-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-16 「项目与标段」父级目录退役 —— 项目台账提升为招投标一级菜单
-- 口径来源：用户指令 —— “去掉中间的「项目与标段」父级分组，
--   「项目台账」直接挂在「招投标」下”，与「投标流程跟踪」同级。
--
-- 现状（system_menu，招投标 6735 下）：
--   6735 招投标（type=1 目录）
--     ├ 6736 外部公告审核管理
--     ├ 6737 信息聚合任务管理
--     ├ 6748 投标流程跟踪
--     ├ 6770「项目与标段」（type=1 目录，纯分组，无 component）
--     │    └ 6771 项目台账（component=bidding/project/index）+ 按钮 6773~6777
--     ├ 6753 公告/公示管理
--     └ 6759 主体信息与授权管理
--
-- 处理后：
--   6735 招投标
--     ├ 6736 外部公告审核管理
--     ├ 6737 信息聚合任务管理
--     ├ 6748 投标流程跟踪
--     ├ 6771 项目台账 ◄—— 提升为一级（parent 6735，sort=4）
--     ├ 6753 公告/公示管理
--     └ 6759 主体信息与授权管理
--
-- 决策：
--   · 6771 改挂 6735（parent 6770 → 6735），sort 置 4（紧跟 6748 投标流程跟踪 sort=3，
--     落在 6753 公告/公示管理 sort=1000 之前）。path='project' 随父路径解析为 /bidding/project。
--   · 6771 下的按钮 6773~6777（新建/修改/标段新建/标段修改/标段查询）parent 仍为 6771，不动，权限点不变。
--   · 6770 已无下级需承载，纯分组目录硬删，连同其 system_role_menu 绑定一并删除
--     （与既有先例一致：提交 73872dfb、2026-09-10-menu-retire-crawl-task-page.sql）。
--   · 6771 本身已持有角色绑定（role 1/2，system_role_menu 直接挂在 6771 上），
--     提升为一级后父节点 6735 亦有绑定，直接可见，无需补绑。
--
-- 幂等/顺序注意：
--   · 必须先改 6771 的 parent，再删 6770 —— 若先删 6770，6771 会变成孤儿（悬空 parent），
--     提升逻辑丢失根因；本脚本已按正确顺序编写，可重复执行（UPDATE 天然幂等，DELETE 二次执行无副作用）。
--   · 硬删 6770 不受 6771 影响（6771 已迁走），其余子菜单（6772 已于 2026-09-26 退役）不在此列。
--
-- 回滚：重跑 history/2026-09-10-project-domain.sql（重建 6770/6771/…/6776 及其角色绑定）可回退分组结构。
-- ============================================================================

-- ① 6771「项目台账」改挂 6735（招投标），sort 紧跟「投标流程跟踪」
UPDATE system_menu
   SET parent_id = 6735, sort = 4, updater = '1', update_time = NOW()
 WHERE id = 6771
   AND deleted = b'0';

-- ② 硬删 6770「项目与标段」目录及其全部角色绑定
DELETE FROM system_role_menu WHERE menu_id = 6770;
DELETE FROM system_menu WHERE id = 6770 AND deleted = b'0';

-- ========== 校验 ==========
-- 期望①：6771 项目台账直接挂 6735，sort=4
SELECT id, name, parent_id, sort, path, component
  FROM system_menu WHERE id = 6771 AND deleted = b'0';

-- 期望②：招投标（6735）直挂子项 = 6736 / 6737 / 6748 / 6771 / 6753 / 6759（6770 应在列外）
SELECT id, name, type, sort, parent_id
  FROM system_menu WHERE parent_id = 6735 AND deleted = b'0' ORDER BY sort, id;

-- 期望③：6770 已删（无行）
SELECT COUNT(*) AS retired_6770 FROM system_menu WHERE id = 6770 AND deleted = b'0';

-- 期望④：6771 下按钮齐全 6773 项目新建 / 6774 项目修改 / 6775 标段新建 / 6776 标段修改 / 6777 标段查询
SELECT id, name, permission, sort, parent_id
  FROM system_menu WHERE parent_id = 6771 AND deleted = b'0' ORDER BY sort, id;
