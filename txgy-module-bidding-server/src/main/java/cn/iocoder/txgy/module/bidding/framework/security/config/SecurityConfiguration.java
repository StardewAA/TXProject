package cn.iocoder.txgy.module.bidding.framework.security.config;

import cn.iocoder.txgy.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.txgy.module.bidding.enums.ApiConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * bidding 模块的 Security 配置。
 * 白名单就几类：Swagger、监控、Druid、RPC 接口，其余都要登录鉴权。
 */
@Configuration("biddingSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("biddingAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // Swagger 接口文档
                registry.requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-ui").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll();
                // Spring Boot Actuator 监控端点
                registry.requestMatchers("/actuator").permitAll()
                        .requestMatchers("/actuator/**").permitAll();
                // Druid 监控页面
                registry.requestMatchers("/druid/**").permitAll();
                // 给其他模块调的 RPC 接口
                registry.requestMatchers(ApiConstants.PREFIX + "/**").permitAll();
            }

        };
    }

}
