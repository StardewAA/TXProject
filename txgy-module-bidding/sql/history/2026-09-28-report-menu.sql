-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-28 补齐「定标书面报告」权限点（挂项目台账 6771 下）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）。
--
-- 背景（招标投标法第47条 定标书面报告子域补齐后）：
--   后端新增 ReportController（bidding:report:submit/query），共 2 个 @PreAuthorize 权限码。
--
-- 挂载口径：报告是「招标项目」（项目台账 6771）的子功能，故 2 个按钮权限点统一挂
--   6771 下，sort 接在既有异议（25）之后（26~27）。
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- ============================================================================

-- ① 2 个按钮型权限点（type=3，挂项目台账 6771）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006217, '提交书面报告', 'bidding:report:submit', 3, 26, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006218, '报告台账', 'bidding:report:query', 3, 27, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 角色授权：沿用所属页面菜单 6771（项目台账）的既有授权
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` IN (60006217, 60006218)
WHERE rm.`menu_id` = 6771 AND rm.`deleted` = b'0';

-- ========== 校验 ==========
-- 期望①：2 条权限点存在，type=3、parent_id=6771
SELECT id, name, permission, type, sort, parent_id, deleted
  FROM `system_menu` WHERE id IN (60006217, 60006218) ORDER BY sort;

-- 期望②：授权行数与页面菜单一致
SELECT (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id = 6771 AND deleted = b'0') AS page_grants,
       (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id IN (60006217, 60006218) AND deleted = b'0') AS button_grants;
