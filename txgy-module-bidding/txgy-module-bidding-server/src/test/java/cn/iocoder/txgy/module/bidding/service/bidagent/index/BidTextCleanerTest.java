package cn.iocoder.txgy.module.bidding.service.bidagent.index;

// [依据] 索引阶段文本清洗三条口径：编码统一（可检索对得上）、去噪（不丢正文）、结构归一（按语义切分）；
//       清洗统计必须可复算，便于专员复核"这份资料到底被改了什么"。

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入库文本清洗单测：锁死编码统一 / 去噪 / 结构归一三条口径，纯 JDK 实现无需起 Spring。
 */
class BidTextCleanerTest {

    private static final String DOC = "炼化公司2026年防腐保温项目施工招标技术文件.docx";

    // ========== 一、编码统一 ==========

    @Test
    void normalizeEncoding_removesBomZeroWidthAndPrivateUse() {
        String raw = "\uFEFFＡＢＣ－２０２６\r\n第一行\u200B内容\u3000续\t接\uF0B7结束\uFFFD";
        assertEquals("ABC-2026\n第一行内容 续 接结束", BidTextCleaner.normalizeEncoding(raw));
    }

    @Test
    void normalizeEncoding_keepsChinesePunctuation() {
        String raw = "（一）合同价款为 １０００ 元，工期 ３０ 日；详见《技术文件》。";
        assertEquals("（一）合同价款为 1000 元，工期 30 日；详见《技术文件》。",
                BidTextCleaner.normalizeEncoding(raw));
    }

    @Test
    void normalizeEncoding_foldsCircledAndRomanNumerals() {
        assertEquals("1-II", BidTextCleaner.normalizeEncoding("①－Ⅱ"));
    }

    // ========== 二、去噪 ==========

    @Test
    void clean_dropsPageNumbersAndRunningHeader() {
        String raw = "招标技术文件\n1\n第一条 总则\n投标人须具备防水防腐保温专业承包壹级资质，并提供近三年类似项目业绩证明。\n"
                + "2\n招标技术文件\n第一条 总则\n投标人须具备防水防腐保温专业承包壹级资质，并提供近三年类似项目业绩证明。\n"
                + "3\n招标技术文件\n投标人应当遵守本文件全部条款。";
        BidTextCleaner.Result result = BidTextCleaner.clean(raw, "招标技术文件.docx");
        assertEquals(3, result.getDroppedPageNoLines(), "1/2/3 是孤立且递增的裸数字页码，应全部删除");
        assertEquals(3, result.getDroppedNoiseLines(), "重复出现的页眉应全部删除");
        assertFalse(result.getText().contains("招标技术文件"), result.getText());
        assertTrue(result.getText().contains("第一条 总则"), result.getText());
        assertFalse(result.getText().matches("(?s).*\\n1\\n.*"), "页码不应留在正文里");
    }

    @Test
    void clean_keepsIsolatedNumberWhenItIsNotAPageSequence() {
        // 单条孤立数字不构成序列，不能当页码删（可能就是表格里的数量/金额）
        String raw = "合同结算价的 3%，质保期为 1 年。\n1000\n质保期起始日从合同有效期结束之日起算。";
        BidTextCleaner.Result result = BidTextCleaner.clean(raw, DOC);
        assertEquals(0, result.getDroppedPageNoLines());
        assertTrue(result.getText().contains("1000"), result.getText());
    }

    @Test
    void clean_dropsDecorationAndAdjacentDuplicateParagraphs() {
        String raw = "三、商务和技术偏差表.....................................................X\n"
                + "----------\n"
                + "投标人应提交履约保证金，金额为合同价的 5%。\n"
                + "投标人应提交履约保证金，金额为合同价的 5%。";
        BidTextCleaner.Result result = BidTextCleaner.clean(raw, DOC);
        assertTrue(result.getText().contains("三、商务和技术偏差表……X"), result.getText());
        assertFalse(result.getText().contains("----------"), result.getText());
        assertEquals(1, result.getDroppedNoiseLines());
        assertEquals(1, result.getDroppedDuplicateLines());
    }

    // ========== 三、结构归一 ==========

    @Test
    void clean_mergesHardWrappedLinesButKeepsClauseBoundaries() {
        String raw = "乙方提供的材料及耗品均应为投标文件所规定的品牌，并在施工前提供产品\n"
                + "合格证或检验合格证书。\n"
                + "（1）具有独立法人资格；\n"
                + "（2）近三年无重大违约记录。";
        String text = BidTextCleaner.clean(raw, DOC).getText();
        assertTrue(text.contains("并在施工前提供产品合格证或检验合格证书。"), text);
        assertTrue(text.contains("\n（1）具有独立法人资格；\n（2）近三年无重大违约记录。"), text);
    }

    @Test
    void clean_addsSpaceWhenJoiningAsciiWords() {
        String raw = "The certificate number is ABC\n12345 issued by the authority.";
        assertEquals("The certificate number is ABC 12345 issued by the authority.",
                BidTextCleaner.clean(raw, DOC).getText());
    }

    @Test
    void clean_keepsTableRowsAndFieldLabelsApart() {
        String tableRaw = "本人 （姓名）系 （供应商名称）的法定代表人（单位负责人），\n"
                + "序号 采购文件章节及条款号 响应文件章节及条款号 偏差说明";
        String tableText = BidTextCleaner.clean(tableRaw, DOC).getText();
        assertTrue(tableText.contains("（单位负责人），\n序号"), tableText);

        String labelRaw = "甲方：延安炼油厂、永坪炼油厂、榆林炼油厂、延安石油化工厂、油田气化工科技公司\n"
                + "乙方：投标方\n总则：";
        assertEquals(3, BidTextCleaner.clean(labelRaw, DOC).getText().split("\n").length);
    }

    // ========== 四、边界与可复算 ==========

    @Test
    void clean_handlesBlankInput() {
        assertEquals("", BidTextCleaner.clean(null, DOC).getText());
        assertEquals("", BidTextCleaner.clean("   \n\n\t ", DOC).getText());
        assertTrue(BidTextCleaner.clean(null, DOC).getDroppedSamples().isEmpty());
    }

    @Test
    void summary_reportsEveryCounter() {
        String summary = BidTextCleaner.clean("正文内容正文内容正文内容正文内容正文内容。", DOC).summary();
        assertTrue(summary.contains("删页码=0"), summary);
        assertTrue(summary.contains("删页眉页脚水印="), summary);
        assertTrue(summary.contains("合并折行="), summary);
        assertTrue(summary.contains("编码归一字符="), summary);
    }
}
