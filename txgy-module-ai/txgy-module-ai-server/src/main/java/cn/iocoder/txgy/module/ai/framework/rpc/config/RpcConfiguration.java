package cn.iocoder.txgy.module.ai.framework.rpc.config;

import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProFeedbackApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProTaskApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProWorkOrderApi;
import cn.iocoder.txgy.module.mes.api.qc.MesQcIpqcApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "aiRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {FileApi.class, AdminUserApi.class,
        MesProWorkOrderApi.class, MesProTaskApi.class, MesProFeedbackApi.class, MesQcIpqcApi.class})
public class RpcConfiguration {
}
