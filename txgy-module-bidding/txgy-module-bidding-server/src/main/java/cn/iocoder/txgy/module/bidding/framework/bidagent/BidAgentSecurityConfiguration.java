package cn.iocoder.txgy.module.bidding.framework.bidagent;

import cn.iocoder.txgy.module.bidding.framework.bidagent.BidAgentProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册标书 Agent 鉴权过滤器。order 设 -200 使其先于 Spring Security（-100）执行，
 * 在无登录 token 的机对机请求到达鉴权链前，先完成 X-Agent-Key 校验。
 */
@Configuration
public class BidAgentSecurityConfiguration {

    /**
     * 契约端 URL 前缀。2026-09 并入 bidding 模块后，契约路径由 /admin-api/bid/agent/** 调整为
     * /admin-api/bidding/bid-agent/contract/**，过滤器注册路径必须同步，否则 X-Agent-Key 校验会被绕过。
     */
    public static final String CONTRACT_URL_PATTERN = "/admin-api/bidding/bid-agent/contract/*";

    @Bean
    public FilterRegistrationBean<BidAgentKeyFilter> bidAgentKeyFilter(BidAgentProperties properties) {
        FilterRegistrationBean<BidAgentKeyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BidAgentKeyFilter(properties.getAgentKey()));
        registration.addUrlPatterns(CONTRACT_URL_PATTERN);
        registration.setName("bidAgentKeyFilter");
        registration.setOrder(-200);
        return registration;
    }
}
