package cn.iocoder.txgy.module.bidding.service.noticeexternal.ai;

import lombok.Data;

/**
 * AI 抽取请求：一条公告的可抽取素材。
 */
@Data
public class AiNoticeExtractRequest {

    /** 公告 id（上下文/日志用） */
    private Long noticeId;

    /** 标题 */
    private String title;

    /** 原站公告类型原文（如 招标公告/中标候选人公示） */
    private String noticeTypeRaw;

    /** 本地抽取文本（PDF 文字层/HTML 去标签；扫描件为空） */
    private String contentText;

    /** PDF 附件可访问 URL（扫描件走多模态/OCR 时用；FileApi 公网 URL 由接入方注入，可为空） */
    private String pdfUrl;

}
