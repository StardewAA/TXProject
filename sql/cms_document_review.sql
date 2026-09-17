-- CMS 审查数据库迁移。只建新增表，不修改已有数据；在现有业务库中执行。
CREATE TABLE IF NOT EXISTS cms_document_review (
  id varchar(36) NOT NULL,
  tenant_id bigint NOT NULL,
  snapshot longtext NOT NULL COMMENT '输入、报告、核验历史的JSON快照',
  original_file longblob NULL COMMENT '失败文件也保留；超限文件除外',
  decision varchar(16) NOT NULL,
  created_at datetime NOT NULL,
  PRIMARY KEY (tenant_id,id),
  KEY idx_cms_review_created (tenant_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cms_knowledge_document (
  review_id varchar(36) NOT NULL,
  tenant_id bigint NOT NULL,
  title varchar(300) NOT NULL,
  validity varchar(32) NOT NULL,
  source_url varchar(2000) NOT NULL,
  text_hash char(64) NOT NULL,
  content longtext NOT NULL,
  published bit(1) NOT NULL DEFAULT 0 COMMENT '审查通过后仅入库，由管理员发布后才可检索',
  deleted_at datetime NULL COMMENT '管理员下架时间，审查记录仍保留',
  created_at datetime NOT NULL,
  PRIMARY KEY (tenant_id,review_id),
  CONSTRAINT fk_cms_knowledge_review FOREIGN KEY (tenant_id,review_id) REFERENCES cms_document_review(tenant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cms_knowledge_chunk (
  review_id varchar(36) NOT NULL,
  tenant_id bigint NOT NULL,
  ordinal int NOT NULL,
  start_offset int NOT NULL,
  end_offset int NOT NULL,
  content text NOT NULL,
  PRIMARY KEY (tenant_id,review_id,ordinal),
  CONSTRAINT fk_cms_chunk_document FOREIGN KEY (tenant_id,review_id) REFERENCES cms_knowledge_document(tenant_id,review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
