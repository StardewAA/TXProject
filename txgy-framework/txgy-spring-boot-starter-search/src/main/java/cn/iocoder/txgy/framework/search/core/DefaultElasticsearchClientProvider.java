package cn.iocoder.txgy.framework.search.core;

import cn.iocoder.txgy.framework.search.config.ElasticsearchSearchProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;

import java.io.IOException;

/** 默认 Elasticsearch 8.x 客户端持有者；构造时不发送网络请求。 */
public final class DefaultElasticsearchClientProvider implements ElasticsearchClientProvider {

    private final RestClientTransport transport;
    private final ElasticsearchClient client;

    public DefaultElasticsearchClientProvider(ElasticsearchSearchProperties properties) {
        HttpHost[] hosts = properties.getUris().stream().map(HttpHost::create).toArray(HttpHost[]::new);
        var builder = RestClient.builder(hosts)
                .setRequestConfigCallback(request -> request
                        .setConnectTimeout((int) properties.getConnectTimeout().toMillis())
                        .setSocketTimeout((int) properties.getSocketTimeout().toMillis()));
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));
            builder.setHttpClientConfigCallback(http -> http.setDefaultCredentialsProvider(credentials));
        }
        this.transport = new RestClientTransport(builder.build(), new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
    }

    @Override
    public ElasticsearchClient client() {
        return client;
    }

    @Override
    public void close() {
        try {
            transport.close();
        } catch (IOException ex) {
            throw new IllegalStateException("关闭 Elasticsearch 客户端失败", ex);
        }
    }

}
