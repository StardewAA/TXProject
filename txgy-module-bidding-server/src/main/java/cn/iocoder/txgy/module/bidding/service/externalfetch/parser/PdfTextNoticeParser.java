package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] PDF 文本公告解析策略（PDF_TEXT_GENERIC）：文件上传通道（.pdf）→ 本地 PDFBox 抽取文本 → 字段启发；
//       国信 e 采等以 PDF 为公告载体的站点可复用本抽取并固化为站点专用规则。

import cn.iocoder.txgy.module.bidding.infra.file.PdfBoxOpenDataLoader;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf.PdfContentExtractor;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf.PdfExtractResult;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf.PdfExtractService;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf.OpenDataLoaderPdfContentExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * 通用 PDF 文本公告解析器（本地 PDFBox 引擎；无文字层的扫描件 PDF → FAILED）。
 */
@Slf4j
@Component
public class PdfTextNoticeParser implements NoticeParser {

    private PdfExtractService pdfExtractService;

    public PdfTextNoticeParser() {
        PdfContentExtractor extractor = new OpenDataLoaderPdfContentExtractor(
                PdfBoxOpenDataLoader.class.getName(), "extract");
        this.pdfExtractService = new PdfExtractService(extractor);
    }

    /** 测试/替换抽取器（package-private） */
    void setPdfExtractService(PdfExtractService pdfExtractService) {
        this.pdfExtractService = pdfExtractService;
    }

    @Override
    public String getParserCode() {
        return "PDF_TEXT_GENERIC";
    }

    @Override
    public String getParserName() {
        return "通用 PDF 文本公告解析器";
    }

    @Override
    public String getDescription() {
        return "PDF 文件 → 本地 PDFBox 抽取文本 → 标题/编号/日期/招标人启发；"
                + "适用国信 e 采等“公告详情为 PDF”的站点（站点版式规则另行固化）。扫描件（无文字层）会失败提示。";
    }

    @Override
    public List<String> supportedPayloadTypes() {
        return List.of("PDF");
    }

    @Override
    public NoticeParseResult parse(ParseInput input) {
        NoticeParseResult out = new NoticeParseResult();
        out.setParserCode(getParserCode());
        out.setParserName(getParserName());
        byte[] bytes = input.getFileBytes();
        if (bytes == null || bytes.length == 0) {
            out.setError("未提供 PDF 文件字节（PDF 载荷仅支持文件上传通道）");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        File temp = null;
        try {
            temp = File.createTempFile("crawl-parser-", ".pdf");
            Files.write(temp.toPath(), bytes);
            PdfExtractResult extracted = pdfExtractService.getExtractor().extract(temp);
            if (extracted == null || extracted.isEmptyResult()) {
                out.setError("PDF 未抽取到文本（扫描件/纯图 PDF 无文字层，或文件损坏）");
                out.setStatus(NoticeParseResult.Status.FAILED);
                return out;
            }
            String text = PdfTextNoticeExtractor.extract(extracted.getText(), out);
            if (out.getOrigUrl() == null && StringUtils.hasText(input.getUrl())) {
                out.setOrigUrl(input.getUrl());
            }
            if (extracted.getPageCount() != null) {
                out.getMeta().put("pageCount", String.valueOf(extracted.getPageCount()));
            }
            if (StringUtils.hasText(text) && out.getStatus() == null) {
                out.finalizeStatus();
            }
            return out;
        } catch (Exception e) {
            log.warn("[PdfTextNoticeParser][file={}] PDF 解析异常", input.getFileName(), e);
            out.setError("PDF 解析异常：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp.toPath());
                } catch (IOException ignored) {
                    // 临时文件清理失败不阻断
                }
            }
        }
    }

}
