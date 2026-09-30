-- ============================================================
-- 标书智能体：检索关键词通道（BM25 / MySQL FULLTEXT + ngram）
-- 日期：2026-09-28 ｜ 域：bidding_bid_agent_chunk
-- 规范依据：《招投标模块开发规范》§4.8（SQL 幂等、无破坏性语句）；文档 24 §5 待办 1
-- 业务目标：
--   现有检索只有「向量 + rerank」一条腿，遇到条款编号、证书编号、金额、工期这类
--   精确词/数字查询容易漏召回（embedding 对数字与专有名词不敏感）。
--   本脚本给切片表补一个中文 ngram 全文索引，供 BidRetrievalServiceImpl 的关键词通道
--   用 MATCH ... AGAINST 做粗召回，再与向量候选做 RRF 融合。
-- 说明：
--   1) 只加索引，不改列、不动现有索引，切片数据无需重写；
--   2) 用 ngram 解析器（中文无空格，standard 解析器按单字切分不可用）。
--      实例 ngram_token_size=2（已实测），运行期不可改；如需修改必须重建本索引；
--   3) 顺序依赖：本脚本必须晚于 2026-09-27-bid-agent-runtime.sql（切片表在该脚本创建），
--      故文件日期取 2026-09-28。
-- 幂等：先查 information_schema 判断索引/表是否存在，存在即跳过；本文件不含 DROP / TRUNCATE / DELETE。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 切片表补中文全文索引 ft_chunk_content（幂等：已存在则跳过）
-- ------------------------------------------------------------
SET @table_exists := (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'bidding_bid_agent_chunk'
);
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bidding_bid_agent_chunk'
      AND index_name = 'ft_chunk_content'
);
SET @ddl := IF(@table_exists = 0,
    'SELECT ''bidding_bid_agent_chunk not exists, skip bm25 index'' AS skipped',
    IF(@index_exists > 0,
        'SELECT ''ft_chunk_content already exists'' AS skipped',
        'ALTER TABLE `bidding_bid_agent_chunk` ADD FULLTEXT INDEX `ft_chunk_content` (`content`) WITH PARSER ngram'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
