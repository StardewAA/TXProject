-- CMS 知识库生命周期增量迁移（MySQL 8.x），可重复执行。执行前请先备份。
DROP PROCEDURE IF EXISTS cms_add_column;
DELIMITER $$
CREATE PROCEDURE cms_add_column(IN p_table varchar(64), IN p_column varchar(64), IN p_definition text)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE statement FROM @ddl;
    EXECUTE statement;
    DEALLOCATE PREPARE statement;
  END IF;
END$$
DELIMITER ;

CALL cms_add_column('cms_knowledge_document', 'doc_category',
  'varchar(32) NOT NULL DEFAULT ''LAW'' COMMENT ''QA:智能问答库, LAW:标准法规库''');
CALL cms_add_column('cms_knowledge_document', 'local_pdf_path',
  'varchar(500) NULL COMMENT ''MinIO或文件服务中的本地原件/快照路径''');
CALL cms_add_column('cms_knowledge_document', 'chunk_strategy',
  'varchar(64) NOT NULL DEFAULT ''REGEX'' COMMENT ''切片策略''');
CALL cms_add_column('cms_knowledge_document', 'chunk_config', 'json NULL COMMENT ''切片参数''');
CALL cms_add_column('cms_knowledge_document', 'review_status',
  'varchar(32) NOT NULL DEFAULT ''PENDING_MANUAL_REVIEW'' COMMENT ''生命周期状态''');
CALL cms_add_column('cms_knowledge_document', 'version', 'bigint NOT NULL DEFAULT 0 COMMENT ''文档乐观锁版本''');

CALL cms_add_column('cms_document_review', 'original_file_path',
  'varchar(500) NULL COMMENT ''服务器磁盘或MinIO/S3中的原件URI''');

CALL cms_add_column('cms_knowledge_chunk', 'es_index_name', 'varchar(64) NULL COMMENT ''实际写入的ES索引''');
CALL cms_add_column('cms_knowledge_chunk', 'page_number', 'int NULL COMMENT ''对应PDF页码''');
CALL cms_add_column('cms_knowledge_chunk', 'ai_review_result',
  'varchar(32) NOT NULL DEFAULT ''PASS'' COMMENT ''PASS/WARN/FAIL''');
CALL cms_add_column('cms_knowledge_chunk', 'ai_review_message', 'varchar(1000) NULL COMMENT ''切片质检说明''');
CALL cms_add_column('cms_knowledge_chunk', 'is_manual_edited',
  'tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否人工修改''');
CALL cms_add_column('cms_knowledge_chunk', 'version', 'bigint NOT NULL DEFAULT 0 COMMENT ''切片乐观锁版本''');
CALL cms_add_column('cms_knowledge_chunk', 'valid',
  'tinyint(1) NOT NULL DEFAULT 1 COMMENT ''部分驳回时是否有效''');

DROP PROCEDURE IF EXISTS cms_add_column;

SET @index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'cms_knowledge_document'
    AND index_name = 'idx_cms_document_lifecycle'
);
SET @index_ddl = IF(@index_exists = 0,
  'CREATE INDEX idx_cms_document_lifecycle ON cms_knowledge_document (tenant_id, doc_category, review_status, deleted_at)',
  'SELECT 1');
PREPARE statement FROM @index_ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;
