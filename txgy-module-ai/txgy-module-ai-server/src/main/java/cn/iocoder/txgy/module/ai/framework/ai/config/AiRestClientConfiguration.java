package cn.iocoder.txgy.module.ai.framework.ai.config;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * AI 服务的 HTTP 客户端兼容配置。
 */
@Configuration(proxyBeanMethods = false)
public class AiRestClientConfiguration {

    /**
     * DeepSeek 可能返回 Brotli（br）压缩内容，但当前 Spring AI 客户端无法正确解压，
     * 最终会将完整 JSON 误判为截断响应。显式声明支持的压缩格式，避免协商到 br；
     * 同时限制建连和读取时长，避免模型提供方异常时长期占用商城请求线程。
     */
    @Bean
    public RestClientCustomizer aiRestClientCustomizer(
            @Value("${txgy.ai.provider.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${txgy.ai.provider.read-timeout-ms:15000}") int readTimeoutMs) {
        if (connectTimeoutMs < 100 || readTimeoutMs < 100 || readTimeoutMs < connectTimeoutMs) {
            throw new IllegalArgumentException("AI provider timeout configuration is invalid");
        }
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeoutMs);
            factory.setReadTimeout(readTimeoutMs);
            builder.requestFactory(factory)
                    .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");
        };
    }

}
