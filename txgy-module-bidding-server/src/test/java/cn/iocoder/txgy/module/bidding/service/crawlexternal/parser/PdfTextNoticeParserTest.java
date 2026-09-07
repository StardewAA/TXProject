package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] PDF_TEXT_GENERIC 单元测试：注入 fake PdfContentExtractor（模拟 PDFBox 抽取文本），验证文本公告字段规则。

import cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf.PdfExtractResult;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf.PdfExtractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfTextNoticeParserTest {

    private PdfTextNoticeParser parser;

    @BeforeEach
    void setUp() {
        parser = new PdfTextNoticeParser();
        parser.setPdfExtractService(new PdfExtractService(pdf -> new PdfExtractResult(
                fakePdfText(), null, 1)));
    }

    /** 模拟国信式公告文本：标题断行 + （招标编号） + 正文 + 落款日期 */
    private String fakePdfText() {
        LocalDate d = LocalDate.now().minusDays(3);
        return "山西某地 100MW 光伏发电项目 EPC 总承包工程\n招标公告\n（招标编号：SXFW-GK-26014）\n"
                + "招标项目\u3000编号：E1401005146001719001\n一、招标条件 本招标项目已具备招标条件。\n"
                + "招标人：山西某某新能源有限公司\n"
                + d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日\n";
    }

    @Test
    void parsesPdfTextNoticeFields() {
        ParseInput input = new ParseInput();
        input.setPayloadType("PDF");
        input.setFileName("a.pdf");
        input.setFileBytes("x".getBytes(StandardCharsets.UTF_8));

        NoticeParseResult result = parser.parse(input);

        assertEquals(NoticeParseResult.Status.SUCCESS, result.getStatus());
        assertNotNull(result.getTitle());
        assertTrue(result.getTitle().contains("招标公告"), "标题应含公告词: " + result.getTitle());
        assertEquals("SXFW-GK-26014", result.getProjectCode());
        assertEquals("E1401005146001719001", result.getTenderProjectCode());
        assertEquals("山西某某新能源有限公司", result.getIssuerName());
        assertNotNull(result.getPublishTime());
        assertEquals("PDF", result.getContentType());
        assertNotNull(result.getContentText());
    }

    @Test
    void emptyBytes_failed() {
        ParseInput input = new ParseInput();
        input.setPayloadType("PDF");
        input.setFileBytes(null);
        NoticeParseResult result = parser.parse(input);
        assertEquals(NoticeParseResult.Status.FAILED, result.getStatus());
    }

}
