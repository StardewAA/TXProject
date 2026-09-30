-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招标全流程「多角色游戏」测试账号（2026-09-22）
-- 三方角色：招标方（建项/建标/提交审批/推进）/ 平台方（推进/时间轴事件）/ 政府审批方（审批 stub/流转查询）
-- 角色菜单授权基于 system_menu（6735 根 / 6771 项目台账 / 6773-6777 项目标段按钮 / 6800 全流程 / 6801-6803 流转三权限）
-- 密码：三个测试账号均为 admin123（bcrypt 与 admin 相同）
-- 重跑安全：先清理再插入
-- ============================================================================

DELETE FROM system_role_menu WHERE role_id IN (70001, 70002, 70003);
DELETE FROM system_user_role WHERE user_id IN (SELECT id FROM system_users WHERE username IN ('bid_tender','bid_platform','bid_gov'));
DELETE FROM system_users WHERE username IN ('bid_tender','bid_platform','bid_gov') AND tenant_id = 1;
DELETE FROM system_role WHERE id IN (70001, 70002, 70003);

-- 1) 三方角色
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(70001, '招标方（测试游戏）', 'bidding_tender', 301, 1, '', 0, 2, '招标全流程游戏角色：建项/建标/提交审批/流转推进', '1', NOW(), '1', NOW(), b'0', 1),
(70002, '平台方（测试游戏）', 'bidding_platform', 302, 1, '', 0, 2, '招标全流程游戏角色：流转推进/时间轴事件（下一步）', '1', NOW(), '1', NOW(), b'0', 1),
(70003, '政府审批方（测试游戏）', 'bidding_gov', 303, 1, '', 0, 2, '招标全流程游戏角色：政府审批（预留 stub）/流转查询', '1', NOW(), '1', NOW(), b'0', 1);

-- 2) 角色菜单授权（招标方：项目/标段 CRUD + 流转执行/查询）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(70001, 6735, '1', NOW(), '1', NOW(), b'0', 1), (70001, 6771, '1', NOW(), '1', NOW(), b'0', 1),
(70001, 6773, '1', NOW(), '1', NOW(), b'0', 1), (70001, 6774, '1', NOW(), '1', NOW(), b'0', 1),
(70001, 6775, '1', NOW(), '1', NOW(), b'0', 1), (70001, 6776, '1', NOW(), '1', NOW(), b'0', 1),
(70001, 6777, '1', NOW(), '1', NOW(), b'0', 1), (70001, 6800, '1', NOW(), '1', NOW(), b'0', 1),
(70001, 6802, '1', NOW(), '1', NOW(), b'0', 1), (70001, 6803, '1', NOW(), '1', NOW(), b'0', 1);
-- 平台方：台账查看 + 流转执行/查询
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(70002, 6735, '1', NOW(), '1', NOW(), b'0', 1), (70002, 6771, '1', NOW(), '1', NOW(), b'0', 1),
(70002, 6777, '1', NOW(), '1', NOW(), b'0', 1), (70002, 6800, '1', NOW(), '1', NOW(), b'0', 1),
(70002, 6802, '1', NOW(), '1', NOW(), b'0', 1), (70002, 6803, '1', NOW(), '1', NOW(), b'0', 1);
-- 政府审批方：流程审批 + 流转查询
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(70003, 6735, '1', NOW(), '1', NOW(), b'0', 1), (70003, 6800, '1', NOW(), '1', NOW(), b'0', 1),
(70003, 6801, '1', NOW(), '1', NOW(), b'0', 1), (70003, 6803, '1', NOW(), '1', NOW(), b'0', 1);

-- 3) 三方测试用户（密码 admin123，与 admin 同 bcrypt）
INSERT INTO system_users (username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
('bid_tender', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '招标方-小王', '招标全流程游戏账号（测试）', NULL, '[]', NULL, NULL, 1, NULL, 0, NULL, NOW(), '1', NOW(), '1', NOW(), b'0', 1),
('bid_platform', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '平台方-小李', '招标全流程游戏账号（测试）', NULL, '[]', NULL, NULL, 1, NULL, 0, NULL, NOW(), '1', NOW(), '1', NOW(), b'0', 1),
('bid_gov', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '政府审批方-张工', '招标全流程游戏账号（测试）', NULL, '[]', NULL, NULL, 0, NULL, 0, NULL, NOW(), '1', NOW(), '1', NOW(), b'0', 1);

-- 4) 用户-角色关联
INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT id, 70001, '1', NOW(), '1', NOW(), b'0', 1 FROM system_users WHERE username = 'bid_tender' AND tenant_id = 1;
INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT id, 70002, '1', NOW(), '1', NOW(), b'0', 1 FROM system_users WHERE username = 'bid_platform' AND tenant_id = 1;
INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT id, 70003, '1', NOW(), '1', NOW(), b'0', 1 FROM system_users WHERE username = 'bid_gov' AND tenant_id = 1;
