package cn.iocoder.txgy.module.wms.framework.rpc.config;

import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "wmsRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, BpmProcessInstanceApi.class})
public class RpcConfiguration {
}
