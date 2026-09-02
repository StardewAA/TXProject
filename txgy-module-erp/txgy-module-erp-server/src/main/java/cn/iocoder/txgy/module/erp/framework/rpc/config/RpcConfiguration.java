package cn.iocoder.txgy.module.erp.framework.rpc.config;

import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "erpRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = AdminUserApi.class)
public class RpcConfiguration {
}
