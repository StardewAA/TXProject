-- =====================================================================
-- CRM 报价管理模块：建表 + 菜单（补提交 41316d19 缺失的数据库脚本）
-- 日期：2026-09-11
-- 说明：报价单链路 客户 -> 商机 -> 报价(quotation) -> 合同
--       本脚本幂等：CREATE TABLE IF NOT EXISTS + 菜单 NOT EXISTS 判断
-- =====================================================================

USE `ruoyi-vue-pro`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. CRM 报价单表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `crm_quotation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报价名称',
  `no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报价单号',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `business_id` bigint DEFAULT NULL COMMENT '商机编号',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人的用户编号',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流编号',
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '审批状态',
  `order_date` datetime DEFAULT NULL COMMENT '报价日期',
  `valid_until` datetime DEFAULT NULL COMMENT '有效期至',
  `contract_id` bigint DEFAULT NULL COMMENT '生成的合同编号',
  `total_product_price` decimal(24,6) DEFAULT NULL COMMENT '产品总金额',
  `discount_percent` decimal(24,6) DEFAULT NULL COMMENT '整单折扣',
  `total_price` decimal(10,2) DEFAULT NULL COMMENT '报价总金额',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_owner_audit_id` (`owner_user_id`,`audit_status`,`id`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE,
  KEY `idx_business_id` (`business_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 报价单表';

-- ---------------------------------------------------------------------
-- 2. CRM 报价单产品明细表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `crm_quotation_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `quotation_id` bigint NOT NULL COMMENT '报价编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_price` decimal(24,6) NOT NULL COMMENT '产品单价',
  `quotation_price` decimal(24,6) NOT NULL COMMENT '报价价格',
  `count` decimal(24,6) NOT NULL COMMENT '数量',
  `total_price` decimal(24,6) NOT NULL COMMENT '总计价格',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_quotation_id` (`quotation_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 报价单产品明细表';

-- ---------------------------------------------------------------------
-- 3. 报价管理菜单（挂在 CRM 顶级目录 2397 下）
-- ---------------------------------------------------------------------
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 32662, '报价管理', '', 2, 98, 2397, 'quotation', 'lucide:file-text', 'crm/quotation/index', 'CrmQuotation', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 32662);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 32663, '报价查询', 'crm:quotation:query', 3, 1, 32662, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 32663);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 32664, '报价创建', 'crm:quotation:create', 3, 2, 32662, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 32664);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 32665, '报价更新', 'crm:quotation:update', 3, 3, 32662, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 32665);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 32666, '报价删除', 'crm:quotation:delete', 3, 4, 32662, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 32666);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 32667, '报价导出', 'crm:quotation:export', 3, 5, 32662, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 32667);

-- ---------------------------------------------------------------------
-- 4. 菜单授权：给拥有"合同查询"权限的角色授予报价菜单（与 CRM 权限范围一致）
-- ---------------------------------------------------------------------
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m2.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
       JOIN `system_menu` m ON m.`id` = rm.`menu_id` AND m.`permission` = 'crm:contract:query' AND m.`deleted` = b'0'
       JOIN `system_menu` m2 ON m2.`id` BETWEEN 32662 AND 32667 AND m2.`deleted` = b'0'
WHERE rm.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m2.`id` AND x.`deleted` = b'0'
  );
