package cn.iocoder.txgy.framework.search.config;

import cn.iocoder.txgy.framework.search.core.LettuceRedisVectorSearchOperations;
import cn.iocoder.txgy.framework.search.core.RedisVectorSearchOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis 向量检索组件自动配置入口。
 */
@AutoConfiguration
@EnableConfigurationProperties(RedisSearchProperties.class)
public class RedisSearchAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(RedisVectorSearchOperations.class)
    @ConditionalOnProperty(prefix = "txgy.search.redis", name = "enabled", havingValue = "true")
    public RedisVectorSearchOperations redisVectorSearchOperations(RedisSearchProperties properties) {
        return new LettuceRedisVectorSearchOperations(properties);
    }

}
