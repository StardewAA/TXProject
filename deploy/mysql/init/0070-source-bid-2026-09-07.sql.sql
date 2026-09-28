-- ============================================================
-- 招投标模块（txgy-module-bid）建表脚本
-- 对应《标书Agent_最终架构设计》第 3.1 章契约
-- ============================================================

CREATE TABLE IF NOT EXISTS `bid_project` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` varchar(255) DEFAULT NULL COMMENT '项目名称',
    `facts_json` text COMMENT '项目事实卡（JSON：工期/质量目标/报价策略/关键约束）',
    `tender_parse_json` text COMMENT '招标文件解析结果（JSON）',
    `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0 启用 1 禁用）',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='招投标项目表';

CREATE TABLE IF NOT EXISTS `bid_enterprise_fact` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `type` varchar(32) DEFAULT NULL COMMENT '类型：LICENSE/QUAL/PERFORMANCE/PERSON/FINANCE/PLAN/PRICE',
    `name` varchar(255) DEFAULT NULL COMMENT '事实名称',
    `value` varchar(1000) DEFAULT NULL COMMENT '事实值',
    `unit` varchar(32) DEFAULT NULL COMMENT '单位',
    `valid_until` date DEFAULT NULL COMMENT '有效期',
    `source_doc_name` varchar(255) DEFAULT NULL COMMENT '来源文档名',
    `source_block_ids` varchar(1000) DEFAULT NULL COMMENT '溯源块编号（逗号分隔）',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_type` (`tenant_id`, `type`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='企业事实表';

CREATE TABLE IF NOT EXISTS `bid_chapter_draft` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `project_id` bigint NOT NULL COMMENT '项目编号',
    `node_id` varchar(64) NOT NULL COMMENT '章节节点编号',
    `content` longtext COMMENT '章节正文',
    `refs` varchar(2000) DEFAULT NULL COMMENT '溯源引用块编号（逗号分隔）',
    `status` varchar(16) DEFAULT 'draft' COMMENT '状态：draft/review/confirmed',
    `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_node_version` (`project_id`, `node_id`, `version`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='章节草稿表';

CREATE TABLE IF NOT EXISTS `bid_task_shadow` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `task_id` varchar(64) NOT NULL COMMENT 'Agent 任务编号',
    `task_type` varchar(16) DEFAULT NULL COMMENT '任务类型：parse/outline/draft/fill',
    `project_id` bigint DEFAULT NULL COMMENT '项目编号',
    `status` varchar(32) DEFAULT NULL COMMENT '任务状态',
    `phase` varchar(32) DEFAULT NULL COMMENT '当前阶段',
    `progress` decimal(5,4) DEFAULT NULL COMMENT '进度 0~1',
    `message` varchar(1000) DEFAULT NULL COMMENT '结果摘要或错误信息',
    `agent_updated_at` varchar(32) DEFAULT NULL COMMENT 'Agent 侧更新时间（ISO 8601）',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='Agent 任务影子状态表';
