-- 外部公告 AI 结构化抽取（PDF→AI 结构化输出，供检索/信息提取；待 AI 渠道就绪后联调）
-- 背景：采集链本地 PDFBox 只能抽文字层文本（扫描件无文字层=FAIL_EMPTY）；AI 抽取可
-- 结构化字段 + 扫描件全文转写，转写全文回写 extraction_text（复用既有 ES contentText 索引源）。
ALTER TABLE `bidding_notice_external`
    ADD COLUMN `ai_extraction_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'AI 结构化抽取状态：NONE/SUCCESS/FAILED' AFTER `extraction_at`,
    ADD COLUMN `ai_meta_json` json NULL COMMENT 'AI 结构化字段集（JSON；含 noticeFullText=全文转写，非空回写 extraction_text）' AFTER `ai_extraction_status`,
    ADD COLUMN `ai_extract_at` datetime NULL COMMENT '最近一次 AI 抽取时间' AFTER `ai_meta_json`;
