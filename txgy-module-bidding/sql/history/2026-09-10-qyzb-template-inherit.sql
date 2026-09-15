-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 2026-09-10 QYZB 配置模型升级：源级 api_config_json=接口模板（一份），栏目只写差异（继承生效见 CrawlSourceRunner）
UPDATE bidding_fetch_source
SET api_config_json = '{"url":"https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage","method":"POST","mapping":{"url":"url","date":"publishDate","title":"title","category":"categoryName","contentHtml":"text"},"rowsPath":"res.rows","totalPath":"res.total","urlPrefix":"https://qyzb.shccmg.com/cms/default/webfile","bodyTemplate":"{\\"pageNo\\":{page},\\"pageSize\\":{size},\\"dto\\":{\\"siteId\\":\\"{siteId}\\",\\"categoryId\\":\\"{categoryId}\\"}}"}',
    entry_urls_json = '[{"name":"招标公告","parser":"QYZB_JSON","siteId":"725","categoryId":"203","maxPages":10,"pageSize":10,"lookbackDays":3,"fullLookbackDays":60},{"name":"非招标公告","parser":"QYZB_JSON","siteId":"725","categoryId":"204","maxPages":10,"pageSize":10,"lookbackDays":3,"fullLookbackDays":60},{"name":"变更公告","parser":"QYZB_JSON","siteId":"725","categoryId":"205","maxPages":10,"pageSize":10,"lookbackDays":3,"fullLookbackDays":60},{"name":"中标公示","parser":"QYZB_JSON","siteId":"725","categoryId":"321","maxPages":10,"pageSize":10,"lookbackDays":1,"fullLookbackDays":60},{"name":"中标公告","parser":"QYZB_JSON","siteId":"725","categoryId":"322","maxPages":10,"pageSize":10,"lookbackDays":1,"fullLookbackDays":60},{"name":"其他公告","parser":"QYZB_JSON","siteId":"725","categoryId":"323","maxPages":10,"pageSize":10,"lookbackDays":1,"fullLookbackDays":60}]'
WHERE source_code = 'QYZB';

SELECT source_code, JSON_LENGTH(entry_urls_json) cols, JSON_LENGTH(api_config_json) tpl_len FROM bidding_fetch_source WHERE source_code = 'QYZB';
