-- ============================================================================
-- ERP 采购侧补全：采购计划 / 采购合同 / 到货检验 / 供应商评价 / 发票管理
-- 2026-09-20
--
-- 说明：
--   1. 本脚本可重复执行（先删菜单授权、按钮、菜单，再重建；建表用 IF NOT EXISTS）。
--   2. 只涉及 ERP 自己的表（erp_*）与系统菜单表（system_menu / system_role_menu），
--      不改动其它业务模块的表。
--   3. 单据状态沿用 ErpAuditStatus：0 草稿 / 10 审批中 / 20 审批通过 / 30 审批驳回 / 40 已撤销。
--      本批 5 个功能走 ERP 内部状态流转（不创建 BPM 流程实例），审批入口在各自列表页。
--   4. 菜单父节点：采购管理 = 2602，财务管理 = 2645（如目标库 id 不同请自行替换）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、建表
-- ---------------------------------------------------------------------------

-- 1. 采购计划
CREATE TABLE IF NOT EXISTS `erp_purchase_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '采购计划编号',
  `plan_name` varchar(128) NOT NULL COMMENT '计划名称',
  `plan_period` varchar(32) DEFAULT NULL COMMENT '计划期间（如 2026-Q4 / 2026-09）',
  `department_id` bigint DEFAULT NULL COMMENT '需求部门编号',
  `requester_id` bigint DEFAULT NULL COMMENT '计划负责人编号',
  `plan_time` datetime NOT NULL COMMENT '编制时间',
  `start_date` datetime DEFAULT NULL COMMENT '计划开始日期',
  `end_date` datetime DEFAULT NULL COMMENT '计划结束日期',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '审批状态',
  `reverse_approve_reason` varchar(200) DEFAULT NULL COMMENT '反审批原因',
  `total_count` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '计划总数量',
  `budget_amount` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '预算金额',
  `purpose` varchar(512) DEFAULT NULL COMMENT '计划说明',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '附件地址',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_purchase_plan_no_tenant` (`no`,`tenant_id`,`deleted`),
  KEY `idx_erp_purchase_plan_status` (`status`),
  KEY `idx_erp_purchase_plan_time` (`plan_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP采购计划';

CREATE TABLE IF NOT EXISTS `erp_purchase_plan_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `plan_id` bigint NOT NULL COMMENT '采购计划编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_unit_id` bigint DEFAULT NULL COMMENT '产品单位编号',
  `count` decimal(24,6) NOT NULL COMMENT '计划数量',
  `target_price` decimal(24,6) DEFAULT NULL COMMENT '参考单价',
  `amount` decimal(24,6) DEFAULT NULL COMMENT '金额',
  `required_date` datetime DEFAULT NULL COMMENT '需求日期',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_erp_purchase_plan_item_plan` (`plan_id`),
  KEY `idx_erp_purchase_plan_item_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP采购计划明细';

-- 2. 采购合同
CREATE TABLE IF NOT EXISTS `erp_purchase_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '合同编号',
  `contract_name` varchar(128) NOT NULL COMMENT '合同名称',
  `supplier_id` bigint NOT NULL COMMENT '供应商编号',
  `purchase_order_id` bigint DEFAULT NULL COMMENT '关联采购订单编号',
  `purchase_order_no` varchar(32) DEFAULT NULL COMMENT '关联采购订单号',
  `sign_date` datetime DEFAULT NULL COMMENT '签订日期',
  `effective_date` datetime DEFAULT NULL COMMENT '生效日期',
  `expire_date` datetime DEFAULT NULL COMMENT '到期日期',
  `total_amount` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '合同金额',
  `tax_rate` decimal(8,4) DEFAULT NULL COMMENT '税率（%）',
  `payment_terms` varchar(256) DEFAULT NULL COMMENT '付款方式',
  `delivery_place` varchar(256) DEFAULT NULL COMMENT '交货地点',
  `signer_id` bigint DEFAULT NULL COMMENT '我方签订人编号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '审批状态',
  `reverse_approve_reason` varchar(200) DEFAULT NULL COMMENT '反审批原因',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '合同附件',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_purchase_contract_no_tenant` (`no`,`tenant_id`,`deleted`),
  KEY `idx_erp_purchase_contract_supplier` (`supplier_id`),
  KEY `idx_erp_purchase_contract_order` (`purchase_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP采购合同';

-- 3. 到货检验
CREATE TABLE IF NOT EXISTS `erp_arrival_inspection` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '检验单号',
  `purchase_order_id` bigint DEFAULT NULL COMMENT '关联采购订单编号',
  `purchase_order_no` varchar(32) DEFAULT NULL COMMENT '关联采购订单号',
  `purchase_in_id` bigint DEFAULT NULL COMMENT '关联采购入库单编号',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商编号',
  `inspect_time` datetime NOT NULL COMMENT '检验时间',
  `inspector_id` bigint DEFAULT NULL COMMENT '检验人编号',
  `inspect_type` tinyint NOT NULL DEFAULT '1' COMMENT '检验方式：1 全检 2 抽检',
  `result` tinyint DEFAULT NULL COMMENT '检验结论：1 合格 2 让步接收 3 退货',
  `conclusion` varchar(512) DEFAULT NULL COMMENT '检验结论说明',
  `origin` varchar(32) DEFAULT NULL COMMENT '物料来源',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '单据状态',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '检验报告附件',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_arrival_inspection_no_tenant` (`no`,`tenant_id`,`deleted`),
  KEY `idx_erp_arrival_inspection_order` (`purchase_order_id`),
  KEY `idx_erp_arrival_inspection_in` (`purchase_in_id`),
  KEY `idx_erp_arrival_inspection_time` (`inspect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP到货检验';

CREATE TABLE IF NOT EXISTS `erp_arrival_inspection_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `inspection_id` bigint NOT NULL COMMENT '检验单编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_unit_id` bigint DEFAULT NULL COMMENT '产品单位编号',
  `delivery_count` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '到货数量',
  `qualified_count` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '合格数量',
  `unqualified_count` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '不合格数量',
  `result` tinyint DEFAULT NULL COMMENT '明细结论：1 合格 2 不合格',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_erp_arrival_inspection_item_inspection` (`inspection_id`),
  KEY `idx_erp_arrival_inspection_item_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP到货检验明细';

-- 4. 供应商评价
CREATE TABLE IF NOT EXISTS `erp_supplier_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '评价单号',
  `supplier_id` bigint NOT NULL COMMENT '供应商编号',
  `eval_period` varchar(32) DEFAULT NULL COMMENT '评价期间',
  `eval_date` datetime DEFAULT NULL COMMENT '评价日期',
  `quality_score` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '质量得分',
  `delivery_score` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '交期得分',
  `price_score` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '价格得分',
  `service_score` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '服务得分',
  `total_score` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '综合得分',
  `grade` varchar(8) DEFAULT NULL COMMENT '等级：A/B/C/D',
  `evaluator_id` bigint DEFAULT NULL COMMENT '评价人编号',
  `comment` varchar(512) DEFAULT NULL COMMENT '评价说明',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '单据状态',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_supplier_evaluation_no_tenant` (`no`,`tenant_id`,`deleted`),
  KEY `idx_erp_supplier_evaluation_supplier` (`supplier_id`),
  KEY `idx_erp_supplier_evaluation_date` (`eval_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP供应商评价';

-- 5. 发票管理
CREATE TABLE IF NOT EXISTS `erp_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '发票单号（系统内）',
  `invoice_type` tinyint NOT NULL DEFAULT '1' COMMENT '发票类型：1 增值税专用发票 2 增值税普通发票 3 电子发票',
  `invoice_no` varchar(64) DEFAULT NULL COMMENT '发票号码',
  `invoice_code` varchar(64) DEFAULT NULL COMMENT '发票代码',
  `title` varchar(128) DEFAULT NULL COMMENT '购方/销方名称',
  `biz_type` tinyint NOT NULL DEFAULT '1' COMMENT '业务类型：1 采购（进项） 2 销售（销项）',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商编号（进项）',
  `customer_id` bigint DEFAULT NULL COMMENT '客户编号（销项）',
  `order_no` varchar(32) DEFAULT NULL COMMENT '关联业务单据号',
  `invoice_date` datetime DEFAULT NULL COMMENT '开票日期',
  `amount` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '价税合计',
  `tax_amount` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '税额',
  `tax_rate` decimal(8,4) DEFAULT NULL COMMENT '税率（%）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0 待认证 1 已认证 2 已入账 3 已作废',
  `certify_time` datetime DEFAULT NULL COMMENT '认证时间',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '发票附件',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_invoice_no_tenant` (`no`,`tenant_id`,`deleted`),
  KEY `idx_erp_invoice_invoice_no` (`invoice_no`),
  KEY `idx_erp_invoice_date` (`invoice_date`),
  KEY `idx_erp_invoice_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP发票管理';

-- ---------------------------------------------------------------------------
-- 二、菜单（可重复执行）
-- ---------------------------------------------------------------------------

-- 清理旧数据（按权限前缀 + 父菜单下同名 path 定位，保证脚本可重复跑）
DELETE FROM `system_role_menu` WHERE `menu_id` IN (
  SELECT `id` FROM (SELECT `id` FROM `system_menu`
                     WHERE `permission` LIKE 'erp:purchase-plan:%'
                        OR `permission` LIKE 'erp:purchase-contract:%'
                        OR `permission` LIKE 'erp:purchase-inspection:%'
                        OR `permission` LIKE 'erp:purchase-evaluation:%'
                        OR `permission` LIKE 'erp:invoice:%') t
);
DELETE FROM `system_menu` WHERE `permission` LIKE 'erp:purchase-plan:%'
                             OR `permission` LIKE 'erp:purchase-contract:%'
                             OR `permission` LIKE 'erp:purchase-inspection:%'
                             OR `permission` LIKE 'erp:purchase-evaluation:%'
                             OR `permission` LIKE 'erp:invoice:%';
DELETE FROM `system_role_menu` WHERE `menu_id` IN (
  SELECT `id` FROM (SELECT `id` FROM `system_menu`
                     WHERE (`parent_id` = 2602 AND `path` IN ('plan','contract','inspection','evaluation'))
                        OR (`parent_id` = 2645 AND `path` = 'invoice')) t
);
DELETE FROM `system_menu` WHERE (`parent_id` = 2602 AND `path` IN ('plan','contract','inspection','evaluation'))
                            OR (`parent_id` = 2645 AND `path` = 'invoice');

-- 2.1 采购计划
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES ('采购计划','',2,5,2602,'plan','ant-design:schedule-outlined','erp/purchase/plan/index','ErpPurchasePlan',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');
SET @m_plan = LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
 ('采购计划查询','erp:purchase-plan:query', 3,1,@m_plan,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购计划新增','erp:purchase-plan:create',3,2,@m_plan,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购计划修改','erp:purchase-plan:update',3,3,@m_plan,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购计划删除','erp:purchase-plan:delete',3,4,@m_plan,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购计划提交','erp:purchase-plan:submit',3,5,@m_plan,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购计划审批','erp:purchase-plan:approve',3,6,@m_plan,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

-- 2.2 采购合同
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES ('采购合同','',2,6,2602,'contract','ant-design:file-done-outlined','erp/purchase/contract/index','ErpPurchaseContract',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');
SET @m_contract = LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
 ('采购合同查询','erp:purchase-contract:query', 3,1,@m_contract,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购合同新增','erp:purchase-contract:create',3,2,@m_contract,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购合同修改','erp:purchase-contract:update',3,3,@m_contract,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购合同删除','erp:purchase-contract:delete',3,4,@m_contract,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购合同提交','erp:purchase-contract:submit',3,5,@m_contract,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('采购合同审批','erp:purchase-contract:approve',3,6,@m_contract,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

-- 2.3 到货检验
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES ('到货检验','',2,7,2602,'inspection','ant-design:audit-outlined','erp/purchase/inspection/index','ErpArrivalInspection',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');
SET @m_inspection = LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
 ('到货检验查询','erp:purchase-inspection:query', 3,1,@m_inspection,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('到货检验新增','erp:purchase-inspection:create',3,2,@m_inspection,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('到货检验修改','erp:purchase-inspection:update',3,3,@m_inspection,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('到货检验删除','erp:purchase-inspection:delete',3,4,@m_inspection,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('到货检验提交','erp:purchase-inspection:submit',3,5,@m_inspection,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('到货检验审批','erp:purchase-inspection:approve',3,6,@m_inspection,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

-- 2.4 供应商评价
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES ('供应商评价','',2,8,2602,'evaluation','ant-design:star-outlined','erp/purchase/evaluation/index','ErpSupplierEvaluation',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');
SET @m_evaluation = LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
 ('供应商评价查询','erp:purchase-evaluation:query', 3,1,@m_evaluation,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('供应商评价新增','erp:purchase-evaluation:create',3,2,@m_evaluation,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('供应商评价修改','erp:purchase-evaluation:update',3,3,@m_evaluation,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('供应商评价删除','erp:purchase-evaluation:delete',3,4,@m_evaluation,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

-- 2.5 发票管理（财务管理下）
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES ('发票管理','',2,3,2645,'invoice','ant-design:account-book-outlined','erp/finance/invoice/index','ErpInvoice',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');
SET @m_invoice = LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
 ('发票查询','erp:invoice:query', 3,1,@m_invoice,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('发票新增','erp:invoice:create',3,2,@m_invoice,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('发票修改','erp:invoice:update',3,3,@m_invoice,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'),
 ('发票删除','erp:invoice:delete',3,4,@m_invoice,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

-- ---------------------------------------------------------------------------
-- 三、角色授权（角色编号取自租户 2001）
--   60000 企业超级管理员 / 60002 全局查看管理员 / 60007 采购管理员
--   60010 质检专员 / 60015 财务管理员
-- ---------------------------------------------------------------------------
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 60000, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 2001 FROM `system_menu` m
 WHERE m.`id` IN (@m_plan, @m_contract, @m_inspection, @m_evaluation, @m_invoice)
    OR m.`parent_id` IN (@m_plan, @m_contract, @m_inspection, @m_evaluation, @m_invoice);

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 60007, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 2001 FROM `system_menu` m
 WHERE m.`id` IN (@m_plan, @m_contract, @m_evaluation)
    OR m.`parent_id` IN (@m_plan, @m_contract, @m_evaluation);

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 60010, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 2001 FROM `system_menu` m
 WHERE m.`id` = @m_inspection OR m.`parent_id` = @m_inspection;

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 60015, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 2001 FROM `system_menu` m
 WHERE m.`id` = @m_invoice OR m.`parent_id` = @m_invoice;

-- ---------------------------------------------------------------------------
-- 四、演示数据（租户 2001，可重复执行）
--   主数据取自该租户已有数据：供应商 7/9/13、客户 9、产品 5/7/9/13/37、
--   采购订单 35(CGDD-2026-0006)/37(CGDD20260917000001)、采购入库 28(CGRK-2026-0007)、
--   部门 50002/50003、用户 80000/80001、销售订单 XSDD-2026-0003。
-- ---------------------------------------------------------------------------

-- 4.1 采购计划
DELETE FROM `erp_purchase_plan_item` WHERE `plan_id` IN (SELECT `id` FROM (SELECT `id` FROM `erp_purchase_plan` WHERE `tenant_id`=2001 AND `no` IN ('CGJH20260915000001','CGJH20260915000002')) t);
DELETE FROM `erp_purchase_plan` WHERE `tenant_id`=2001 AND `no` IN ('CGJH20260915000001','CGJH20260915000002');

INSERT INTO `erp_purchase_plan` (`no`,`plan_name`,`plan_period`,`department_id`,`requester_id`,`plan_time`,`start_date`,`end_date`,`status`,`total_count`,`budget_amount`,`purpose`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES ('CGJH20260915000001','2026年四季度防腐保温材料采购计划','2026-Q4',50002,80000,'2026-09-15 09:00:00','2026-10-01 00:00:00','2026-12-31 00:00:00',10,1300.000000,39650.000000,'四季度管道防腐保温项目集中备料，按项目进度分批发货','80000',NOW(),'80000',NOW(),b'0',2001);
SET @plan1 = LAST_INSERT_ID();
INSERT INTO `erp_purchase_plan_item` (`plan_id`,`product_id`,`product_unit_id`,`count`,`target_price`,`amount`,`required_date`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
 (@plan1,9,23,500.000000,26.500000,13250.000000,'2026-11-30 00:00:00','80000',NOW(),'80000',NOW(),b'0',2001),
 (@plan1,5,6,300.000000,18.000000,5400.000000,'2026-11-15 00:00:00','80000',NOW(),'80000',NOW(),b'0',2001),
 (@plan1,7,5,500.000000,42.000000,21000.000000,'2026-12-10 00:00:00','80000',NOW(),'80000',NOW(),b'0',2001);

INSERT INTO `erp_purchase_plan` (`no`,`plan_name`,`plan_period`,`department_id`,`requester_id`,`plan_time`,`start_date`,`end_date`,`status`,`total_count`,`budget_amount`,`purpose`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES ('CGJH20260915000002','2026年10月管道支架及辅材采购计划','2026-10',50003,80001,'2026-09-15 10:30:00','2026-10-01 00:00:00','2026-10-31 00:00:00',0,800.000000,9600.000000,'生产质检部提报的月度辅材需求（草稿，待完善后提交）','80001',NOW(),'80001',NOW(),b'0',2001);
SET @plan2 = LAST_INSERT_ID();
INSERT INTO `erp_purchase_plan_item` (`plan_id`,`product_id`,`product_unit_id`,`count`,`target_price`,`amount`,`required_date`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
 (@plan2,37,32,800.000000,12.000000,9600.000000,'2026-10-20 00:00:00','80001',NOW(),'80001',NOW(),b'0',2001);

-- 4.2 采购合同
DELETE FROM `erp_purchase_contract` WHERE `tenant_id`=2001 AND `no` IN ('CGHT20260915000001','CGHT20260915000002');
INSERT INTO `erp_purchase_contract` (`no`,`contract_name`,`supplier_id`,`purchase_order_id`,`purchase_order_no`,`sign_date`,`effective_date`,`expire_date`,`total_amount`,`tax_rate`,`payment_terms`,`delivery_place`,`signer_id`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
 ('CGHT20260915000001','防腐涂料年度采购合同',9,35,'CGDD-2026-0006','2026-01-10 00:00:00','2026-01-15 00:00:00','2026-12-31 00:00:00',580000.000000,13.0000,'月结30天，电汇','天信管业仓库1号',80000,20,'80000',NOW(),'80000',NOW(),b'0',2001),
 ('CGHT20260915000002','工程物资采购合同（西安建元）',13,37,'CGDD20260917000001','2026-09-18 00:00:00','2026-09-18 00:00:00','2027-09-17 00:00:00',3490245.000000,13.0000,'货到验收合格后 45 天付款','天信管业仓库2号',80000,10,'80000',NOW(),'80000',NOW(),b'0',2001);

-- 4.3 到货检验
DELETE FROM `erp_arrival_inspection_item` WHERE `inspection_id` IN (SELECT `id` FROM (SELECT `id` FROM `erp_arrival_inspection` WHERE `tenant_id`=2001 AND `no` IN ('DHJY20260915000001','DHJY20260915000002')) t);
DELETE FROM `erp_arrival_inspection` WHERE `tenant_id`=2001 AND `no` IN ('DHJY20260915000001','DHJY20260915000002');
INSERT INTO `erp_arrival_inspection` (`no`,`purchase_order_id`,`purchase_order_no`,`purchase_in_id`,`supplier_id`,`inspect_time`,`inspector_id`,`inspect_type`,`result`,`conclusion`,`origin`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
 ('DHJY20260915000001',35,'CGDD-2026-0006',28,9,'2026-09-16 10:00:00',80001,1,1,'外观无破损，漆膜厚度抽检 5 点全部合格，随车质保书与检测报告齐全','江苏鑫盛防腐涂料有限公司',20,'80001',NOW(),'80001',NOW(),b'0',2001),
 ('DHJY20260915000002',37,'CGDD20260917000001',NULL,13,'2026-09-19 15:20:00',80001,2,2,'抽检发现 40 张彩钢板边角有轻微磕碰变形，不影响使用，作让步接收处理','西安建元工程物资有限公司',10,'80001',NOW(),'80001',NOW(),b'0',2001);
SET @ins1 = (SELECT `id` FROM `erp_arrival_inspection` WHERE `tenant_id`=2001 AND `no`='DHJY20260915000001');
SET @ins2 = (SELECT `id` FROM `erp_arrival_inspection` WHERE `tenant_id`=2001 AND `no`='DHJY20260915000002');
INSERT INTO `erp_arrival_inspection_item` (`inspection_id`,`product_id`,`product_unit_id`,`delivery_count`,`qualified_count`,`unqualified_count`,`result`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
 (@ins1,9,23,500.000000,500.000000,0.000000,1,'80001',NOW(),'80001',NOW(),b'0',2001),
 (@ins2,13,25,800.000000,760.000000,40.000000,2,'80001',NOW(),'80001',NOW(),b'0',2001);

-- 4.4 供应商评价
DELETE FROM `erp_supplier_evaluation` WHERE `tenant_id`=2001 AND `no` IN ('GYSPJ20260915000001','GYSPJ20260915000002');
INSERT INTO `erp_supplier_evaluation` (`no`,`supplier_id`,`eval_period`,`eval_date`,`quality_score`,`delivery_score`,`price_score`,`service_score`,`total_score`,`grade`,`evaluator_id`,`comment`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
 ('GYSPJ20260915000001',7,'2026-Q3','2026-09-19 00:00:00',92.00,88.00,85.00,90.00,89.20,'B',80000,'涂料批次稳定性好，交期偶有延后 2~3 天，价格略高于市场均价，售后响应及时。',20,'80000',NOW(),'80000',NOW(),b'0',2001),
 ('GYSPJ20260915000002',13,'2026-Q3','2026-09-19 00:00:00',95.00,93.00,90.00,96.00,93.50,'A',80000,'工程物资配套齐全，供货及时，可长期合作；建议后续增加常用规格的备货量。',20,'80000',NOW(),'80000',NOW(),b'0',2001);

-- 4.5 发票
DELETE FROM `erp_invoice` WHERE `tenant_id`=2001 AND `no` IN ('FPD20260915000001','FPD20260915000002','FPD20260915000003');
INSERT INTO `erp_invoice` (`no`,`invoice_type`,`invoice_no`,`invoice_code`,`title`,`biz_type`,`supplier_id`,`customer_id`,`order_no`,`invoice_date`,`amount`,`tax_amount`,`tax_rate`,`status`,`certify_time`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
 ('FPD20260915000001',1,'25327001','045002100111','天信管业防腐保温有限公司',1,9,NULL,'CGDD-2026-0006','2026-09-18 00:00:00',655400.000000,75400.000000,13.0000,1,'2026-09-19 09:00:00','80000',NOW(),'80000',NOW(),b'0',2001),
 ('FPD20260915000002',1,'25327002','045002100111','天信管业防腐保温有限公司',1,13,NULL,'CGDD20260917000001','2026-09-19 00:00:00',3490245.000000,401546.000000,13.0000,0,NULL,'80000',NOW(),'80000',NOW(),b'0',2001),
 ('FPD20260915000003',3,'25327003','045002100111','宝鸡石油输送管有限公司',2,NULL,9,'XSDD-2026-0003','2026-09-19 00:00:00',1080000.000000,124247.790000,13.0000,2,'2026-09-19 16:00:00','80000',NOW(),'80000',NOW(),b'0',2001);
