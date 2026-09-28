package cn.iocoder.txgy.framework.search;

import cn.iocoder.txgy.framework.search.config.ElasticsearchSearchProperties;
import cn.iocoder.txgy.framework.search.config.SearchBackendAutoConfiguration;
import cn.iocoder.txgy.framework.search.config.SearchCacheProperties;
import cn.iocoder.txgy.framework.search.core.ElasticsearchSearchOperations;
import cn.iocoder.txgy.framework.search.core.ElasticsearchClientProvider;
import cn.iocoder.txgy.framework.search.core.SearchCacheOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SearchBackendAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SearchBackendAutoConfiguration.class));

    @Test
    void defaultsRemainDisabledAndDoNotConnect() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ElasticsearchSearchProperties.class);
            assertThat(context).hasSingleBean(SearchCacheProperties.class);
            assertThat(context).doesNotHaveBean(ElasticsearchSearchOperations.class);
            assertThat(context).doesNotHaveBean(ElasticsearchClientProvider.class);
            assertThat(context).doesNotHaveBean(SearchCacheOperations.class);
            assertThat(context.getBean(ElasticsearchSearchProperties.class).isEnabled()).isFalse();
            assertThat(context.getBean(SearchCacheProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void bindsElasticsearchAndCacheSettings() {
        runner.withPropertyValues(
                "txgy.search.elasticsearch.uris[0]=https://es.example.test:9200",
                "txgy.search.elasticsearch.index-prefix=cms-test",
                "txgy.search.elasticsearch.bulk-size=200",
                "txgy.search.cache.enabled=true",
                "txgy.search.cache.ttl=2m")
                .run(context -> {
                    ElasticsearchSearchProperties search = context.getBean(ElasticsearchSearchProperties.class);
                    SearchCacheProperties cache = context.getBean(SearchCacheProperties.class);
                    assertThat(search.getUris()).containsExactly("https://es.example.test:9200");
                    assertThat(search.getIndexPrefix()).isEqualTo("cms-test");
                    assertThat(search.getBulkSize()).isEqualTo(200);
                    assertThat(cache.isEnabled()).isTrue();
                    assertThat(cache.getTtl()).isEqualTo(java.time.Duration.ofMinutes(2));
                });
    }

    @Test
    void createsIsolatedElasticsearch8ClientOnlyWhenEnabled() {
        runner.withPropertyValues("txgy.search.elasticsearch.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ElasticsearchClientProvider.class));
    }
}
