-- =====================================================================
-- 2026-09-28 生命周期催办权限点（五页改造缺接口契约 Q15 配套）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）；
--           五页改造缺接口契约 Q15（催办 POST /bidding/cert-lifecycle/urge；同日幂等不叠加）。
--
-- 幂等：菜单 INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1，租户 0）默认放行全部权限，无需授权（先例 60005215 alert-handle 同口径）。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005404, '生命周期催办（提醒审核）', 'bidding:certificate:lifecycle:urge', 3, 30, 60005400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
