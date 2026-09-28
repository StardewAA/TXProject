-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-26 新增「企业资质到期预警处理」独立权限点（60005215）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）；
--           《电子资质域-待决策与残留事项清单》④（岗位职责分离审核要求：发现与核销分权）。
--
-- 问题（待决策清单 ④，拍板 2026-09-26）：
--   POST /bidding/qualification/alert/handle 此前复用 bidding:qualification:alert-run
--   （"执行一次扫描"的运维码）。同一码同时控制"触发扫描"与"核销预警"：
--   ① 审计要求"发现"与"核销"分权 —— 给经办人授"处理预警"权限时，连带拿到了
--      "补跑扫描"这种运维动作，权限面过宽；
--   ② 运维补跑扫描的角色（平台运维）不需要也不应该默认能核销企业的预警闭环。
--   故登记独立码 bidding:qualification:alert-handle（本质是按钮级职责分离，
--   与 60005214 alert-run（运维扫描）解耦）。
--
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- 版本：契约《电子证照模块-前端接口契约-v1.0》§1.2 预警清单 / 精读摘要 row 60 同步更新；
--       后端 QualificationController#handleAlert 已切换 @PreAuthorize。
-- ============================================================================

-- ① 按钮型权限点（type=3，挂企业资质页 60005200，sort 接在既有 15（60005214 alert-run）之后）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005215, '预警处理', 'bidding:qualification:alert-handle', 3, 16, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 角色授权：沿用所属页面菜单 60005200（企业资质）的既有授权 ——
--    能看到"企业资质"页的角色，同时拿到"预警处理"按钮权限（类似 2026-09-21 规则配置中心
--    的授权手法：按钮继承页面授权，无需手工逐角色勾选；之后仍可在「角色授权」界面单独调整）。
--    注意：本次**不**给 60005214（alert-run 扫描）的既有角色自动授 60005215 ——
--    初衷是"核销未必等于能扫描"，若业务上希望扫描角色也默认能处理，可在角色授权界面手动勾选。
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, 60005215, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
WHERE rm.`menu_id` = 60005200 AND rm.`deleted` = b'0';