package cn.iocoder.txgy.module.pay.framework.rpc.config;

import cn.iocoder.txgy.module.system.api.social.SocialClientApi;
import cn.iocoder.txgy.module.trade.api.wms.TradeWmsStockApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "payRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {SocialClientApi.class, TradeWmsStockApi.class})
public class RpcConfiguration {
}
