-- =====================================================================
-- CRM 新增【售后回访与质保管理】业务模块 建表脚本
-- 日期：2026-09-07
-- 说明：基于 V1.0 PRD；质保起算以「项目完工验收日期」为准（可手动修改），
--       质保到期日 = 验收日期 + 质保年限 - 1 天，允许人工修改。
--       本脚本幂等：DROP TABLE IF EXISTS 后重建（仅限本模块新增表）。
-- 应用范围：ruoyi-vue-pro 业务库
-- =====================================================================

USE `ruoyi-vue-pro`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. crm_after_sale_warranty 质保档案（模块主表）
--    一条已生效合同(audit_status=20)可对应多条（按工程项目拆分）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_after_sale_warranty`;
CREATE TABLE `crm_after_sale_warranty` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '质保编号，WB+yyyyMMdd+3位流水',
  `contract_id` bigint NOT NULL COMMENT '来源合同编号，关联 crm_contract.id',
  `customer_id` bigint NOT NULL COMMENT '客户编号，冗余，关联 crm_customer.id',
  `contract_product_id` bigint DEFAULT NULL COMMENT '质保标的-合同产品行编号，关联 crm_contract_product.id，可空',
  `project_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工程项目名称',
  `project_accept_date` date NOT NULL COMMENT '项目完工验收日期（质保起算日，可手动修改）',
  `warranty_years` int NOT NULL COMMENT '质保年限，1-5 年',
  `warranty_end_date` date NOT NULL COMMENT '质保到期日期，默认=验收日期+年限-1天，可人工修改',
  `warranty_scope` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '质保范围（防腐部位/介质环境/责任边界等描述）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '质保状态：1=质保中 2=即将到期 3=已到期终止',
  `owner_user_id` bigint DEFAULT NULL COMMENT '业务负责人（原合同业务员）',
  `service_user_id` bigint DEFAULT NULL COMMENT '售后责任人',
  `last_visit_time` datetime DEFAULT NULL COMMENT '最近一次回访时间（冗余）',
  `next_visit_time` datetime DEFAULT NULL COMMENT '计划下次回访时间（冗余）',
  `remind_sent_date` date DEFAULT NULL COMMENT '最近一次到期提醒发送日期（防重复推送）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_contract_id` (`contract_id`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE,
  KEY `idx_status_end` (`status`,`warranty_end_date`) USING BTREE,
  KEY `idx_warranty_end_date` (`warranty_end_date`) USING BTREE,
  KEY `idx_owner_user_id` (`owner_user_id`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后质保档案表';

-- ---------------------------------------------------------------------
-- 2. crm_after_sale_config 售后设置（单行配置）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_after_sale_config`;
CREATE TABLE `crm_after_sale_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `warranty_warn_days` int NOT NULL DEFAULT '30' COMMENT '质保到期提前提醒天数，范围 7-180',
  `auto_visit_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否开启自动生成回访计划',
  `auto_visit_cycle_type` tinyint NOT NULL DEFAULT '1' COMMENT '自动回访周期类型：1=每季度 2=每半年 3=每年 4=质保到期前重点回访',
  `auto_visit_cycle_value` int NOT NULL DEFAULT '0' COMMENT '自动回访周期倍数/到期前天数（配合 auto_visit_cycle_type）',
  `task_remind_days` int NOT NULL DEFAULT '3' COMMENT '回访任务临期提醒提前天数',
  `task_overdue_days` int NOT NULL DEFAULT '3' COMMENT '回访任务逾期判定天数（窗口期天数：窗口期截止日期 = 计划回访日期 + 本值）',
  `allow_early_visit` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否允许提前提交回访记录：0=不允许（未到计划回访日期禁止提交）1=允许',
  `ticket_overdue_days` int NOT NULL DEFAULT '7' COMMENT '工单超时未处理预警天数',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后设置表';

-- 初始化单行配置（id=1）
INSERT INTO `crm_after_sale_config`
(`id`, `warranty_warn_days`, `auto_visit_enabled`, `auto_visit_cycle_type`, `auto_visit_cycle_value`, `task_remind_days`, `task_overdue_days`, `ticket_overdue_days`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (1, 30, b'0', 1, 0, 3, 3, 7, '1', NOW(), '1', NOW(), b'0', 1);

-- ---------------------------------------------------------------------
-- 3. crm_after_sale_visit_plan 回访计划
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_after_sale_visit_plan`;
CREATE TABLE `crm_after_sale_visit_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `warranty_id` bigint NOT NULL COMMENT '关联质保档案编号',
  `plan_type` tinyint NOT NULL DEFAULT '1' COMMENT '计划类型：1=手动 2=自动',
  `cycle_type` tinyint NOT NULL DEFAULT '1' COMMENT '周期类型：1=每季度 2=每半年 3=每年 4=质保到期前重点回访',
  `cycle_value` int NOT NULL DEFAULT '0' COMMENT '周期倍数 或 到期前天数（cycle_type=4 时）',
  `start_date` date DEFAULT NULL COMMENT '计划生效开始日期（默认=质保开始/验收日期）',
  `end_date` date DEFAULT NULL COMMENT '计划生效结束日期（默认=质保到期日）',
  `assignee_type` tinyint NOT NULL DEFAULT '1' COMMENT '任务处理人类型：1=售后人员 2=业务员',
  `assignee_user_id` bigint DEFAULT NULL COMMENT '指定处理人（为空则按类型取售后责任人/业务负责人）',
  `visit_type` tinyint NOT NULL DEFAULT '1' COMMENT '默认回访方式：1=电话 2=上门 3=微信 4=邮件',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '计划状态：1=启用 2=停用 3=已完成',
  `last_task_date` date DEFAULT NULL COMMENT '最近一次生成任务的日期（自动计划断点续排）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_warranty_id` (`warranty_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后回访计划表';

-- ---------------------------------------------------------------------
-- 4. crm_after_sale_visit_task 回访任务（执行实例）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_after_sale_visit_task`;
CREATE TABLE `crm_after_sale_visit_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `plan_id` bigint NOT NULL DEFAULT '0' COMMENT '来源计划编号，0=手动创建任务',
  `warranty_id` bigint NOT NULL COMMENT '质保档案编号',
  `customer_id` bigint NOT NULL COMMENT '客户编号（冗余）',
  `contract_id` bigint NOT NULL COMMENT '合同编号（冗余）',
  `assignee_user_id` bigint NOT NULL COMMENT '处理人（售后人员或业务员）',
  `task_date` date NOT NULL COMMENT '计划执行日期（计划回访日期）',
  `window_end_date` date DEFAULT NULL COMMENT '窗口期截止日期 = 计划执行日期 + 回访任务逾期判定天数；当前日期超过该日期判定为已逾期',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '任务状态：1=待处理 2=已完成 3=已逾期',
  `finish_time` datetime DEFAULT NULL COMMENT '实际完成时间',
  `overdue_sent_date` date DEFAULT NULL COMMENT '临期提醒发送日期（防重复）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务备注/回访提示',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_assignee_status` (`assignee_user_id`,`status`) USING BTREE,
  KEY `idx_task_date` (`task_date`) USING BTREE,
  KEY `idx_plan_id` (`plan_id`) USING BTREE,
  KEY `idx_warranty_id` (`warranty_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后回访任务表';

-- ---------------------------------------------------------------------
-- 5. crm_after_sale_visit_record 回访记录（含满意度，永久留存）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_after_sale_visit_record`;
CREATE TABLE `crm_after_sale_visit_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `task_id` bigint DEFAULT NULL COMMENT '关联回访任务编号，手动登记可为空',
  `status` tinyint NOT NULL DEFAULT '2' COMMENT '记录状态：1=待填写 2=已填写',
  `warranty_id` bigint NOT NULL COMMENT '质保档案编号',
  `customer_id` bigint NOT NULL COMMENT '客户编号（冗余）',
  `contract_id` bigint NOT NULL COMMENT '合同编号（冗余）',
  `contact_id` bigint DEFAULT NULL COMMENT '被访联系人编号，关联 crm_contact.id',
  `visit_type` tinyint NOT NULL DEFAULT '1' COMMENT '回访方式：1=电话 2=上门 3=微信 4=邮件',
  `visit_time` datetime NOT NULL COMMENT '回访时间',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '回访内容',
  `satisfaction_level` tinyint DEFAULT NULL COMMENT '满意度：5=非常满意 4=满意 3=一般 2=不满意 1=非常不满意',
  `satisfaction_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '满意度文字备注',
  `next_visit_date` date DEFAULT NULL COMMENT '建议下次回访日期',
  `pic_urls` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '现场照片 URL 数组(JSON)',
  `file_urls` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '附件 URL 数组(JSON)',
  `ticket_id` bigint DEFAULT NULL COMMENT '本次回访转出的问题工单编号（可空）',
  `business_id` bigint DEFAULT NULL COMMENT '本次回访转出的新商机编号（可空）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者（回访人）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_task_id` (`task_id`) USING BTREE,
  KEY `idx_warranty_time` (`warranty_id`,`visit_time`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后回访记录表';

-- ---------------------------------------------------------------------
-- 6. crm_after_sale_ticket 售后问题工单
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_after_sale_ticket`;
CREATE TABLE `crm_after_sale_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工单编号，AT+yyyyMMddHHmmss+4位随机',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题标题',
  `warranty_id` bigint DEFAULT NULL COMMENT '关联质保档案编号（可空）',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `contract_id` bigint DEFAULT NULL COMMENT '合同编号（可空）',
  `visit_record_id` bigint DEFAULT NULL COMMENT '来源回访记录编号（客户报障为空）',
  `source` tinyint NOT NULL DEFAULT '1' COMMENT '工单来源：1=回访发现 2=客户报障',
  `priority` tinyint NOT NULL DEFAULT '2' COMMENT '优先级：1=高 2=中 3=低',
  `problem_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问题类型（字典）',
  `description` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问题描述',
  `contact_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '报障联系人姓名（快照）',
  `contact_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '报障联系人电话（快照）',
  `assignee_user_id` bigint DEFAULT NULL COMMENT '处理人（售后工程师/外协）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '工单状态：1=待处理 2=处理中 3=已完成 4=已关闭',
  `handle_result` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理结果说明',
  `start_time` datetime DEFAULT NULL COMMENT '开始处理时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `close_time` datetime DEFAULT NULL COMMENT '关闭归档时间',
  `close_user_id` bigint DEFAULT NULL COMMENT '关闭人编号',
  `is_chargeable` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否转有偿服务（超质保范围）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者（报单人）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_status_assignee` (`status`,`assignee_user_id`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE,
  KEY `idx_warranty_id` (`warranty_id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后问题工单表';

-- ---------------------------------------------------------------------
-- 7. crm_after_sale_ticket_note 工单处理流转记录（留痕）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_after_sale_ticket_note`;
CREATE TABLE `crm_after_sale_ticket_note` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `ticket_id` bigint NOT NULL COMMENT '工单编号',
  `operate_type` tinyint NOT NULL COMMENT '操作类型：1=指派 2=状态变更 3=处理记录 4=转有偿 5=关闭',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理内容/变更说明',
  `operator_user_id` bigint NOT NULL COMMENT '操作人',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ticket_id` (`ticket_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后工单流转记录表';

-- ---------------------------------------------------------------------
-- 8. crm_after_sale_business_rel 售后转商机关联
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_after_sale_business_rel`;
CREATE TABLE `crm_after_sale_business_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `business_id` bigint NOT NULL COMMENT '新商机编号，关联 crm_business.id',
  `warranty_id` bigint NOT NULL COMMENT '来源质保档案编号',
  `visit_record_id` bigint DEFAULT NULL COMMENT '来源回访记录编号（可空）',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `source_type` tinyint NOT NULL COMMENT '商机内容方向：1=复购 2=续签 3=扩项/新工程',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '转化说明',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_business_id` (`business_id`) USING BTREE,
  KEY `idx_warranty_id` (`warranty_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后转商机关联表';

-- ---------------------------------------------------------------------
-- 9. crm_business 商机表扩展（新增来源字段，最小侵入）
-- ---------------------------------------------------------------------
ALTER TABLE `crm_business`
  ADD COLUMN `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商机来源（字典 crm_business_source；after_sale=售后回访）' AFTER `remark`,
  ADD COLUMN `source_rel_id` bigint NOT NULL DEFAULT '0' COMMENT '来源关联编号（售后转商机时= crm_after_sale_business_rel.id）' AFTER `source`;
