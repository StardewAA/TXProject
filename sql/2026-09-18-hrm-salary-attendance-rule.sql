-- ============================================================
-- HRM 迭代 3.1：考勤计薪规则（出勤/缺勤按比例折算，HR 可配）
-- 1) 新建 hrm_salary_attendance_rule
-- 2) 写一条默认规则（防腐保温工程队口径）
-- 3) 在「薪资管理」下登记 4 个权限按钮并授权租户 2020 超管与 HR 角色
--    （页面下一轮补；先保证接口权限可分配）
-- 幂等：可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `hrm_salary_attendance_rule` (
  `id`                          bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name`                        varchar(128) NOT NULL COMMENT '规则名称',
  `full_attendance_days`        decimal(5,2) NOT NULL DEFAULT 26 COMMENT '满勤天数',
  `daily_wage_mode`             tinyint      NOT NULL DEFAULT 1 COMMENT '日薪折算方式：1 月薪÷满勤天数 2 按日薪',
  `absence_deduct_rate`         decimal(6,2) NOT NULL DEFAULT 100 COMMENT '缺勤扣款比例（%）',
  `full_attendance_bonus_rate`  decimal(6,2) NOT NULL DEFAULT 0 COMMENT '满勤奖比例（%，按日薪）',
  `personal_leave_rate`         decimal(6,2) NOT NULL DEFAULT 100 COMMENT '事假扣款比例（%）',
  `sick_leave_rate`             decimal(6,2) NOT NULL DEFAULT 60 COMMENT '病假扣款比例（%）',
  `late_to_absence_times`       int          NOT NULL DEFAULT 0 COMMENT '迟到早退几次折算 1 天缺勤',
  `default_flag`                bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否默认规则',
  `status`                      tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
  `remark`                      varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`                     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`                 datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`                     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`                 datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`                   bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_default` (`default_flag`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 考勤计薪规则';

-- 默认规则（防腐保温工程队口径：满勤 26 天、月薪÷满勤、缺勤全额扣、满勤奖 10%、事假全额扣、病假扣 60%、迟到 3 次算 1 天）
INSERT INTO `hrm_salary_attendance_rule`
  (`id`, `name`, `full_attendance_days`, `daily_wage_mode`, `absence_deduct_rate`, `full_attendance_bonus_rate`,
   `personal_leave_rate`, `sick_leave_rate`, `late_to_absence_times`, `default_flag`, `status`, `remark`,
   `creator`, `updater`, `tenant_id`)
VALUES (6900, '防腐保温工程队标准计薪规则', 26.00, 1, 100.00, 10.00, 100.00, 60.00, 3, b'1', 0,
        '满勤 26 天；月薪÷满勤得日薪；缺勤全额扣；满勤奖按日薪 10%；事假全额扣；病假扣 60%；迟到早退 3 次折算 1 天',
        'salary-dev', 'salary-dev', 2020)
ON DUPLICATE KEY UPDATE name = VALUES(name), full_attendance_days = VALUES(full_attendance_days),
                        absence_deduct_rate = VALUES(absence_deduct_rate),
                        full_attendance_bonus_rate = VALUES(full_attendance_bonus_rate),
                        personal_leave_rate = VALUES(personal_leave_rate), sick_leave_rate = VALUES(sick_leave_rate),
                        late_to_absence_times = VALUES(late_to_absence_times), default_flag = b'1', status = 0,
                        deleted = b'0';

-- 权限按钮（挂在「薪资管理」1533 下；页面下一轮补）
INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6860, '考勤计薪规则查询', 'hrm:salary:attendance-rule:query', 3, 20, 1533, '', '', '', 0, b'1', b'1', b'1', 'salary-dev', 'salary-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6861, '考勤计薪规则新增', 'hrm:salary:attendance-rule:create', 3, 21, 1533, '', '', '', 0, b'1', b'1', b'1', 'salary-dev', 'salary-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6862, '考勤计薪规则修改', 'hrm:salary:attendance-rule:update', 3, 22, 1533, '', '', '', 0, b'1', b'1', b'1', 'salary-dev', 'salary-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6863, '考勤计薪规则删除', 'hrm:salary:attendance-rule:delete', 3, 23, 1533, '', '', '', 0, b'1', b'1', b'1', 'salary-dev', 'salary-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_role_menu` (role_id, menu_id, tenant_id, creator, updater)
SELECT r.id, m.id, r.tenant_id, 'salary-dev', 'salary-dev'
FROM `system_role` r
JOIN `system_menu` m ON m.id IN (6860, 6861, 6862, 6863)
WHERE r.tenant_id = 2020 AND r.deleted = 0
  AND (r.id = 60366 OR r.id = 60468)
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM `system_role_menu`
                                 WHERE role_id = r.id AND menu_id = m.id AND tenant_id = r.tenant_id) t);
