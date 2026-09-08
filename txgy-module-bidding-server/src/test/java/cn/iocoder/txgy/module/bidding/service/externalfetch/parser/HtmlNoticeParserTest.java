package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] HTML_GENERIC 解析器：标题(h1)、发布单位行启发、发布日期、附件候选。

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlNoticeParserTest {

    @Test
    void parsesHeadingIssuerDateAndAttachment() {
        LocalDate past = LocalDate.now().minusDays(3);
        String html = "<html><head><title>某防腐保温工程招标公告 - 测试平台</title></head><body>"
                + "<h1>某炼化装置防腐保温维修项目招标公告</h1>"
                + "<p>招标人：陕西某建设有限公司</p>"
                + "<p>发布日期：" + past.getYear() + "年" + past.getMonthValue() + "月" + past.getDayOfMonth() + "日</p>"
                + "<a href=\"/files/zbwj.pdf\">招标文件下载</a>"
                + "</body></html>";

        NoticeParseResult result = new HtmlNoticeParser().parse(input(html, "https://example.com/n/1"));

        assertEquals(NoticeParseResult.Status.SUCCESS, result.getStatus());
        assertEquals("某炼化装置防腐保温维修项目招标公告", result.getTitle());
        assertEquals("陕西某建设有限公司", result.getIssuerName());
        assertEquals(past.atStartOfDay(), result.getPublishTime());
        assertEquals(1, result.getAttachments().size());
        assertEquals("https://example.com/files/zbwj.pdf", result.getAttachments().get(0).getUrl());
        assertEquals("招标文件下载", result.getAttachments().get(0).getName());
    }

    @Test
    void emptyHtml_failed() {
        NoticeParseResult result = new HtmlNoticeParser().parse(input("   ", null));
        assertEquals(NoticeParseResult.Status.FAILED, result.getStatus());
        assertNotNull(result.getError());
    }

    @Test
    void onlyBodyText_partialWithWarning() {
        NoticeParseResult result = new HtmlNoticeParser().parse(input("<html><body><p>只有正文没有标题结构</p></body></html>", null));
        assertEquals(NoticeParseResult.Status.PARTIAL, result.getStatus());
        assertTrue(!result.getWarnings().isEmpty());
    }

    private static ParseInput input(String html, String url) {
        ParseInput input = new ParseInput();
        input.setPayloadType("HTML");
        input.setContent(html);
        input.setUrl(url);
        return input;
    }

}
