package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;

// [依据] 外部信息获取 v1.0 §四：API 源样板（配置驱动列表直取：POST/GET + 分页 + rowsPath/字段映射 + URL 绝对化）。

import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.NoticeParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiFetchDriverTest {

    private HttpFetchClient httpFetchClient;
    private ApiFetchDriver driver;

    @BeforeEach
    void setUp() {
        httpFetchClient = mock(HttpFetchClient.class);
        driver = new ApiFetchDriver();
        ReflectionTestUtils.setField(driver, "httpFetchClient", httpFetchClient);
    }

    private static final String RESP = """
            {"code":0,"res":{"total":2,"rows":[
            {"title":"某项目招标公告","url":"/3ywgg/20260901/111.html","categoryName":"招标公告",
            "publishDate":"2026-09-01T10:00:00","text":"<p>招标正文</p>"},
            {"title":"某项目变更公告","url":"/3ywgg/20260902/222.html","categoryName":"变更公告",
            "publishDate":"2026-09-02T10:00:00","text":"<p>变更正文</p>"}]}}
            """;

    private String qyzConfig() {
        return """
                {"method":"POST","url":"https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage",
                "rowsPath":"res.rows","totalPath":"res.total",
                "mapping":{"title":"title","url":"url","date":"publishDate","category":"categoryName","contentHtml":"text"},
                "urlPrefix":"https://qyzb.shccmg.com/cms/default/webfile"}
                """;
    }

    @Test
    void postConfig_fetchesAndMapsEntries() throws Exception {
        ApiFetchConfig config = ApiFetchConfig.parse(qyzConfig());
        assertNotNull(config);
        when(httpFetchClient.postJsonWithHeaders(any(String.class), any(String.class), anyMap())).thenReturn(RESP);

        List<NoticeParseResult.ListEntry> entries = driver.fetchPage(config, 1);

        assertEquals(2, entries.size());
        NoticeParseResult.ListEntry first = entries.get(0);
        assertEquals("某项目招标公告", first.getTitle());
        assertEquals("https://qyzb.shccmg.com/cms/default/webfile/3ywgg/20260901/111.html", first.getUrl());
        assertEquals("招标公告", first.getCategory());
        assertEquals("2026-09-01T10:00:00", first.getDate());
        assertTrue(first.getContentHtml().contains("招标正文"));
        // 验证 POST body 渲染：page 占位替换
        verify(httpFetchClient).postJsonWithHeaders(org.mockito.ArgumentMatchers.eq(
                "https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage"),
                org.mockito.ArgumentMatchers.contains("\"pageNo\":1"), anyMap());
    }

    @Test
    void getConfig_appendsPageQueryAndAbsoluteUrl() throws Exception {
        String configJson = """
                {"method":"GET","url":"https://data.example.com/gateway/api/1/kfsj",
                "headers":{"X-Client-Id":"tok-1"},
                "pageParam":"page","sizeParam":"size","pageSize":20,"rowsPath":"data.list",
                "mapping":{"title":"notice_name","url":"url","date":"publish_time"}}
                """;
        ApiFetchConfig config = ApiFetchConfig.parse(configJson);
        when(httpFetchClient.getTextWithHeaders(any(String.class), anyMap())).thenReturn("""
                {"code":0,"data":{"list":[{"notice_name":"某某公告","url":"https://a.com/x.html","publish_time":"2026-09-01"}]}}
                """);

        List<NoticeParseResult.ListEntry> entries = driver.fetchPage(config, 3);

        assertEquals(1, entries.size());
        assertEquals("某某公告", entries.get(0).getTitle());
        assertEquals("https://a.com/x.html", entries.get(0).getUrl());
        assertNull(entries.get(0).getContentHtml()); // 无全文映射 → null
        verify(httpFetchClient).getTextWithHeaders(
                org.mockito.ArgumentMatchers.contains("page=3&size=20"), anyMap());
        verify(httpFetchClient).getTextWithHeaders(any(String.class),
                org.mockito.ArgumentMatchers.eq(Map.of("X-Client-Id", "tok-1")));
    }

    @Test
    void parse_invalidJson_returnsNull() {
        assertNull(ApiFetchConfig.parse(null));
        assertNull(ApiFetchConfig.parse("not-json"));
        assertNull(ApiFetchConfig.parse(""));
    }

    @Test
    void rowWithoutUrl_skipped() throws Exception {
        ApiFetchConfig config = ApiFetchConfig.parse(qyzConfig());
        when(httpFetchClient.postJsonWithHeaders(any(String.class), any(String.class), anyMap())).thenReturn("""
                {"code":0,"res":{"rows":[{"title":"无URL行","categoryName":"招标公告","publishDate":"2026-09-01","text":"x"}]}}
                """);
        List<NoticeParseResult.ListEntry> entries = driver.fetchPage(config, 1);
        assertEquals(0, entries.size()); // 无 url 行不产出条目
    }

    @Test
    void apispaceOfficialApi_configDrivenFetch() throws Exception {
        // 真实官方 API 形态（APISpace 项目检索，2026-09-09 实测响应）：POST+Token头+bodyTemplate分页+data.data路径
        // 仅 mock HTTP 验证 driver 组装（不真发请求，保护免费额度）
        String configJson = """
                {"method":"POST","url":"https://23330.o.apispace.com/project-info-upgrade/project-list",
                "headers":{"X-APISpace-Token":"tok-demo","Content-Type":"application/json"},
                "bodyTemplate":"{\\"pageID\\":{page},\\"pageNumber\\":{size}}",
                "pageSize":20,"rowsPath":"data.data",
                "mapping":{"title":"title","date":"publishTime"}}
                """;
        ApiFetchConfig config = ApiFetchConfig.parse(configJson);
        assertNotNull(config);
        String resp = """
                {"data":{"total":22812,"pageId":1,"hasNext":true,"data":[
                {"id":1,"title":"济南某项目公开招标招标公告","publishTime":"2026-09-08 23:58:19","projectMoney":"55万","url":"https://src.example.gov.cn/t/1"},
                {"id":2,"title":"安多县某改造项目招标公告","publishTime":"2026-09-08 22:10:00","url":"https://src.example.gov.cn/t/2"}]}}
                """;
        when(httpFetchClient.postJsonWithHeaders(any(String.class), any(String.class), anyMap())).thenReturn(resp);

        List<NoticeParseResult.ListEntry> entries = driver.fetchPage(config, 1);

        assertEquals(2, entries.size());
        assertEquals("济南某项目公开招标招标公告", entries.get(0).getTitle());
        assertEquals("2026-09-08 23:58:19", entries.get(0).getDate()); // publishTime 映射到 date
        // 验证请求组装：Token 头 + body 中 pageID/pageNumber（占位替换）
        verify(httpFetchClient).postJsonWithHeaders(
                org.mockito.ArgumentMatchers.eq("https://23330.o.apispace.com/project-info-upgrade/project-list"),
                org.mockito.ArgumentMatchers.contains("pageID"),
                org.mockito.ArgumentMatchers.eq(java.util.Map.of("X-APISpace-Token", "tok-demo", "Content-Type", "application/json")));
    }

}
