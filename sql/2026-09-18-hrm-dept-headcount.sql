-- ============================================================
-- HRM 迭代 1.4：部门编制管理
-- 1) 新建 hrm_dept_headcount（按部门设定编制人数）
-- 2) HRM 下挂「部门编制」菜单与权限（授权租户 2020 超管与 HR 角色）
-- 幂等：可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `hrm_dept_headcount` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `dept_id`     bigint       NOT NULL COMMENT '部门编号（关联 system 部门）',
  `headcount`   int          NOT NULL DEFAULT 0 COMMENT '编制人数',
  `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 部门编制';

-- ============ 菜单与权限（挂在 HRM 人力资源下，排在组织管理之后） ============
INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6850, '部门编制', 'hrm:employee:headcount:query', 2, 9, 1476, 'headcount', 'lucide:users-round',
        'hrm/dept/headcount/index', 'HrmDeptHeadcount', 0, b'1', b'1', b'1', 'headcount-dev', 'headcount-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id),
                        path = VALUES(path), component = VALUES(component), component_name = VALUES(component_name),
                        sort = VALUES(sort), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6851, '编制查询', 'hrm:employee:headcount:query', 3, 1, 6850, '', '', '', 0, b'1', b'1', b'1', 'headcount-dev', 'headcount-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6852, '编制新增', 'hrm:employee:headcount:create', 3, 2, 6850, '', '', '', 0, b'1', b'1', b'1', 'headcount-dev', 'headcount-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6853, '编制修改', 'hrm:employee:headcount:update', 3, 3, 6850, '', '', '', 0, b'1', b'1', b'1', 'headcount-dev', 'headcount-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6854, '编制删除', 'hrm:employee:headcount:delete', 3, 4, 6850, '', '', '', 0, b'1', b'1', b'1', 'headcount-dev', 'headcount-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_role_menu` (role_id, menu_id, tenant_id, creator, updater)
SELECT r.id, m.id, r.tenant_id, 'headcount-dev', 'headcount-dev'
FROM `system_role` r
JOIN `system_menu` m ON m.id IN (6850, 6851, 6852, 6853, 6854)
WHERE r.tenant_id = 2020 AND r.deleted = 0
  AND (r.id = 60366 OR r.id = 60468)
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM `system_role_menu`
                                 WHERE role_id = r.id AND menu_id = m.id AND tenant_id = r.tenant_id) t);
