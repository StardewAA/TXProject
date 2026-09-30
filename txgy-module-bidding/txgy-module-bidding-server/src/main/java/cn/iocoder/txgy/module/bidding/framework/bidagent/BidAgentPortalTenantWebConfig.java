package cn.iocoder.txgy.module.bidding.framework.bidagent;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册门户标书工坊租户兜底拦截器。
 *
 * <p>覆盖标书 Agent 管理端接口（框架会通过 addPathPrefix 同时挂 /admin-api 前缀，两种形态都拦截）；
 * 契约端 /contract（Node 底座机对机，tenantId 由请求参数显式携带）不拦截。
 */
@Configuration
public class BidAgentPortalTenantWebConfig implements WebMvcConfigurer {

    @Resource
    private BidAgentPortalTenantInterceptor portalTenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(portalTenantInterceptor)
                .addPathPatterns("/admin-api/bidding/bid-agent/**", "/bidding/bid-agent/**")
                .excludePathPatterns("/admin-api/bidding/bid-agent/contract/**", "/bidding/bid-agent/contract/**");
    }
}
