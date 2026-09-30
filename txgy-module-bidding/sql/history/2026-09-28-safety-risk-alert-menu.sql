-- =====================================================================
-- 2026-09-28 安许风险条目一键预警权限点（五页改造缺接口契约 Q22 配套）
--
-- 规范依据：Q22 契约——POST /bidding/safety-permit/risk/alert/batch（所选条目批量预警，幂等）；
--           权限点 bidding:safety-risk:alert（与 Q18/Q19 同父级 60005500）。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1）默认放行全部权限，无需授权（先例 60005511 同口径）。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- id 用 60005512（60005508 号段整理：Q18/Q19 菜单改用 60005509~511，本按钮顺延 512）
(60005512, '一键预警', 'bidding:safety-risk:alert', 3, 34, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
