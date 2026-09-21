package cn.iocoder.txgy.module.ai.framework.ai.config;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * AI 服务的 HTTP 客户端兼容配置。
 */
@Configuration(proxyBeanMethods = false)
public class AiRestClientConfiguration {

    /**
     * DeepSeek 可能返回 Brotli（br）压缩内容，但当前 Spring AI 客户端无法正确解压，
     * 最终会将完整 JSON 误判为截断响应。显式声明支持的压缩格式，避免协商到 br。
     */
    @Bean
    public RestClientCustomizer aiRestClientCompressionCustomizer() {
        return builder -> builder.defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");
    }

}
