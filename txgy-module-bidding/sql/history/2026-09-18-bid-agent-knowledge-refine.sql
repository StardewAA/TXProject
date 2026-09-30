-- ============================================================
-- 标书智能体：知识库管理模块完善（切片人工治理）
-- 日期：2026-09-18 ｜ 域：bidding_bid_agent_chunk
-- 规范依据：《招投标模块开发规范》§4.8（SQL 幂等、无破坏性语句）
-- 业务目标：
--   切片管理（知识库管理 → 切片管理）支持人工编辑/拆分/合并/删除切片，
--   需要给切片表补「是否人工修改」标记，区分机器切片与人工优化切片，
--   便于质量巡检与「修改后重建向量」的闭环提示。
-- 说明：切片表 bidding_bid_agent_chunk 是检索链（向量 + BM25）共用的切片台账，
--       本脚本只补列，不动现有列/索引，存量切片数据无需重写。
-- 幂等：DDL 走 information_schema 判断 + PREPARE，本文件不含 DROP / TRUNCATE / DELETE。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 切片表补「是否人工修改」列（幂等：已存在则跳过）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bidding_bid_agent_chunk'
      AND column_name = 'edited'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `bidding_bid_agent_chunk` ADD COLUMN `edited` tinyint NOT NULL DEFAULT 0 COMMENT ''是否被人工编辑过：0 否 1 是'' AFTER `content`',
    'SELECT ''edited already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
