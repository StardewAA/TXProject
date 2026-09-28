package cn.iocoder.txgy.framework.search.core;

/**
 * Search + JSON 公共契约，未提供实现或注册 Spring Bean。
 * 索引按已批准 schema 创建；只管理本组件拥有的业务/租户键。
 * 实现应原子比较 revision；删除后需保留防止旧事件恢复数据的版本依据。
 */
public interface JsonSearchOperations {
    void ensureIndex(String collection);
    SearchResult search(JsonSearchRequest request);
    void upsert(JsonSearchDocument document);
    void remove(String collection, long tenantId, String sourceId, long revision);
}
