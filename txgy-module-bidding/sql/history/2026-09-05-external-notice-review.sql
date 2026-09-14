-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- =====================================================================
-- 外部公告域业务逻辑审查修复迁移（2026-09-05）
-- 对应代码：CrawlTaskServiceImpl（跨批次任务唯一键）、ExternalRedirectService（源附加放行域名）
-- 依赖：先执行 sql/2026-09-03-external-notice.sql（本文件在其后顺序执行；DbIT 自举同样按 09-03 → 09-05 顺序）
-- 规范依据：《招投标模块开发规范》§4.4/§四；设计文档 v0.6 §三/§四/§九
-- =====================================================================

-- 1) 采集任务：唯一键从 (url_hash, source_code, deleted) 扩为 (job_id, url_hash, source_code, deleted)
--    目的：同一 URL 跨批次（二轮调度/人工重采）可落新任务行，不与历史 SUCCESS/FAILED 行互斥；
--    同批次内重复 URL 仍由唯一键去重（job_id 由调度统一归一，无批次=0L，避免 MySQL 唯一键对 NULL 失效）。
ALTER TABLE `bidding_crawl_task`
    DROP INDEX `uk_task_url_hash`,
    ADD UNIQUE KEY `uk_task_job_url` (`job_id`, `url_hash`, `source_code`, `deleted`);

-- 2) 采集源：新增“额外放行域名”列（半角逗号分隔）
--    目的：附件溯源链接常落在源站 CDN/文件服务器（非 base_url 域），/go 白名单 = base_url 域名 ∪ 本列；
--    无该配置的源行为不变（仍只放行 base_url 域及其子域）。
ALTER TABLE `bidding_crawl_source`
    ADD COLUMN `allowed_hosts` varchar(512) DEFAULT NULL COMMENT '额外放行域名（半角逗号分隔：附件 CDN/文件服务器等溯源域名）' AFTER `base_url`;
