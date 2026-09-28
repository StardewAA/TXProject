-- CMS 法律效力定时核验待办（MySQL 8.x）。程序只生成风险待办，管理员确认后才归档。
CREATE TABLE IF NOT EXISTS cms_law_validity_task (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  review_id varchar(36) NOT NULL,
  event_key char(64) NOT NULL COMMENT '同一文档、风险类型、远端状态的幂等键',
  title varchar(300) NOT NULL,
  source_url varchar(2000) NOT NULL,
  issue_type varchar(32) NOT NULL COMMENT 'SOURCE_UNREACHABLE/REMOTE_STATUS_REVIEW/STATUS_MISMATCH',
  current_validity varchar(32) NOT NULL,
  remote_status varchar(64) NULL,
  remote_title varchar(300) NULL,
  message varchar(1000) NOT NULL,
  task_status varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DISMISSED/ARCHIVED/RECOVERED',
  occurrence_count int NOT NULL DEFAULT 1,
  first_detected_at datetime NOT NULL,
  last_checked_at datetime NOT NULL,
  resolved_at datetime NULL,
  resolution_note varchar(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cms_law_validity_event (tenant_id,event_key),
  KEY idx_cms_law_validity_pending (tenant_id,task_status,last_checked_at),
  KEY idx_cms_law_validity_document (tenant_id,review_id),
  CONSTRAINT fk_cms_law_validity_document
    FOREIGN KEY (tenant_id,review_id)
    REFERENCES cms_knowledge_document (tenant_id,review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法律效力与官方来源核验待办';
