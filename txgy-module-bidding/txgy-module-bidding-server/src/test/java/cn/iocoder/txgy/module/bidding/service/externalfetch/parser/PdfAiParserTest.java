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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfAiParserTest {

    @Test
    void parse_pdfTextToAi_industryFilled() throws Exception {
        PdfAiParser parser = new PdfAiParser();
        // mock PDF 文本抽取：PdfTextNoticeParser.parse 返回带 contentText 的 result
        PdfTextNoticeParser pdfParser = mock(PdfTextNoticeParser.class);
        NoticeParseResult local = new NoticeParseResult();
        local.setContentText("陕西某管道防腐保温项目正文内容");
        local.setTitle("陕西某管道防腐保温项目");
        when(pdfParser.parse(any(ParseInput.class))).thenReturn(local);
        ReflectionTestUtils.setField(parser, "pdfTextNoticeParser", pdfParser);
        // mock AI
        AiNoticeExtractor ai = mock(AiNoticeExtractor.class);
        ReflectionTestUtils.setField(parser, "aiNoticeExtractor", ai);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", "陕西某管道防腐保温项目");
        fields.put("industryFlag", "BELONGS");
        fields.put("industryConfidence", 0.9);
        when(ai.extract(any(AiNoticeExtractRequest.class)))
                .thenReturn(new AiNoticeExtractResult(fields, null));

        ParseInput in = new ParseInput();
        in.setPayloadType("PDF");
        in.setFileBytes(new byte[]{0x25, 0x50, 0x44, 0x46}); // 假 PDF 头
        NoticeParseResult r = parser.parse(in);

        assertEquals(NoticeParseResult.Status.SUCCESS, r.getStatus());
        assertEquals("PDF", r.getContentType());
        assertEquals("BELONGS", r.getIndustryFlag());
        assertNotNull(r.getAiMetaJson());
    }

    @Test
    void parse_aiDisabled_returnsFailed() {
        PdfAiParser parser = new PdfAiParser();
        ParseInput in = new ParseInput();
        in.setPayloadType("PDF");
        in.setFileBytes(new byte[]{1, 2, 3});
        NoticeParseResult r = parser.parse(in);
        assertEquals(NoticeParseResult.Status.FAILED, r.getStatus());
        assertNotNull(r.getError());
    }
}
