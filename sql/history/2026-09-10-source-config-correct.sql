-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 2026-09-10 采集源配置正确化（实测校准）
-- 1) EBIDDING.base_url 错填 qyzb.shccmg.com URL（真站=秦源招标电子招标平台 www.ebidding.com.cn）——影响 /go 与下载白名单语义
UPDATE bidding_fetch_source SET base_url = 'https://www.ebidding.com.cn'
WHERE source_code = 'EBIDDING';

-- 2) QYZB entryUrlsJson 第 2 栏目名"采购公告"错（站/API 实测 204=非招标公告；站点无"采购公告"栏目）
UPDATE bidding_fetch_source
SET entry_urls_json = REPLACE(entry_urls_json, '"name": "采购公告"', '"name": "非招标公告"')
WHERE source_code = 'QYZB';

-- 校验
SELECT source_code, base_url, allowed_hosts FROM bidding_fetch_source;
SELECT entry_urls_json LIKE '%非招标公告%' AS renamed FROM bidding_fetch_source WHERE source_code = 'QYZB';
