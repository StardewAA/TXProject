package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] 国信站点解析 v2：列表 entries + 详情 pdf.js PDF 直链提取。

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GxEbidNoticeParserTest {

    private String listHtml() {
        return "<html><body><ul class=\"newslist\">"
                + "<li><a href=\"https://gx.e-bidding.org/sdny_bulletin/2026-07-10/11025.html\" title=\"山西某项目EPC总承包监理招标公告\"><h5>山西某项目EPC总承包监理招标公告</h5></a></li>"
                + "</ul></body></html>";
    }

    private String detailHtml() throws Exception {
        String real = "https://gx.e-bidding.org/bidprocurement/datacenter-cebpubserver/cebpubserver/dataCeboubServerCommonController/openFileById?fileType=2&id=234794aaa08d48bcb4c409d4bf7ff921";
        String fileParam = java.net.URLEncoder.encode(java.net.URLEncoder.encode(real, java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        return "<html><head><title>国信e采（山西）</title></head><body>"
                + "<iframe src=\"https://gx.e-bidding.org//resource/css/pdfjs/web/viewer.html?file=" + fileParam + "&page=1\"></iframe>"
                + "</body></html>";
    }

    @Test
    void parsesListEntries() {
        ParseInput input = new ParseInput();
        input.setPayloadType("HTML");
        input.setContent(listHtml());
        NoticeParseResult result = new GxEbidNoticeParser().parse(input);
        assertEquals("LIST", result.getPageType());
        assertEquals(1, result.getEntries().size());
        assertEquals("https://gx.e-bidding.org/sdny_bulletin/2026-07-10/11025.html", result.getEntries().get(0).getUrl());
    }

    @Test
    void detailPage_extractsPdfUrl() throws Exception {
        ParseInput input = new ParseInput();
        input.setPayloadType("HTML");
        input.setContent(detailHtml());
        input.setUrl("https://gx.e-bidding.org/sdny_bulletin/2026-06-18/10835.html");
        NoticeParseResult result = new GxEbidNoticeParser().parse(input);
        assertEquals("DETAIL_PDF", result.getPageType());
        assertEquals("PDF", result.getContentType());
        String pdfUrl = result.getMeta().get("pdfDownloadUrl");
        assertTrue(pdfUrl != null && pdfUrl.contains("openFileById?fileType=2&id=234794aaa08d48bcb4c409d4bf7ff921"),
                "PDF 直链解码失败: " + pdfUrl);
        assertEquals(1, result.getAttachments().size());
    }

    @Test
    void shellPage_unknown() {
        ParseInput input = new ParseInput();
        input.setPayloadType("HTML");
        input.setContent("<html><body>壳</body></html>");
        NoticeParseResult result = new GxEbidNoticeParser().parse(input);
        assertEquals("UNKNOWN", result.getPageType());
    }

}
