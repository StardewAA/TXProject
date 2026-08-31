package cn.iocoder.txgy.module.infra.framework.file.config;

import cn.iocoder.txgy.module.infra.framework.file.core.client.FileClientFactory;
import cn.iocoder.txgy.module.infra.framework.file.core.client.FileClientFactoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件配置类
 *
 * @author OPENLAB BS
 */
@Configuration(proxyBeanMethods = false)
public class TxgyFileAutoConfiguration {

    @Bean
    public FileClientFactory fileClientFactory() {
        return new FileClientFactoryImpl();
    }

}
