-- =====================================================================
-- 员工资质 + 证件全生命周期 菜单/权限注册脚本 —— txgy-module-bidding 小模块（M11 员工资质 / M12 全生命周期）
-- 依据：
--   1) 《招投标模块开发规范》§4.4（权限码随 sql/ 菜单脚本登记，与 Controller @PreAuthorize 一一对应）、
--      §4.7（权限命名 bidding:<域>:<动作>）；
--   2) 用户范围口径（2026-09-13 确认）：做到"证照全生命周期管理（中级）"——变更、注销、年检、到期提醒、
--      版本管理，不做签发；故无"签发"相关权限点，一键配证/合规核验亦不在本期（对应权限点不注册）；
--   3) 父节点 60002000 资质与证书管理（见 sql/history/2026-09-08-certificate-menu.sql）；
--      号段延续：60002300 员工资质、60002400 证件生命周期。
-- 用法：在 system 库执行（只加菜单行，不触碰既有模块表结构/源码）。
-- =====================================================================

-- 1. 员工资质（页面 + 按钮）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(60002300, '员工资质', 'bidding:personnel:profile:query', 2, 11, 60002000, 'personnel', 'ep:user', 'bidding/personnel/index', 'PersonnelIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002301, '员工建档（一人一档）', 'bidding:personnel:profile:create', 3, 12, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002302, '档案修改/在职状态流转', 'bidding:personnel:profile:update', 3, 13, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002303, '员工证书登记', 'bidding:personnel:cert:create', 3, 14, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002304, '证书修改/状态流转（吊销注销恢复）', 'bidding:personnel:cert:update', 3, 15, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002305, '员工证书删除', 'bidding:personnel:cert:delete', 3, 16, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002306, '证书检索/到期清单/细分统计', 'bidding:personnel:cert:query', 3, 17, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002307, '履历登记（培训/社保/劳动合同）', 'bidding:personnel:record:create', 3, 18, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002308, '履历查询（培训/社保/劳动合同）', 'bidding:personnel:record:query', 3, 19, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002309, '员工证件到期预警扫描', 'bidding:personnel:alert-run', 3, 20, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`), `deleted` = b'0';

-- 2. 证件全生命周期（页面 + 按钮）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(60002400, '证件全生命周期', 'bidding:certificate:lifecycle:query', 2, 21, 60002000, 'cert-lifecycle', 'ep:refresh', 'bidding/certificate/lifecycle/index', 'CertLifecycleIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002401, '生命周期发起（变更/续期/注销吊销/年检）', 'bidding:certificate:lifecycle:apply', 3, 22, 60002400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002402, '生命周期审核与生效（回写台账）', 'bidding:certificate:lifecycle:audit', 3, 23, 60002400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002403, '企业材料完备性自查（身份/资质/实力）', 'bidding:qualification:material:query', 3, 24, 60002400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`), `deleted` = b'0';
