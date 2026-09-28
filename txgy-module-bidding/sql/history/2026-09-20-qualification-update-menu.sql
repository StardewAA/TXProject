-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-20 补登「企业资质修改」权限点（60005216）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）；
--           《企业资质页-页面级契约-v1.0.md》§4 G2（详情/编辑独立页）。
--
-- 问题（资质域权限点漏登记）：
--   前端「企业资质」详情页的「编辑资质 / 保存 / 保存并提交审批」以及新增的
--   PUT /bidding/qualification/update，权限码为 bidding:qualification:update。
--   但该码此前**从未登记进 system_menu** —— 资质域的既有登记只到
--   query/create/renew/approve/reject/standard/element/review/permit/alert-run/alert-handle
--   （见 master-credential-sync.sql 60005200~60005215）。
--
--   对照先例：证照台账（bidding:certificate:update）早在 60005102 就登记了。
--   本码属于同类漏登记，不是新增业务能力。
--
--   影响（为什么要补）：前端 can() 的实现是「accessCodes 为空则放行，否则 hasAccessByCodes([code])」。
--   未登记的权限码对普通角色恒为 false → 「编辑资质」按钮永不渲染，
--   只有超管（*:*:*）能看到并使用，形成「超管能用、业务角色全看不见」的畸形状态。
--
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- 版本：后端 QualificationController#update 同步以 @PreAuthorize('bidding:qualification:update') 落地。
-- ============================================================================

-- ① 按钮型权限点（type=3，挂企业资质页 60005200，sort 接在既有 16（60005215 alert-handle）之后）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005216, '资质修改', 'bidding:qualification:update', 3, 17, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 角色授权：沿用所属页面菜单 60005200（企业资质）的既有授权 ——
--    能看到"企业资质"页的角色，同时拿到「资质修改」按钮权限
--    （与 2026-09-26 预警处理 60005215 同一授权手法：按钮继承页面授权，
--     无需手工逐角色勾选；之后仍可在「角色授权」界面单独调整）。
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, 60005216, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
WHERE rm.`menu_id` = 60005200 AND rm.`deleted` = b'0';
