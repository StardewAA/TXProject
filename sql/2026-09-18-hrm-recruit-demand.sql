-- ============================================================
-- HRM 迭代 1.1：用工需求单
-- 1) 新建 hrm_recruit_demand（项目部/用工部门提需求，HR 按需求招聘、回写到岗人数）
-- 2) hrm_recruit_post 增加 demand_id，关联需求单
-- 3) 招聘管理下挂「用工需求单」菜单与权限，并授权给租户 2020 的超管与 HR 角色
-- 幂等：可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `hrm_recruit_demand` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '需求编号',
  `demand_no`          varchar(64)  NOT NULL COMMENT '需求单号（REQ-yyyyMMdd-序号）',
  `demand_name`        varchar(128) NOT NULL COMMENT '需求名称',
  `dept_id`            bigint       DEFAULT NULL COMMENT '用工部门/项目部编号',
  `company_profile_id` bigint       DEFAULT NULL COMMENT '用工主体编号',
  `work_type`          varchar(64)  DEFAULT NULL COMMENT '工种',
  `cert_require`       varchar(512) DEFAULT NULL COMMENT '证照要求（多个用、分隔）',
  `headcount`          int          NOT NULL DEFAULT 0 COMMENT '需求人数',
  `onboard_count`      int          NOT NULL DEFAULT 0 COMMENT '已到岗人数',
  `plan_entry_date`    date         DEFAULT NULL COMMENT '计划进场日期',
  `expect_salary`      varchar(64)  DEFAULT NULL COMMENT '参考单价',
  `urgency`            tinyint      DEFAULT 1 COMMENT '紧急程度：1 普通 2 紧急 3 特急',
  `status`             tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1 待招聘 2 招聘中 3 已完成 4 已取消',
  `applicant`          varchar(64)  DEFAULT NULL COMMENT '提出人',
  `contact_mobile`     varchar(32)  DEFAULT NULL COMMENT '联系方式',
  `remark`             varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`            varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`            varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_demand_no` (`demand_no`),
  KEY `idx_status` (`status`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 用工需求单';

-- 招聘职位关联需求单（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'hrm_recruit_post' AND column_name = 'demand_id');
SET @ddl = IF(@col_exists = 0,
              'ALTER TABLE `hrm_recruit_post` ADD COLUMN `demand_id` bigint DEFAULT NULL COMMENT ''关联用工需求单编号'', ADD KEY `idx_demand_id` (`demand_id`)',
              'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ 菜单与权限（挂在「招聘管理」下，sort=5 排在最前） ============
INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6820, '用工需求单', 'hrm:recruit:demand:query', 2, 5, 1486, 'demand', 'lucide:clipboard-list',
        'hrm/recruit/demand/index', 'RecruitDemand', 0, b'1', b'1', b'1', 'demand-dev', 'demand-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id),
                        path = VALUES(path), component = VALUES(component), component_name = VALUES(component_name),
                        sort = VALUES(sort), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6821, '需求查询', 'hrm:recruit:demand:query', 3, 1, 6820, '', '', '', 0, b'1', b'1', b'1', 'demand-dev', 'demand-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6822, '需求新增', 'hrm:recruit:demand:create', 3, 2, 6820, '', '', '', 0, b'1', b'1', b'1', 'demand-dev', 'demand-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6823, '需求修改', 'hrm:recruit:demand:update', 3, 3, 6820, '', '', '', 0, b'1', b'1', b'1', 'demand-dev', 'demand-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6824, '需求删除', 'hrm:recruit:demand:delete', 3, 4, 6820, '', '', '', 0, b'1', b'1', b'1', 'demand-dev', 'demand-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

-- 授权：租户 2020 的「甲方超级管理员」「HR角色」
INSERT INTO `system_role_menu` (role_id, menu_id, tenant_id, creator, updater)
SELECT r.id, m.id, r.tenant_id, 'demand-dev', 'demand-dev'
FROM `system_role` r
JOIN `system_menu` m ON m.id IN (6820, 6821, 6822, 6823, 6824)
WHERE r.tenant_id = 2020 AND r.deleted = 0
  AND (r.id = 60366 OR r.id = 60468)
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM `system_role_menu`
                                 WHERE role_id = r.id AND menu_id = m.id AND tenant_id = r.tenant_id) t);
