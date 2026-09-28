-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招标全流程管理（2026-09-20）：政府审批预留接口 权限菜单
-- 规范依据：《招投标模块开发规范》§5 权限点 + 用户裁定：政府审批接口预留（stub 直接返回通过）
-- 对应端点：POST /bidding/flow/approval（GovernmentApprovalController，权限 bidding:flow:approval）
-- ============================================================================

-- 目录：招标全流程（type=1 目录，parent=6735 bidding 根目录；6800-6809 区间空闲）
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6800, '招标全流程', '', 1, 40, 6735, 'flow', 'ep:set-up', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
-- 按钮：流程审批（政府审批预留 stub 端点 bidding:flow:approval，parent=6800 目录）
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6801, '流程审批', 'bidding:flow:approval', 3, 1, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
