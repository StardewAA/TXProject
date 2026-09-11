package cn.iocoder.txgy.module.product.config.wms;

import cn.iocoder.txgy.module.product.service.wms.readonly.ProductWmsReadonlyQueryApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * 商城侧 WMS 只读查询配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProductWmsReadonlyProperties.class)
@EnableFeignClients(clients = ProductWmsReadonlyQueryApi.class)
public class ProductWmsReadonlyConfiguration {
}
