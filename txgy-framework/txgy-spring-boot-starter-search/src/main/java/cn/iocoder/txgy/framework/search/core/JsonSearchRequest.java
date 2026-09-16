package cn.iocoder.txgy.framework.search.core;

import java.math.BigDecimal;
import java.util.List;

/**
 * 多资料库内部查询契约。collection 由业务接口固定选择，tenantId 来自认证上下文。
 * 此模型不是前端请求。实现必须额外做字段白名单、值转义和强制权限过滤。
 * 空关键词表示分页浏览；不得绕过已发布/租户过滤，也不直接接收 Redis 表达式。
 */
public record JsonSearchRequest(String collection, long tenantId, String keyword, int offset, int limit,
                                List<TagFilter> tags, List<NumericRange> ranges) {
    public JsonSearchRequest {
        if (collection == null || !collection.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("collection 必须是服务端白名单内的业务标识");
        }
        if (tenantId <= 0 || offset < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("租户必须有效，offset >= 0，limit 在 1 到 100 之间");
        }
        if (keyword != null && keyword.length() > 200) {
            throw new IllegalArgumentException("关键词不能超过 200 字符");
        }
        keyword = keyword == null ? "" : keyword;
        tags = tags == null ? List.of() : List.copyOf(tags);
        ranges = ranges == null ? List.of() : List.copyOf(ranges);
    }

    private static void validateField(String field) {
        if (field == null || !field.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("过滤字段格式错误");
        }
        if (List.of("tenant_id", "publish_status", "visibility").contains(field)) {
            throw new IllegalArgumentException("权限字段必须由服务端强制添加，不能由查询条件覆盖");
        }
    }

    /** 同一字段 values 为 OR，不同过滤条件之间为 AND。 */
    public record TagFilter(String field, List<String> values) {
        public TagFilter {
            validateField(field);
            if (values == null || values.isEmpty() || values.size() > 50) {
                throw new IllegalArgumentException("标签值数量必须在 1 到 50 之间");
            }
            values = List.copyOf(values);
            if (values.stream().anyMatch(value -> value.isBlank() || value.length() > 200)) {
                throw new IllegalArgumentException("标签值不能为空或超过 200 字符");
            }
        }
    }

    /** 闭区间；null 端点表示无界。一个条件必须至少提供一个端点。 */
    public record NumericRange(String field, BigDecimal minimum, BigDecimal maximum) {
        public NumericRange {
            validateField(field);
            if (minimum == null && maximum == null) {
                throw new IllegalArgumentException("数值范围至少需要一个端点");
            }
            if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
                throw new IllegalArgumentException("数值范围下限不能高于上限");
            }
        }
    }
}
