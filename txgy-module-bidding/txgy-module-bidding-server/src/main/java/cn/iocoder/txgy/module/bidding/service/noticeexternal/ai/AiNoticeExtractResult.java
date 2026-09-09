package cn.iocoder.txgy.module.bidding.service.noticeexternal.ai;

import lombok.Data;

import java.util.Map;

/**
 * AI 抽取结果。
 */
@Data
public class AiNoticeExtractResult {

    /** 结构化字段（JSON-ready；noticeFullText 约定承载全文转写，非空时调用方回写 extraction_text） */
    private Map<String, Object> fields;

    /** 全文转写（扫描件 OCR/转写产物，可为空） */
    private String fullText;

    public AiNoticeExtractResult() {
    }

    public AiNoticeExtractResult(Map<String, Object> fields, String fullText) {
        this.fields = fields;
        this.fullText = fullText;
    }

}
