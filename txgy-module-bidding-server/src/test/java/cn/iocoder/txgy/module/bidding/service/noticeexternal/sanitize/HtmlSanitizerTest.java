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

}
