package cn.iocoder.txgy.module.bidding.service.noticeexternal.ai;
// 规范依据：设计文档 v0.6 §七（AI 解析/分类由 AI 侧能力承载）；P2：AI 结构化抽取接入（待渠道 key 联调）

/**
 * AI 公告结构化抽取器（PDF/HTML 公告 → 结构化字段 + 可选全文转写）。
 *
 * <p>职责边界：只做"文本/文件 → 结构化 JSON"，不感知落库/状态机；
 * 状态推进与错误码由 {@link NoticeExternalAiExtractService} 负责。
 * 实现：{@link AiChatNoticeExtractor}（对接 AI 模块 chat 会话 REST，bidding.ai.enabled=true 时生效）。
 */
public interface AiNoticeExtractor {

    /** 引擎标识（ai-chat 等） */
    String name();

    /**
     * 执行抽取。
     *
     * @return 抽取结果（fields 必非空；fullText 可选=扫描件全文转写）；调用异常由调用方捕获归类
     */
    AiNoticeExtractResult extract(AiNoticeExtractRequest request);

}
