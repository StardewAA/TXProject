package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] view-source 包装还原：line-content 行拼接 + 实体反转义。

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlViewSourceDecoderTest {

    @Test
    void decodesLineContentWrapper() {
        String wrapper = "<table><tr><td class=\"line-number\" value=\"1\"></td>"
                + "<td class=\"line-content\"><span class=\"html-tag\">&lt;h1&gt;</span>标题"
                + "<span class=\"html-tag\">&lt;/h1&gt;</span></td></tr></table>";
        String decoded = HtmlViewSourceDecoder.decodeIfNeeded(wrapper);
        assertEquals("<h1>标题</h1>", decoded);
    }

    @Test
    void plainHtml_unchanged() {
        String plain = "<html><body><h1>x</h1></body></html>";
        assertEquals(plain, HtmlViewSourceDecoder.decodeIfNeeded(plain));
    }

}
