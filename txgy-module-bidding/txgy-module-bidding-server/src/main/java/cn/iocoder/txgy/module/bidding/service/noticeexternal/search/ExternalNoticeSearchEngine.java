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
     * 关键词匹配公告 id（中文按「字面包含」精确匹配，详见 {@link ExternalNoticeIndexMapping}）。
     *
     * <p>检索异常返回空集并不抛错，避免列表页被打挂。
     */
    MatchResult matchNoticeIds(String keyword, int limit);

    /**
     * 检索结果：命中 id + 真实命中总数。
     *
     * <p><b>为什么要把 total 单独带出来（契约 B5）</b>：旧实现把"取回上限"（200）当作 total 下发，
     * 前端分页器永远显示"共 200 条"、翻页翻不出新内容。真实 total 由 ES {@code hits.total.value}
     * 给出，调用方据此判断是否发生了截断（{@code total > ids.size()}）。
     *
     * @param ids   命中公告 id（已按相关度排序，最多 limit 条）
     * @param total ES 真实命中总数（可能大于 ids.size()，即被 limit 截断）
     */
    record MatchResult(List<Long> ids, long total) {

        /** 空结果（检索异常/无输入时的安全返回值）。 */
        public static MatchResult empty() {
            return new MatchResult(List.of(), 0L);
        }

        /** 是否发生了截断（真实命中数超过取回上限）。 */
        public boolean truncated() {
            return total > ids.size();
        }
    }

}
