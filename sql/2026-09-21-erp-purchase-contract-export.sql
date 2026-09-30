-- ERP 采购合同结构化条款、模板与 PDF 导出（仅结构及必要默认配置，不含测试业务数据）

ALTER TABLE `erp_purchase_contract`
  ADD COLUMN `delivery_deadline` datetime DEFAULT NULL COMMENT '约定交货日期' AFTER `delivery_place`,
  ADD COLUMN `sign_place` varchar(128) DEFAULT NULL COMMENT '签订地点' AFTER `delivery_deadline`,
  ADD COLUMN `currency` varchar(32) DEFAULT NULL COMMENT '币种' AFTER `sign_place`,
  ADD COLUMN `quality_standard` text COMMENT '质量标准快照' AFTER `signer_id`,
  ADD COLUMN `acceptance_period_days` int DEFAULT NULL COMMENT '验收期限（天）' AFTER `quality_standard`,
  ADD COLUMN `warranty_period_months` int DEFAULT NULL COMMENT '质保期限（月）' AFTER `acceptance_period_days`,
  ADD COLUMN `after_sale_response_hours` int DEFAULT NULL COMMENT '售后响应时限（小时）' AFTER `warranty_period_months`,
  ADD COLUMN `on_site_service_hours` int DEFAULT NULL COMMENT '到场服务时限（小时）' AFTER `after_sale_response_hours`,
  ADD COLUMN `max_repair_times` int DEFAULT NULL COMMENT '最大维修次数' AFTER `on_site_service_hours`,
  ADD COLUMN `advance_payment_ratio` decimal(8,4) DEFAULT NULL COMMENT '预付款比例（%）' AFTER `max_repair_times`,
  ADD COLUMN `advance_payment_days` int DEFAULT NULL COMMENT '预付款支付期限（天）' AFTER `advance_payment_ratio`,
  ADD COLUMN `acceptance_payment_ratio` decimal(8,4) DEFAULT NULL COMMENT '验收款比例（%）' AFTER `advance_payment_days`,
  ADD COLUMN `acceptance_payment_days` int DEFAULT NULL COMMENT '验收款支付期限（天）' AFTER `acceptance_payment_ratio`,
  ADD COLUMN `warranty_retention_ratio` decimal(8,4) DEFAULT NULL COMMENT '质保金比例（%）' AFTER `acceptance_payment_days`,
  ADD COLUMN `warranty_release_days` int DEFAULT NULL COMMENT '质保金释放期限（天）' AFTER `warranty_retention_ratio`,
  ADD COLUMN `warranty_release_condition` varchar(256) DEFAULT NULL COMMENT '质保金释放条件' AFTER `warranty_release_days`,
  ADD COLUMN `buyer_reject_penalty_ratio` decimal(8,4) DEFAULT NULL COMMENT '甲方拒收违约金比例（%）' AFTER `warranty_release_condition`,
  ADD COLUMN `buyer_overdue_rate` decimal(8,4) DEFAULT NULL COMMENT '甲方逾期付款每日违约金率（‰）' AFTER `buyer_reject_penalty_ratio`,
  ADD COLUMN `buyer_termination_days` int DEFAULT NULL COMMENT '甲方逾期付款解约天数' AFTER `buyer_overdue_rate`,
  ADD COLUMN `seller_quality_penalty_ratio` decimal(8,4) DEFAULT NULL COMMENT '乙方质量不合格违约金比例（%）' AFTER `buyer_termination_days`,
  ADD COLUMN `seller_overdue_rate` decimal(8,4) DEFAULT NULL COMMENT '乙方逾期交货每日违约金率（‰）' AFTER `seller_quality_penalty_ratio`,
  ADD COLUMN `seller_termination_days` int DEFAULT NULL COMMENT '乙方逾期交货解约天数' AFTER `seller_overdue_rate`,
  ADD COLUMN `seller_termination_compensation_ratio` decimal(8,4) DEFAULT NULL COMMENT '乙方逾期解约赔偿比例（%）' AFTER `seller_termination_days`,
  ADD COLUMN `ip_defect_penalty_ratio` decimal(8,4) DEFAULT NULL COMMENT '知识产权瑕疵违约金比例（%）' AFTER `seller_termination_compensation_ratio`,
  ADD COLUMN `buyer_name` varchar(128) DEFAULT NULL COMMENT '甲方名称快照' AFTER `ip_defect_penalty_ratio`,
  ADD COLUMN `buyer_legal_representative` varchar(64) DEFAULT NULL COMMENT '甲方法定代表人或授权代表' AFTER `buyer_name`,
  ADD COLUMN `buyer_address` varchar(256) DEFAULT NULL COMMENT '甲方地址' AFTER `buyer_legal_representative`,
  ADD COLUMN `buyer_bank_name` varchar(128) DEFAULT NULL COMMENT '甲方开户行' AFTER `buyer_address`,
  ADD COLUMN `buyer_bank_account` varchar(64) DEFAULT NULL COMMENT '甲方银行账号' AFTER `buyer_bank_name`,
  ADD COLUMN `buyer_phone` varchar(32) DEFAULT NULL COMMENT '甲方电话' AFTER `buyer_bank_account`,
  ADD COLUMN `buyer_fax` varchar(32) DEFAULT NULL COMMENT '甲方传真' AFTER `buyer_phone`,
  ADD COLUMN `supplier_name_snapshot` varchar(128) DEFAULT NULL COMMENT '乙方名称快照' AFTER `buyer_fax`,
  ADD COLUMN `seller_legal_representative` varchar(64) DEFAULT NULL COMMENT '乙方法定代表人或授权代表' AFTER `supplier_name_snapshot`,
  ADD COLUMN `seller_address` varchar(256) DEFAULT NULL COMMENT '乙方地址' AFTER `seller_legal_representative`,
  ADD COLUMN `seller_bank_name` varchar(128) DEFAULT NULL COMMENT '乙方开户行' AFTER `seller_address`,
  ADD COLUMN `seller_bank_account` varchar(64) DEFAULT NULL COMMENT '乙方银行账号' AFTER `seller_bank_name`,
  ADD COLUMN `seller_phone` varchar(32) DEFAULT NULL COMMENT '乙方电话' AFTER `seller_bank_account`,
  ADD COLUMN `seller_fax` varchar(32) DEFAULT NULL COMMENT '乙方传真' AFTER `seller_phone`,
  ADD COLUMN `template_id` bigint DEFAULT NULL COMMENT '合同模板编号' AFTER `seller_fax`,
  ADD COLUMN `template_name` varchar(128) DEFAULT NULL COMMENT '合同模板名称快照' AFTER `template_id`,
  ADD COLUMN `template_version` varchar(32) DEFAULT NULL COMMENT '合同模板版本快照' AFTER `template_name`,
  ADD COLUMN `legal_terms` longtext COMMENT '通用法律条款快照' AFTER `template_version`;

