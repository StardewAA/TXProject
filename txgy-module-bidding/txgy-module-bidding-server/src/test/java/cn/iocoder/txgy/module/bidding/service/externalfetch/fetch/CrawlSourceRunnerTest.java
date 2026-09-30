package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;
// [依据] 下载器执行器：QYZB 栏目一轮（列表 JSON → 全文直采 ingest）与 seen 去重、配置解析。

import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParserRegistry;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestDTO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import java.time.LocalDate;

class CrawlSourceRunnerTest {

    private static final String ONE_ROW = """
            {"code":0,"res":{"total":1,"pageNo":1,"hasNext":false,"rows":[
            {"title":"某项目变更公告","url":"/3ywgg/20260827/1277640981956526080.html",
            "categoryName":"变更公告","publishDate":"2026-08-27T14:49:07.000+0800",
            "text":"<p>某公司项目变更公告正文</p><p>采购人：陕西某矿业有限公司</p>"}]}}
            """;

    private static final String DUP_ROWS = """
            {"code":0,"res":{"total":2,"pageNo":1,"hasNext":false,"rows":[
            {"title":"某项目变更公告A","url":"/3ywgg/20260827/1277640981956526080.html",
            "categoryName":"变更公告","publishDate":"2026-08-27T14:49:07.000+0800",
            "text":"<p>正文A</p><p>采购人：陕西某矿业有限公司</p>"},
            {"title":"某项目变更公告B","url":"/3ywgg/20260827/1277640981956526080.html",
            "categoryName":"变更公告","publishDate":"2026-08-27T14:49:07.000+0800",
            "text":"<p>正文B</p><p>采购人：陕西某矿业有限公司</p>"}]}}
            """;

    private HttpFetchClient httpFetchClient;
    private ExternalNoticeIngestService ingestService;
    private CrawlSourceMapper sourceMapper;
    private CrawlSourceRunner runner;
    private ApiFetchDriver apiFetchDriver;

    @BeforeEach
    void setUp() {
        httpFetchClient = mock(HttpFetchClient.class);
        ingestService = mock(ExternalNoticeIngestService.class);
        sourceMapper = mock(CrawlSourceMapper.class);
        runner = new CrawlSourceRunner();
        ReflectionTestUtils.setField(runner, "httpFetchClient", httpFetchClient);
        ReflectionTestUtils.setField(runner, "parserRegistry", realRegistry());
        ReflectionTestUtils.setField(runner, "externalNoticeIngestService", ingestService);
        ReflectionTestUtils.setField(runner, "crawlSourceMapper", sourceMapper);
        ReflectionTestUtils.setField(runner, "crawlTaskMapper", mock(CrawlTaskMapper.class));
        apiFetchDriver = new ApiFetchDriver();
        ReflectionTestUtils.setField(apiFetchDriver, "httpFetchClient", httpFetchClient);
        ReflectionTestUtils.setField(runner, "apiFetchDriver", apiFetchDriver);
    }

    private ParserRegistry realRegistry() {
        ParserRegistry registry = new ParserRegistry(List.of(
                new cn.iocoder.txgy.module.bidding.service.externalfetch.parser.QyzJsonListParser(),
                new cn.iocoder.txgy.module.bidding.service.externalfetch.parser.GxEbidNoticeParser()));
        registry.init();
        return registry;
    }

    private CrawlSourceDO source() {
        CrawlSourceDO source = new CrawlSourceDO();
        source.setSourceCode("QYZB-DEMO");
        source.setEnabled(true);
        source.setChannelCode("GENERIC");
        source.setParserCodeDefault("QYZB_JSON");
        source.setFetchIntervalSeconds(180);
        source.setEntryUrlsJson("""
                [{"name":"变更公告","parser":"QYZB_JSON","categoryId":"205","siteId":"725"}]
                """);
        return source;
    }

