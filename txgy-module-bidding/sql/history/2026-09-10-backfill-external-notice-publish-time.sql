-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- ============================================================================
-- 外部公告发布时间存量回填（BR-5）
-- [依据] 《招标投标法》第16条 / 《招标公告和公示信息发布管理办法》第5条：招标公告应载明（并公示）时间信息；
--        列表“发布日期”与时间倒序依赖 bidding_notice_external.publish_time 非空、非零值。
-- 说明：
--   1. 本脚本面向演示库/历史库中 publish_time 为 NULL 或零日期（'0000-00-00 00:00:00'，宽松模式）的记录；
--   2. 回填口径：publish_time 为空/零 → 取 create_time（入库留档时间），再兜底 last_fetch_time/当前时间；
--      （抓取时间语义与 DO 注释“解析失败取抓取时间”一致；后续新入库由 ingest 原文日期解析器兜底）
--   3. 若库含零日期且当前为严格模式，请先执行第 0 步转换零日期为 NULL。
--   4. 新入库保证：ExternalNoticeIngestServiceImpl.resolvePublishTime —— 解析器命中原文发布日期，
--      否则取抓取时间，恒非空（BR-5 第 3 点）。
-- 适用范围：bidding_notice_external（G 轨主档）。请 DBA 在目标运行库执行并复核行数。
-- ============================================================================

-- 第 0 步（仅当存在零日期且无法直接比较时执行）：零日期归一为 NULL
-- UPDATE bidding_notice_external SET publish_time = NULL
--  WHERE publish_time = '0000-00-00 00:00:00';

-- 第 1 步：存量回填（可重复执行；先 SELECT 预览再放开注释执行 UPDATE）
-- SELECT id, title, publish_time, create_time, last_fetch_time FROM bidding_notice_external
--  WHERE publish_time IS NULL OR YEAR(publish_time) < 2000;

UPDATE bidding_notice_external
   SET publish_time = COALESCE(create_time, last_fetch_time, NOW())
 WHERE publish_time IS NULL OR YEAR(publish_time) < 2000;
