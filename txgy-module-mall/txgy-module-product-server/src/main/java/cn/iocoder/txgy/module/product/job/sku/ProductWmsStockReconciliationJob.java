package cn.iocoder.txgy.module.product.job.sku;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import org.springframework.stereotype.Component;

/** 保留旧任务名称用于兼容调度配置；不再以 WMS 现存量推算或同步可售库存。 */
@Component
public class ProductWmsStockReconciliationJob {
    @TenantJob
    public String execute() {
        return "旧WMS可售库存对账已停用：WMS仅供现存库存查询，库存履约请查看ERP请求记录";
    }
}

