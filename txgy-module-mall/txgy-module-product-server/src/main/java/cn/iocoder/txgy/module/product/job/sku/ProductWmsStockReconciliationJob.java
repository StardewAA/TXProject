package cn.iocoder.txgy.module.product.job.sku;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.product.service.sku.ProductSkuService;
import cn.iocoder.txgy.module.product.service.sku.dto.ProductWmsStockReconciliationDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** 每日商城库存对账任务；差异会写入告警日志并由后台对账页处理。 */
@Component
@Slf4j
public class ProductWmsStockReconciliationJob {

    @Resource
    private ProductSkuService productSkuService;

    @TenantJob
    public String execute() {
        List<ProductWmsStockReconciliationDTO> results = productSkuService.getWmsStockReconciliation(null);
        long differenceCount = results.stream().filter(item -> !ProductWmsStockReconciliationDTO.STATUS_NORMAL
                .equals(item.getStatus())).count();
        if (differenceCount > 0) {
            results.stream().filter(item -> !ProductWmsStockReconciliationDTO.STATUS_NORMAL.equals(item.getStatus()))
                    .limit(20).forEach(item -> log.warn(
                            "[reconciliation][商城/WMS库存差异 skuId={}, spuId={}, cached={}, available={}, status={}]",
                            item.getProductSkuId(), item.getSpuId(), item.getCachedStock(), item.getAvailableStock(), item.getStatus()));
        }
        ReconciliationSummary summary = new ReconciliationSummary(results.size(), differenceCount);
        return String.format("WMS库存自动对账完成：检查 %d 个 SKU，发现 %d 个差异", summary.total(), summary.differences());
    }

    record ReconciliationSummary(long total, long differences) {}
}

