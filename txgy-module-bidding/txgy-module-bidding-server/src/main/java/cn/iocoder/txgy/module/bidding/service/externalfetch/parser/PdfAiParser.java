package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;

// [依据] 用户口径 2026-09-09：PDF+AI 解析器——PDFBox 抽正文文本→平台 AI 结构化（含行业相关判定）。

import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiNoticeExtractRequest;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiNoticeExtractResult;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 解析器：PDF 正文 → 平台 AI 结构化抽取（PDFBox 先抽文本，AI 出字段/行业判定）。
 * 继承 HtmlAiParser 复用 AI 字段回填逻辑；差异在正文来源=PDF 文字层。
 */
@Component
public class PdfAiParser extends HtmlAiParser {

    @Resource
    private PdfTextNoticeParser pdfTextNoticeParser; // PDFBox 抽正文文本

    @Override public String getParserCode() { return "PDF_AI"; }
    @Override public String getParserName() { return "PDF + AI 结构化解析（PDFBox 抽正文→平台 AI，含行业相关判定）"; }
    @Override public String getDescription() { return "先 PDFBox 抽 PDF 文字层正文，再送平台 AI 结构化抽取与防腐保温行业判定（需 bidding.ai.enabled=true）；输出字段同 HTML_AI。"; }
    @Override public List<String> supportedPayloadTypes() { return List.of("PDF"); }

    @Override
    public NoticeParseResult parse(ParseInput input) {
        NoticeParseResult out = new NoticeParseResult();
        out.setParserCode(getParserCode());
        out.setParserName(getParserName());
        if (aiNoticeExtractor == null) { // 继承字段受保护可见需确认——改用访问器
            out.setError("AI 未启用：bidding.ai.enabled=true 且配置 ai-server 后可用（PDF_AI 解析器）");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        if (input == null || input.getFileBytes() == null || input.getFileBytes().length == 0) {
            out.setError("未提供 PDF 文件字节（PDF 载荷仅支持文件上传通道）");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        // 1) PDFBox 抽正文
        NoticeParseResult local = pdfTextNoticeParser.parse(input);
        String text = local.getContentText();
        if (!StringUtils.hasText(text)) {
            out.setError("PDF 无文字层（扫描件）：需 OCR/多模态 AI 另处理（当前 PDF_AI 要求文字层）");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        // 2) 送 AI
        try {
            AiNoticeExtractRequest req = new AiNoticeExtractRequest();
            req.setTitle(local.getTitle());
            req.setContentText(text);
            AiNoticeExtractResult result = aiNoticeExtractor.extract(req);
            applyAiFields(out, local, result);
            out.setContentType("PDF");
        } catch (Exception e) {
            out.setError("AI 解析失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        out.finalizeStatus();
        return out;
    }
}
