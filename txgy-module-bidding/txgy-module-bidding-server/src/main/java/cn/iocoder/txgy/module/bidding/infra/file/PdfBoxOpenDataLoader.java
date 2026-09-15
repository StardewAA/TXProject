package cn.iocoder.txgy.module.bidding.infra.file;
// 规范依据：设计文档 v0.6 §7.3/§14.2/§15.6（本地 OpenDataLoader 抽取：只抽文本+表格，不 OCR、不排版重建；空结果=上层 FAIL_EMPTY）
// 说明：本类即「OpenDataLoader 本地引擎」的落地实现（PDFBox 封装，配置 bidding.ext-notice.pdf.open-data-loader.loader-class 指向本类）。
// 接口契约（反射绑定，见 service/noticeexternal/pdf/OpenDataLoaderPdfContentExtractor）：
//   public Object extract(File pdf) —— 返回对象须暴露 getText() / getTables() / getPageCount()。

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenDataLoader 本地 PDF 抽取引擎（PDFBox 2.x）。
 *
 * <ul>
 *   <li>文本：PDFTextStripper 按页拼接（保留页面分隔）；抽取不到任何字符 → text 为空串；</li>
 *   <li>表格：PDFBox 不提供版面表格识别（非 OCR 语义），本版返回空表结构（null → 上层按“无表格”处理；
 *       仅当 text 亦为空时才落入 FAIL_EMPTY——符合 v0.6“未抽到任何文本/表格即判失败”的裁决口径，裁决在 PdfExtractService）；</li>
 *   <li>安全：加密 PDF 拒绝（PDFPasswordProtectedException 上抛）、页数上限防资源耗尽（默认 512 页）；</li>
 *   <li>有文字层（/Font+ToUnicode）的真实公告 → text 非空 → SUCCESS；纯扫描件（无文字层）→ text 空 → FAIL_EMPTY。</li>
 * </ul>
 */
@Slf4j
public class PdfBoxOpenDataLoader {

    /** 页数上限：超限直接拒绝，防止恶意 PDF 资源耗尽 */
    private static final int MAX_PAGES = 512;

    /**
     * 抽取结果（反射约定 getText/getTables/getPageCount）。
     * tables 语义：二维数组（行 → 单元格字符串），当前恒为空表（见类注释）。
     */
    public static class Result {

        private final String text;
        private final List<List<String>> tables;
        private final Integer pageCount;

        public Result(String text, List<List<String>> tables, Integer pageCount) {
            this.text = text;
            this.tables = tables;
            this.pageCount = pageCount;
        }

        public String getText() {
            return text;
        }

        public List<List<String>> getTables() {
            return tables;
        }

        public Integer getPageCount() {
            return pageCount;
        }
    }

    public Object extract(File pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            if (document.isEncrypted()) {
                throw new IOException("OpenDataLoader 拒绝处理加密 PDF: " + pdf.getName()
                        + "（需要口令的文档无法本地抽取）");
            }
            int pages = document.getNumberOfPages();
            if (pages > MAX_PAGES) {
                throw new IOException("OpenDataLoader 拒绝处理超长 PDF（页数 " + pages + " > 上限 "
                        + MAX_PAGES + "）: " + pdf.getName());
            }
            PDFTextStripper stripper = new PDFTextStripper();
            // 中文/全角标点视为可抽取文本；空页不产生字符
            String text = stripper.getText(document);
            log.info("[extract][pdf={} pages={} textChars={}]", pdf.getName(), pages, text.length());
            // 表格识别 v1 不做（PDFBox 无版面结构化能力）；返回空表，由 PdfExtractService 裁决空结果
            return new Result(text, new ArrayList<>(), pages);
        }
    }

}
