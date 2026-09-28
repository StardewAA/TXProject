-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-28 补齐「投标保证金」权限点（挂项目台账 6771 下）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）。
--
-- 背景（《电子招标投标全流程说明书》§7 保证金登记断点补齐后）：
--   后端新增 BidDepositController（bidding:deposit:save/query），共 2 个 @PreAuthorize 权限码；
--   若不在 system_menu 注册，非超管角色在「角色授权」界面看不到这些权限点 → 授不出去 → 403。
--
-- 挂载口径：投标保证金是「招标项目」（项目台账 6771）的子功能，故 2 个按钮权限点统一挂
--   6771 下，sort 接在既有资格预审（17）之后（18~19）。
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- ============================================================================

-- ① 2 个按钮型权限点（type=3，挂项目台账 6771）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006209, '登记投标保证金', 'bidding:deposit:save', 3, 18, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006210, '投标保证金台账', 'bidding:deposit:query', 3, 19, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006211, '退还投标保证金', 'bidding:deposit:refund', 3, 20, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 角色授权：沿用所属页面菜单 6771（项目台账）的既有授权 ——
--    能看到「项目台账」页的角色，同时拿到这 2 个按钮权限（按钮继承页面授权）。
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` IN (60006209, 60006210, 60006211)
WHERE rm.`menu_id` = 6771 AND rm.`deleted` = b'0';

-- ========== 校验 ==========
-- 期望①：3 条权限点存在，type=3、parent_id=6771
SELECT id, name, permission, type, sort, parent_id, deleted
  FROM `system_menu` WHERE id IN (60006209, 60006210, 60006211) ORDER BY sort;

-- 期望②：授权行数与页面菜单一致
SELECT (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id = 6771 AND deleted = b'0') AS page_grants,
       (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id IN (60006209, 60006210, 60006211) AND deleted = b'0') AS button_grants;
