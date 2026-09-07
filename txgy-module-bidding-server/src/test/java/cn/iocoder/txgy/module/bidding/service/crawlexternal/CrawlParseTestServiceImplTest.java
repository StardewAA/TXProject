package cn.iocoder.txgy.module.bidding.service.crawlexternal;
// [依据] 解析器测试服务：路由/载荷校验/类型归一/错误码 041~045 覆盖（Mockito，无 Spring）。

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlParseTestRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlParserInfoRespVO;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.NoticeParseResult;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.ParseInput;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.NoticeParser;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.ParserRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlParseTestServiceImplTest {

    private ParserRegistry parserRegistry;
    private CrawlParseTestServiceImpl service;

    private final NoticeParser htmlParser = parserStub("HTML_GENERIC", "通用 HTML", List.of("HTML"));

    @BeforeEach
    void setUp() {
        parserRegistry = mock(ParserRegistry.class);
        service = new CrawlParseTestServiceImpl();
        ReflectionTestUtils.setField(service, "parserRegistry", parserRegistry);
        when(parserRegistry.get("HTML_GENERIC")).thenReturn(htmlParser);
        when(parserRegistry.all()).thenReturn(List.of(htmlParser));
    }

    private static NoticeParser parserStub(String code, String name, List<String> types) {
        return new NoticeParser() {
            @Override
            public String getParserCode() {
                return code;
            }

            @Override
            public String getParserName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "";
            }

            @Override
            public List<String> supportedPayloadTypes() {
                return types;
            }

            @Override
            public NoticeParseResult parse(ParseInput input) {
                return successOf(code, name);
            }
        };
    }

    private static NoticeParseResult successOf(String code, String name) {
        NoticeParseResult result = new NoticeParseResult();
        result.setParserCode(code);
        result.setParserName(name);
        result.setTitle("测试标题");
        result.setContentText("正文");
        result.finalizeStatus();
        return result;
    }

    @Test
    void listParsers_mapsToVos() {
        List<CrawlParserInfoRespVO> list = service.listParsers();
        assertEquals(1, list.size());
        assertEquals("HTML_GENERIC", list.get(0).getCode());
        assertEquals(List.of("HTML"), list.get(0).getSupportedTypes());
    }

    @Test
    void unknownParser_throws041() {
        when(parserRegistry.get("NOPE")).thenThrow(new IllegalArgumentException("未知解析器"));
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.testParse("NOPE", "HTML", "<p>x</p>", null, null, null));
        assertEquals(ErrorCodeConstants.CRAWL_PARSER_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void emptyContent_throws042() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.testParse("HTML_GENERIC", "HTML", "   ", null, null, null));
        assertEquals(ErrorCodeConstants.CRAWL_PARSER_PAYLOAD_MISSING.getCode(), e.getCode());
    }

    @Test
    void unsupportedPayloadType_throws043() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.testParse("HTML_GENERIC", "JSON", "{\"a\":1}", null, null, null));
        assertEquals(ErrorCodeConstants.CRAWL_PARSER_INPUT_TYPE_UNSUPPORTED.getCode(), e.getCode());
    }

    @Test
    void fileHtml_inferredAndParsed() {
        byte[] bytes = "<html><h1>t</h1></html>".getBytes(StandardCharsets.UTF_8);
        CrawlParseTestRespVO resp = service.testParse("HTML_GENERIC", "FILE", null, "a.html", "https://x.com/n", bytes);
        assertEquals("HTML", resp.getPayloadType());
        assertEquals("测试标题", resp.getTitle());
        assertEquals(NoticeParseResult.Status.SUCCESS.name(), resp.getStatus());
        assertEquals("a.html", resp.getFileName());
        assertTrue(resp.getDurationMs() >= 0);
    }

    @Test
    void fileUnsupportedExt_throws044() {
        byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.testParse("HTML_GENERIC", "FILE", null, "a.txt", null, bytes));
        assertEquals(ErrorCodeConstants.CRAWL_PARSER_FILE_INVALID.getCode(), e.getCode());
    }

    @Test
    void fileMissing_throws042() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.testParse("HTML_GENERIC", "FILE", null, "a.html", null, null));
        assertEquals(ErrorCodeConstants.CRAWL_PARSER_PAYLOAD_MISSING.getCode(), e.getCode());
    }

}
