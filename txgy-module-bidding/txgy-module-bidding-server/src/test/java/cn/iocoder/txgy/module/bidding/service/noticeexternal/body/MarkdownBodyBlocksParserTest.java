package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AI 兜底输出的 Markdown → 块 容错转换测试。
 */
class MarkdownBodyBlocksParserTest {

    @Test
    void parse_mdTable_restoresTableBlock() {
        String md = "## 一、项目基本信息\n\n"
                + "| 项目 | 内容 |\n"
                + "|------|------|\n"
                + "| **项目名称** | 炼化公司装置大修 |\n"
                + "| **招标编号** | DYZB2025-11-G141 |\n";
        List<BodyBlock> blocks = MarkdownBodyBlocksParser.parse(md);

        assertEquals(2, blocks.size());
        assertEquals("heading", blocks.get(0).getType());
        assertEquals(2, blocks.get(0).getLevel());
        assertEquals("一、项目基本信息", blocks.get(0).getText());
        assertEquals("table", blocks.get(1).getType());
        assertEquals(List.of("项目", "内容"), blocks.get(1).getHeaders());
        assertEquals(2, blocks.get(1).getRows().size());
        assertEquals("项目名称", blocks.get(1).getRows().get(0).get(0)); // ** 剥除
        assertEquals("DYZB2025-11-G141", blocks.get(1).getRows().get(1).get(1));
    }

    @Test
    void parse_paragraphsAndHeadings_docOrder() {
        String md = "# 标题\n\n首段文字。\n\n后续一段。";
        List<BodyBlock> blocks = MarkdownBodyBlocksParser.parse(md);
        assertEquals(3, blocks.size());
        assertEquals(1, blocks.get(0).getLevel());
        assertEquals("首段文字。", blocks.get(1).getText());
        assertEquals("后续一段。", blocks.get(2).getText());
    }

    @Test
    void parse_lists_orderedAndUnordered() {
        String md = "- 甲\n- 乙\n\n1. 一\n2. 二\n";
        List<BodyBlock> blocks = MarkdownBodyBlocksParser.parse(md);
        assertEquals(2, blocks.size());
        assertEquals(false, blocks.get(0).getOrdered());
        assertEquals(List.of("甲", "乙"), blocks.get(0).getItems());
        assertEquals(true, blocks.get(1).getOrdered());
        assertEquals(List.of("一", "二"), blocks.get(1).getItems());
    }

    @Test
    void parse_tableWithoutSeparator_firstLineActsAsHeader() {
        String md = "| 排名 | 供应商 | 金额（元） |\n| 1 | 河南宇祯 | 2,297,150.00 |\n";
        List<BodyBlock> blocks = MarkdownBodyBlocksParser.parse(md);
        assertEquals(1, blocks.size());
        BodyBlock table = blocks.get(0);
        assertEquals(List.of("排名", "供应商", "金额（元）"), table.getHeaders());
        assertEquals(1, table.getRows().size());
    }

}