    @Test
    void qyzCategory_ingestsFullTextRow() throws Exception {
        when(sourceMapper.selectOne(any())).thenReturn(source());
        when(httpFetchClient.postJson(any(String.class), any(String.class))).thenReturn(ONE_ROW);
        when(ingestService.ingest(any())).thenReturn(100L);

        CrawlSourceRunner.RunSummary summary = runner.runSource("QYZB-DEMO");

        assertEquals(1, summary.getCategories().size());
        assertEquals(1, summary.getTotalIngested());
        ArgumentCaptor<ExternalNoticeIngestDTO> captor = ArgumentCaptor.forClass(ExternalNoticeIngestDTO.class);
        verify(ingestService).ingest(captor.capture());
        ExternalNoticeIngestDTO dto = captor.getValue();
        assertEquals("https://qyzb.shccmg.com/cms/default/webfile/3ywgg/20260827/1277640981956526080.html", dto.getOrigUrl());
        assertEquals("某项目变更公告", dto.getTitle());
        assertEquals("HTML", dto.getContentType());
        assertTrue(dto.getContentHtml().contains("变更公告正文"));
        assertEquals("陕西某矿业有限公司", dto.getIssuerName());
        assertEquals("2026-08-27", dto.getPublishTime().toLocalDate().toString());
    }

    @Test
    void duplicateUrlInSameRun_ingestedOnce() throws Exception {
        when(sourceMapper.selectOne(any())).thenReturn(source());
        when(httpFetchClient.postJson(any(String.class), any(String.class))).thenReturn(DUP_ROWS);
        when(ingestService.ingest(any())).thenReturn(100L);

        CrawlSourceRunner.RunSummary summary = runner.runSource("QYZB-DEMO");

        assertEquals(1, summary.getTotalIngested()); // 同 URL 两行 → seen 去重仅 1 次
        verify(ingestService, times(1)).ingest(any());
    }

    @Test
    void emptyConfigs_noop() throws Exception {
        CrawlSourceDO source = source();
        source.setEntryUrlsJson("[]");
        when(sourceMapper.selectOne(any())).thenReturn(source);
        CrawlSourceRunner.RunSummary summary = runner.runSource("QYZB-DEMO");
        // 审查 A9：空配置不再静默 SUCCESS——汇总带 __empty__ 错误类别 + failed=1（前端可提示）
        assertEquals(1, summary.getCategories().size());
        assertEquals("__empty__", summary.getCategories().get(0).getName());
        assertEquals(1, summary.getTotalFailed());
        verify(ingestService, never()).ingest(any());
    }

    @Test
    void parseConfigs_readsDetailParser() {
        // 2026-09-24 修复回归：parseConfigs 此前漏读 entry_urls_json 的 detailParser 字段，
        // 导致 QYZB(HTML_AI)/GX(PDF_AI) 的 AI 解析器在爬虫链路从未生效（effectiveDetailParser 永远回退列表解析器）。
        CrawlSourceDO source = source();
        source.setEntryUrlsJson("""
                [{"name":"招标公告","parser":"QYZB_JSON","categoryId":"203","siteId":"725","detailParser":"HTML_AI"},
                 {"name":"无AI栏目","parser":"GX_E_BIDDING","listUrl":"https://gx.example/list","detailParser":""}]
                """);
        @SuppressWarnings("unchecked")
        java.util.List<CrawlSourceRunner.CategoryConfig> configs =
                (java.util.List<CrawlSourceRunner.CategoryConfig>) ReflectionTestUtils.invokeMethod(
                        runner, "parseConfigs", source.getEntryUrlsJson());
        assertEquals(2, configs.size());
        assertEquals("HTML_AI", configs.get(0).getDetailParser(), "配置有 detailParser 时应读出");
        assertEquals("QYZB_JSON", configs.get(0).getParser(), "列表解析器保持 QYZB_JSON（执行路由不变）");
        assertEquals(null, configs.get(1).getDetailParser(), "空白 detailParser 应归一为 null（回退列表解析器）");
        // effectiveDetailParser 兜底链路：有 detailParser 用之，否则回退 parser
        assertEquals("HTML_AI", CrawlSourceRunnerTest.effective(configs.get(0)));
        assertEquals("GX_E_BIDDING", CrawlSourceRunnerTest.effective(configs.get(1)));
    }

    /** 镜像 CrawlSourceRunner.effectiveDetailParser 的兜底口径（static private，测试侧等价复刻） */
    private static String effective(CrawlSourceRunner.CategoryConfig config) {
        return config.getDetailParser() != null && !config.getDetailParser().isBlank()
                ? config.getDetailParser() : config.getParser();
    }

    // ===== 增量回溯（文档 §11）：时间窗口翻页、窗口越界停页、页上限、未启用保持单页 =====

