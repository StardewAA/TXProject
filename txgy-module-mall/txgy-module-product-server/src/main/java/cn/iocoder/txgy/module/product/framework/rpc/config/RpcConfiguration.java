package cn.iocoder.txgy.module.product.framework.rpc.config;

import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.member.api.level.MemberLevelApi;
import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.trade.api.delivery.DeliveryTemplateApi;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "productRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {MemberUserApi.class, MemberLevelApi.class, DeliveryTemplateApi.class,
        WmsIntegrationApi.class, WmsStockReadApi.class, AdminUserApi.class, FileApi.class})
public class RpcConfiguration {
}
