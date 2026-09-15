package cn.iocoder.txgy.framework.search.core;

import java.util.Map;
import java.util.Objects;

/** 可重建的索引副本；原始资料仍由业务数据库保存。 */
public record SearchDocument(long tenantId, String sourceId, String version,
                             String title, String content, Map<String, String> metadata) {

    public SearchDocument {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 必须来自有效的租户上下文");
        }
        Objects.requireNonNull(sourceId, "sourceId 不能为空");
        Objects.requireNonNull(version, "version 不能为空");
        Objects.requireNonNull(content, "content 不能为空");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

}
