package cn.iocoder.txgy.module.bpm.framework.rpc.config;

import cn.iocoder.txgy.module.bpm.api.event.CrmContractStatusListener;
import cn.iocoder.txgy.module.bpm.api.event.CrmQuotationStatusListener;
import cn.iocoder.txgy.module.bpm.api.event.CrmReceivableStatusListener;
import cn.iocoder.txgy.module.bpm.api.event.ErpPurchaseOrderStatusListener;
import cn.iocoder.txgy.module.bpm.api.event.ErpSaleOrderStatusListener;
import cn.iocoder.txgy.module.bpm.api.event.MesSaleOrderStatusListener;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.PostApi;
import cn.iocoder.txgy.module.system.api.dict.DictDataApi;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.permission.PermissionApi;
import cn.iocoder.txgy.module.system.api.permission.RoleApi;
import cn.iocoder.txgy.module.system.api.sms.SmsSendApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.wms.api.warehouse.WmsWarehousePersonnelApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "bpmRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {RoleApi.class, DeptApi.class, PostApi.class, AdminUserApi.class, SmsSendApi.class, DictDataApi.class,
        PermissionApi.class, NotifyMessageSendApi.class, WmsWarehousePersonnelApi.class})
public class RpcConfiguration {

    // ========== 特殊：解决微 txgy-cloud 微服务场景下，跨服务（进程）无法 Listener 的问题 ==========

    @Bean
    @ConditionalOnMissingBean(name = "crmReceivableStatusListener")
    public CrmReceivableStatusListener crmReceivableStatusListener() {
        return new CrmReceivableStatusListener();
    }

    @Bean
    @ConditionalOnMissingBean(name = "crmContractStatusListener")
    public CrmContractStatusListener crmContractStatusListener() {
        return new CrmContractStatusListener();
    }

    @Bean
    @ConditionalOnMissingBean(name = "crmQuotationStatusListener")
    public CrmQuotationStatusListener crmQuotationStatusListener() {
        return new CrmQuotationStatusListener();
    }

    @Bean
    @ConditionalOnMissingBean(name = "mesSaleOrderStatusListener")
    public MesSaleOrderStatusListener mesSaleOrderStatusListener() {
        return new MesSaleOrderStatusListener();
    }

}
