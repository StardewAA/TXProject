-- =============================================================
-- HRM 工号规则管理菜单与权限
-- 菜单挂在「员工管理」(1477) 下；权限授予原本拥有 hrm:employee:update 的角色
-- 幂等：可重复执行
-- =============================================================

INSERT INTO `ruoyi-vue-pro`.`system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`,
     `updater`, `update_time`, `deleted`)
SELECT 6995, '工号规则', 'hrm:employee-no-rule:query', 2, 20, 1477, 'no-rule', 'ep:postcard',
       'hrm/employee/no-rule/index', 'HrmEmployeeNoRule', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `ruoyi-vue-pro`.`system_menu` WHERE `permission` = 'hrm:employee-no-rule:query'
);

INSERT INTO `ruoyi-vue-pro`.`system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`,
     `updater`, `update_time`, `deleted`)
SELECT 6996, '工号规则维护', 'hrm:employee-no-rule:update', 3, 1, 6995, '', '#', '',
       '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `ruoyi-vue-pro`.`system_menu` WHERE `permission` = 'hrm:employee-no-rule:update'
);

-- 授予原本拥有「员工档案维护」权限的角色，保证升级后无人丢权限
INSERT INTO `ruoyi-vue-pro`.`system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT src.role_id, target.id, '1', NOW(), '1', NOW(), b'0', src.tenant_id
FROM (
    SELECT rm.role_id, r.tenant_id
    FROM `ruoyi-vue-pro`.`system_role_menu` rm
    JOIN `ruoyi-vue-pro`.`system_role` r ON r.id = rm.role_id
    JOIN `ruoyi-vue-pro`.`system_menu` m ON m.id = rm.menu_id
    WHERE m.permission = 'hrm:employee:update' AND rm.deleted = b'0'
    GROUP BY rm.role_id, r.tenant_id
) src
JOIN `ruoyi-vue-pro`.`system_menu` target
  ON target.permission IN ('hrm:employee-no-rule:query', 'hrm:employee-no-rule:update')
WHERE NOT EXISTS (
    SELECT 1 FROM `ruoyi-vue-pro`.`system_role_menu` exist
    WHERE exist.role_id = src.role_id AND exist.menu_id = target.id
);
