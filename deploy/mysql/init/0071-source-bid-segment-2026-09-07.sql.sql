-- ============================================================
-- 招投标模块（txgy-module-bid）检索片段表
-- 用于向量入库后的溯源回表
-- ============================================================

CREATE TABLE IF NOT EXISTS `bid_segment` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `vector_id` varchar(64) DEFAULT NULL COMMENT '向量库中的向量 ID',
    `project_id` bigint DEFAULT NULL COMMENT '项目编号',
    `doc_type` varchar(16) DEFAULT NULL COMMENT '文档类型：TENDER/FACT/TEMPLATE',
    `source_doc_name` varchar(255) DEFAULT NULL COMMENT '来源文档名',
    `chapter_path` varchar(255) DEFAULT NULL COMMENT '章节路径',
    `page_no` int DEFAULT NULL COMMENT '页码',
    `source_block_ids` varchar(1000) DEFAULT NULL COMMENT '溯源块编号（逗号分隔）',
    `content` text COMMENT '片段内容',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_doc` (`tenant_id`, `source_doc_name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='投标检索片段表';
