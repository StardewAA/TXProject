package cn.iocoder.txgy.module.bidding.service.externalfetch.samples;
// 规范依据：设计文档 v0.6 §7.3/§15.6（PDF 本地抽取）；真实样本：docs/招投标版块数据提供/炼化公司…2026 装置大修消缺公开招标（HTML+PDF）

import cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf.OpenDataLoaderPdfContentExtractor;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.sanitize.HtmlSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实样本资产测试：炼化公司 2026 装置大修消缺公开招标公告（HTML 70KB / PDF 6 页）。
 *
 * <p>HTML：真实第三方页面 —— 验证 Jsoup 标题抽取与 HtmlSanitizer 白名单净化（含脚本剥离、正文保留），
 * 以及入库长度上限合规。
 * <p>PDF：%PDF-1.7、含 /Font 与 /ToUnicode（有文本层，OpenDataLoader 接入后预期抽取 SUCCESS 而非 FAIL_EMPTY）；
 * 抽取器当前未绑定本地库坐标 → extract() 给出显式 IllegalStateException（防静默跳过，见设计 §7.3 适配说明）。
 */
class RealSampleFilesTest {

    private static String readSample(String name) throws Exception {
        try (InputStream in = RealSampleFilesTest.class.getResourceAsStream("/samples/" + name)) {
            assertNotNull(in, "样本缺失: samples/" + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void html_parseTitleAndSanitizeRealGovPage() throws Exception {
        String raw = readSample("lianhua-2026-notice.html");
        assertTrue(raw.contains("<script") || raw.contains("<SCRIPT"),
                "真实页面应含脚本（否则净化断言无区分度）——如样本结构变化请复核");

        Document doc = Jsoup.parse(raw);
        String title = doc.title();
        assertTrue(title.contains("招标"), "Jsoup 应能抽取真实 <title>: " + title);
        assertTrue(raw.contains("消缺") && raw.contains("装置"), "样本应含招标公告正文");
        assertTrue(raw.length() < 8 * 1024 * 1024, "样本长度应在入库上限内");

        String safe = HtmlSanitizer.sanitize(raw);
        assertFalse(safe.contains("<script"), "净化后不得残留 <script>");
        assertFalse(safe.contains("onclick") || safe.contains("onerror"), "净化后不得残留事件属性");
        assertTrue(safe.contains("招标") && safe.contains("消缺"), "净化后应保留招标正文");
    }

    @Test
    void pdf_validAsset_withTextLayer_extractorUnboundIsExplicit() throws Exception {
        try (InputStream in = RealSampleFilesTest.class.getResourceAsStream("/samples/lianhua-2026-notice.pdf")) {
            assertNotNull(in, "样本缺失: samples/lianhua-2026-notice.pdf");
            byte[] pdf = in.readAllBytes();
            assertTrue(pdf.length > 1000, "PDF 样本非空");
            String head = new String(pdf, 0, 8, StandardCharsets.ISO_8859_1);
            assertTrue(head.startsWith("%PDF-"), "样本应为合法 PDF，实际头: " + head);
        }
        // OpenDataLoader 本地库坐标未引入前：抽取必须显式失败（防静默跳过），不得假装成功
        OpenDataLoaderPdfContentExtractor extractor = new OpenDataLoaderPdfContentExtractor();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> extractor.extract(new File("samples/lianhua-2026-notice.pdf")));
        assertTrue(ex.getMessage().contains("未接入"), "失败信息应指向 OpenDataLoader 接入缺口: " + ex.getMessage());
    }

}
