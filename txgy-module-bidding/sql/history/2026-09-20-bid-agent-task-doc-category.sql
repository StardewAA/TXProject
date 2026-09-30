-- ============================================================
-- 标书智能体：任务台账补 doc_category 列（P1-6 修复：标书类型选择此前无任何作用）
-- 日期：2026-09-20 ｜ 域：bidding_bid_agent_task
-- 背景：前台工坊「技术标书/商务标书」选择只驱动按钮高亮，未落库、未透传编排侧。
-- 口径：落台账 + 透传底座 payload，编排侧按类型组织章节大纲。
-- 幂等：先查 information_schema 再 ALTER。
-- ============================================================

SET NAMES utf8mb4;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_task' AND column_name = 'doc_category');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_task` ADD COLUMN `doc_category` varchar(16) DEFAULT NULL COMMENT ''标书类型：technical 技术标书 / business 商务标书（前台工坊选择，透传编排侧）'' AFTER `bid_section_code`', 'SELECT ''task.doc_category exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
