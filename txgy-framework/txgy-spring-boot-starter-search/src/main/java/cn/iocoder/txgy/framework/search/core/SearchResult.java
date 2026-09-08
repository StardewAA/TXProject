package cn.iocoder.txgy.framework.search.core;

import java.util.List;

/**
 * 通用检索结果。score 是相关性分数，不代表法律结论的正确率。
 * 业务层返回用户前须再次核对版本、发布状态和访问权限。
 */
public record SearchResult(long total, List<Hit> hits) {

    public SearchResult {
        if (total < 0) {
            throw new IllegalArgumentException("total 不能为负数");
        }
        hits = List.copyOf(hits);
    }

    public record Hit(String sourceId, String version, String title, String excerpt, double score) {
    }

}
