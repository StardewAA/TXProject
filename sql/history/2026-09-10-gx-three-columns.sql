-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 2026-09-10 GX 栏目补全（实测：依法招标最新 07-10=站方久未发布；非依法招标/非依法采购当日有更新，iframe 列表同构）
-- 原单栏目保留；新增 02 非依法招标 / 00 非依法采购（窗口 3 天起步，历史可调大后 run-full）
UPDATE bidding_fetch_source
SET entry_urls_json = '[{"name":"依法招标-招标公告","parser":"GX_E_BIDDING","listUrl":"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=01&tabName=招标公告&page=1","maxPages":10,"lookbackDays":120,"fullLookbackDays":120},{"name":"非依法招标","parser":"GX_E_BIDDING","listUrl":"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=02&page=1","maxPages":10,"lookbackDays":3,"fullLookbackDays":120},{"name":"非依法采购（采购信息）","parser":"GX_E_BIDDING","listUrl":"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=00&tabName=采购信息&page=1","maxPages":10,"lookbackDays":3,"fullLookbackDays":120}]'
WHERE source_code = 'GX';

SELECT source_code, JSON_LENGTH(entry_urls_json) cols FROM bidding_fetch_source WHERE source_code = 'GX';
