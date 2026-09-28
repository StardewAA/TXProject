-- ============================================================
-- HRM 第 5 步（第三批·上）：证照中心
--   对应需求文档 R3（证照类型字典 + 一人多证 + 证照档案 + 到期预警）
--   1) 新建 hrm_certificate_type（证照类型字典，含是否特种作业/必持/预警天数）
--   2) hrm_employee_certificate 扩展：类型、复审日期、扫描件附件、核验状态
--   3) 「HRM 人力资源」下新增「证照管理」（证照类型 / 证照台账）
-- 执行库：ruoyi-vue-pro
-- 说明：ALTER 与 CREATE 不支持重复执行（重复执行会报已存在，可忽略）；
--       数据部分使用 WHERE NOT EXISTS 保护，可重复执行。
-- ============================================================

-- ------------------------------------------------------------
-- 【1】证照类型字典
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hrm_certificate_type` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name`               varchar(128) NOT NULL COMMENT '证照类型名称',
  `code`               varchar(64)  NOT NULL COMMENT '编码',
  `special_operation`  bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否特种作业',
  `mandatory`          bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否必持（缺证不得上岗）',
  `need_review`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否需要复审',
  `warn_days`          int          NOT NULL DEFAULT 60 COMMENT '到期预警提前天数',
  `issuing_authority`  varchar(128) DEFAULT NULL COMMENT '发证机关',
  `sort`               int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `status`             tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0 启用，1 停用',
  `remark`             varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`            varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`            varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 证照类型字典';

-- ------------------------------------------------------------
-- 【2】员工证照扩展（原表只有名称/编号/有效期，缺类型、附件、核验）
-- ------------------------------------------------------------
ALTER TABLE `hrm_employee_certificate`
  ADD COLUMN `type_id`        bigint        DEFAULT NULL COMMENT '证照类型编号（hrm_certificate_type.id）' AFTER `employee_id`,
  ADD COLUMN `review_date`    date          DEFAULT NULL COMMENT '复审日期' AFTER `end_time`,
  ADD COLUMN `file_urls`      varchar(2048) DEFAULT NULL COMMENT '证件扫描件/照片（逗号分隔地址串）' AFTER `issuing_time`,
  ADD COLUMN `verify_status`  tinyint       NOT NULL DEFAULT 0 COMMENT '核验状态：0 未核验，1 已核验，2 核验不通过' AFTER `file_urls`,
  ADD COLUMN `verify_user_id` bigint        DEFAULT NULL COMMENT '核验人（后台用户编号）' AFTER `verify_status`,
  ADD COLUMN `verify_time`    datetime      DEFAULT NULL COMMENT '核验时间' AFTER `verify_user_id`;

-- ------------------------------------------------------------
-- 【3】菜单：HRM → 证照管理（证照类型 / 证照台账）
-- ------------------------------------------------------------
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6783, '证照管理', '', 1, 28, 1476, 'certificate', 'ep:postcard', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6783);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6784, '证照类型', 'hrm:certificate-type:query', 2, 1, 6783, 'type', 'ep:collection-tag', 'hrm/certificate/type/index', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6784);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6785, '证照台账', 'hrm:certificate:ledger:query', 2, 2, 6783, 'ledger', 'ep:files', 'hrm/certificate/ledger/index', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6785);

-- 权限按钮
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6786, '证照类型新增', 'hrm:certificate-type:create', 3, 1, 6784, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6786);
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6787, '证照类型修改', 'hrm:certificate-type:update', 3, 2, 6784, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6787);
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6788, '证照类型删除', 'hrm:certificate-type:delete', 3, 3, 6784, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6788);
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6789, '证照核验', 'hrm:certificate:ledger:verify', 3, 1, 6785, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6789);
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6790, '证照台账导出', 'hrm:certificate:ledger:export', 3, 2, 6785, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6790);

-- 给已拥有「HRM 人力资源」的角色补授
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', IFNULL(r.`tenant_id`, 0)
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
JOIN `system_menu` m ON m.`id` IN (6783, 6784, 6785, 6786, 6787, 6788, 6789, 6790)
WHERE rm.`deleted` = b'0' AND rm.`menu_id` = 1476
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id` AND x.`deleted` = b'0'
  );

-- ------------------------------------------------------------
-- 【4】种子：防腐保温行业常见证照类型（租户 2020）
-- ------------------------------------------------------------
INSERT INTO `hrm_certificate_type`
  (`name`, `code`, `special_operation`, `mandatory`, `need_review`, `warn_days`, `issuing_authority`, `sort`, `status`, `remark`, `tenant_id`, `creator`, `updater`)
SELECT t.`name`, t.`code`, t.`special_operation`, t.`mandatory`, t.`need_review`, t.`warn_days`, t.`authority`, t.`sort`, 0, t.`remark`, 2020, 'admin', 'admin'
FROM (
  SELECT '高处作业操作证' AS `name`, 'HIGH_WORK' AS `code`, b'1' AS `special_operation`, b'1' AS `mandatory`, b'1' AS `need_review`, 60 AS `warn_days`, '应急管理局' AS `authority`, 1 AS `sort`, '登高架设、高处安装维护拆除作业' AS `remark`
  UNION ALL SELECT '焊接与热切割作业证', 'WELDING', b'1', b'1', b'1', 60, '应急管理局', 2, '电焊、气焊、热切割作业'
  UNION ALL SELECT '架子工（登高架设）证', 'SCAFFOLDER', b'1', b'1', b'1', 60, '应急管理局', 3, '脚手架搭设与拆除'
  UNION ALL SELECT '喷砂（涂装）作业证', 'SANDBLASTING', b'1', b'1', b'1', 60, '应急管理局', 4, '喷砂除锈、涂料涂装作业'
  UNION ALL SELECT '无损检测（探伤）资格证', 'NDT', b'1', b'1', b'1', 90, '市场监督管理局', 5, 'UT/RT/PT/MT 等检测项目'
  UNION ALL SELECT '起重机械作业证', 'CRANE', b'1', b'0', b'1', 60, '市场监督管理局', 6, '塔吊、汽车吊、起重指挥'
  UNION ALL SELECT '电工证（低压/高压）', 'ELECTRICIAN', b'1', b'0', b'1', 60, '应急管理局', 7, '低压/高压电工作业'
  UNION ALL SELECT '安全生产管理人员（三类人员 C 证）', 'SAFETY_C', b'0', b'1', b'1', 90, '住建主管部门', 8, '项目安全员持证'
  UNION ALL SELECT '职业健康体检合格证', 'HEALTH_CHECK', b'0', b'1', b'0', 30, '职业健康体检机构', 9, '接触粉尘、噪声等岗位体检'
  UNION ALL SELECT '职业技能等级证（防腐/保温工）', 'SKILL_LEVEL', b'0', b'0', b'0', 0, '人社部门/评价机构', 10, '技能等级（初级/中级/高级）'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM `hrm_certificate_type` x WHERE x.`tenant_id` = 2020 AND x.`code` = t.`code` AND x.`deleted` = b'0'
);

-- 核对
SELECT `id`, `name`, `code`, `special_operation`, `mandatory`, `warn_days`
FROM `hrm_certificate_type` WHERE `deleted` = b'0' ORDER BY `sort`;
SELECT `id`, `name`, `parent_id`, `permission` FROM `system_menu` WHERE `id` BETWEEN 6783 AND 6790 ORDER BY `id`;
