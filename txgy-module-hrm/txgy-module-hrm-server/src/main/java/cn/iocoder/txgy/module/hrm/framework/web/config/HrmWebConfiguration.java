package cn.iocoder.txgy.module.hrm.framework.web.config;

import cn.iocoder.txgy.framework.swagger.config.TxgySwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * hrm 模块的 web 组件的 Configuration
 *
 * @author OPENLAB BS
 */
@Configuration(proxyBeanMethods = false)
public class HrmWebConfiguration {

    /**
     * hrm 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi hrmGroupedOpenApi() {
        return TxgySwaggerAutoConfiguration.buildGroupedOpenApi("hrm");
    }

}
