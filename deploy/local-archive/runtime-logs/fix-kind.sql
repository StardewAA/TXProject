UPDATE cms_document_review
SET snapshot = REPLACE(REPLACE(snapshot, '"kind": "QA"', '"kind": "STANDARD"'), '"kind":"QA"', '"kind":"STANDARD"')
WHERE tenant_id=1 AND original_file_path LIKE '/files/assets/PDF%';
SELECT COUNT(*) FROM cms_document_review WHERE tenant_id=1 AND original_file_path LIKE '/files/assets/PDF%';