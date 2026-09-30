-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 投标流程跟踪管理端总览补齐（2026-09-28）：负责人/公司快照列 + 阶段/事件字典 + 菜单权限归位
-- 依据：《后端变更请求-投标流程跟踪管理端总览-2026-09-18.md》§3/§4/§6.4
--   +《后端变更请求-投标流程跟踪管理端总览-落地核对清单-2026-09-28.md》A2~A5
-- 用户关切：投标流程数据缺负责人/公司字段、无法跟踪操作记录 → 落快照列 + 事件史出参（get 补 events[]）
-- ============================================================================

-- 1) bidding_process_track 加快照列（负责人/公司/地区/金额/截止；契约 §3 方案 B 建议）
ALTER TABLE bidding_process_track ADD COLUMN owner_user_id bigint DEFAULT NULL COMMENT '负责人用户ID（投标员工；契约 §5 ProcessTrackItemVO.ownerUserId）' AFTER user_id;
ALTER TABLE bidding_process_track ADD COLUMN owner_name varchar(64) DEFAULT NULL COMMENT '负责人姓名快照（AdminUserApi 建档时点取值）' AFTER owner_user_id;
ALTER TABLE bidding_process_track ADD COLUMN owner_post varchar(128) DEFAULT NULL COMMENT '负责人职务（可空，页面降级显示 —）' AFTER owner_name;
ALTER TABLE bidding_process_track ADD COLUMN owner_phone varchar(32) DEFAULT NULL COMMENT '负责人联系方式（可空，页面降级显示 —）' AFTER owner_post;
ALTER TABLE bidding_process_track ADD COLUMN tenant_name varchar(128) DEFAULT NULL COMMENT '公司名快照（平台授权 SubjectAuth.tenantName 时点取值）' AFTER owner_phone;
ALTER TABLE bidding_process_track ADD COLUMN region varchar(64) DEFAULT NULL COMMENT '地区（BidSection/TenderProject regionCode 时点取值）' AFTER tenant_name;
ALTER TABLE bidding_process_track ADD COLUMN amount varchar(64) DEFAULT NULL COMMENT '预算金额（BidSection.contractReckonPrice 时点取值）' AFTER region;
ALTER TABLE bidding_process_track ADD COLUMN deadline datetime DEFAULT NULL COMMENT '投标截止时间（bid_end_time 时点取值；flow-timer 域 DO 同步后回填）' AFTER amount;

-- 2) 阶段/事件字典注册（契约 §4.1 命名对齐；09-28 核对清单 A4）
DELETE FROM bidding_dict_data WHERE dict_type IN ('bidding_process_state','bidding_process_event');

-- 阶段字典 bidding_process_state（5 条）
-- 2026-09-20 号段修正：原用 ...8501~8505，已被同日 2026-09-28-bidding-code-set-dict.sql 的
--   shaanxi_tender_organize_form(8501~8503) / shaanxi_trade_mode(8504~8505) 先行占用
--   ⇒ 本文件 INSERT 不带 IGNORE，重复执行会直接报主键冲突。
--   按 check-seed-id-collision.js 的建议改为 ...8527~8531（紧接 8526 之后，库/两包均空闲）。
INSERT INTO bidding_dict_data (id, tenant_id, dict_type, dict_value, dict_label, color_type, terminal, sort, enabled, remark, creator, create_time, updater, update_time, deleted) VALUES
('710000000000008527', 0, 'bidding_process_state', 'INTENTION', '意向投标', 'default', b'0', 1, b'1', '投标流程跟踪阶段（契约 §4.1）：关注项目建档', '1', NOW(), '1', NOW(), b'0'),
('710000000000008528', 0, 'bidding_process_state', 'QUALIFICATION_REVIEW', '资质审核', 'warning', b'0', 2, b'1', '投标流程跟踪阶段：报名后等待平台审核', '1', NOW(), '1', NOW(), b'0'),
('710000000000008529', 0, 'bidding_process_state', 'PENDING_BID', '待投标', 'primary', b'0', 3, b'1', '投标流程跟踪阶段：审核通过准备标书', '1', NOW(), '1', NOW(), b'0'),
('710000000000008530', 0, 'bidding_process_state', 'BIDDED', '已投标', 'success', b'0', 4, b'1', '投标流程跟踪阶段：已递交加密投标文件', '1', NOW(), '1', NOW(), b'0'),
('710000000000008531', 0, 'bidding_process_state', 'TERMINATED', '流程结束', 'info', b'0', 5, b'1', '投标流程跟踪阶段：审核不通过或取消', '1', NOW(), '1', NOW(), b'0');

-- 事件字典 bidding_process_event（6 条）
INSERT INTO bidding_dict_data (id, tenant_id, dict_type, dict_value, dict_label, color_type, terminal, sort, enabled, remark, creator, create_time, updater, update_time, deleted) VALUES
('710000000000008601', 0, 'bidding_process_event', 'CREATE', '新建意向', 'default', b'0', 1, b'1', '投标流程事件（契约 §4.1）：建档', '1', NOW(), '1', NOW(), b'0'),
('710000000000008602', 0, 'bidding_process_event', 'REGISTER', '报名投标', 'default', b'0', 2, b'1', '投标流程事件：INTENTION→QUALIFICATION_REVIEW', '1', NOW(), '1', NOW(), b'0'),
('710000000000008603', 0, 'bidding_process_event', 'APPROVE', '审核通过', 'success', b'0', 3, b'1', '投标流程事件：QUALIFICATION_REVIEW→PENDING_BID', '1', NOW(), '1', NOW(), b'0'),
('710000000000008604', 0, 'bidding_process_event', 'REJECT', '审核不通过', 'danger', b'0', 4, b'1', '投标流程事件：QUALIFICATION_REVIEW→TERMINATED', '1', NOW(), '1', NOW(), b'0'),
('710000000000008605', 0, 'bidding_process_event', 'UPLOAD_DOCUMENT', '上传标书', 'primary', b'0', 5, b'1', '投标流程事件：PENDING_BID→BIDDED', '1', NOW(), '1', NOW(), b'0'),
('710000000000008606', 0, 'bidding_process_event', 'CANCEL', '取消投标', 'info', b'0', 6, b'1', '投标流程事件：非终态→TERMINATED', '1', NOW(), '1', NOW(), b'0');

-- 3) 菜单权限归位（契约 §6.4；09-28 核对清单 A5）
-- 3.1 菜单 6748 permission 改回 bidding:process-track:query（页面 v2 已不再调用 notice-external 接口）
UPDATE system_menu SET permission = 'bidding:process-track:query', updater = '1', update_time = NOW() WHERE id = 6748 AND deleted = b'0';

-- 3.2 增补 bidding:process-track:overview（仅超管；跨租户总览，决定 tenantId 参数与 tenants 候选下发）
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(60006222, '投标流程总览（超管）', 'bidding:process-track:overview', 3, 19, 6748, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 3.3 授权：overview 仅绑 super_admin（本部署 role_id=1；见 2026-09-09-notice-menu-under-bidding.sql 先例）
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 1, m.id, '1', NOW(), '1', NOW(), b'0', 0
FROM system_menu m
WHERE m.id = 60006222 AND m.deleted = b'0';

-- ============================================================================
-- 校验 SELECT（执行后人工核对）
-- SELECT id, name, permission FROM system_menu WHERE id IN (6748, 60006222);
-- SELECT dict_type, dict_value, dict_label FROM bidding_dict_data WHERE dict_type IN ('bidding_process_state','bidding_process_event') ORDER BY dict_type, sort;
-- SHOW COLUMNS FROM bidding_process_track LIKE 'owner%';
