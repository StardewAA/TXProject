package cn.iocoder.txgy.module.wms.framework.rpc.config;

import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.erp.api.wms.ErpWmsCallbackApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.permission.PermissionApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "wmsRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, DeptApi.class, PermissionApi.class,
        BpmProcessInstanceApi.class, ErpWmsCallbackApi.class})
public class RpcConfiguration {
}
