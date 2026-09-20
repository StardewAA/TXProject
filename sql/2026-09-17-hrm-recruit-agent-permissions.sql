-- HRM 招聘 Agent 管理端权限增量脚本。
--
-- 为候选人摘要、应聘匹配、面试问题建议以及 Agent 任务查询（审计）新增权限点，
-- 并授予现有 HR 角色。沿用 2026-09-14-hrm-recruit-application-permissions.sql 的写法：
-- 权限点挂在「候选人查询」菜单下，角色授权只针对既有 HR 角色，重复执行不会产生重复数据。

CREATE TEMPORARY TABLE tmp_hrm_recruit_agent_permissions (
    permission VARCHAR(100) NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    sort INT NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_hrm_recruit_agent_permissions (permission, name, sort) VALUES
    ('hrm:recruit:agent:candidate-summary', 'Candidate AI summary', 41),
    ('hrm:recruit:agent:application-match', 'Application AI match', 42),
    ('hrm:recruit:agent:interview-question', 'Interview question suggestion', 43),
    ('hrm:recruit:agent:audit-query', 'Recruit agent task query', 44);

INSERT INTO system_menu (
    name, permission, type, sort, parent_id, path, icon, component, component_name,
    status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted
)
SELECT item.name, item.permission, 3, item.sort, parent.id, NULL, '#', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM tmp_hrm_recruit_agent_permissions item
JOIN system_menu parent
  ON parent.permission = 'hrm:recruit:candidate:query'
 AND parent.type = 2
 AND parent.deleted = b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu existing
    WHERE existing.permission = item.permission AND existing.deleted = b'0'
);

INSERT INTO system_role_menu (
    role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT role.id, menu.id, 'admin', NOW(), 'admin', NOW(), b'0', role.tenant_id
FROM system_role role
JOIN system_menu menu
  ON menu.deleted = b'0'
JOIN tmp_hrm_recruit_agent_permissions item
  ON item.permission = menu.permission
WHERE role.code IN ('hr_role_2019', 'hr_role_2020', 'hr_role_2021', 'hr_role_2022', 'hr_role_2023', 'hr_role_2024')
  AND role.deleted = b'0'
  AND role.tenant_id IN (2019, 2020, 2021, 2022, 2023, 2024)
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id = role.id
        AND existing.menu_id = menu.id
        AND existing.tenant_id = role.tenant_id
        AND existing.deleted = b'0'
  );

DROP TEMPORARY TABLE tmp_hrm_recruit_agent_permissions;
