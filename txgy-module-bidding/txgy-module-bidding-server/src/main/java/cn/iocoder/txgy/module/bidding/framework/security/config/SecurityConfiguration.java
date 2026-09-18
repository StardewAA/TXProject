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
 *
 * <p>规范出处：docs/招投标模块开发规范.md §4.4 权限控制（管理员后台接口全部走登录态 +
 * @PreAuthorize 权限码；写接口叠加 @Idempotent，由 BiddingArchitectureRulesTest 门禁）。
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
                // 公共服务大厅：公告/公示公开只读接口（跨租户读 + 公开级别白名单在 Service 层执行）
                registry.requestMatchers("/bidding/notice/public/**").permitAll();
                // 外部公告大厅：公开分页/详情/外链中转（净化与域名白名单在 Service 层执行）
                registry.requestMatchers("/bidding/notice-external/**").permitAll();
                // 给其他模块调的 RPC 接口
                registry.requestMatchers(ApiConstants.PREFIX + "/**").permitAll();
            }

        };
    }

}
