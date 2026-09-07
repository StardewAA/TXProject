package cn.iocoder.txgy.module.bidding.service.crawlexternal.fetch;
// [依据] 下载器执行器：QYZB 栏目一轮（列表 JSON → 全文直采 ingest）与 seen 去重、配置解析。

import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.ParserRegistry;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    }

    private ParserRegistry realRegistry() {
        ParserRegistry registry = new ParserRegistry(List.of(
                new cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.QyzJsonListParser(),
                new cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.GxEbidNoticeParser()));
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
        assertTrue(summary.getCategories().isEmpty());
        assertEquals(0, summary.getTotalIngested());
        verify(ingestService, never()).ingest(any());
    }

}
