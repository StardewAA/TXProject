-- ============================================================
-- HRM 迭代 1.3：员工黑名单
-- 1) 新建 hrm_employee_blacklist
-- 2) 员工管理下挂「黑名单」菜单与权限（授权租户 2020 超管与 HR 角色）
-- 幂等：可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `hrm_employee_blacklist` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `employee_id`    bigint       DEFAULT NULL COMMENT '员工编号（可空）',
  `name`           varchar(64)  NOT NULL COMMENT '姓名',
  `mobile`         varchar(32)  NOT NULL COMMENT '手机号（命中校验主要依据）',
  `id_card_no`     varchar(32)  DEFAULT NULL COMMENT '身份证号',
  `reason`         varchar(500) NOT NULL COMMENT '加入原因',
  `source`         tinyint      DEFAULT 1 COMMENT '来源：1 HRM 手工登记 2 现场上报 3 其他',
  `join_time`      datetime     DEFAULT NULL COMMENT '加入时间',
  `release_time`   datetime     DEFAULT NULL COMMENT '解除时间',
  `permanent_flag` bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否永久',
  `status`         tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1 生效 2 已解除',
  `remark`         varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`        varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_mobile` (`mobile`, `status`),
  KEY `idx_name` (`name`),
  KEY `idx_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 员工黑名单';

-- ============ 菜单与权限（挂在「员工管理」下） ============
INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6830, '黑名单', 'hrm:employee:blacklist:query', 2, 10, 1477, 'blacklist', 'lucide:user-x',
        'hrm/employee/blacklist/index', 'HrmEmployeeBlacklist', 0, b'1', b'1', b'1', 'blacklist-dev', 'blacklist-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id),
                        path = VALUES(path), component = VALUES(component), component_name = VALUES(component_name),
                        sort = VALUES(sort), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6831, '黑名单查询', 'hrm:employee:blacklist:query', 3, 1, 6830, '', '', '', 0, b'1', b'1', b'1', 'blacklist-dev', 'blacklist-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6832, '黑名单新增', 'hrm:employee:blacklist:create', 3, 2, 6830, '', '', '', 0, b'1', b'1', b'1', 'blacklist-dev', 'blacklist-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6833, '黑名单修改/解除', 'hrm:employee:blacklist:update', 3, 3, 6830, '', '', '', 0, b'1', b'1', b'1', 'blacklist-dev', 'blacklist-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6834, '黑名单删除', 'hrm:employee:blacklist:delete', 3, 4, 6830, '', '', '', 0, b'1', b'1', b'1', 'blacklist-dev', 'blacklist-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_role_menu` (role_id, menu_id, tenant_id, creator, updater)
SELECT r.id, m.id, r.tenant_id, 'blacklist-dev', 'blacklist-dev'
FROM `system_role` r
JOIN `system_menu` m ON m.id IN (6830, 6831, 6832, 6833, 6834)
WHERE r.tenant_id = 2020 AND r.deleted = 0
  AND (r.id = 60366 OR r.id = 60468)
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM `system_role_menu`
                                 WHERE role_id = r.id AND menu_id = m.id AND tenant_id = r.tenant_id) t);
