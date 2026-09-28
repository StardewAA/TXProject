package cn.iocoder.txgy.module.mes.framework.rpc.config;

import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.erp.api.sale.ErpSaleOrderApi;
import cn.iocoder.txgy.module.system.api.dept.PostApi;
import cn.iocoder.txgy.module.system.api.dict.DictDataApi;
import cn.iocoder.txgy.module.system.api.permission.RoleApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "mesRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, PostApi.class, RoleApi.class, DictDataApi.class,
        ErpSaleOrderApi.class, BpmProcessInstanceApi.class, WmsIntegrationApi.class})
public class RpcConfiguration {
}