CREATE TABLE IF NOT EXISTS `erp_purchase_contract_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(128) NOT NULL COMMENT '模板名称',
  `version` varchar(32) NOT NULL COMMENT '模板版本',
  `content` longtext NOT NULL COMMENT '通用法律条款',
  `default_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认模板',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0启用 1停用',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_purchase_contract_template_name_version` (`name`,`version`,`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP采购合同条款模板';

INSERT INTO `erp_purchase_contract_template`
(`name`,`version`,`content`,`default_flag`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '企业采购合同通用模板','v1.0',
       '货物应为全新且权属清晰，不得侵犯第三方知识产权。乙方应按合同约定完成交付、质量整改和售后服务。合同履行过程中发生争议，双方应先行协商；协商不成的，依法维护各自合法权益。未尽事宜由双方另行签订补充协议。本合同经双方签章后生效。',
       b'1',0,'admin',NOW(),'admin',NOW(),b'0',2001
WHERE NOT EXISTS (
  SELECT 1 FROM `erp_purchase_contract_template`
  WHERE `name`='企业采购合同通用模板' AND `version`='v1.0' AND `tenant_id`=2001 AND `deleted`=b'0'
);

SET @contract_menu_id = (
  SELECT `id` FROM `system_menu`
  WHERE `component`='erp/purchase/contract/index' AND `deleted`=b'0'
  ORDER BY `id` DESC LIMIT 1
);
INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '采购合同导出','erp:purchase-contract:export',3,7,@contract_menu_id,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE @contract_menu_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `permission`='erp:purchase-contract:export' AND `deleted`=b'0'
);
INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '采购合同模板管理','erp:purchase-contract:template',3,8,@contract_menu_id,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE @contract_menu_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `permission`='erp:purchase-contract:template' AND `deleted`=b'0'
);

-- 已经能查看采购合同的角色可导出 PDF；已经能修改合同的角色可维护条款模板。
-- 这样升级后原有业务角色无需管理员逐个补选新按钮，同时不会把权限扩大给无合同权限的账号。
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT source_rm.`role_id`, target_menu.`id`, 'admin', NOW(), 'admin', NOW(), b'0', source_rm.`tenant_id`
FROM `system_role_menu` source_rm
JOIN `system_menu` source_menu ON source_menu.`id`=source_rm.`menu_id`
JOIN `system_menu` target_menu ON target_menu.`permission`='erp:purchase-contract:export' AND target_menu.`deleted`=b'0'
WHERE source_menu.`permission`='erp:purchase-contract:query'
  AND source_menu.`deleted`=b'0' AND source_rm.`deleted`=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.`role_id`=source_rm.`role_id` AND existing.`menu_id`=target_menu.`id`
      AND existing.`tenant_id`=source_rm.`tenant_id` AND existing.`deleted`=b'0'
  );

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT source_rm.`role_id`, target_menu.`id`, 'admin', NOW(), 'admin', NOW(), b'0', source_rm.`tenant_id`
FROM `system_role_menu` source_rm
JOIN `system_menu` source_menu ON source_menu.`id`=source_rm.`menu_id`
JOIN `system_menu` target_menu ON target_menu.`permission`='erp:purchase-contract:template' AND target_menu.`deleted`=b'0'
WHERE source_menu.`permission`='erp:purchase-contract:update'
  AND source_menu.`deleted`=b'0' AND source_rm.`deleted`=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.`role_id`=source_rm.`role_id` AND existing.`menu_id`=target_menu.`id`
      AND existing.`tenant_id`=source_rm.`tenant_id` AND existing.`deleted`=b'0'
  );
