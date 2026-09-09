package cn.iocoder.txgy.module.bidding.framework.web.config;

import cn.iocoder.txgy.framework.swagger.config.TxgySwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * bidding 模块的 Web 配置。
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
