package cn.iocoder.txgy.framework.search.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Redis 检索组件自动配置入口。
 * 当前只提供配置绑定与接口契约，尚未注册 RedisSearchClient 实现。
 */
@AutoConfiguration
@EnableConfigurationProperties(RedisSearchProperties.class)
public class RedisSearchAutoConfiguration {
}
