package cn.iocoder.txgy.module.bidding.framework.web.config;

import cn.iocoder.txgy.framework.swagger.config.TxgySwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * bidding 模块的 Web 配置（Swagger 分组，供网关/聚合层发现）。
 *
 * <p>规范出处：docs/招投标模块开发规范.md §4 分层与接口风格；设计遵循/针对：按管理后台域拆分为
 * 证照/资质两组 OpenAPI 分组，便于演示与前后端按域联调。
 */
@Configuration(proxyBeanMethods = false)
public class BiddingWebConfiguration {

    /**
     * 给 bidding 单独开一个 Swagger 分组，接口文档好找。
     */
    @Bean
    public GroupedOpenApi biddingGroupedOpenApi() {
        return TxgySwaggerAutoConfiguration.buildGroupedOpenApi("bidding");
    }

}
