package cn.iocoder.txgy.framework.search.core;

import java.util.Map;

/**
 * 内部检索请求。tenantId 必须由服务端认证上下文提供，不能直接接受前端传值。
 * filters 的字段和值仍须由具体查询构造器执行白名单校验与转义。
 */
public record SearchRequest(long tenantId, String keyword, int offset, int limit,
                            Map<String, String> filters) {

    public SearchRequest {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 必须来自有效的租户上下文");
        }
        if (keyword == null || keyword.isBlank() || keyword.length() > 200) {
            throw new IllegalArgumentException("检索词不能为空且不能超过 200 字符");
        }
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("offset 不能为负数，limit 必须在 1 到 100 之间");
        }
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }

}
