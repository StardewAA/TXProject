package cn.iocoder.txgy.framework.search.config;

import cn.iocoder.txgy.framework.search.core.DefaultElasticsearchClientProvider;
import cn.iocoder.txgy.framework.search.core.ElasticsearchClientProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Elasticsearch 与 Lettuce 缓存公共入口。
 */
@AutoConfiguration
@EnableConfigurationProperties({ElasticsearchSearchProperties.class, SearchCacheProperties.class})
public class SearchBackendAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ElasticsearchClientProvider.class)
    @ConditionalOnProperty(prefix = "txgy.search.elasticsearch", name = "enabled", havingValue = "true")
    public ElasticsearchClientProvider elasticsearchClientProvider(ElasticsearchSearchProperties properties) {
        return new DefaultElasticsearchClientProvider(properties);
    }

}
