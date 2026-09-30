package cn.iocoder.txgy.module.erp.framework.rpc.config;

import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.mes.api.integration.MesIntegrationApi;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "erpRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, BpmProcessInstanceApi.class, NotifyMessageSendApi.class,
        FileApi.class, WmsIntegrationApi.class, MesIntegrationApi.class})
public class RpcConfiguration {
}
