-- ============================================================
-- HRM P0-1：合同管理切片（模板管理 + 签署记录）
--   1) 菜单调整：「用工主体信息」从「合同管理」移出，直挂 HRM 人力资源
--   2) 修正「合同管理」与「考勤管理」排序同为 30 的冲突
--   3) 「合同管理」下新增子菜单：模板管理、签署记录（含权限按钮）
--   4) 合同模板增加：适用用工主体、模板变量清单、变更留痕
--   5) 新增签署记录表 hrm_contract_sign_record
-- 执行库：ruoyi-vue-pro
-- 说明：ALTER 语句不支持重复执行（重复执行会报列已存在，可忽略）；
--       菜单部分使用 WHERE NOT EXISTS 保护，可重复执行。
-- 执行后需清理权限缓存（见文末）
-- ============================================================

-- ------------------------------------------------------------
-- 【1】合同模板扩展字段
-- ------------------------------------------------------------
ALTER TABLE `hrm_contract_template`
  ADD COLUMN `employer_id`    bigint       NULL COMMENT '适用用工主体编号（hrm_company_profile.id）' AFTER `employ_type`,
  ADD COLUMN `variables`      varchar(2000) NULL COMMENT '模板变量清单（逗号分隔，如 ${companyName},${employeeName}）' AFTER `file_hash`,
  ADD COLUMN `change_note`    varchar(500) NULL COMMENT '本次变更说明' AFTER `variables`,
  ADD COLUMN `change_user_id` bigint       NULL COMMENT '变更人编号' AFTER `change_note`,
  ADD COLUMN `change_time`    datetime     NULL COMMENT '变更时间' AFTER `change_user_id`;

-- ------------------------------------------------------------
-- 【2】签署记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hrm_contract_sign_record` (
  `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contract_id`       bigint       NOT NULL COMMENT '合同编号（hrm_employee_contract.id）',
  `employee_id`       bigint       NOT NULL COMMENT '员工编号',
  `contract_no`       varchar(64)  DEFAULT NULL COMMENT '合同编号（冗余，便于台账展示）',
  `sign_employer_id`  bigint       DEFAULT NULL COMMENT '签署用工主体编号（hrm_company_profile.id）',
  `sign_employer_name` varchar(128) DEFAULT NULL COMMENT '签署用工主体名称（冗余）',
  `sign_person`       varchar(64)  DEFAULT NULL COMMENT '签署人（乙方签署人姓名）',
  `sign_way`          tinyint      DEFAULT NULL COMMENT '签署方式：1 线下签署，2 电子签章',
  `sign_time`         datetime     DEFAULT NULL COMMENT '签署时间',
  `file_urls`         varchar(2048) DEFAULT NULL COMMENT '签署件附件地址列表（与 hrm_employee_contract.file_urls 一致，存逗号分隔的地址串）',
  `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0 待签，1 已签，2 已作废',
  `urge_count`        int          NOT NULL DEFAULT 0 COMMENT '催办次数',
  `last_urge_time`    datetime     DEFAULT NULL COMMENT '最后催办时间',
  `remark`            varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`           varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`           varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_employee_id` (`employee_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 合同签署记录';

-- ------------------------------------------------------------
-- 【3】修正菜单排序冲突：合同管理 30 -> 25（考勤管理保持 30）
-- ------------------------------------------------------------
UPDATE `system_menu`
SET `sort` = 25, `updater` = 'admin', `update_time` = NOW()
WHERE `id` = 6750 AND `name` = '合同管理' AND `deleted` = b'0';

-- ------------------------------------------------------------
-- 【4】「用工主体信息」移出「合同管理」，直挂 HRM 人力资源（1476）
-- ------------------------------------------------------------
UPDATE `system_menu`
SET `parent_id` = 1476, `sort` = 26, `updater` = 'admin', `update_time` = NOW()
WHERE `name` = '用工主体信息' AND `deleted` = b'0' AND `parent_id` = 6750;

