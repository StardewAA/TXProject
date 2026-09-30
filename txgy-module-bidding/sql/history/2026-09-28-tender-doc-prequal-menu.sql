-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- ⚠️ 已作废（2026-09-29 统一）：本脚本 8 行全部**不要执行** —— 已由他批次登记，重复登记会产生同码多承载
--   （角色授权界面出现两项同名权限点；getMenuIdListByPermissionFromCache 返回多 id，判权虽不坏，但界面混乱）。
--   · 60006201 bidding:tender-doc:download / 60006202 bidding:tender-doc:query
--       → 已由 sql/history/2026-09-29-权限审计批次-菜单与授权统一.sql 登记为 60006302/60006303（**已入库生效**）；
--   · 60006203~60006208 bidding:prequal:*（query/save/publish/close/apply/review）
--       → 已由 60006235~60006240（挂 6735）登记并入库生效。
--   保留本文件仅作历史记录（号段让位与口径依据）。如需恢复执行，请先删除冲突的旧承载行。
-- ============================================================================
-- 2026-09-28 补齐「招标文件下载名单」与「资格预审」权限点（挂项目台账 6771 下）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）。
--
-- 背景（《电子招标投标全流程说明书》§5/§6 两大流程断点补齐后）：
--   后端新增 TenderDocDownloadController（bidding:tender-doc:download/query）与
--   PrequalController（bidding:prequal:save/publish/close/query/apply/review），
--   共 8 个 @PreAuthorize 权限码；若不在 system_menu 注册，非超管角色在「角色授权」
--   界面看不到这些权限点 → 永远授不出去 → 直接调接口 403（功能"安静消失"，不报错）。
--
-- 挂载口径：下载名单、资格预审都是「招标项目」（项目台账 6771）的子功能，故 8 个按钮
--   权限点统一挂 6771 下，sort 接在既有 6773 项目新建/6774 项目修改之后（10~17）。
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- ============================================================================

-- ① 8 个按钮型权限点（type=3，挂项目台账 6771）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006201, '记录招标文件下载', 'bidding:tender-doc:download', 3, 10, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006202, '招标文件下载名单', 'bidding:tender-doc:query', 3, 11, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006203, '资格预审查询', 'bidding:prequal:query', 3, 12, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006204, '资格预审创建', 'bidding:prequal:save', 3, 13, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006205, '资格预审发布', 'bidding:prequal:publish', 3, 14, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006206, '资格预审关闭', 'bidding:prequal:close', 3, 15, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006207, '提交资格预审申请', 'bidding:prequal:apply', 3, 16, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006208, '资格预审审查', 'bidding:prequal:review', 3, 17, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 角色授权：沿用所属页面菜单 6771（项目台账）的既有授权 ——
--    能看到「项目台账」页的角色，同时拿到这 8 个按钮权限（按钮继承页面授权，手法同
--    2026-09-21 规则配置中心）；之后仍可在「角色授权」界面单独调整。
--    注：apply（投标方申请）/review（资格审查委员会）语义上属其他角色，若需细分权限面，
--        在角色授权界面勾选/取消即可，本脚本只保证「权限点已注册、能看到」。
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` IN (60006201, 60006202, 60006203, 60006204, 60006205, 60006206, 60006207, 60006208)
WHERE rm.`menu_id` = 6771 AND rm.`deleted` = b'0';

-- ========== 校验 ==========
-- 期望①：8 条权限点存在，type=3、parent_id=6771
SELECT id, name, permission, type, sort, parent_id, deleted
  FROM `system_menu` WHERE id IN (60006201, 60006202, 60006203, 60006204, 60006205, 60006206, 60006207, 60006208) ORDER BY sort;

-- 期望②：授权行数与页面菜单一致（页面未授权时两者同为 0）
SELECT (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id = 6771 AND deleted = b'0') AS page_grants,
       (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id IN (60006201, 60006202, 60006203, 60006204, 60006205, 60006206, 60006207, 60006208) AND deleted = b'0') AS button_grants;

-- 期望③：门禁脚本判定「可授权」（菜单注册齐全）
--   cd txgy-module-bidding && node sql/tools/check-permission-coverage.js
