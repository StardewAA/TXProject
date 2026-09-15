package cn.iocoder.txgy.framework.search.core;

/**
 * 索引管理契约。实现时必须限制到指定租户和业务索引。
 * 不提供 FLUSHDB、FLUSHALL 或清除整个共享索引的接口。
 */
public interface RedisIndexManager {

    void ensureIndex();

    void upsert(SearchDocument document);

    void delete(long tenantId, String sourceId);

}
