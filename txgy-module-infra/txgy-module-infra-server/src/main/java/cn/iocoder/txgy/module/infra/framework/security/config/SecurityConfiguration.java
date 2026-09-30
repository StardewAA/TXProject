package cn.iocoder.txgy.module.infra.framework.security.config;

import cn.iocoder.txgy.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.txgy.module.infra.enums.ApiConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Infra 模块的 Security 配置
 */
@Configuration(proxyBeanMethods = false, value = "infraSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("infraAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // Swagger 接口文档
                registry.requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-ui").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll();
                // Spring Boot Actuator 的安全配置
                registry.requestMatchers("/actuator").permitAll()
                        .requestMatchers("/actuator/**").permitAll();
                // Druid 监控
                registry.requestMatchers("/druid/**").permitAll();
                // 文件读取：URL 层放行（公开资源需匿名可读），
                // 公开/私有的真实判定在 FileController / AppFileController 内按文件记录 + 登录身份 + 归属/参与方完成
                // 后台通道：/admin-api（后台用户）
                registry.requestMatchers(buildAdminApi("/infra/file/*/get/**")).permitAll();
                registry.requestMatchers(buildAdminApi("/infra/file/*/preview/**")).permitAll();
                // 用户端通道：/app-api（会员；会员 token 无法通过 /admin-api 的 userType 校验，故必须提供本通道）
                registry.requestMatchers(buildAppApi("/infra/file/*/get/**")).permitAll();
                registry.requestMatchers(buildAppApi("/infra/file/*/preview/**")).permitAll();

                // TODO 芋艿：这个每个项目都需要重复配置，得捉摸有没通用的方案
                // RPC 服务的安全配置
                registry.requestMatchers(ApiConstants.PREFIX + "/**").permitAll();
            }

        };
    }

}
