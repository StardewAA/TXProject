package cn.iocoder.txgy.framework.search.core;

import java.util.Objects;

/** Redis 中的最小向量记录；法规全文与完整元数据不得放在这里。 */
public record RedisVectorDocument(long tenantId, String sourceId, String version, float[] vector) {

    public RedisVectorDocument {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 必须大于 0");
        }
        Objects.requireNonNull(sourceId, "sourceId 不能为空");
        Objects.requireNonNull(version, "version 不能为空");
        Objects.requireNonNull(vector, "vector 不能为空");
        if (sourceId.isBlank() || version.isBlank() || vector.length == 0) {
            throw new IllegalArgumentException("sourceId、version 和 vector 不能为空");
        }
        vector = vector.clone();
    }

    @Override
    public float[] vector() {
        return vector.clone();
    }

}
