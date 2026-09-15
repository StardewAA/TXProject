package cn.iocoder.txgy.module.bidding.service.noticeexternal.search;
// 规范依据：设计文档 v0.6 §7/§15.3、05 §2（外部公告全文检索走 ES 索引 bidding-notice-external，业务表不开放 LIKE）；
// 《招投标模块开发规范》§4.6

import java.util.List;

/**
 * 外部公告关键词检索引擎。
 *
 * <p>实现：{@link EsExternalNoticeSearchEngine}（配置 {@code bidding.es.enabled=true} 启用，
 * 原生 HTTP 检索 bidding-notice-external 索引，与索引通知器 {@code EsExternalNoticeIndexNotifier} 同索引）；
 * 未启用时用 {@link DevDisabledExternalNoticeSearchEngine}，关键词检索直接抛错，
 * 杜绝降级成业务表 LIKE（规范 4.6）。
 *
 * <p>命中只回公告 id 集合，列表/详情仍由主表承载（含 ACTIVE/SOCIAL 过滤与分页兜底）。
 */
public interface ExternalNoticeSearchEngine {

    /** 引擎标识（es / dev） */
    String name();

    /**
     * 关键词匹配公告 id（multi_match：标题加权 > 项目名 > 项目编号/正文）。
     *
     * @return 公告 id 集合（主表过滤后展示；检索异常返回空集并不抛错，避免列表页被打挂）
     */
    List<Long> matchNoticeIds(String keyword, int limit);

}
