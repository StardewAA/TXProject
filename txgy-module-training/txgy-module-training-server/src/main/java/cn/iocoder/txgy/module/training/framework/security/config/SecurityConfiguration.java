package cn.iocoder.txgy.module.training.framework.security.config;

import cn.iocoder.txgy.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.txgy.module.training.enums.ApiConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * 云培训学院模块的 Security 配置。
 */
@Configuration("trainingSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("trainingAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(
                    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // Swagger 接口文档
                registry.requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-ui").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll();
                // Spring Boot Actuator
                registry.requestMatchers("/actuator").permitAll()
                        .requestMatchers("/actuator/**").permitAll();
                // Druid 监控
                registry.requestMatchers("/druid/**").permitAll();
                // RPC 服务
                registry.requestMatchers(ApiConstants.PREFIX + "/**").permitAll();
                // 资源中心：文档预览 / 视频播放直出（携带 @PermitAll 双保险，供 <iframe>/<video> 匿名访问）
                registry.requestMatchers("/training/document/preview").permitAll()
                        .requestMatchers("/training/video/play").permitAll();
                // 模块九：证书公开验真免登录公共接口（97，H5 独立页面）
                // 注意：框架对 controller.admin 包统一加了 /admin-api 前缀，匹配需带前缀
                registry.requestMatchers("/admin-api/training/analytics/cert-verify/public").permitAll()
                        .requestMatchers("/training/analytics/cert-verify/public").permitAll();
            }

        };
    }

}
