package cn.iocoder.txgy.module.trade.job.order;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.Resource;

/**
 * 交易订单的自动过期 Job
 *
 * @author OPENLAB BS
 */
@Component
@Slf4j
public class TradeOrderAutoCancelJob {

    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;

    @Resource
    private TenantFrameworkService tenantFrameworkService;

    /** 补偿幂等及历史积分对账完成前保持关闭，不能仅依赖调度配置类条件。 */
    @Value("${txgy.trade.order.local-auto-cancel-enabled:false}")
    private boolean localAutoCancelEnabled;

    @XxlJob("tradeOrderAutoCancelJob")
    @TenantJob // 多租户
    public String execute() {
        int count = tradeOrderUpdateService.cancelOrderBySystem();
        return String.format("过期订单 %s 个", count);
    }

    /**
     * 显式开启后的本地定时兜底：不依赖 XXL-JOB 调度中心；默认关闭，先完成补偿幂等与对账。
     * 按租户执行，避免后台管理端和用户端看到的订单状态不一致。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 10_000L)
    public void fallbackExecute() {
        // 其他配置（例如 XXL-JOB）也可能启用 @Scheduled；关闭开关时仍禁止实际扫描。
        if (!localAutoCancelEnabled) return;
        tenantFrameworkService.getTenantIds().forEach(tenantId ->
                TenantUtils.execute(tenantId, () -> {
                    int count = tradeOrderUpdateService.cancelOrderBySystem();
                    if (count > 0) {
                        log.info("[fallbackExecute][tenant({}) 自动取消 {} 个过期订单]", tenantId, count);
                    }
                }));
    }

}
