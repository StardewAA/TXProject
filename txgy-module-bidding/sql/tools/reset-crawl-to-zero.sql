-- ============================================================================
-- 编码声明：本文件为 UTF-8（无 BOM）；执行必须带 --default-character-set=utf8mb4
-- 文件：sql/tools/reset-crawl-to-zero.sql
-- 用途：外部信息获取（爬虫）归零重置 —— 让三个下载器从 0 开始全量重爬，并把解析器切到 AI。
-- 规范依据：
--   * 《外部信息获取 v1.0》§五（MySQL 持久队列 = 唯一真相：bidding_fetch_task/job 为运行痕迹，可清）
--     §2.2 原文文件化（正文存 infra_file_content，不在本包清理范围，故公告可整表重灌）
--   * 用户口径 2026-09-10：「布隆过滤器导致无法全量爬，把状态都重置了从 0 开始；
--     三个下载器都改成 AI 的；ES 也要重置」
-- 业务目标：
--   ① 清掉「已抓过」的判定痕迹（DB 任务表 + Redis 布隆位图 + ES 索引），使全量窗口内的历史公告重新进入采集队列；
--   ② 把 QYZB/GX/EBIDDING 三源的「详情解析器」显式指向 AI（HTML_AI / PDF_AI），
--      公告落库即带 industryFlag/industryConfidence/aiMetaJson（防腐保温行业判定可查可复核）。
-- 为什么必须成组执行：
--   布隆位图（Redis）与 DB 任务表、ES 索引三者是同一份「已处理」记忆的三个副本；
--   只清其中一个会出现「DB 允许入队但布隆判定已见」或「ES 有旧文档但 DB 无记录」的不一致，
--   表现为「明明清空了却爬不动」或「检索命中打不开」。故本文与 reset-crawl-to-zero.sh 必须配套执行。
-- 风险与前置（务必先读）：
--   ① 本文件含 TRUNCATE，属破坏性操作，**不是幂等同步包**，只在归零场景人工执行一次；
--   ② 执行前必须先备份（见 Step 0）；外部公告原文已在 ES/infra_file_content 留有副本，
--      但任务/批次运行痕迹清空后不可恢复；
--   ③ 电子证照/资质（certificate）相关表与菜单一律不在本文件范围内（他人并行开发中）。
-- 执行：mysql -h127.0.0.1 -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < reset-crawl-to-zero.sql
-- ============================================================================

SET NAMES utf8mb4;

-- ========== Step 0：备份（先执行，勿跳过）==========
-- 在容器内执行以便保留 UTF-8（Windows 管道会导致中文乱码，见 docs 中「数据恢复」记录）：
--   docker exec txgy-mysql sh -c "mysqldump -uroot -p123456 --default-character-set=utf8mb4 \
--     --single-transaction --set-gtid-purged=OFF ruoyi-vue-pro \
--     bidding_notice_external bidding_notice_external_attachment bidding_notice_external_meta \
--     bidding_fetch_task bidding_fetch_job bidding_fetch_source > /tmp/reset-backup.sql"
--   docker cp txgy-mysql:/tmp/reset-backup.sql ./backups/$(date +%F)-pre-reset-backup.sql
-- 备份未成功前，不要执行 Step 2。

-- ========== Step 1：关爬虫（先停，再改配置；否则「停停改改」期间数据对不齐）==========
-- 关源启用开关：调度器 30s tick 只挑 enabled=1 的源，关掉即不再产生新任务；
-- 已在途任务由 Step 2 的整表清空回收（服务应处于停止状态时执行本文件）。
UPDATE bidding_fetch_source SET enabled = 0;

-- ========== Step 2：清运行痕迹（任务/批次/外部公告主档及子表）==========
-- 语义：bidding_fetch_task/job = 采集运行痕迹（规范 §五：可清，不影响业务数据）；
--       bidding_notice_external* = 采集产出主档，归零后由全量爬取重建。
TRUNCATE TABLE bidding_fetch_task;
TRUNCATE TABLE bidding_fetch_job;
TRUNCATE TABLE bidding_notice_external_attachment;
TRUNCATE TABLE bidding_notice_external_meta;
TRUNCATE TABLE bidding_notice_external;
-- 说明：bidding_notice（内部公告）/bidding_notice_content 为站内自建公告，不属爬虫产出，
--       默认不动；若确需连站内公告一起归零，另行人工确认后单独执行（当前为 0 行，多数场景无需处理）。

-- ========== Step 3：三源详情解析器切 AI + 启用 ==========
-- 配置模型（CrawlSourceRunner.CategoryConfig）：栏目的 parser=列表/栏目解析器（决定执行路由与去重语义），
-- detailParser=详情/落库解析器（本次改为 AI）。task.parser_code 仍写列表解析器，detailParser 随 payload 下发，
-- 二者解耦：既保留「GX 详情→PDF 直链」「QYZB JSON 直采」路由，又让正文结构化与行业判定走平台 AI。

