-- ============================================================================
-- 外部信息获取模块更名迁移（2026-09-10）
-- 1) 采集域三表 bidding_crawl_* -> bidding_fetch_*（信息获取）
-- 2) bidding_fetch_source 增加 fetch_type + api_config_json（API 源样板）
-- 前置：bidding_crawl_task.payload_json 已存在（sql/2026-09-10-crawl-task-payload-queue.sql）
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p123456 ruoyi-vue-pro < 本文件
-- ============================================================================
RENAME TABLE bidding_crawl_source TO bidding_fetch_source,
             bidding_crawl_job   TO bidding_fetch_job,
             bidding_crawl_task  TO bidding_fetch_task;

ALTER TABLE bidding_fetch_source
    ADD COLUMN fetch_type varchar(16) NOT NULL DEFAULT 'CRAWLER'
        COMMENT '获取方式：CRAWLER=爬虫(列表HTML) / API=接口直取(JSON)' AFTER channel_code,
    ADD COLUMN api_config_json json NULL
        COMMENT 'API 源配置：{method,url,headers,params,page,rowsPath,totalPath,fieldMapping,envelope}' AFTER fetch_type;
