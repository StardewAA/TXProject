-- ============================================================================
-- 下载器采集源配置（2026-09-10，机制经 2026-09-07 实测：接口/分页/categoryId 全确认）
-- 依据：docs/爬虫解析器插件化架构与测试接口.md §9~§11；频控：间隔 ≥120s（保存校验 1-060-106-046）。
-- 说明：entry_urls_json 为栏目配置数组（name/parser/categoryId 或 listUrl）；
--       QYZB 六栏目 categoryId：203=1ywgg 招标公告 /204=2ywgg 非招标公告 /205=3ywgg 变更公告
--                             /321=zbgs 中标公示 /322=zbgg 中标公告 /323=qtgg 其他公告
-- 冒烟建议：先只启用一个栏目（如仅 3ywgg）执行 POST /bidding/crawl-source/run 验证，再放开全部。
-- ============================================================================

-- 秦源（qyzb.shccmg.com）
INSERT INTO bidding_crawl_source
(id, source_code, source_name, base_url, allowed_hosts, entry_urls_json, channel_code, parser_code_default,
 fetch_interval_seconds, robots_ok, enabled, remark, creator, create_time, updater, update_time, deleted)
VALUES (690010001, 'QYZB', '秦源招标电子招标平台', 'https://qyzb.shccmg.com', NULL,
'[{"name":"变更公告","parser":"QYZB_JSON","categoryId":"205","siteId":"725"}]',
'GENERIC', 'QYZB_JSON', 180, b'1', b'1', '下载器 v1 冒烟：先单栏目（3ywgg/205）；放开全部见文件头注释', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE source_name = VALUES(source_name), base_url = VALUES(base_url),
 entry_urls_json = VALUES(entry_urls_json), parser_code_default = VALUES(parser_code_default),
 fetch_interval_seconds = VALUES(fetch_interval_seconds);

-- 全栏目模板（启用时替换 entry_urls_json）：
-- [{"name":"招标公告","parser":"QYZB_JSON","categoryId":"203","siteId":"725"},
--  {"name":"非招标公告","parser":"QYZB_JSON","categoryId":"204","siteId":"725"},
--  {"name":"变更公告","parser":"QYZB_JSON","categoryId":"205","siteId":"725"},
--  {"name":"中标公示","parser":"QYZB_JSON","categoryId":"321","siteId":"725"},
--  {"name":"中标公告","parser":"QYZB_JSON","categoryId":"322","siteId":"725"},
--  {"name":"其他公告","parser":"QYZB_JSON","categoryId":"323","siteId":"725"}]

-- 国信 e 采（山西）gx.e-bidding.org
INSERT INTO bidding_crawl_source
(id, source_code, source_name, base_url, allowed_hosts, entry_urls_json, channel_code, parser_code_default,
 fetch_interval_seconds, robots_ok, enabled, remark, creator, create_time, updater, update_time, deleted)
VALUES (690010002, 'GX', '国信e采（山西）', 'https://gx.e-bidding.org', NULL,
'[{"name":"依法招标-招标公告","parser":"GX_E_BIDDING","listUrl":"https://gx.e-bidding.org/sxyczscms/category/bulletinListNew.html?dates=300&categoryId=2&tenderMethod=01&tabName=招标公告&page=1"}]',
'GENERIC', 'GX_E_BIDDING', 300, b'1', b'0', '下载器 v1：详情=PDF（fileId 待 FileApi P2）；默认停用，冒烟时人工启用', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE source_name = VALUES(source_name), base_url = VALUES(base_url),
 entry_urls_json = VALUES(entry_urls_json), parser_code_default = VALUES(parser_code_default),
 fetch_interval_seconds = VALUES(fetch_interval_seconds);
