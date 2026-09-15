package cn.iocoder.txgy.module.crm.config.wms;

import cn.iocoder.txgy.module.crm.service.wms.CrmWmsProductQueryApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * CRM 读取 WMS 商品的配置类。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CrmWmsProductReadProperties.class)
@EnableFeignClients(clients = CrmWmsProductQueryApi.class)
public class CrmWmsProductReadConfiguration {
}
