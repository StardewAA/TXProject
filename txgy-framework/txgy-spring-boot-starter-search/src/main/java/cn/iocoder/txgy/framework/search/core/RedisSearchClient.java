package cn.iocoder.txgy.framework.search.core;

/** 检索客户端契约。尚无实现，不应将骨架视为已完成的检索服务。 */
public interface RedisSearchClient {

    SearchResult search(SearchRequest request);

}
