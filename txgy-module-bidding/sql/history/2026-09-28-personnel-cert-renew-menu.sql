-- =====================================================================
-- 2026-09-28 员工证书批量续期权限点（五页改造缺接口契约 Q11 配套）
--
-- 规范依据：Q11 契约——POST /bidding/personnel-certificate/batch-renew（逐条回执）；
--           权限点建议 bidding:personnel:cert:renew。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1）默认放行全部权限，无需授权（先例 60005215 同口径）。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005310, '员工证书批量续期', 'bidding:personnel:cert:renew', 3, 21, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
