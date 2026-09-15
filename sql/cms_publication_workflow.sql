-- 将“审查入库”与“对外发布”分离。新装库的默认值已在 cms_document_review.sql 中设为 0。
-- 本迁移只修改后续新文档的默认值，不会下架已发布的存量内容。
ALTER TABLE cms_knowledge_document
  MODIFY COLUMN published bit(1) NOT NULL DEFAULT b'0'
  COMMENT '审查通过后仅入库，由管理员发布后才可检索';
