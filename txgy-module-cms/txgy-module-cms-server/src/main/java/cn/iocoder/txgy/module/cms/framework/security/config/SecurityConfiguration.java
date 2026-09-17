package cn.iocoder.txgy.module.cms.framework.security.config;

import cn.iocoder.txgy.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * CMS 模块的 Security 配置。
 */
@Configuration(proxyBeanMethods = false, value = "cmsSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("cmsAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(
                    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-ui").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/druid/**").permitAll()
                        .requestMatchers("/actuator").permitAll()
                        .requestMatchers("/actuator/**").permitAll();
            }

        };
    }

}
