package cn.iocoder.txgy.module.bidding.service.notice.search;

import java.util.List;

/**
 * 公告全文检索引擎抽象（规范 4.6：全文走 ES，业务表不做全文 LIKE）。
 *
 * <p>实现：{@link EsNoticeSearchEngine}（配置 {@code bidding.es.enabled=true} 启用，
 * 原生 HTTP 访问 ES，索引独立命名空间）；未启用时用 {@link DevDisabledSearchEngine}，
 * 关键词检索直接报 {@code NOTICE_SEARCH_UNAVAILABLE}，杜绝降级成业务表 LIKE。
 */
public interface NoticeSearchEngine {

    /** 引擎名（日志/诊断用） */
    String name();

    /** 索引/更新一条公告文档（含正文全文与招标项目基础信息） */
    void index(NoticeEsDoc doc);

    /** 删除公告文档 */
    void remove(Long noticeId);

    /**
     * 全文关键词命中公告 id（按发布时间倒序，上限 limit）。
     *
     * @param keyword 关键词
     * @param limit   最大返回条数
     * @return 命中的公告 id；未启用引擎时抛 NOTICE_SEARCH_UNAVAILABLE
     */
    List<Long> matchIds(String keyword, int limit);
}
