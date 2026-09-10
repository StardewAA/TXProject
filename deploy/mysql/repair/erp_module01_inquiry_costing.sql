-- ERP 模块 01：采购申请、询价比价、定标和造价管理。
-- 仅创建业务结构和菜单，不包含演示企业、供应商、商品或订单数据。

CREATE TABLE IF NOT EXISTS `erp_purchase_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '采购申请编号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '审批状态',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT 'BPM流程实例编号',
  `request_time` datetime NOT NULL COMMENT '申请时间',
  `required_date` datetime DEFAULT NULL COMMENT '需求日期',
  `department_id` bigint DEFAULT NULL COMMENT '申请部门编号',
  `total_count` decimal(24,6) NOT NULL DEFAULT 0 COMMENT '申请总数量',
  `budget_amount` decimal(24,6) NOT NULL DEFAULT 0 COMMENT '预算金额',
  `purpose` varchar(512) DEFAULT NULL COMMENT '采购用途',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '附件地址',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_purchase_request_no_tenant` (`no`,`tenant_id`,`deleted`),
  KEY `idx_erp_purchase_request_status` (`status`),
  KEY `idx_erp_purchase_request_time` (`request_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP采购申请';

CREATE TABLE IF NOT EXISTS `erp_purchase_request_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `request_id` bigint NOT NULL COMMENT '采购申请编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_unit_id` bigint NOT NULL COMMENT '产品单位编号',
  `count` decimal(24,6) NOT NULL COMMENT '申请数量',
  `converted_count` decimal(24,6) NOT NULL DEFAULT 0 COMMENT '已转询价数量',
  `target_price` decimal(24,6) DEFAULT NULL COMMENT '目标单价',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_erp_purchase_request_item_request` (`request_id`),
  KEY `idx_erp_purchase_request_item_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP采购申请明细';

CREATE TABLE IF NOT EXISTS `erp_inquiry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '询价单编号',
  `purchase_request_id` bigint DEFAULT NULL COMMENT '来源采购申请编号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '询价审批状态',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '询价BPM流程实例编号',
  `business_status` tinyint NOT NULL DEFAULT 0 COMMENT '业务状态',
  `award_status` tinyint NOT NULL DEFAULT 0 COMMENT '定标审批状态',
  `award_process_instance_id` varchar(64) DEFAULT NULL COMMENT '定标BPM流程实例编号',
  `title` varchar(128) NOT NULL COMMENT '询价标题',
  `inquiry_mode` tinyint NOT NULL DEFAULT 1 COMMENT '询价方式 1公开 2邀请',
  `deadline` datetime NOT NULL COMMENT '报价截止时间',
  `delivery_date` datetime DEFAULT NULL COMMENT '期望到货日期',
  `delivery_address` varchar(512) DEFAULT NULL COMMENT '交付地址',
  `currency` varchar(16) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `payment_term` varchar(256) DEFAULT NULL COMMENT '付款条件',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '附件地址',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `total_count` decimal(24,6) NOT NULL DEFAULT 0 COMMENT '询价总数量',
  `quote_count` int NOT NULL DEFAULT 0 COMMENT '有效报价数量',
  `purchase_order_ids` varchar(512) DEFAULT NULL COMMENT '生成的采购订单编号集合',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_inquiry_no_tenant` (`no`,`tenant_id`,`deleted`),
  KEY `idx_erp_inquiry_request` (`purchase_request_id`),
  KEY `idx_erp_inquiry_status` (`status`,`business_status`,`award_status`),
  KEY `idx_erp_inquiry_deadline` (`deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP询价单';

CREATE TABLE IF NOT EXISTS `erp_inquiry_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `inquiry_id` bigint NOT NULL COMMENT '询价单编号',
  `purchase_request_item_id` bigint DEFAULT NULL COMMENT '来源采购申请明细编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_unit_id` bigint NOT NULL COMMENT '产品单位编号',
  `count` decimal(24,6) NOT NULL COMMENT '询价数量',
  `quality_requirement` varchar(512) DEFAULT NULL COMMENT '质量要求',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_erp_inquiry_item_inquiry` (`inquiry_id`),
  KEY `idx_erp_inquiry_item_request_item` (`purchase_request_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP询价明细';

CREATE TABLE IF NOT EXISTS `erp_inquiry_quote` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '报价编号',
  `inquiry_id` bigint NOT NULL COMMENT '询价单编号',
  `supplier_id` bigint NOT NULL COMMENT '供应商编号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '报价状态 0草稿 10已提交 20有效 30撤回 40作废',
  `version` int NOT NULL DEFAULT 1 COMMENT '报价版本',
  `submitted_at` datetime DEFAULT NULL COMMENT '提交时间',
  `delivery_date` datetime DEFAULT NULL COMMENT '承诺到货日期',
  `payment_term` varchar(256) DEFAULT NULL COMMENT '付款条件',
  `total_price` decimal(24,6) NOT NULL DEFAULT 0 COMMENT '含税总金额',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '报价附件',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_inquiry_quote_no_tenant` (`no`,`tenant_id`,`deleted`),
  UNIQUE KEY `uk_erp_inquiry_supplier_version` (`inquiry_id`,`supplier_id`,`version`,`deleted`),
  KEY `idx_erp_inquiry_quote_inquiry` (`inquiry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP询价报价';

CREATE TABLE IF NOT EXISTS `erp_inquiry_quote_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `quote_id` bigint NOT NULL COMMENT '报价编号',
  `inquiry_item_id` bigint NOT NULL COMMENT '询价明细编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_unit_id` bigint NOT NULL COMMENT '产品单位编号',
  `count` decimal(24,6) NOT NULL COMMENT '报价数量',
  `product_price` decimal(24,6) NOT NULL COMMENT '未税单价',
  `tax_percent` decimal(12,4) NOT NULL DEFAULT 0 COMMENT '税率百分比',
  `tax_price` decimal(24,6) NOT NULL DEFAULT 0 COMMENT '税额',
  `freight_price` decimal(24,6) NOT NULL DEFAULT 0 COMMENT '运费',
  `total_price` decimal(24,6) NOT NULL COMMENT '含税总金额',
  `awarded` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否推荐中标',
  `award_count` decimal(24,6) DEFAULT NULL COMMENT '中标数量',
  `award_reason` varchar(512) DEFAULT NULL COMMENT '推荐原因',
  `remark` varchar(512) DEFAULT NULL COMMENT '偏离说明',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_erp_inquiry_quote_item_quote` (`quote_id`),
  KEY `idx_erp_inquiry_quote_item_inquiry_item` (`inquiry_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP询价报价明细';

CREATE TABLE IF NOT EXISTS `erp_cost_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '造价结果编号',
  `name` varchar(128) NOT NULL COMMENT '造价项目名称',
  `project_code` varchar(64) DEFAULT NULL COMMENT '外部项目编码',
  `cost_version` int NOT NULL DEFAULT 1 COMMENT '造价版本',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '审批状态',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT 'BPM流程实例编号',
  `business_status` tinyint NOT NULL DEFAULT 0 COMMENT '业务状态',
  `source_type` tinyint NOT NULL DEFAULT 1 COMMENT '来源 1手工 2文件 3接口',
  `currency` varchar(16) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `total_price` decimal(24,6) NOT NULL DEFAULT 0 COMMENT '造价总金额',
  `mapped_count` int NOT NULL DEFAULT 0 COMMENT '已映射明细数',
  `item_count` int NOT NULL DEFAULT 0 COMMENT '明细总数',
  `received_at` datetime DEFAULT NULL COMMENT '接收时间',
  `converted_type` tinyint DEFAULT NULL COMMENT '转换类型 1销售报价 2销售订单 3项目预算 4归档',
  `converted_id` bigint DEFAULT NULL COMMENT '转换目标编号',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '来源附件',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_cost_result_no_tenant` (`no`,`tenant_id`,`deleted`),
  UNIQUE KEY `uk_erp_cost_project_version` (`project_code`,`cost_version`,`tenant_id`,`deleted`),
  KEY `idx_erp_cost_status` (`status`,`business_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP造价结果';

CREATE TABLE IF NOT EXISTS `erp_cost_result_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `cost_result_id` bigint NOT NULL COMMENT '造价结果编号',
  `line_no` int NOT NULL COMMENT '外部行号',
  `external_material_code` varchar(64) DEFAULT NULL COMMENT '外部材料编码',
  `material_name` varchar(128) NOT NULL COMMENT '材料名称',
  `specification` varchar(256) DEFAULT NULL COMMENT '规格',
  `unit_name` varchar(64) DEFAULT NULL COMMENT '单位名称',
  `product_id` bigint DEFAULT NULL COMMENT 'ERP产品编号',
  `product_unit_id` bigint DEFAULT NULL COMMENT 'ERP产品单位编号',
  `mapping_status` tinyint NOT NULL DEFAULT 0 COMMENT '映射状态 0待映射 10已映射 20忽略',
  `count` decimal(24,6) NOT NULL COMMENT '数量',
  `unit_price` decimal(24,6) NOT NULL COMMENT '单价',
  `tax_percent` decimal(12,4) NOT NULL DEFAULT 0 COMMENT '税率百分比',
  `total_price` decimal(24,6) NOT NULL COMMENT '金额',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_cost_result_line` (`cost_result_id`,`line_no`,`deleted`),
  KEY `idx_erp_cost_result_item_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP造价结果明细';

-- 采购订单来源快照，重复执行安全。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='erp_purchase_order' AND COLUMN_NAME='source_type')=0,
  'ALTER TABLE erp_purchase_order ADD COLUMN source_type VARCHAR(32) NULL COMMENT ''来源类型'' AFTER process_instance_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='erp_purchase_order' AND COLUMN_NAME='source_id')=0,
  'ALTER TABLE erp_purchase_order ADD COLUMN source_id BIGINT NULL COMMENT ''来源编号'' AFTER source_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='erp_purchase_order' AND COLUMN_NAME='source_no')=0,
  'ALTER TABLE erp_purchase_order ADD COLUMN source_no VARCHAR(64) NULL COMMENT ''来源单号'' AFTER source_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='erp_purchase_order' AND COLUMN_NAME='business_chain_no')=0,
  'ALTER TABLE erp_purchase_order ADD COLUMN business_chain_no VARCHAR(64) NULL COMMENT ''全局业务链号'' AFTER source_no', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 菜单：采购申请、询价管理、定标管理放采购管理；造价管理放销售管理。
-- 兼容既有环境中 ERP 根菜单使用 `erp` 或 `/erp` 两种路径。
SET @erp_menu_id = (SELECT id FROM system_menu WHERE path IN ('erp', '/erp') AND type=1 AND deleted=b'0' ORDER BY id LIMIT 1);
SET @purchase_menu_id = (SELECT id FROM system_menu WHERE parent_id=@erp_menu_id AND path='purchase' AND deleted=b'0' ORDER BY id LIMIT 1);
SET @sale_menu_id = (SELECT id FROM system_menu WHERE parent_id=@erp_menu_id AND path='sale' AND deleted=b'0' ORDER BY id LIMIT 1);

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '采购申请','',2,1,@purchase_menu_id,'request','ant-design:file-add-outlined','erp/purchase/request/index','ErpPurchaseRequest',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE @purchase_menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@purchase_menu_id AND path='request' AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '询价管理','',2,2,@purchase_menu_id,'inquiry','ant-design:search-outlined','erp/purchase/inquiry/index','ErpInquiry',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE @purchase_menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@purchase_menu_id AND path='inquiry' AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '定标管理','',2,3,@purchase_menu_id,'award','ant-design:trophy-outlined','erp/purchase/award/index','ErpAwardManagement',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE @purchase_menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@purchase_menu_id AND path='award' AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '造价管理','',2,1,@sale_menu_id,'cost','ant-design:calculator-outlined','erp/sale/cost/index','ErpCostResult',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE @sale_menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE parent_id=@sale_menu_id AND path='cost' AND deleted=b'0');

SET @request_menu_id=(SELECT id FROM system_menu WHERE parent_id=@purchase_menu_id AND path='request' AND deleted=b'0' LIMIT 1);
SET @inquiry_menu_id=(SELECT id FROM system_menu WHERE parent_id=@purchase_menu_id AND path='inquiry' AND deleted=b'0' LIMIT 1);
SET @award_menu_id=(SELECT id FROM system_menu WHERE parent_id=@purchase_menu_id AND path='award' AND deleted=b'0' LIMIT 1);
SET @cost_menu_id=(SELECT id FROM system_menu WHERE parent_id=@sale_menu_id AND path='cost' AND deleted=b'0' LIMIT 1);

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT x.name,x.permission,3,x.sort_no,x.parent_id,'','','',NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM (
  SELECT '采购申请查询' name,'erp:purchase-request:query' permission,1 sort_no,@request_menu_id parent_id UNION ALL
  SELECT '采购申请新增','erp:purchase-request:create',2,@request_menu_id UNION ALL
  SELECT '采购申请修改','erp:purchase-request:update',3,@request_menu_id UNION ALL
  SELECT '采购申请删除','erp:purchase-request:delete',4,@request_menu_id UNION ALL
  SELECT '采购申请提交','erp:purchase-request:submit',5,@request_menu_id UNION ALL
  SELECT '采购申请转询价','erp:purchase-request:convert',6,@request_menu_id UNION ALL
  SELECT '询价查询','erp:inquiry:query',1,@inquiry_menu_id UNION ALL
  SELECT '询价新增','erp:inquiry:create',2,@inquiry_menu_id UNION ALL
  SELECT '询价修改','erp:inquiry:update',3,@inquiry_menu_id UNION ALL
  SELECT '询价删除','erp:inquiry:delete',4,@inquiry_menu_id UNION ALL
  SELECT '询价提交','erp:inquiry:submit',5,@inquiry_menu_id UNION ALL
  SELECT '询价发布','erp:inquiry:publish',6,@inquiry_menu_id UNION ALL
  SELECT '询价截止','erp:inquiry:close',7,@inquiry_menu_id UNION ALL
  SELECT '报价维护','erp:inquiry-quote:manage',8,@inquiry_menu_id UNION ALL
  SELECT '定标查询','erp:award:query',1,@award_menu_id UNION ALL
  SELECT '推荐定标','erp:award:update',2,@award_menu_id UNION ALL
  SELECT '定标提交','erp:award:submit',3,@award_menu_id UNION ALL
  SELECT '定标确认','erp:award:confirm',4,@award_menu_id UNION ALL
  SELECT '造价查询','erp:cost-result:query',1,@cost_menu_id UNION ALL
  SELECT '造价新增','erp:cost-result:create',2,@cost_menu_id UNION ALL
  SELECT '造价修改','erp:cost-result:update',3,@cost_menu_id UNION ALL
  SELECT '造价删除','erp:cost-result:delete',4,@cost_menu_id UNION ALL
  SELECT '造价提交','erp:cost-result:submit',5,@cost_menu_id UNION ALL
  SELECT '造价转换','erp:cost-result:convert',6,@cost_menu_id
) x
WHERE x.parent_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission=x.permission AND m.deleted=b'0');

-- 已拥有采购或销售菜单的角色自动获得对应的新菜单和按钮权限。
INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT DISTINCT source.role_id,target.id,'1',NOW(),'1',NOW(),b'0',source.tenant_id
FROM system_role_menu source
JOIN system_menu target ON target.id IN (@request_menu_id,@inquiry_menu_id,@award_menu_id,@cost_menu_id)
   OR target.parent_id IN (@request_menu_id,@inquiry_menu_id,@award_menu_id,@cost_menu_id)
WHERE source.menu_id IN (@purchase_menu_id,@sale_menu_id) AND source.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu old WHERE old.role_id=source.role_id AND old.menu_id=target.id AND old.tenant_id=source.tenant_id AND old.deleted=b'0');

-- BPM 流程采用系统现有流程设计器维护，可配置任意级审批人。
-- 部署代码后需在“工作流程 -> 流程模型”分别创建并发布以下流程标识：
-- erp-purchase-request-approval（采购申请）、erp-inquiry-approval（询价发布）、
-- erp-inquiry-award-approval（定标）、erp-cost-result-approval（造价结果）。
