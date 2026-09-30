-- ============================================================
-- HRM 迭代 3.2：比例上限配置（按计薪项 / 参保项分别设置）
-- 与 sql/2026-09-24-hrm-salary-rate-limit.sql 内容一致，用于已部署库的补齐
-- 幂等：可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `hrm_salary_rate_limit` (
  `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type`              tinyint      NOT NULL COMMENT '业务类型：1 考勤计薪规则 2 参保方案',
  `item_code`             varchar(64)  NOT NULL COMMENT '计薪项/参保项编码',
  `item_name`             varchar(64)  NOT NULL COMMENT '计薪项/参保项名称',
  `rate_limit`            decimal(6,2) NOT NULL DEFAULT 100.00 COMMENT '比例上限（%）：考勤计薪项使用',
  `corporate_rate_limit`  decimal(6,2) DEFAULT NULL COMMENT '公司缴纳比例上限（%）：参保项目使用',
  `personal_rate_limit`   decimal(6,2) DEFAULT NULL COMMENT '个人缴纳比例上限（%）：参保项目使用',
  `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
  `remark`                varchar(255) DEFAULT NULL COMMENT '备注',
  `creator`               varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`               varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_biz_item` (`tenant_id`, `biz_type`, `item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 比例上限配置（按计薪项/参保项）';

INSERT INTO `hrm_salary_rate_limit`
  (`biz_type`, `item_code`, `item_name`, `rate_limit`, `status`, `remark`, `creator`, `updater`, `tenant_id`)
VALUES
  (1, 'absenceDeductRate', '缺勤扣款比例', 200.00, 0, '缺勤扣款比例上限；沿用旧口径 200%，可按需下调', 'salary-dev', 'salary-dev', 2020),
  (1, 'fullAttendanceBonusRate', '满勤奖比例', 200.00, 0, '满勤奖比例上限（按日薪计算）；沿用旧口径 200%', 'salary-dev', 'salary-dev', 2020),
  (1, 'personalLeaveRate', '事假扣款比例', 200.00, 0, '事假扣款比例上限；沿用旧口径 200%', 'salary-dev', 'salary-dev', 2020),
  (1, 'sickLeaveRate', '病假扣款比例', 200.00, 0, '病假扣款比例上限；沿用旧口径 200%', 'salary-dev', 'salary-dev', 2020)
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `hrm_salary_rate_limit`
  (`biz_type`, `item_code`, `item_name`, `corporate_rate_limit`, `personal_rate_limit`, `status`, `remark`,
   `creator`, `updater`, `tenant_id`)
SELECT 2, t.code, t.name, t.corporate_limit, t.personal_limit, 0,
       '参保项目缴纳比例上限，参保方案中不得超过该值', 'salary-dev', 'salary-dev', 2020
FROM (
  SELECT '1' AS code, '养老保险' AS name, 25.00 AS corporate_limit, 10.00 AS personal_limit
  UNION ALL SELECT '2', '医疗保险', 15.00, 5.00
  UNION ALL SELECT '3', '失业保险', 5.00, 5.00
  UNION ALL SELECT '4', '工伤保险', 5.00, 1.00
  UNION ALL SELECT '5', '生育保险', 5.00, 1.00
  UNION ALL SELECT '6', '补充大病医疗', 10.00, 10.00
  UNION ALL SELECT '7', '补充养老', 20.00, 20.00
  UNION ALL SELECT '8', '残保金', 5.00, 1.00
  UNION ALL SELECT '9', '社保自定义', 100.00, 100.00
  UNION ALL SELECT '10', '公积金', 12.00, 12.00
  UNION ALL SELECT '11', '公积金自定义', 100.00, 100.00
) t
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 计薪项（薪资项）单位：选择计薪项后自动带出该项单位
SET @unit_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hrm_salary_option'
                      AND COLUMN_NAME = 'unit');
SET @unit_ddl = IF(@unit_exists = 0,
  'ALTER TABLE `hrm_salary_option` ADD COLUMN `unit` varchar(16) DEFAULT NULL COMMENT ''单位（如 元/月、元/天、次、%）'' AFTER `name`',
  'SELECT 1');
PREPARE unit_stmt FROM @unit_ddl;
EXECUTE unit_stmt;
DEALLOCATE PREPARE unit_stmt;

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6910, '比例上限', 'hrm:salary:rate-limit:query', 2, 8, 1533, 'rate-limit',
        'hrm/salary/config/rate-limit/index', 'HrmSalaryRateLimit', 0, b'1', b'1', b'1', 'salary-dev', 'salary-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id),
                        path = VALUES(path), component = VALUES(component),
                        component_name = VALUES(component_name), deleted = b'0';

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, component, component_name,
                           status, visible, keep_alive, always_show, creator, updater)
VALUES (6911, '比例上限修改', 'hrm:salary:rate-limit:update', 3, 8, 1533, '', '', '', 0, b'1', b'1', b'1', 'salary-dev', 'salary-dev')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = b'0';

INSERT INTO `system_role_menu` (role_id, menu_id, tenant_id, creator, updater)
SELECT r.id, m.id, r.tenant_id, 'salary-dev', 'salary-dev'
FROM `system_role` r
JOIN `system_menu` m ON m.id IN (6910, 6911)
WHERE r.tenant_id = 2020 AND r.deleted = 0
  AND (r.id = 60366 OR r.id = 60468)
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM `system_role_menu`
                                 WHERE role_id = r.id AND menu_id = m.id AND tenant_id = r.tenant_id) t);
