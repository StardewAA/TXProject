package cn.iocoder.txgy.module.bidding.service.noticeexternal.ai;

/**
 * 外部公告 AI 结构化抽取服务。
 *
 * <p>职责：单条公告触发 AI 抽取（管理端手动，幂等=重复触发覆盖重跑）——
 * 取本地抽取文本/PDF 附件素材 → 调 {@link AiNoticeExtractor} → 结构化字段落
 * ai_meta_json + ai_extraction_status=SUCCESS；全文转写非空回写 extraction_text
 * （视同抽取成功，复用既有 ES contentText 索引源），随后重放 ES 索引使新文本可检索。
 */
public interface NoticeExternalAiExtractService {

    /**
     * 对指定外部公告执行 AI 结构化抽取。
     *
     * @param id 外部公告 id
     * @return 抽取结果快照
     */
    AiExtractSnapshot extract(Long id);

}
