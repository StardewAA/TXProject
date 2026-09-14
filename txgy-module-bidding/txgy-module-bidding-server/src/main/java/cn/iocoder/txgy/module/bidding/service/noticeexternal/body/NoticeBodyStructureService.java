package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;

/**
 * 正文结构化服务（联调清单 R12② 策略链：常规 HTML 解析 → 不足时 AI 兜底排版）。
 *
 * <p>产物：body_blocks_json（结构化块数组）+ body_parser_code（HTML_STRUCTURE/AI_FALLBACK），
 * detail 下发后前端块渲染优先，contentHtmlSafe 兜底；content_html 原文留档不动。
 */
public interface NoticeBodyStructureService {

    /**
     * 人工触发正文结构化。
     *
     * @param id    公告 id
     * @param useAi true=强制 AI 兜底排版（AI_FALLBACK）；false=常规 HTML 结构解析（HTML_STRUCTURE）
     * @return 结构化快照
     */
    NoticeBodyStructureSnapshot structure(Long id, boolean useAi);

}
