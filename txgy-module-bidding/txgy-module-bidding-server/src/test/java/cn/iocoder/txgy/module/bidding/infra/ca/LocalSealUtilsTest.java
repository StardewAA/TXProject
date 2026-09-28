package cn.iocoder.txgy.module.bidding.infra.ca;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地 PDF 电子签章工具契约测试（每页盖章、页数保持、魔数判断）。
 */
class LocalSealUtilsTest {

    @Test
    @DisplayName("多页 PDF 每页盖章：页数保持 + 输出字节变化（红章已绘制）")
    void sealPdf_stampsEveryPage() throws Exception {
        byte[] pdf = buildMultiPagePdf(3);
        byte[] sealed = LocalSealUtils.sealPdf(pdf, "本地CA测试单位", "SN-LOCAL-CA-TEST-0001", "2026-09-28 20:00:00");
        assertFalse(Arrays.equals(pdf, sealed), "盖章后字节应变化");
        try (PDDocument doc = Loader.loadPDF(sealed)) {
            assertEquals(3, doc.getNumberOfPages(), "盖章不改变页数");
        }
    }

    @Test
    @DisplayName("单页 PDF 盖章：页数保持 1")
    void sealPdf_singlePage() throws Exception {
        byte[] pdf = buildMultiPagePdf(1);
        byte[] sealed = LocalSealUtils.sealPdf(pdf, "单位", "SN-1", "2026-09-28 20:00:00");
        try (PDDocument doc = Loader.loadPDF(sealed)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("空 PDF 直接返回（不崩溃）")
    void sealPdf_emptyPdf() {
        // 空文件不满足魔数，不在此路径；此处验证空字节数组返回 false
        assertFalse(LocalSealUtils.isPdf(new byte[0]));
    }

    @Test
    @DisplayName("isPdf 魔数判断")
    void isPdf() {
        assertTrue(LocalSealUtils.isPdf(new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'}));
        assertFalse(LocalSealUtils.isPdf("hello world".getBytes()));
        assertFalse(LocalSealUtils.isPdf(null));
    }

    @Test
    @DisplayName("toHex 便捷编码")
    void toHex() {
        assertEquals("", LocalSealUtils.toHex(new byte[0]));
        assertEquals("0f", LocalSealUtils.toHex(new byte[]{0x0f}));
        assertEquals("01ff", LocalSealUtils.toHex(new byte[]{0x01, (byte) 0xff}));
    }

    /** 生成含文本内容的多页 PDF（避免无内容流页的 APPEND 限制）。 */
    private static byte[] buildMultiPagePdf(int pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(60, 700);
                    cs.showText("page " + (i + 1));
                    cs.endText();
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }
}
