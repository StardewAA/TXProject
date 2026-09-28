CREATE TEMPORARY TABLE tmp_hrm_recruit_permissions (
    permission VARCHAR(100) NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    sort INT NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_hrm_recruit_permissions (permission, name, sort) VALUES
    ('hrm:recruit:application:create', 'Create application', 21),
    ('hrm:recruit:application:query', 'Query applications', 22),
    ('hrm:recruit:application:screen', 'Screen applications', 23),
    ('hrm:recruit:application:eliminate', 'Reject applications', 24),
    ('hrm:recruit:application:interview', 'Enter interviews', 25),
    ('hrm:recruit:application:withdraw', 'Withdraw applications', 26),
    ('hrm:recruit:offer:create', 'Create offers', 27),
    ('hrm:recruit:offer:query', 'Query offers', 28),
    ('hrm:recruit:offer:submit', 'Submit offers', 29),
    ('hrm:recruit:offer:approve', 'Approve offers', 30),
    ('hrm:recruit:offer:withdraw', 'Withdraw offers', 31),
    ('hrm:recruit:offer:send', 'Send offers', 32),
    ('hrm:recruit:offer:confirm', 'Confirm offers', 33),
    ('hrm:recruit:offer:onboard', 'Onboard candidates', 34);

INSERT INTO system_menu (
    name, permission, type, sort, parent_id, path, icon, component, component_name,
    status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted
)
SELECT item.name, item.permission, 3, item.sort, parent.id, NULL, '#', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM tmp_hrm_recruit_permissions item
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
JOIN tmp_hrm_recruit_permissions item
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

CREATE TEMPORARY TABLE tmp_hrm_parent_menus (
    menu_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_hrm_parent_menus (menu_id) VALUES
    (1476), (1477), (1486), (1505), (1509), (1520), (1533), (1564),
    (1571), (1584), (1585), (1591), (1614), (1619);

INSERT INTO system_role_menu (
    role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT role.id, menu.id, 'admin', NOW(), 'admin', NOW(), b'0', role.tenant_id
FROM system_role role
JOIN system_menu menu
  ON menu.id IN (SELECT menu_id FROM tmp_hrm_parent_menus)
WHERE role.code IN ('hr_role_2019', 'hr_role_2020', 'hr_role_2021', 'hr_role_2022', 'hr_role_2023', 'hr_role_2024')
  AND role.deleted = b'0'
  AND role.tenant_id IN (2019, 2020, 2021, 2022, 2023, 2024)
  AND menu.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id = role.id
        AND existing.menu_id = menu.id
        AND existing.tenant_id = role.tenant_id
        AND existing.deleted = b'0'
  );

DROP TEMPORARY TABLE tmp_hrm_parent_menus;

DROP TEMPORARY TABLE tmp_hrm_recruit_permissions;
