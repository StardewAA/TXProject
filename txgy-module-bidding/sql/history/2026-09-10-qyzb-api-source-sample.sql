-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- ============================================================================
-- QYZB 升级为 API 源样板：栏目对象内嵌 apiConfig（runner 走 runApiCategory 新路径）
-- bodyTemplate 用 {page}/{size}/{siteId}/{categoryId} 占位（driver renderBody 替换）
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p123456 ruoyi-vue-pro < 本文件
-- ============================================================================
UPDATE bidding_fetch_source SET entry_urls_json = JSON_ARRAY(
    JSON_OBJECT('name','招标公告','parser','QYZB_JSON','siteId','725','categoryId','203',
        'lookbackDays',3,'fullLookbackDays',60,'maxPages',10,'pageSize',10,
        'apiConfig', JSON_OBJECT(
            'method','POST',
            'url','https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage',
            'bodyTemplate','{"pageNo":{page},"pageSize":{size},"dto":{"siteId":"{siteId}","categoryId":"{categoryId}"}}',
            'rowsPath','res.rows','totalPath','res.total',
            'mapping', JSON_OBJECT('title','title','url','url','date','publishDate','category','categoryName','contentHtml','text'),
            'urlPrefix','https://qyzb.shccmg.com/cms/default/webfile')),
    JSON_OBJECT('name','采购公告','parser','QYZB_JSON','siteId','725','categoryId','204',
        'lookbackDays',3,'fullLookbackDays',60,'maxPages',10,'pageSize',10,
        'apiConfig', JSON_OBJECT(
            'method','POST',
            'url','https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage',
            'bodyTemplate','{"pageNo":{page},"pageSize":{size},"dto":{"siteId":"{siteId}","categoryId":"{categoryId}"}}',
            'rowsPath','res.rows','totalPath','res.total',
            'mapping', JSON_OBJECT('title','title','url','url','date','publishDate','category','categoryName','contentHtml','text'),
            'urlPrefix','https://qyzb.shccmg.com/cms/default/webfile')),
    JSON_OBJECT('name','变更公告','parser','QYZB_JSON','siteId','725','categoryId','205',
        'lookbackDays',3,'fullLookbackDays',60,'maxPages',10,'pageSize',10,
        'apiConfig', JSON_OBJECT(
            'method','POST',
            'url','https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage',
            'bodyTemplate','{"pageNo":{page},"pageSize":{size},"dto":{"siteId":"{siteId}","categoryId":"{categoryId}"}}',
            'rowsPath','res.rows','totalPath','res.total',
            'mapping', JSON_OBJECT('title','title','url','url','date','publishDate','category','categoryName','contentHtml','text'),
            'urlPrefix','https://qyzb.shccmg.com/cms/default/webfile'))
) WHERE source_code = 'QYZB';
