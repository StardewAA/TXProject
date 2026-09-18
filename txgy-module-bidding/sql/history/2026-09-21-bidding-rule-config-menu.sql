-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-21 补齐「规则配置中心」权限点（安许风险联动页 60005500 下的 ⑤ 面板）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）。
--
-- 问题（由 sql/tools/check-permission-coverage.js 发现）：
--   后端 RuleConfigController 用 bidding:rule-config:query / bidding:rule-config:update 校验，
--   前端 ⑤ 规则配置面板也用这两个码判权，但 system_menu 里**从未注册**这两个权限点。
--   后果：非超管角色在「角色授权」界面里看不到这两项 → 永远授不出去 →
--         ⑤ 面板（阈值生效值 / 发布与灰度 / 版本历史与回滚 / 读取审计）对普通角色整块不可见，
--         直接调接口也是 403。这类缺口不报错，功能只是"安静地消失"。
--
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- ============================================================================

-- ① 两个按钮型权限点（type=3，挂安许风险联动页 60005500，sort 接在既有 26~29 之后）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005505, '规则配置查询', 'bidding:rule-config:query', 3, 30, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005506, '规则配置修改 / 发布 / 回滚', 'bidding:rule-config:update', 3, 31, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 角色授权：沿用所属页面菜单 60005500 的既有授权（能看到这一页的角色，同时拿到这两个按钮权限）
--    新库里 60005500 若尚未授权，本语句不插入任何行 —— 语义即「按钮继承页面授权」，符合预期；
--    之后在「角色授权」界面勾选即可（权限点已注册，界面里能看到）。
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` IN (60005505, 60005506)
WHERE rm.`menu_id` = 60005500 AND rm.`deleted` = b'0';

-- ========== 校验 ==========
-- 期望①：两条权限点存在，type=3、parent_id=60005500
SELECT id, name, permission, type, parent_id, deleted
  FROM `system_menu` WHERE id IN (60005505, 60005506) ORDER BY id;

-- 期望②：授权行数与页面菜单一致（页面未授权时两者同为 0）
SELECT (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id = 60005500 AND deleted = b'0') AS page_grants,
       (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id IN (60005505, 60005506) AND deleted = b'0') AS button_grants;

-- 期望③：门禁脚本判定「可授权」（菜单注册齐全）
--   cd txgy-module-bidding && node sql/tools/check-permission-coverage.js
