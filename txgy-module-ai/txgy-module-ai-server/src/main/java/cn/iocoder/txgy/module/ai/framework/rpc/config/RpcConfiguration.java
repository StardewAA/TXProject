package cn.iocoder.txgy.module.ai.framework.rpc.config;

import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProAndonRecordApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProCardApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProFeedbackApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProProcessApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProProjectApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProRouteApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProTaskApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProWorkOrderApi;
import cn.iocoder.txgy.module.mes.api.pro.MesProWorkRecordApi;
import cn.iocoder.txgy.module.mes.api.qc.MesQcIpqcApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "aiRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {FileApi.class, AdminUserApi.class,
        MesProWorkOrderApi.class, MesProTaskApi.class, MesProFeedbackApi.class, MesQcIpqcApi.class,
        MesProAndonRecordApi.class, MesProProjectApi.class, MesProProcessApi.class, MesProRouteApi.class,
        MesProWorkRecordApi.class, MesProCardApi.class})
public class RpcConfiguration {
}
