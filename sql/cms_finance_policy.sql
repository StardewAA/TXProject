-- 企业金融政策库增量迁移（MySQL 8.x）。执行前请备份。
CREATE TABLE IF NOT EXISTS cms_finance_policy_ext (
  tenant_id bigint NOT NULL COMMENT '租户编号',
  review_id varchar(36) NOT NULL COMMENT '关联知识文档审查编号',
  applicable_regions json NULL COMMENT '适用地区数组',
  applicable_industries json NULL COMMENT '适用行业数组',
  applicable_enterprise_types json NULL COMMENT '适用企业类型数组',
  support_type varchar(64) NULL COMMENT '支持方式',
  amount_min decimal(15,2) NULL COMMENT '最低金额',
  amount_max decimal(15,2) NULL COMMENT '最高金额',
  amount_unit varchar(16) NOT NULL DEFAULT '万元' COMMENT '金额单位',
  support_rate decimal(5,2) NULL COMMENT '支持比例（%）',
  support_description text NULL COMMENT '原始金额描述',
  apply_start_time datetime NULL COMMENT '申报开始时间',
  apply_end_time datetime NULL COMMENT '申报截止时间',
  policy_status tinyint NOT NULL DEFAULT 1 COMMENT '1现行 2即将截止 3已废止',
  interpretation_status varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REVIEWING/APPROVED/REJECTED',
  interpretation_json json NULL COMMENT '经过后端校验的AI结构化解读',
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  version int NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, review_id),
  KEY idx_finance_policy_deadline (tenant_id, policy_status, apply_end_time),
  CONSTRAINT fk_finance_policy_document FOREIGN KEY (tenant_id, review_id)
    REFERENCES cms_knowledge_document (tenant_id, review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业金融政策结构化扩展';

-- 将历史 POLICY 文档从法规库迁移到金融政策库。仅修改未归档知识文档的路由字段；
-- 执行后必须运行 ES 初始化脚本并在 CMS 管理端执行一次“重建索引”。
UPDATE cms_knowledge_document d
JOIN cms_document_review r ON r.tenant_id=d.tenant_id AND r.id=d.review_id
SET d.doc_category='FINANCE_POLICY', d.version=d.version+1
WHERE d.doc_category='LAW'
  AND JSON_UNQUOTE(JSON_EXTRACT(r.snapshot, '$.report.metadata.kind'))='POLICY';
