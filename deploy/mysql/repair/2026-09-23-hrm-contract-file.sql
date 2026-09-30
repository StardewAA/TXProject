-- B1：合同文件台账（区分「系统生成稿 / 签署件」，支持版本溯源）
CREATE TABLE IF NOT EXISTS `hrm_employee_contract_file` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `contract_id` bigint NOT NULL COMMENT '合同编号',
    `employee_id` bigint DEFAULT NULL COMMENT '员工编号',
    `file_type` tinyint NOT NULL DEFAULT 1 COMMENT '文件类型：1 系统生成稿，2 签署件',
    `version` int NOT NULL DEFAULT 1 COMMENT '版本号（同一合同同类型自增）',
    `file_name` varchar(255) NOT NULL COMMENT '展示文件名（员工姓名-合同类型）',
    `file_url` varchar(1024) NOT NULL COMMENT '文件地址',
    `template_id` bigint DEFAULT NULL COMMENT '生成稿使用的合同模板编号（版本溯源）',
    `render_snapshot` text COMMENT '生成时的关键字段快照 JSON（薪资/期限/主体等）',
    `sign_record_id` bigint DEFAULT NULL COMMENT '签署件对应的签署记录编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_contract` (`contract_id`),
    KEY `idx_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 合同文件台账';
