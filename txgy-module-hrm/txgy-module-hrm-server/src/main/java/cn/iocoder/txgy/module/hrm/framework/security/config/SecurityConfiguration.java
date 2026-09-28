package cn.iocoder.txgy.module.hrm.framework.security.config;

import cn.iocoder.txgy.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.txgy.module.hrm.enums.ApiConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Hrm 模块的 Security 配置
 */
@Configuration("hrmSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("hrmAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // Swagger 接口文档
                registry.requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-ui").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll();
                // Spring Boot Actuator：Q-008 / I-005 收紧——
                // 仅保留健康检查（容器/网关健康探测依赖它）为匿名可访问，
                // 其余 /actuator/**（beans、env、heapdump、loggers 等）必须登录后访问。
                registry.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator", "/actuator/**").authenticated();
                // Druid 监控台：同样不再匿名开放（登录后可访问，Druid 自身另有登录账号可叠加）
                registry.requestMatchers("/druid/**").authenticated();
                // RPC 服务的安全配置
                registry.requestMatchers(ApiConstants.PREFIX + "/**").permitAll();
            }

        };
    }

}
