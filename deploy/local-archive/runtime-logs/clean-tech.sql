DELETE c FROM `ruoyi-vue-pro`.cms_knowledge_chunk c
  JOIN `ruoyi-vue-pro`.cms_knowledge_document d
    ON d.review_id = c.review_id AND d.tenant_id = c.tenant_id
 WHERE d.tenant_id = 1 AND d.doc_category = 'QA';
DELETE FROM `ruoyi-vue-pro`.cms_knowledge_document WHERE tenant_id = 1 AND doc_category = 'QA';
DELETE FROM `ruoyi-vue-pro`.cms_document_review WHERE tenant_id = 1 AND original_file_path LIKE '/files/assets/PDF%';
SELECT COUNT(*) AS qa_docs FROM `ruoyi-vue-pro`.cms_knowledge_document WHERE tenant_id=1 AND doc_category='QA';
SELECT COUNT(*) AS chunks FROM `ruoyi-vue-pro`.cms_knowledge_chunk WHERE tenant_id=1;