-- 若历史授权链路缺少 HRM 人力资源父菜单，补齐，避免菜单树被整体过滤
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, 1476, 'admin', NOW(), 'admin', NOW(), b'0', IFNULL(r.`tenant_id`, 0)
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
JOIN `system_menu` m ON m.`id` = rm.`menu_id` AND m.`deleted` = b'0'
WHERE rm.`deleted` = b'0' AND m.`name` = '用工主体信息'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = 1476 AND x.`deleted` = b'0'
  );

-- ------------------------------------------------------------
-- 【5】「合同管理」下新增子菜单
-- ------------------------------------------------------------
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6751, '模板管理', 'hrm:contract:template:query', 2, 1, 6750, 'template', 'ep:document-copy', 'hrm/contract/template/index', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6751);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6752, '签署记录', 'hrm:contract:sign:query', 2, 2, 6750, 'sign-record', 'ep:edit-pen', 'hrm/contract/sign-record/index', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6752);

-- 权限按钮
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6753, '模板查询', 'hrm:contract:template:query', 3, 1, 6751, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6753);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6754, '模板新增', 'hrm:contract:template:create', 3, 2, 6751, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6754);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6755, '模板修改', 'hrm:contract:template:update', 3, 3, 6751, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6755);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6756, '模板删除', 'hrm:contract:template:delete', 3, 4, 6751, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6756);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6757, '模板上传', 'hrm:contract:template:upload', 3, 5, 6751, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6757);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6758, '模板下载', 'hrm:contract:template:download', 3, 6, 6751, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6758);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6759, '签署记录查询', 'hrm:contract:sign:query', 3, 1, 6752, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6759);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6760, '签署记录新增', 'hrm:contract:sign:create', 3, 2, 6752, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6760);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6761, '签署记录修改', 'hrm:contract:sign:update', 3, 3, 6752, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6761);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6762, '签署记录删除', 'hrm:contract:sign:delete', 3, 4, 6752, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6762);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6763, '签署催办', 'hrm:contract:sign:urge', 3, 5, 6752, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6763);

-- ------------------------------------------------------------
-- 【6】给已拥有「合同管理」的角色补授新增菜单
-- ------------------------------------------------------------
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, nm.`id`, 'admin', NOW(), 'admin', NOW(), b'0', IFNULL(r.`tenant_id`, 0)
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
JOIN `system_menu` nm ON nm.`id` IN (6751, 6752, 6753, 6754, 6755, 6756, 6757, 6758,
                                     6759, 6760, 6761, 6762, 6763)
WHERE rm.`deleted` = b'0' AND rm.`menu_id` = 6750
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = nm.`id` AND x.`deleted` = b'0'
  );

-- ------------------------------------------------------------
-- 【7】核对：HRM 人力资源下的菜单树
-- ------------------------------------------------------------
SELECT p.`id` AS parent_id, p.`name` AS parent_name, p.`sort` AS parent_sort,
       c.`id` AS child_id, c.`name` AS child_name, c.`type`, c.`sort`, c.`path`,
       c.`component`, c.`permission`, COUNT(rm.`role_id`) AS role_count
FROM `system_menu` p
JOIN `system_menu` c ON c.`parent_id` = p.`id` AND c.`deleted` = b'0'
LEFT JOIN `system_role_menu` rm ON rm.`menu_id` = c.`id` AND rm.`deleted` = b'0'
WHERE p.`id` IN (1476, 6750) AND p.`deleted` = b'0'
GROUP BY p.`id`, p.`name`, p.`sort`, c.`id`, c.`name`, c.`type`, c.`sort`, c.`path`, c.`component`, c.`permission`
ORDER BY p.`id`, c.`sort`;

-- ------------------------------------------------------------
-- 【8】执行后清理权限缓存：
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern '*menu_role_ids*' | xargs -r redis-cli del; redis-cli --scan --pattern '*permission_menu_ids*' | xargs -r redis-cli del; redis-cli --scan --pattern '*user_role_ids*' | xargs -r redis-cli del"
-- ------------------------------------------------------------
