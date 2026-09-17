package cn.iocoder.txgy.framework.search.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

/**
 * 项目锁定的 Elasticsearch 8.x Java Client 入口。
 * 使用包装器可避免与 Spring Boot 自身的 Elasticsearch 自动配置互相覆盖。
 */
public interface ElasticsearchClientProvider extends AutoCloseable {

    ElasticsearchClient client();

    @Override
    void close();

}
