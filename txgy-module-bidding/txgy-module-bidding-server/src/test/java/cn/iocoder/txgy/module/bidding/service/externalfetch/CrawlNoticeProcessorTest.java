package cn.iocoder.txgy.module.bidding.service.externalfetch;

// [依据] 外部信息获取 v1.0 六：消费端执行器（payload 直采 / rawFileUrl 源文件拉取 / 缺素材报错走重试）。

import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.HttpFetchClient;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestDTO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.iocoder.txgy.module.bidding.service.externalfetch.audit.CrawlDownloadAuditor;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.RobotsGuard;
import cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadGate;
import cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadNotDueException;
import cn.iocoder.txgy.module.bidding.service.externalfetch.validate.DownloadIntegrityValidator;
import java.nio.charset.StandardCharsets;

class CrawlNoticeProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExternalNoticeIngestService ingestService;
    private HttpFetchClient httpFetchClient;
    private cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper crawlSourceMapper;
    // 2026-09-11 补齐：合规链路新增的依赖（robots 预检 / 下载闸位 / 完整性校验 / 审计）必须注入，
    //   否则单测里是 null → NPE（此前 CrawlNoticeProcessorTest 2F+1E 的根因）
    private RobotsGuard robotsGuard;
    private CrawlDownloadGate crawlDownloadGate;
    private DownloadIntegrityValidator integrityValidator;
    private CrawlDownloadAuditor downloadAuditor;
    private CrawlNoticeProcessor processor;

    @BeforeEach
    void setUp() {
        ingestService = mock(ExternalNoticeIngestService.class);
        httpFetchClient = mock(HttpFetchClient.class);
        crawlSourceMapper = mock(cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper.class);
        when(crawlSourceMapper.selectOne(any())).thenReturn(null); // 无源上下文=放行（SSRF 校验仅在源配置存在时强约束）
        // 默认：robots 允许抓取、闸位随时可得（"被禁止"与"未到间隔"另有专门用例覆盖）
        robotsGuard = mock(RobotsGuard.class);
        when(robotsGuard.isAllowed(any())).thenReturn(true);
        crawlDownloadGate = mock(CrawlDownloadGate.class);
        when(crawlDownloadGate.tryAcquire(any(), any(), any())).thenReturn(true);
        integrityValidator = mock(DownloadIntegrityValidator.class);
        DownloadIntegrityValidator.Result pass = mock(DownloadIntegrityValidator.Result.class);
        when(pass.pass()).thenReturn(true);
        when(pass.getVerdict()).thenReturn(DownloadIntegrityValidator.Verdict.PASS);
        when(pass.getSha256()).thenReturn("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        when(pass.getDetail()).thenReturn("OK");
        when(integrityValidator.validateText(any(), any())).thenReturn(pass);
        downloadAuditor = mock(CrawlDownloadAuditor.class);
        processor = new CrawlNoticeProcessor();
        ReflectionTestUtils.setField(processor, "externalNoticeIngestService", ingestService);
        ReflectionTestUtils.setField(processor, "httpFetchClient", httpFetchClient);
        ReflectionTestUtils.setField(processor, "crawlSourceMapper", crawlSourceMapper);
        ReflectionTestUtils.setField(processor, "robotsGuard", robotsGuard);
        ReflectionTestUtils.setField(processor, "crawlDownloadGate", crawlDownloadGate);
        ReflectionTestUtils.setField(processor, "integrityValidator", integrityValidator);
        ReflectionTestUtils.setField(processor, "downloadAuditor", downloadAuditor);
        when(ingestService.ingest(any())).thenReturn(888L);
    }

    private cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO sourceRow(String sourceCode, String baseUrl, String allowed) {
        cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO s =
                new cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO();
        s.setSourceCode(sourceCode);
        s.setBaseUrl(baseUrl);
        s.setAllowedHosts(allowed);
        return s;
    }

    private CrawlTaskDO task(Map<String, Object> payload) throws Exception {
        CrawlTaskDO task = new CrawlTaskDO();
        task.setId(7L);
        task.setSourceCode("QYZB");
        task.setParserCode("QYZB_JSON");
        task.setPayloadJson(MAPPER.writeValueAsString(payload));
        return task;
    }

    @Test
    void execute_payloadFullText_ingestsDirectly() throws Exception {
        CrawlTaskDO t = task(Map.of("title", "某项目招标公告", "url", "https://a.com/x.html",
                "date", "2026-09-01", "category", "招标公告", "contentHtml", "<p>正文</p><p>招标人：某公司</p>"));
        Long id = processor.execute(t);
        assertEquals(888L, id);
        ArgumentCaptor<ExternalNoticeIngestDTO> cap = ArgumentCaptor.forClass(ExternalNoticeIngestDTO.class);
        verify(ingestService).ingest(cap.capture());
        assertEquals("https://a.com/x.html", cap.getValue().getOrigUrl());
        assertEquals("某项目招标公告", cap.getValue().getTitle());
        assertEquals("HTML", cap.getValue().getContentType());
        assertTrue(cap.getValue().getContentHtml().contains("正文"));
        assertEquals("某公司", cap.getValue().getIssuerName());
    }

    @Test
    void execute_rawFileUrl_fetchesThenIngests() throws Exception {
        CrawlTaskDO t = task(Map.of("title", "某公告", "url", "https://a.com/x.html",
                "rawFileUrl", "http://file-svc/raw/abc.html"));
        // 生产 API 为 fetchText(url) → FetchOutcome（携带状态码/耗时/字节数供监控与完整性校验）；
        //   FetchOutcome.of 是本次新增的公开构造入口（原构造器为包内可见，跨包单测无法构造）
        when(httpFetchClient.fetchText("http://file-svc/raw/abc.html"))
                .thenReturn(HttpFetchClient.FetchOutcome.of("http://file-svc/raw/abc.html", 200, 12L, null,
                        "<p>从文件拉的正文</p><p>采购人：甲公司</p>".getBytes(StandardCharsets.UTF_8)));
        Long id = processor.execute(t);
        assertEquals(888L, id);
        ArgumentCaptor<ExternalNoticeIngestDTO> cap = ArgumentCaptor.forClass(ExternalNoticeIngestDTO.class);
        verify(ingestService).ingest(cap.capture());
        assertTrue(cap.getValue().getContentHtml().contains("从文件拉的正文"));
        assertEquals("甲公司", cap.getValue().getIssuerName());
    }

    @Test
    void execute_rawFileForeignHostRejected_whenSourceExists() throws Exception {
        // SSRF 防护：源配置存在时，非平台文件端点的 rawFileUrl 须在源白名单内
        when(crawlSourceMapper.selectOne(any())).thenReturn(sourceRow("QYZB", "https://qyzb.shccmg.com", null));
        CrawlTaskDO t = task(Map.of("title", "某公告", "url", "https://qyzb.shccmg.com/x.html",
                "rawFileUrl", "http://evil.example.com/raw/abc.html"));
        assertThrows(IllegalStateException.class, () -> processor.execute(t));
    }

    @Test
    void execute_robotsDisallowed_skipsWithoutConsumingGate() throws Exception {
        // 2026-09-11 合规语义回归：被 robots.txt 禁止 = 规则性跳过（SKIPPED），**不是失败**；
        //   且判定必须发生在占闸之前——绝不消耗宝贵的 2 分钟下载额度（原实现两者都错了）。
        when(robotsGuard.isAllowed(any())).thenReturn(false);
        CrawlTaskDO t = task(Map.of("title", "被禁公告", "url", "https://a.com/forbidden.html"));
        assertThrows(CrawlSkipException.class, () -> processor.execute(t));
        verify(crawlDownloadGate, never()).tryAcquire(any(), any(), any());
    }

    @Test
    void execute_gateNotDue_throwsNotDueWithoutDownloading() throws Exception {
        // 未到合规下载间隔：抛 NotDue（上层回 PENDING 排队、不计失败次数），且绝不发起任何下载
        when(crawlDownloadGate.tryAcquire(any(), any(), any())).thenReturn(false);
        CrawlTaskDO t = task(Map.of("title", "排队公告", "url", "https://a.com/x.html"));
        assertThrows(CrawlDownloadNotDueException.class, () -> processor.execute(t));
        verify(httpFetchClient, never()).fetchText(any());
    }

    @Test
    void execute_noMaterial_throws() throws Exception {
        CrawlTaskDO t = task(Map.of("title", "无正文公告", "url", "https://a.com/y.html"));
        assertThrows(IllegalStateException.class, () -> processor.execute(t));
    }
}