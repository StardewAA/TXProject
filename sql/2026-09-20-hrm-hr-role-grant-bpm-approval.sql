-- ============================================================
-- Offer 审批接入 BPM 后的配套权限：给「HR角色」开通审批中心（待办/已办/我的流程）
--
-- 背景：Offer 审批改为 BPM 流程后，审批人（HR 角色）需要在
--       「工作流程 → 审批中心 → 待办任务」里处理任务；
--       此前 HR 角色只有 HRM 菜单，打开待办会报 403「没有该操作权限」。
--
-- 授予范围：各租户的 HR 角色（hr_role_2019 ~ hr_role_2024）
-- 授予菜单：审批中心(1200) / 我的流程(1201) / 待办任务(1207) / 已办任务(1208)
--          + 按钮权限：流程实例的查询(1202)、流程任务的查询(1221)、流程任务的更新(1222)
-- 幂等：可重复执行
-- ============================================================

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `tenant_id`, `creator`, `updater`)
SELECT r.id, m.id, r.tenant_id, 'hrm-offer-bpm', 'hrm-offer-bpm'
FROM `system_role` r
JOIN `system_menu` m ON m.id IN (1185, 1200, 1201, 1207, 1208, 1202, 1221, 1222)
WHERE r.deleted = b'0'
  AND r.code IN ('hr_role_2019', 'hr_role_2020', 'hr_role_2021',
                 'hr_role_2022', 'hr_role_2023', 'hr_role_2024')
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id, tenant_id FROM `system_role_menu`) t
                  WHERE t.role_id = r.id AND t.menu_id = m.id AND t.tenant_id = r.tenant_id);

-- 核对：HR 角色应各拿到 8 个菜单
-- SELECT r.code, COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id = rm.role_id
--  WHERE rm.menu_id IN (1185,1200,1201,1207,1208,1202,1221,1222) GROUP BY r.code;