    @Test
    void qyz_lookback_pagesUntilWindowEdgeAndSkipsOlderRows() throws Exception {
        CrawlSourceDO source = source();
        source.setEntryUrlsJson("""
                [{"name":"变更公告","parser":"QYZB_JSON","categoryId":"205","siteId":"725","lookbackDays":1}]
                """);
        when(sourceMapper.selectOne(any())).thenReturn(source);
        LocalDate today = LocalDate.now();
        String page1 = qyzPage(true,
                qyzRow("新公告A", "/3ywgg/20260827/a.html", today + "T09:00:00.000+0800"),
                qyzRow("新公告B", "/3ywgg/20260827/b.html", today + "T08:00:00.000+0800"));
        String page2 = qyzPage(false,
                qyzRow("三天前公告", "/3ywgg/20260827/c.html", today.minusDays(3) + "T09:00:00.000+0800"));
        when(httpFetchClient.postJson(any(String.class), any(String.class)))
                .thenReturn(page1, page2);
        when(ingestService.ingest(any())).thenReturn(100L);

        CrawlSourceRunner.RunSummary summary = runner.runSource("QYZB-DEMO");

        // 窗口内 2 条全采；第 2 页出现早于窗口(1 天)的条目 → 不采集且停页
        assertEquals(2, summary.getTotalIngested());
        assertEquals(2, summary.getCategories().get(0).getFetched());
        verify(ingestService, times(2)).ingest(any());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpFetchClient, times(2)).postJson(any(String.class), body.capture());
        assertTrue(body.getAllValues().get(1).contains("\"pageNo\":2"), "第 2 次请求应翻到第 2 页");
    }

    @Test
    void qyz_lookback_maxPagesCapsPaging() throws Exception {
        CrawlSourceDO source = source();
        source.setEntryUrlsJson("""
                [{"name":"变更公告","parser":"QYZB_JSON","categoryId":"205","siteId":"725","lookbackDays":30,"maxPages":2}]
                """);
        when(sourceMapper.selectOne(any())).thenReturn(source);
        LocalDate today = LocalDate.now();
        String page1 = qyzPage(true, qyzRow("A", "/x/a.html", today + "T09:00:00.000+0800"));
        String page2 = qyzPage(true, qyzRow("B", "/x/b.html", today + "T08:00:00.000+0800"));
        String page3 = qyzPage(false, qyzRow("C", "/x/c.html", today + "T07:00:00.000+0800"));
        when(httpFetchClient.postJson(any(String.class), any(String.class)))
                .thenReturn(page1, page2, page3);
        when(ingestService.ingest(any())).thenReturn(100L);

        CrawlSourceRunner.RunSummary summary = runner.runSource("QYZB-DEMO");

        // maxPages=2 → 只翻两页即停（第 3 页不应请求）
        assertEquals(2, summary.getTotalIngested());
        verify(httpFetchClient, times(2)).postJson(any(String.class), any(String.class));
    }

    @Test
    void qyz_withoutLookback_keepsSinglePageBehavior() throws Exception {
        // 未配置 lookbackDays/maxPages → 保持 v1：即便 hasNext=true 也只取第一页
        CrawlSourceDO source = source();
        when(sourceMapper.selectOne(any())).thenReturn(source);
        LocalDate today = LocalDate.now();
        String page1 = qyzPage(true, qyzRow("A", "/x/a.html", today + "T09:00:00.000+0800"));
        String page2 = qyzPage(true, qyzRow("B", "/x/b.html", today + "T08:00:00.000+0800"));
        when(httpFetchClient.postJson(any(String.class), any(String.class)))
                .thenReturn(page1, page2);
        when(ingestService.ingest(any())).thenReturn(100L);

        CrawlSourceRunner.RunSummary summary = runner.runSource("QYZB-DEMO");

        assertEquals(1, summary.getTotalIngested());
        verify(httpFetchClient, times(1)).postJson(any(String.class), any(String.class));
    }

    @Test
    void recordTask_attachesRunJobId_whenEntryFails() throws Exception {
        // 批次化：runner.runSource(code, jobId) 失败任务落库应挂上 jobId（前端任务页可按批次检索）
        CrawlSourceDO source = source();
        when(sourceMapper.selectOne(any())).thenReturn(source);
        when(httpFetchClient.postJson(any(String.class), any(String.class))).thenReturn(ONE_ROW);
        when(ingestService.ingest(any())).thenThrow(new IllegalStateException("boom"));
        CrawlTaskMapper taskMapper = mock(CrawlTaskMapper.class);
        ReflectionTestUtils.setField(runner, "crawlTaskMapper", taskMapper);

        CrawlSourceRunner.RunSummary summary = runner.runSource("QYZB-DEMO", 9001L);

        assertEquals(1, summary.getTotalFailed());
        ArgumentCaptor<CrawlTaskDO> captor = ArgumentCaptor.forClass(CrawlTaskDO.class);
        verify(taskMapper, times(1)).insert(captor.capture());
        assertEquals(Long.valueOf(9001L), captor.getValue().getJobId());
    }


    @Test
    void apiCategory_entryWithApiConfig_runsDriverAndIngests() throws Exception {
        // API 源栏目（apiConfig 声明）：走 ApiFetchDriver（postJsonWithHeaders）→ 全文直采 ingest；不触碰 QYZB parser/postJson
        CrawlSourceDO source = source();
        source.setEntryUrlsJson("""
                [{"name":"变更公告","parser":"QYZB_JSON","categoryId":"205","siteId":"725",
                "apiConfig":{"method":"POST","url":"https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage",
                "rowsPath":"res.rows",
                "mapping":{"title":"title","url":"url","date":"publishDate","category":"categoryName","contentHtml":"text"},
                "urlPrefix":"https://qyzb.shccmg.com/cms/default/webfile"}}]
                """);
        when(sourceMapper.selectOne(any())).thenReturn(source);
        when(httpFetchClient.postJsonWithHeaders(any(String.class), any(String.class), anyMap())).thenReturn(ONE_ROW);
        when(ingestService.ingest(any())).thenReturn(100L);

        CrawlSourceRunner.RunSummary summary = runner.runSource("QYZB-DEMO");

        assertEquals(1, summary.getTotalIngested());
        verify(httpFetchClient, never()).postJson(any(String.class), any(String.class)); // 未走旧 QYZB 常量路径
        ArgumentCaptor<ExternalNoticeIngestDTO> captor = ArgumentCaptor.forClass(ExternalNoticeIngestDTO.class);
        verify(ingestService).ingest(captor.capture());
        assertEquals("https://qyzb.shccmg.com/cms/default/webfile/3ywgg/20260827/1277640981956526080.html", captor.getValue().getOrigUrl());
    }

    @Test
    void apiCategory_rowWithoutContentHtml_marksFailed() throws Exception {
        CrawlSourceDO source = source();
        source.setEntryUrlsJson("""
                [{"name":"字段列表无全文","parser":"QYZB_JSON",
                "apiConfig":{"method":"POST","url":"https://api.example.com/list",
                "rowsPath":"rows","mapping":{"title":"title","url":"url","date":"date"}}}]
                """);
        when(sourceMapper.selectOne(any())).thenReturn(source);
        when(httpFetchClient.postJsonWithHeaders(any(String.class), any(String.class), anyMap())).thenReturn("""
                {"rows":[{"title":"某公告","url":"https://api.example.com/x.html","date":"${today}"}]}
                """.replace("${today}", java.time.LocalDate.now().toString()));
        when(ingestService.ingest(any())).thenReturn(100L);

        CrawlSourceRunner.RunSummary summary = runner.runSource("QYZB-DEMO");

        // 审查 B7：无全文映射=配置缺陷 → SKIPPED（不记 FAILED 不污染失败率/不触发调度退避）
        assertEquals(0, summary.getTotalFailed());
        assertEquals(0, summary.getTotalIngested());
        verify(ingestService, never()).ingest(any());
    }

    private static String qyzPage(boolean hasNext, String... rows) {
        StringBuilder sb = new StringBuilder("{\"code\":0,\"res\":{\"total\":").append(rows.length)
                .append(",\"pageNo\":1,\"hasNext\":").append(hasNext).append(",\"rows\":[");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(rows[i]);
        }
        return sb.append("]}}").toString();
    }

    private static String qyzRow(String title, String url, String date) {
        return "{\"title\":\"" + title + "\",\"url\":\"" + url + "\",\"categoryName\":\"变更公告\","
                + "\"publishDate\":\"" + date + "\",\"text\":\"<p>正文" + title + "</p>\"}";
    }

}
