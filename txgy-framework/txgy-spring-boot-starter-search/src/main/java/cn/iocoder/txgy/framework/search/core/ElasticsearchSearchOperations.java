package cn.iocoder.txgy.framework.search.core;

/**
 * Elasticsearch 主检索契约。未来实现使用官方 Elasticsearch Java Client 8.x。
 * 索引、查询、写入和删除仍须执行租户、发布状态、版本及权限约束。
 */
public interface ElasticsearchSearchOperations extends JsonSearchOperations {
}
