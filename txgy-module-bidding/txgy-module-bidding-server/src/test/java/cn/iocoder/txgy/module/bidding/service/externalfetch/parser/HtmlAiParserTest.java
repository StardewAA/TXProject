package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;

import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiNoticeExtractRequest;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiNoticeExtractResult;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiNoticeExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HtmlAiParserTest {

    private AiNoticeExtractor ai;
    private HtmlAiParser parser;

    private void setUp(boolean enabled) {
        parser = new HtmlAiParser();
        ai = mock(AiNoticeExtractor.class);
        if (enabled) {
            ReflectionTestUtils.setField(parser, "aiNoticeExtractor", ai);
        }
    }

    private ParseInput html(String body) {
        ParseInput in = new ParseInput();
        in.setPayloadType("HTML");
        in.setContent("<html><head><title>某装置防腐保温维修项目</title></head><body><h1>陕西某公司储罐防腐保温项目招标公告</h1><p>" + body + "</p><p>招标人：陕西某管道防腐公司</p></body></html>");
        return in;
    }

    @Test
    void parse_aiDisabled_returnsFailed() {
        setUp(false);
        NoticeParseResult r = parser.parse(html("正文"));
        assertEquals(NoticeParseResult.Status.FAILED, r.getStatus());
        assertNotNull(r.getError());
    }

    @Test
    void parse_aiReturnsIndustry_industryFieldsFilled() throws Exception {
        setUp(true);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", "陕西某公司储罐防腐保温项目招标公告");
        fields.put("projectName", "某炼化储罐防腐保温工程");
        fields.put("issuer", "陕西某管道防腐公司");
        fields.put("industryFlag", "BELONGS");
        fields.put("industryConfidence", 0.95);
        fields.put("industryReason", "涉及储罐防腐保温施工");
        when(ai.extract(any(AiNoticeExtractRequest.class)))
                .thenReturn(new AiNoticeExtractResult(fields, null));

        NoticeParseResult r = parser.parse(html("正文内容"));

        assertEquals(NoticeParseResult.Status.SUCCESS, r.getStatus());
        assertEquals("陕西某公司储罐防腐保温项目招标公告", r.getTitle());
        assertEquals("某炼化储罐防腐保温工程", r.getProjectName());
        assertEquals("陕西某管道防腐公司", r.getIssuerName());
        assertEquals("BELONGS", r.getIndustryFlag());
        assertEquals("0.95", r.getIndustryConfidence());
        assertNotNull(r.getAiMetaJson());
        // 正文保留（AI 不取正文）
        assertNotNull(r.getContentText());
        assertNotNull(r.getContentHtml());
    }

    @Test
    void parse_aiOutputsNull_error() throws Exception {
        setUp(true);
        when(ai.extract(any(AiNoticeExtractRequest.class))).thenReturn(new AiNoticeExtractResult(null, null));
        NoticeParseResult r = parser.parse(html("正文"));
        assertEquals(NoticeParseResult.Status.FAILED, r.getStatus());
    }
}
