-- 为指定租户创建 HRM 角色及两位 HR 账号，仅绑定现有 HRM 菜单权限。
CREATE TEMPORARY TABLE tmp_hrm_hr_roles (
    tenant_id BIGINT NOT NULL,
    role_name VARCHAR(30) NOT NULL,
    role_code VARCHAR(100) NOT NULL,
    sort INT NOT NULL,
    PRIMARY KEY (tenant_id, role_code)
);

INSERT INTO tmp_hrm_hr_roles (tenant_id, role_name, role_code, sort) VALUES
    (2019, 'HR角色', 'hr_role_2019', 21),
    (2020, 'HR角色', 'hr_role_2020', 21),
    (2021, 'HR角色', 'hr_role_2021', 21),
    (2022, 'HR角色', 'hr_role_2022', 21),
    (2023, 'HR角色', 'hr_role_2023', 21),
    (2024, 'HR角色', 'hr_role_2024', 21);

INSERT INTO system_role (
    name, code, sort, data_scope, data_scope_dept_ids, status, type, remark,
    creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT r.role_name, r.role_code, r.sort, 1, '[]', 0, 2,
       'HRM 人力资源权限角色', 'admin', NOW(), 'admin', NOW(), b'0', r.tenant_id
FROM tmp_hrm_hr_roles r
JOIN system_tenant t ON t.id = r.tenant_id AND t.deleted = b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM system_role existing
    WHERE existing.tenant_id = r.tenant_id
      AND existing.code = r.role_code
      AND existing.deleted = b'0'
);

INSERT INTO system_role_menu (
    role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT role.id, menu.id, 'admin', NOW(), 'admin', NOW(), b'0', role.tenant_id
FROM system_role role
JOIN tmp_hrm_hr_roles seed
  ON seed.tenant_id = role.tenant_id
 AND seed.role_code = role.code
 AND role.deleted = b'0'
JOIN system_menu menu
  ON menu.deleted = b'0'
 AND menu.permission LIKE 'hrm:%'
WHERE NOT EXISTS (
    SELECT 1
    FROM system_role_menu existing
    WHERE existing.tenant_id = role.tenant_id
      AND existing.role_id = role.id
      AND existing.menu_id = menu.id
      AND existing.deleted = b'0'
);

CREATE TEMPORARY TABLE tmp_hrm_hr_users (
    tenant_id BIGINT NOT NULL,
    username VARCHAR(30) NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    role_code VARCHAR(100) NOT NULL,
    PRIMARY KEY (tenant_id, username)
);

INSERT INTO tmp_hrm_hr_users (tenant_id, username, nickname, role_code) VALUES
    (2019, 'yanhereli_hr', 'HR1', 'hr_role_2019'),
    (2019, 'yanhereli_hr2', 'HR2', 'hr_role_2019'),
    (2020, 'qinchuan_hr', 'HR1', 'hr_role_2020'),
    (2020, 'qinchuan_hr2', 'HR2', 'hr_role_2020'),
    (2021, 'longyuan_hr', 'HR1', 'hr_role_2021'),
    (2021, 'longyuan_hr2', 'HR2', 'hr_role_2021'),
    (2022, 'xibeichengtou_hr', 'HR1', 'hr_role_2022'),
    (2022, 'xibeichengtou_hr2', 'HR2', 'hr_role_2022'),
    (2023, 'hanhai_hr', 'HR1', 'hr_role_2023'),
    (2023, 'hanhai_hr2', 'HR2', 'hr_role_2023'),
    (2024, 'shanbeigongkuang_hr', 'HR1', 'hr_role_2024'),
    (2024, 'shanbeigongkuang_hr2', 'HR2', 'hr_role_2024');

INSERT INTO system_users (
    username, password, nickname, remark, dept_id, post_ids, email, mobile, sex,
    avatar, status, login_ip, login_date, creator, create_time, updater,
    update_time, deleted, tenant_id
)
SELECT seed.username, '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG',
       seed.nickname, 'HR账号', NULL, '[]', '', '', 0, '', 0, '', NULL,
       'admin', NOW(), 'admin', NOW(), b'0', seed.tenant_id
FROM tmp_hrm_hr_users seed
JOIN system_tenant tenant
  ON tenant.id = seed.tenant_id
 AND tenant.deleted = b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM system_users existing
    WHERE existing.tenant_id = seed.tenant_id
      AND existing.username = seed.username
      AND existing.deleted = b'0'
);

INSERT INTO system_user_role (
    user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT user.id, role.id, 'admin', NOW(), 'admin', NOW(), b'0', user.tenant_id
FROM tmp_hrm_hr_users seed
JOIN system_users user
  ON user.tenant_id = seed.tenant_id
 AND user.username = seed.username
 AND user.deleted = b'0'
JOIN system_role role
  ON role.tenant_id = seed.tenant_id
 AND role.code = seed.role_code
 AND role.deleted = b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM system_user_role existing
    WHERE existing.tenant_id = user.tenant_id
      AND existing.user_id = user.id
      AND existing.role_id = role.id
      AND existing.deleted = b'0'
);

DROP TEMPORARY TABLE tmp_hrm_hr_users;
DROP TEMPORARY TABLE tmp_hrm_hr_roles;
