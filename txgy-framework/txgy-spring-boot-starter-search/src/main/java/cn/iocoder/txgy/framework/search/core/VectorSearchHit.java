package cn.iocoder.txgy.framework.search.core;

/** Redis 语义检索命中。distance 越小越相似，融合时使用 rank 而非原始距离。 */
public record VectorSearchHit(String sourceId, String version, double distance) {
}
