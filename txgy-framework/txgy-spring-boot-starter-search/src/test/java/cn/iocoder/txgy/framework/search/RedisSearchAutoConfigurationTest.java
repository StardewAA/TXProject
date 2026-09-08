package cn.iocoder.txgy.framework.search;

import cn.iocoder.txgy.framework.search.config.RedisSearchAutoConfiguration;
import cn.iocoder.txgy.framework.search.config.RedisSearchProperties;
import cn.iocoder.txgy.framework.search.core.RedisSearchClient;
import cn.iocoder.txgy.framework.search.core.RedisVectorSearchOperations;
import cn.iocoder.txgy.framework.search.core.JsonSearchOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSearchAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisSearchAutoConfiguration.class));

    @Test
    void defaultsDoNotCreateAClientOrConnectToRedis() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RedisSearchProperties.class);
            assertThat(context).doesNotHaveBean(RedisSearchClient.class);
            assertThat(context).doesNotHaveBean(JsonSearchOperations.class);
            assertThat(context).doesNotHaveBean(RedisVectorSearchOperations.class);
            RedisSearchProperties properties = context.getBean(RedisSearchProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getPort()).isEqualTo(6380);
            assertThat(properties.getIndexPrefix()).isEqualTo("txgy:cms");
            assertThat(properties.getVectorDimensions()).isEqualTo(768);
        });
    }

    @Test
    void bindsIndependentSearchConnectionProperties() {
        runner.withPropertyValues("txgy.search.redis.host=search-redis",
                        "txgy.search.redis.port=6381", "txgy.search.redis.index-prefix=txgy:cms:test")
                .run(context -> {
                    RedisSearchProperties properties = context.getBean(RedisSearchProperties.class);
                    assertThat(properties.getHost()).isEqualTo("search-redis");
                    assertThat(properties.getPort()).isEqualTo(6381);
                    assertThat(properties.getIndexPrefix()).isEqualTo("txgy:cms:test");
                });
    }

    @Test
    void createsVectorOperationsOnlyWhenEnabled() {
        runner.withPropertyValues("txgy.search.redis.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(RedisVectorSearchOperations.class));
    }

}
