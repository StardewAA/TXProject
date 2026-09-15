package cn.iocoder.txgy.module.bidding.infra.file;
// 规范依据：设计文档 v0.6 §7.3/§14.2/§15.6（本地 OpenDataLoader 抽取文本；空结果=FAIL_EMPTY 素材）
// 真实样本：samples/lianhua-2026-notice.pdf（6 页含文字层 /Font+ToUnicode，docs 提供的招标公告）

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenDataLoader 本地引擎（PDFBox 封装）真实样本测试：
 * 含文字层真实公告 → text 非空（SUCCESS 素材）；空白页 PDF → text 空 + 空表（FAIL_EMPTY 素材）。
 */
class PdfBoxOpenDataLoaderTest {

    @TempDir
    Path tempDir;

    private PdfBoxOpenDataLoader.Result extractSamplePdf(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, "样本缺失: " + resource);
            Path p = tempDir.resolve("sample.pdf");
            Files.copy(in, p);
            Object raw = new PdfBoxOpenDataLoader().extract(p.toFile());
            assertTrue(raw instanceof PdfBoxOpenDataLoader.Result, "结果应遵循反射约定 Result");
            return (PdfBoxOpenDataLoader.Result) raw;
        }
    }

    @Test
    void realTextLayerPdf_extractsTextAndPages() throws Exception {
        PdfBoxOpenDataLoader.Result r = extractSamplePdf("/samples/lianhua-2026-notice.pdf");
        assertNotNull(r.getText());
        assertTrue(r.getText().length() > 100, "含文字层的真实公告应抽取到正文: " + r.getText().length());
        assertNotNull(r.getPageCount());
        assertTrue(r.getPageCount() >= 1);
        assertTrue(r.getTables() != null && r.getTables().isEmpty(),
                "PDFBox 不做版面表格识别 → 空表结构，由上层裁决");
    }

    @Test
    void blankPagePdf_textEmpty_tablesEmpty() throws Exception {
        Path p = tempDir.resolve("blank.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage()); // 空白页
            doc.save(p.toFile());
        }
        Object raw = new PdfBoxOpenDataLoader().extract(p.toFile());
        PdfBoxOpenDataLoader.Result r = (PdfBoxOpenDataLoader.Result) raw;
        assertTrue(r.getText() == null || r.getText().isBlank(), "无文字层/空白页 → 文本为空（FAIL_EMPTY 裁决素材）");
        assertTrue(r.getTables().isEmpty());
    }

}
