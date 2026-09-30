-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-17 标书审查 · 管理端重新设计（前四个模块落地菜单）
--
-- 方案依据：docs/模块设计与规范/标书审查-管理端重新设计.md
--   §4.2 菜单与权限点定稿；第六章 SQL 骨架。
--
-- 本次改动（只做前四个模块：审查任务 / 问题复核 / 审查规则库 / 报告与统计）：
--   ① 新增页面 60007450「问题复核」——跨任务待复核工作台（前端 issue/IssuePanel.vue）；
--   ② 重排目录下页面顺序：审查任务(1) / 问题复核(2) / 审查规则库(3) / 报告与统计(4)；
--   ③ 60007103「结果复核」→ 改名「问题项复核」并上移到目录 60007000：
--      该动作现在被「审查任务详情」与「问题复核」两个页面共用，
--      挂在各自页面下会产生两个同权限码的重复点（正是本次要消除的问题）；
--   ④ 新页面按现有口径授权（与 60007000 目录的授权角色对齐）。
--
-- 为什么 id 一律不变：system_role_menu 的 (role_id, menu_id) 语义必须保持，
-- 只改 parent_id / sort / name，才能做到"零授权回归"。
--
-- 未包含（属第五个模块「AI 与运行配置」，按用户要求本次不做）：
--   60007460 页面、60007302「AI 配置维护」的父级迁移。
--
-- 幂等：INSERT IGNORE + UPDATE，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-bidding-sync.sql 同库）。
-- ============================================================================

-- ① 新增页面：问题复核（path=issue，组件 bidding/bidReview/issue/IssuePanel.vue）
INSERT IGNORE INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (60007450, '问题复核', 'bidding:bid-review:query', 2, 2, 60007000, 'issue', NULL,
   'bidding/bidReview/issue/IssuePanel', 'BidReviewIssuePanel', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 页面顺序：审查任务(1) / 问题复核(2) / 审查规则库(3) / 报告与统计(4)
UPDATE `system_menu` SET `sort` = 3, `updater` = '1', `update_time` = NOW() WHERE `id` = 60007200;
UPDATE `system_menu` SET `sort` = 4, `updater` = '1', `update_time` = NOW() WHERE `id` = 60007300;

-- ③ 「结果复核」→「问题项复核」，挂到两页的共同父目录 60007000
UPDATE `system_menu`
   SET `parent_id` = 60007000, `name` = '问题项复核', `type` = 3,
       `updater` = '1', `update_time` = NOW()
 WHERE `id` = 60007103 AND `deleted` = b'0';

-- ④ 授权：新页面与 60007000 目录的现有授权对齐（不可见目录 → 不插入任何行，属预期）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` = 60007450
WHERE rm.`menu_id` = 60007000 AND rm.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` r2
                  WHERE r2.`role_id` = rm.`role_id` AND r2.`menu_id` = m.`id` AND r2.`deleted` = b'0');

-- ========== 校验 ==========
-- 期望①：目录 60007000 下 4 个页面，sort = 1/2/3/4
SELECT id, name, type, sort, path, component
  FROM `system_menu` WHERE parent_id = 60007000 AND deleted = b'0' ORDER BY sort;

-- 期望②：60007103 的 parent_id = 60007000、name = '问题项复核'
--        60007100 下只剩 60007101 / 60007102 / 60007104
SELECT id, name, type, parent_id, permission
  FROM `system_menu` WHERE id IN (60007103, 60007450) OR parent_id = 60007100 ORDER BY id;

-- 期望③：新页面授权行数 = 目录 60007000 的授权行数
SELECT (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id = 60007000 AND deleted = b'0') AS dir_grants,
       (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id = 60007450 AND deleted = b'0') AS page_grants;
