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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlNoticeProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExternalNoticeIngestService ingestService;
    private HttpFetchClient httpFetchClient;
    private CrawlNoticeProcessor processor;

    @BeforeEach
    void setUp() {
        ingestService = mock(ExternalNoticeIngestService.class);
        httpFetchClient = mock(HttpFetchClient.class);
        processor = new CrawlNoticeProcessor();
        ReflectionTestUtils.setField(processor, "externalNoticeIngestService", ingestService);
        ReflectionTestUtils.setField(processor, "httpFetchClient", httpFetchClient);
        when(ingestService.ingest(any())).thenReturn(888L);
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
        when(httpFetchClient.getText("http://file-svc/raw/abc.html"))
                .thenReturn("<p>从文件拉的正文</p><p>采购人：甲公司</p>");
        Long id = processor.execute(t);
        assertEquals(888L, id);
        ArgumentCaptor<ExternalNoticeIngestDTO> cap = ArgumentCaptor.forClass(ExternalNoticeIngestDTO.class);
        verify(ingestService).ingest(cap.capture());
        assertTrue(cap.getValue().getContentHtml().contains("从文件拉的正文"));
        assertEquals("甲公司", cap.getValue().getIssuerName());
    }

    @Test
    void execute_noMaterial_throws() throws Exception {
        CrawlTaskDO t = task(Map.of("title", "无正文公告", "url", "https://a.com/y.html"));
        assertThrows(IllegalStateException.class, () -> processor.execute(t));
    }
}