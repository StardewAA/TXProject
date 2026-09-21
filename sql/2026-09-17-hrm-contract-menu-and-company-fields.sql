-- ============================================================
-- 1) HRM 人力资源下新增二级菜单「合同管理」，并把「用工主体信息」迁移到其下
-- 2) 用工主体信息增加企查查式字段：成立日期、注册资本、企业类型、经营范围、参保人数
-- 执行库：ruoyi-vue-pro；可重复执行
-- 执行后需清理权限缓存（见文末）
-- ============================================================

-- 【1】用工主体信息补充字段
ALTER TABLE `hrm_company_profile`
  ADD COLUMN `establish_date`     date          NULL COMMENT '成立日期' AFTER `legal_person`,
  ADD COLUMN `registered_capital` varchar(64)   NULL COMMENT '注册资本' AFTER `establish_date`,
  ADD COLUMN `company_type`       varchar(64)   NULL COMMENT '企业类型' AFTER `registered_capital`,
  ADD COLUMN `business_scope`     varchar(1000) NULL COMMENT '经营范围' AFTER `company_type`,
  ADD COLUMN `insured_count`      int           NULL COMMENT '参保人数' AFTER `business_scope`;

-- 【2】新增二级菜单「合同管理」（挂在 HRM 人力资源 1476 下）
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '合同管理', '', 1, 30, 1476, 'contract', 'ep:document', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `name` = '合同管理' AND `parent_id` = 1476 AND `deleted` = b'0'
);

SET @contractMenuId := (
  SELECT `id` FROM `system_menu`
  WHERE `name` = '合同管理' AND `parent_id` = 1476 AND `deleted` = b'0' LIMIT 1
);

-- 【3】把「用工主体信息」迁到「合同管理」下（父菜单必须在授权集合内，否则会被过滤）
UPDATE `system_menu`
SET `parent_id` = @contractMenuId, `sort` = 1, `updater` = 'admin', `update_time` = NOW()
WHERE `name` = '用工主体信息' AND `deleted` = b'0' AND `parent_id` <> @contractMenuId;

-- 【4】给已拥有「用工主体信息」的角色补授「合同管理」父菜单
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, @contractMenuId, 'admin', NOW(), 'admin', NOW(), b'0', IFNULL(r.`tenant_id`, 0)
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
JOIN `system_menu` m ON m.`id` = rm.`menu_id` AND m.`deleted` = b'0'
WHERE rm.`deleted` = b'0' AND m.`name` = '用工主体信息'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = @contractMenuId AND x.`deleted` = b'0'
  );

-- 【5】核对
SELECT p.`id` AS parent_id, p.`name` AS parent_name, c.`id` AS child_id, c.`name` AS child_name,
       c.`path`, c.`component`, COUNT(rm.`role_id`) AS role_count
FROM `system_menu` p
JOIN `system_menu` c ON c.`parent_id` = p.`id` AND c.`deleted` = b'0'
LEFT JOIN `system_role_menu` rm ON rm.`menu_id` = c.`id` AND rm.`deleted` = b'0'
WHERE p.`name` = '合同管理' AND p.`deleted` = b'0'
GROUP BY p.`id`, p.`name`, c.`id`, c.`name`, c.`path`, c.`component`;

-- 【6】执行后清理权限缓存：
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern '*menu_role_ids*' | xargs -r redis-cli del; redis-cli --scan --pattern '*permission_menu_ids*' | xargs -r redis-cli del; redis-cli --scan --pattern '*user_role_ids*' | xargs -r redis-cli del"
