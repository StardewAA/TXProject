-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 源配置对齐：增量小窗口 + 首跑全量窗口分离（2026-09-10）
-- 语义：lookbackDays=增量窗口（每次调度只回溯 N 天）；fullLookbackDays=首跑全量窗口（源无 SUCCESS 历史时回溯 60 天）
-- fetch_interval_seconds=300（下载器间隔 5 分钟，用户口径；原 QYZB 180 偏密）
UPDATE bidding_crawl_source SET fetch_interval_seconds = 300, entry_urls_json = '[{"name": "招标公告", "parser": "QYZB_JSON", "siteId": "725", "maxPages": 10, "pageSize": 10, "categoryId": "203", "lookbackDays": 3, "fullLookbackDays": 60}, {"name": "采购/招商", "parser": "QYZB_JSON", "siteId": "725", "maxPages": 10, "pageSize": 10, "categoryId": "204", "lookbackDays": 3, "fullLookbackDays": 60}, {"name": "变更公告", "parser": "QYZB_JSON", "siteId": "725", "maxPages": 10, "pageSize": 10, "categoryId": "205", "lookbackDays": 3, "fullLookbackDays": 60}]' WHERE source_code = 'QYZB';
UPDATE bidding_crawl_source SET fetch_interval_seconds = 300, entry_urls_json = '[{"name": "依法招标-招标公告", "parser": "GX_E_BIDDING", "listUrl": "https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=01&tabName=招标公告&page=1", "maxPages": 10, "lookbackDays": 60, "fullLookbackDays": 60}]' WHERE source_code = 'GX';
