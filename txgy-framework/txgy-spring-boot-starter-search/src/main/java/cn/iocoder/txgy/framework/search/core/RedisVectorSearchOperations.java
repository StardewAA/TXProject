package cn.iocoder.txgy.framework.search.core;

import java.util.List;

/** Redis 8 / Redis Stack 向量搜索能力。collection 必须是服务端定义的业务集合。 */
public interface RedisVectorSearchOperations {

    void ensureIndex(String collection);

    void upsert(String collection, RedisVectorDocument document);

    List<VectorSearchHit> search(String collection, long tenantId, float[] queryVector, int topK);

    void delete(String collection, long tenantId, String sourceId);

}
