package cn.iocoder.txgy.module.bidding.service.noticeexternal.sanitize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTML 白名单净化测试（安全评审意见 1：存储型 XSS 输出侧兜底）。
 */
class HtmlSanitizerTest {

    @Test
    void sanitize_removesScriptAndEventHandlers() {
        String dirty = "<div onclick=\"steal()\">正文<script>alert(1)</script>"
                + "<img src=x onerror=alert(1)></div>";
        String clean = HtmlSanitizer.sanitize(dirty);

        assertFalse(clean.contains("<script"));
        assertFalse(clean.contains("onclick"));
        assertFalse(clean.contains("onerror"));
        assertTrue(clean.contains("正文"));
    }

    @Test
    void sanitize_removesIframeFormAndStyleTag() {
        String dirty = "<iframe src=\"https://evil.example/x\"></iframe>"
                + "<form action=\"https://evil.example\"><input name=x></form>"
                + "<style>body{display:none}</style><p>留档格式</p>";
        String clean = HtmlSanitizer.sanitize(dirty);

        assertFalse(clean.contains("<iframe"));
        assertFalse(clean.contains("<form"));
        assertFalse(clean.contains("<input"));
        assertFalse(clean.contains("<style"));
        assertTrue(clean.contains("<p>留档格式</p>"));
    }

    @Test
    void sanitize_removesJavascriptHrefAndKeepsHttp() {
        String dirty = "<a href=\"javascript:alert(1)\">坏链</a><a href=\"https://src.example/a\">好链</a>";
        String clean = HtmlSanitizer.sanitize(dirty);

        assertFalse(clean.contains("javascript:"));
        assertTrue(clean.contains("https://src.example/a"));
    }

    @Test
    void sanitize_stripsStyleAttributesButKeepsPlainFormat() {
        String clean = HtmlSanitizer.sanitize("<p style=\"color:red\">加粗<b>行</b></p>");
        assertFalse(clean.contains("style"));
        assertTrue(clean.contains("<b>行</b>"));
    }

    @Test
    void sanitize_blankOrNull_returnsAsIs() {
        assertNull(HtmlSanitizer.sanitize(null));
        assertEquals("   ", HtmlSanitizer.sanitize("   "));
    }

    @Test
    void sanitize_keepsTableListHeadingStructure() {
        // 联调清单 R7/R12①：候选人/成交/标段等表格化信息需保结构下发，前端公文表格样式渲染
        String html = "<h1>候选人公示</h1><table><thead><tr><th>排名</th><th>候选供应商名称</th></tr></thead>"
                + "<tbody><tr><td>1</td><td>河南省宇祯建设有限公司</td></tr>"
                + "<tr><td>2</td><td>陕西省化建工程有限公司</td></tr></tbody></table>"
                + "<ul><li>要求一</li><li>要求二</li></ul><p>正文段落</p>";
        String clean = HtmlSanitizer.sanitize(html);

        assertTrue(clean.contains("<h1>候选人公示</h1>"), clean);
        assertTrue(clean.contains("<table>"), clean);
        assertTrue(clean.contains("<thead>"), clean);
        assertTrue(clean.contains("<th>排名</th>"), clean);
        assertTrue(clean.contains("<td>河南省宇祯建设有限公司</td>"), clean);
        assertTrue(clean.contains("<ul>"), clean);
        assertTrue(clean.contains("<p>正文段落</p>"), clean);
    }

    @Test
    void sanitize_dropsNonHttpImgSrc_andScriptTags() {
        String dirty = "<p>图</p><img src=\"data:image/png;base64,AAAA\"><img src=\"javascript:alert(1)\">"
                + "<img src=\"https://src.example/ok.png\"><script>alert(2)</script>";
        String clean = HtmlSanitizer.sanitize(dirty);

        assertFalse(clean.contains("data:image"), clean);
        assertFalse(clean.contains("javascript:"), clean);
        assertFalse(clean.contains("<script"), clean);
        assertTrue(clean.contains("https://src.example/ok.png"), clean);
    }

}
