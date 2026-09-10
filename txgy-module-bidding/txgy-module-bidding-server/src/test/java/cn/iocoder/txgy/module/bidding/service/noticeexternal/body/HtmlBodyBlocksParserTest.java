package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 常规 HTML 正文结构化解析测试（策略链 HTML_STRUCTURE 档）。
 */
class HtmlBodyBlocksParserTest {

    @Test
    void parse_structuredHtml_keepsDocOrder() {
        String html = "<div class=\"notice\"><h1>标题</h1><p>导语段。</p>"
                + "<h2>一、候选人</h2>"
                + "<table><thead><tr><th>排名</th><th>名称</th></tr></thead>"
                + "<tbody><tr><td>1</td><td>甲公司</td></tr></tbody></table>"
                + "<ul><li>要求一</li><li>要求二</li></ul>"
                + "<p>联系方式：张三 010-123</p></div>";
        List<BodyBlock> blocks = HtmlBodyBlocksParser.parse(html);

        assertEquals(6, blocks.size());
        assertEquals("heading", blocks.get(0).getType());
        assertEquals(1, blocks.get(0).getLevel());
        assertEquals("标题", blocks.get(0).getText());
        assertEquals("paragraph", blocks.get(1).getType());
        assertEquals("heading", blocks.get(2).getType());
        assertEquals("table", blocks.get(3).getType());
        assertEquals(List.of("排名", "名称"), blocks.get(3).getHeaders());
        assertEquals(List.of(List.of("1", "甲公司")), blocks.get(3).getRows());
        assertEquals("list", blocks.get(4).getType());
        assertFalse(blocks.get(4).getOrdered());
        assertEquals(List.of("要求一", "要求二"), blocks.get(4).getItems());
        assertEquals("paragraph", blocks.get(5).getType());
    }

    @Test
    void parse_skipsScriptStyle_ignoresEmptyContainers() {
        String html = "<script>alert(1)</script><style>p{}</style><div><p>正文</p></div><p></p>";
        List<BodyBlock> blocks = HtmlBodyBlocksParser.parse(html);
        assertEquals(1, blocks.size());
        assertEquals("正文", blocks.get(0).getText());
    }

    @Test
    void parse_noTable_whenFlattenedStickyText() {
        // 拍平粘连：无 table 标签，全在 p 里 → 只有一个长段落（isStickyCandidate 交由服务层判定）
        String html = "<p>排序 中标候选人名称 投标下浮率（%）1 陕西化建工程有限责任公司 1.80 合同签订之日起至2027年3月31日</p>";
        List<BodyBlock> blocks = HtmlBodyBlocksParser.parse(html);
        assertEquals(1, blocks.size());
        assertEquals("paragraph", blocks.get(0).getType());
        assertTrue(blocks.get(0).getText().contains("1.80"));
    }

    @Test
    void parse_tableWithoutThead_firstThRowBecomesHeaders() {
        String html = "<table><tr><th>名称</th><th>金额</th></tr><tr><td>乙</td><td>100</td></tr></table>";
        List<BodyBlock> blocks = HtmlBodyBlocksParser.parse(html);
        assertEquals(1, blocks.size());
        BodyBlock table = blocks.get(0);
        assertEquals(List.of("名称", "金额"), table.getHeaders());
        assertEquals(List.of(List.of("乙", "100")), table.getRows());
    }

    @Test
    void parse_blank_returnsEmpty() {
        assertTrue(HtmlBodyBlocksParser.parse(null).isEmpty());
        assertTrue(HtmlBodyBlocksParser.parse("  ").isEmpty());
    }

}
