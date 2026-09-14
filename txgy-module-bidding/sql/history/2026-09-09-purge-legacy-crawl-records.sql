-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 2026-09-09 一次性清理：删除旧体系爬虫运行记录（task/job），之后按批次 jobId 管理。
-- 背景：bidding_crawl_task 残留 10 条旧版失败记录均无 job_id（旧 runner 只记失败、无批次归属）；
--       bidding_crawl_job 2 条为早期冒烟批次。采集源配置(bidding_crawl_source)与采集公告(bidding_notice_external)不受影响。
-- 管理口径（当前代码已实现，见 docs/模块设计与规范/2026-09-08-crawl-contract-batch.md）：
--   每轮执行(人工 MANUAL / 调度 SCHEDULE) = 一个批次 bidding_crawl_job(带 jobId)；
--   仅失败条目落 bidding_crawl_task 并挂 jobId，可在前端任务页按 jobId 过滤后单条重试；
--   成功后进入公告库，不落任务行（避免海量痕迹）。

DELETE FROM bidding_crawl_task;
DELETE FROM bidding_crawl_job;

