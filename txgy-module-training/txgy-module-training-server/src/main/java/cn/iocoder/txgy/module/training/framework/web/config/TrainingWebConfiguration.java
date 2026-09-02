package cn.iocoder.txgy.module.training.framework.web.config;

import cn.iocoder.txgy.framework.swagger.config.TxgySwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 云培训学院 Web 配置。 */
@Configuration(proxyBeanMethods = false)
public class TrainingWebConfiguration {

    @Bean
    public GroupedOpenApi trainingGroupedOpenApi() {
        return TxgySwaggerAutoConfiguration.buildGroupedOpenApi("training");
    }
}
