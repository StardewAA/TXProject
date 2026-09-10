package cn.iocoder.txgy.module.trade.job.order;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
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

    @XxlJob("tradeOrderAutoCancelJob")
    @TenantJob // 多租户
    public String execute() {
        int count = tradeOrderUpdateService.cancelOrderBySystem();
        return String.format("过期订单 %s 个", count);
    }

    /**
     * 本地定时兜底：即使 XXL-JOB 调度中心未启动，也要自动关闭超时未支付订单。
     * 按租户执行，避免后台管理端和用户端看到的订单状态不一致。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 10_000L)
    public void fallbackExecute() {
        tenantFrameworkService.getTenantIds().forEach(tenantId ->
                TenantUtils.execute(tenantId, () -> {
                    int count = tradeOrderUpdateService.cancelOrderBySystem();
                    if (count > 0) {
                        log.info("[fallbackExecute][tenant({}) 自动取消 {} 个过期订单]", tenantId, count);
                    }
                }));
    }

}
