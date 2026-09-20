-- ============================================================
-- HRM 请假申请改为「人工审批」所需的权限菜单
--
-- 背景：员工端发起请假时原实现调用 Flowable 发起审批流程，
--       但 hrm_attendance_leave 流程定义并未发布，导致发起请假直接报
--       「1009003002 流程定义不存在」，员工请假闭环被阻断。
--       现与 HRM 其他流程（员工异动审批、Offer 审批）保持一致，改为
--       「发起 → 待审批 → HR 人工通过/驳回」的手动审批。
--
-- 本脚本只补权限菜单：新增按钮权限 hrm:attendance:leave:update，
-- 并自动授予「已经拥有请假记录查询权限」的全部角色，避免漏配导致 403。
-- 幂等：可重复执行。
-- ============================================================

-- 1. 新增「请假审批」按钮权限（挂在「请假记录」菜单 1521 下）
INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, icon, component,
                           component_name, status, visible, keep_alive, always_show,
                           creator, updater)
VALUES (6990, '请假审批', 'hrm:attendance:leave:update', 3, 3, 1521, '', '', '', '',
        0, b'1', b'1', b'1', 'leave-approve-dev', 'leave-approve-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission),
                        parent_id = VALUES(parent_id), sort = VALUES(sort), deleted = b'0';

-- 2. 授予「已有请假记录查询权限」的角色（含各租户 HR 角色与租户管理员）
INSERT INTO `system_role_menu` (role_id, menu_id, tenant_id, creator, updater)
SELECT DISTINCT rm.role_id, 6990, rm.tenant_id, 'leave-approve-dev', 'leave-approve-dev'
FROM `system_role_menu` rm
JOIN `system_menu` m_query ON m_query.id = rm.menu_id
                          AND m_query.permission = 'hrm:attendance:leave:query'
                          AND m_query.deleted = b'0'
WHERE NOT EXISTS (SELECT 1
                  FROM (SELECT role_id, menu_id, tenant_id
                        FROM `system_role_menu`
                        WHERE menu_id = 6990) t
                  WHERE t.role_id = rm.role_id AND t.tenant_id = rm.tenant_id);
