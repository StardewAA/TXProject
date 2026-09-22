package cn.iocoder.txgy.module.trade.job.wms;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsOutboundService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 由调度中心按租户重试并同步商城 WMS 出库申请。 */
@Component
@RequiredArgsConstructor
public class TradeWmsShipmentSyncJob {

    private final TradeWmsOutboundService outboundService;

    @XxlJob("tradeWmsShipmentSyncJob")
    @TenantJob
    public String execute() {
        int recovered = outboundService.recoverPaidOrdersWithoutShipment(100);
        int synced = outboundService.retryAndSync(100);
        return "补建商城 WMS 出库申请 " + recovered + " 条，同步 " + synced + " 条";
    }
}
