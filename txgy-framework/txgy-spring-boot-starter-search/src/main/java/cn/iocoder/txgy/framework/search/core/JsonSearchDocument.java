package cn.iocoder.txgy.framework.search.core;

import java.util.Map;
import java.util.Set;

/**
 * 可重建的 JSON 索引投影，不是原始资料。
 * fields 保留数字、数组等 JSON 类型；生产者必须按业务 schema 校验并提供不可变嵌套值。
 * tenant_id/source_id/revision 由实现根据外层可信字段写入，禁止 fields 覆盖。
 * 此类型尚无序列化器或 Redis 写入实现，Map.copyOf 仅保护顶层映射。
 */
public record JsonSearchDocument(String collection, long tenantId, String sourceId, long revision,
                                 Map<String, Object> fields) {
    public JsonSearchDocument {
        if (collection == null || !collection.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("collection 格式错误");
        }
        if (tenantId <= 0 || sourceId == null || sourceId.isBlank() || revision < 1) {
            throw new IllegalArgumentException("租户、来源编号、修订号必须有效");
        }
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        if (fields.keySet().stream().anyMatch(Set.of("tenant_id", "source_id", "revision")::contains)) {
            throw new IllegalArgumentException("JSON 业务字段不能覆盖系统身份字段");
        }
    }
}
