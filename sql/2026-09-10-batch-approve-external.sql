-- 2026-09-10 外部公告存量批量审核上线（平台运营一次性放行）
-- 背景：es.enabled 启用前/审核演示期积压的 NEW 公告（674 条，素材齐备：661 HTML 全含正文、13 PDF）
--       从未进公告大厅（大厅只展示 ACTIVE）；本次按平台运营口径批量转 ACTIVE+APPROVED，
--       随后执行 POST /bidding/notice-external/reindex 全量重建 ES 索引。
-- 幂等：仅处理 NEW+UNREVIEWED。

UPDATE bidding_notice_external
SET status = 'ACTIVE', audit_status = 'APPROVED'
WHERE status = 'NEW' AND audit_status = 'UNREVIEWED';

SELECT status, audit_status, COUNT(*) c FROM bidding_notice_external GROUP BY status, audit_status;
