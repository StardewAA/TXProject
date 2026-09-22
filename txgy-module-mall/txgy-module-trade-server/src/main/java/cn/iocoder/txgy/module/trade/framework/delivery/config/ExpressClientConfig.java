package cn.iocoder.txgy.module.trade.framework.delivery.config;

import cn.iocoder.txgy.module.trade.framework.delivery.core.client.ExpressClient;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.ExpressClientFactory;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.impl.ExpressClientFactoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * 快递客户端端配置类：
 *
 * 1. 快递客户端工厂 {@link ExpressClientFactory}
 * 2. 默认的快递客户端实现 {@link ExpressClient}
 *
 * @author jason
 */
@Configuration(proxyBeanMethods = false)
public class ExpressClientConfig {

    @Bean
    public ExpressClientFactory expressClientFactory(TradeExpressProperties tradeExpressProperties,
                                                     RestTemplate restTemplate) {
        // Dedicated client: do not mutate the shared RPC/HTTP bean or permit unbounded waits.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);
        return new ExpressClientFactoryImpl(tradeExpressProperties, new RestTemplate(requestFactory));
    }

    @Bean
    public ExpressClient defaultExpressClient(ExpressClientFactory expressClientFactory) {
        return expressClientFactory.getDefaultExpressClient();
    }

}