-- QYZB（秦源电子招标平台，API 直采 JSON；正文在 res.rows.text，六栏目）→ detailParser = HTML_AI
UPDATE bidding_fetch_source
SET entry_urls_json = '[{"name":"招标公告","parser":"QYZB_JSON","detailParser":"HTML_AI","siteId":"725","maxPages":10,"pageSize":10,"categoryId":"203","lookbackDays":3,"fullLookbackDays":60},{"name":"非招标公告","parser":"QYZB_JSON","detailParser":"HTML_AI","siteId":"725","maxPages":10,"pageSize":10,"categoryId":"204","lookbackDays":3,"fullLookbackDays":60},{"name":"变更公告","parser":"QYZB_JSON","detailParser":"HTML_AI","siteId":"725","maxPages":10,"pageSize":10,"categoryId":"205","lookbackDays":3,"fullLookbackDays":60},{"name":"中标公示","parser":"QYZB_JSON","detailParser":"HTML_AI","siteId":"725","maxPages":10,"pageSize":10,"categoryId":"321","lookbackDays":1,"fullLookbackDays":60},{"name":"中标公告","parser":"QYZB_JSON","detailParser":"HTML_AI","siteId":"725","maxPages":10,"pageSize":10,"categoryId":"322","lookbackDays":1,"fullLookbackDays":60},{"name":"其他公告","parser":"QYZB_JSON","detailParser":"HTML_AI","siteId":"725","maxPages":10,"pageSize":10,"categoryId":"323","lookbackDays":1,"fullLookbackDays":60}]',
    enabled = 1,
    remark = '下载器 v2（2026-09-10）：列表=QYZB_JSON，详情=HTML_AI（平台 AI 结构化+防腐保温行业判定）；全量窗口 60 天'
WHERE source_code = 'QYZB';

-- GX（国信 e 采，HTML 列表 → 详情 PDF；三栏目）→ detailParser = PDF_AI
UPDATE bidding_fetch_source
SET entry_urls_json = '[{"name":"依法招标-招标公告","parser":"GX_E_BIDDING","detailParser":"PDF_AI","listUrl":"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=01&tabName=招标公告&page=1","maxPages":10,"lookbackDays":120,"fullLookbackDays":120},{"name":"非依法招标","parser":"GX_E_BIDDING","detailParser":"PDF_AI","listUrl":"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=02&page=1","maxPages":10,"lookbackDays":3,"fullLookbackDays":120},{"name":"非依法采购（采购信息）","parser":"GX_E_BIDDING","detailParser":"PDF_AI","listUrl":"https://gx.e-bidding.org/sxyczscms//category/iframe.html?dates=300&categoryId=2&tenderMethod=00&tabName=采购信息&page=1","maxPages":10,"lookbackDays":3,"fullLookbackDays":120}]',
    enabled = 1,
    remark = '下载器 v2（2026-09-10）：列表=GX_E_BIDDING，详情=PDF_AI（PDFBox 抽文本→平台 AI）；注意：详情 PDF 直链需站点登录态，未获 Cookie 前该源详情会失败（见运维文档）'
WHERE source_code = 'GX';

-- EBIDDING（秦源招标电子招标平台 https://www.ebidding.com.cn）：entry_urls_json 为空，无法开爬。
-- 现状与处置：该源尚无「栏目列表 URL」，且 2026-09-10 站点对详情/文件收紧鉴权（openFileById 未登录返回 0 字节）。
-- 处置：先补栏目配置再启用（示例模板如下，listUrl 需替换为真实栏目地址后执行）；
--       解析器口径与 QYZB 一致：列表=EBIDDING（站点规则），详情=HTML_AI（正式页面为 HTML 而非 PDF）。
-- UPDATE bidding_fetch_source
-- SET entry_urls_json = '[{"name":"招标公告","parser":"EBIDDING","detailParser":"HTML_AI","listUrl":"https://www.ebidding.com.cn/（真实栏目地址待补）","maxPages":10,"lookbackDays":3,"fullLookbackDays":60}]',
--     enabled = 1,
--     remark = '下载器 v2（2026-09-10）：列表=EBIDDING，详情=HTML_AI；栏目地址待业务确认'
-- WHERE source_code = 'EBIDDING';
-- 未配置栏目期间保持停用，避免调度器每 30s 触发一次空跑：
UPDATE bidding_fetch_source SET enabled = 0,
    remark = '下载器 v2（2026-09-10）：待补栏目 entry_urls_json 后再启用；详情解析器口径=HTML_AI'
WHERE source_code = 'EBIDDING';

-- ========== Step 4：校验（执行完贴回结果）==========
-- 4.1 三源配置与开关（enabled 应为 QYZB=1/GX=1/EBIDDING=0；entry_urls_json 内应含 detailParser）
SELECT source_code, source_name, enabled, parser_code_default, fetch_type,
       JSON_EXTRACT(entry_urls_json, '$[0].detailParser') AS col1_detail_parser,
       JSON_LENGTH(entry_urls_json) AS column_count
FROM bidding_fetch_source WHERE source_code IN ('QYZB', 'GX', 'EBIDDING');

-- 4.2 归零校验（四项均应为 0）
SELECT (SELECT COUNT(*) FROM bidding_fetch_task)             AS task_rows,
       (SELECT COUNT(*) FROM bidding_fetch_job)              AS job_rows,
       (SELECT COUNT(*) FROM bidding_notice_external)        AS external_rows,
       (SELECT COUNT(*) FROM bidding_notice_external_attachment) AS attachment_rows;

-- 4.3 解析器已注册清单（HTML_AI/PDF_AI 必须在列；应用启动后查 GET /bidding/crawl-parser/list 更直观）
SELECT '解析器清单以应用接口 GET /admin-api/bidding/crawl-parser/list 为准' AS note;
