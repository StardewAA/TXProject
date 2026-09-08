-- ============================================================================
-- bidding_crawl_task 增加 payload_json：列表阶段快照（QYZB 全文 HTML / GX 标题日期）
-- 设计：MySQL = 持久队列（唯一真相），Redis Stream = 轻信号（仅 taskId），全文不进 Redis
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p123456 ruoyi-vue-pro < 本文件
-- ============================================================================
ALTER TABLE `bidding_crawl_task`
    ADD COLUMN `payload_json` MEDIUMTEXT NULL COMMENT '列表阶段条目快照 JSON（QYZB 全文 HTML/GX 标题日期；持久队列载体，Redis 消息仅带 taskId）' AFTER `message`;
