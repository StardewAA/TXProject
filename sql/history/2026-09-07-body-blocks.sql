-- =====================================================================
-- 外部公告：正文结构化块（联调清单 R12② + AI 兜底排版）
-- 背景：源站正文可能是真 <table>（常规解析直接块化），也可能是拍平粘连文本
--（无结构，常规解析器给不出表格）——引入正文解析策略链：常规解析（HTML_STRUCTURE）
-- 产物不足时人工触发 AI 兜底（AI_FALLBACK）把脏正文恢复为结构化块。detail 下发
-- body_blocks_json，前端按块组件渲染；content_html 原文留档不受影响。
-- =====================================================================
ALTER TABLE `bidding_notice_external`
    ADD COLUMN `body_parser_code` varchar(32) NULL COMMENT '正文结构化策略码：HTML_STRUCTURE=常规 HTML 结构解析 / AI_FALLBACK=AI 兜底排版（parser_code 记录采集侧 URL 绑定策略，不混用）' AFTER `ai_extract_at`,
    ADD COLUMN `body_blocks_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '正文结构化状态：NONE/SUCCESS/FAILED' AFTER `body_parser_code`,
    ADD COLUMN `body_blocks_json` json NULL COMMENT '正文结构化块（JSON 数组：[{type:heading/paragraph/table/list,...}]；SUCCESS 时下发，前端块渲染优先）' AFTER `body_blocks_status`,
    ADD COLUMN `body_blocks_at` datetime NULL COMMENT '最近一次正文结构化时间' AFTER `body_blocks_json`;
