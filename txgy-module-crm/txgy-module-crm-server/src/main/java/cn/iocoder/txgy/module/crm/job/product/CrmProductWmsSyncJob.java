package cn.iocoder.txgy.module.crm.job.product;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.crm.controller.admin.product.vo.product.CrmProductWmsSyncRespVO;
import cn.iocoder.txgy.module.crm.service.product.CrmProductWmsSyncService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * WMS 商品（进仓产品）自动同步导入 CRM 产品的 Job
 *
 * <p>按租户逐个执行，读取 WMS 商品规格并幂等导入 CRM 产品管理。</p>
 *
 * @author OPENLAB BS
 */
@Component
public class CrmProductWmsSyncJob {

    @Resource
    private CrmProductWmsSyncService productWmsSyncService;

    @XxlJob("crmProductWmsSyncJob")
    @TenantJob
    public String execute() {
        CrmProductWmsSyncRespVO result = productWmsSyncService.syncFromWms();
        if (Boolean.TRUE.equals(result.getSkipped())) {
            return "跳过同步：" + result.getMessage();
        }
        return String.format("WMS 产品同步：拉取 %s 个规格，新增 %s，更新 %s，无变化 %s，编码冲突 %s，失败 %s",
                result.getTotal(), result.getCreated(), result.getUpdated(), result.getUnchanged(),
                result.getConflict(), result.getFailed());
    }

}
