-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 2026-09-10 外部公告 source_name 快照回填（P2-3）
-- 背景：ingest 落库有 source_name 列，但上游 DTO 长期未传（683/687 行为 NULL）；
--       已加 ingest 兜底（按 source_code 查源配置取名），本脚本回填存量。
-- 幂等可重跑。

UPDATE bidding_notice_external e
JOIN bidding_fetch_source s ON e.source_code = s.source_code AND s.deleted = 0
SET e.source_name = s.source_name
WHERE e.source_name IS NULL OR e.source_name = '';

-- 校验：应剩余 0 条缺失
SELECT COUNT(*) AS still_missing
FROM bidding_notice_external
WHERE source_name IS NULL OR source_name = '';
