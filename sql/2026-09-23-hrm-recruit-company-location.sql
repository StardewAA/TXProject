-- HRM 招聘企业主页与职位工作地址。

SET @schema_name := DATABASE();
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'hrm_recruit_post' AND COLUMN_NAME = 'work_address') = 0,
  'ALTER TABLE `hrm_recruit_post` ADD COLUMN `work_address` varchar(255) DEFAULT NULL COMMENT ''详细工作地址''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'hrm_recruit_post' AND COLUMN_NAME = 'longitude') = 0,
  'ALTER TABLE `hrm_recruit_post` ADD COLUMN `longitude` decimal(10,6) DEFAULT NULL COMMENT ''工作地址经度（BD09 百度坐标系）''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'hrm_recruit_post' AND COLUMN_NAME = 'latitude') = 0,
  'ALTER TABLE `hrm_recruit_post` ADD COLUMN `latitude` decimal(10,6) DEFAULT NULL COMMENT ''工作地址纬度（BD09 百度坐标系）''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `hrm_recruit_company_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '企业招聘档案编号',
  `name` varchar(255) NOT NULL COMMENT '企业名称快照',
  `short_name` varchar(128) DEFAULT NULL COMMENT '企业简称',
  `logo` varchar(500) DEFAULT NULL COMMENT '企业 Logo 文件地址',
  `industry` varchar(128) DEFAULT NULL COMMENT '所属行业',
  `scale` varchar(64) DEFAULT NULL COMMENT '企业规模',
  `nature` varchar(64) DEFAULT NULL COMMENT '企业性质',
  `summary` varchar(2000) DEFAULT NULL COMMENT '企业简介',
  `products` text COMMENT '产品与服务介绍 JSON 数组',
  `address` varchar(255) DEFAULT NULL COMMENT '企业地址',
  `area_id` int DEFAULT NULL COMMENT '企业所在地区编号',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '企业地址经度（BD09）',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '企业地址纬度（BD09）',
  `website` varchar(255) DEFAULT NULL COMMENT '企业官网',
  `established_date` date DEFAULT NULL COMMENT '成立日期',
  `registered_capital` varchar(64) DEFAULT NULL COMMENT '注册资本',
  `credit_code` varchar(64) DEFAULT NULL COMMENT '统一社会信用代码',
  `legal_person` varchar(64) DEFAULT NULL COMMENT '法定代表人',
  `data_source` varchar(64) DEFAULT NULL COMMENT '资料来源',
  `data_source_url` varchar(500) DEFAULT NULL COMMENT '资料来源链接',
  `verified` bit(1) NOT NULL DEFAULT b'0' COMMENT '信息是否已人工核实',
  `sort` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 已发布，1 已下架',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '所属租户编号（一租户一份招聘档案）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hrm_recruit_company_profile_tenant` (`tenant_id`, `deleted`),
  KEY `idx_hrm_recruit_company_profile_status` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HRM 招聘企业主页档案';

SET @recruit_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `name` = '招聘管理' AND `path` = 'recruit' AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '企业信息', 'hrm:recruit:company:query', 2, 25, @recruit_menu_id, 'company', 'ep:office-building',
       'hrm/recruit/company/index', 'HrmRecruitCompany', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @recruit_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @recruit_menu_id AND `path` = 'company' AND `deleted` = b'0'
  );

SET @company_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @recruit_menu_id AND `path` = 'company' AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '企业信息查询', 'hrm:recruit:company:query', 3, 1, @company_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @company_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @company_menu_id AND `permission` = 'hrm:recruit:company:query' AND `deleted` = b'0'
  );

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '企业信息修改', 'hrm:recruit:company:update', 3, 2, @company_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @company_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @company_menu_id AND `permission` = 'hrm:recruit:company:update' AND `deleted` = b'0'
  );

INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT existing_permission.`role_id`, permission_menu.`id`, 'admin', NOW(), 'admin', NOW(), b'0',
       IFNULL(role.`tenant_id`, 0)
FROM `system_role_menu` existing_permission
JOIN `system_menu` existing_menu
  ON existing_menu.`id` = existing_permission.`menu_id`
 AND existing_menu.`permission` = 'hrm:recruit:post:query'
 AND existing_menu.`deleted` = b'0'
JOIN `system_role` role ON role.`id` = existing_permission.`role_id`
JOIN `system_menu` permission_menu
  ON permission_menu.`parent_id` = @company_menu_id
 AND permission_menu.`permission` IN ('hrm:recruit:company:query', 'hrm:recruit:company:update')
 AND permission_menu.`deleted` = b'0'
WHERE existing_permission.`deleted` = b'0'
  AND @company_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` role_permission
    WHERE role_permission.`role_id` = existing_permission.`role_id`
      AND role_permission.`menu_id` = permission_menu.`id`
      AND role_permission.`deleted` = b'0'
  );
