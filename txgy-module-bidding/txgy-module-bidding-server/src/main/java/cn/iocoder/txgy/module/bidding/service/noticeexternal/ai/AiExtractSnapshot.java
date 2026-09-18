package cn.iocoder.txgy.module.bidding.service.noticeexternal.ai;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 抽取结果快照（返回给管理端展示）。
 */
@Data
public class AiExtractSnapshot {

    /** 公告 id */
    private Long id;

    /** AI 抽取状态：SUCCESS/FAILED */
    private String aiExtractionStatus;

    /** 结构化字段数（SUCCESS 时>0） */
    private Integer metaFieldCount;

    /** 全文转写长度（回写 extraction_text 后，字符数；无转写为 0） */
    private Integer fullTextLength;

    /** 防腐保温行业判定：BELONGS/NOT_BELONGS/UNCERTAIN（SUCCESS 且 AI 给出合法值时非空） */
    private String industryFlag;

    /** 行业判定置信度 0~1 */
    private BigDecimal industryConfidence;

}
