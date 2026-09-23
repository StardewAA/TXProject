package cn.iocoder.txgy.module.trade.job.portal;

import cn.iocoder.txgy.module.trade.service.portal.MallTenantSalesDailyService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Refreshes the last 30 days used by the platform portal 7/30-day rankings. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "txgy.trade.portal-sales", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class MallTenantSalesDailyRefreshJob {

    private final MallTenantSalesDailyService service;

    /**
     * XXL-JOB 是正式调度入口；只有未部署调度中心的环境才显式开启本地兜底。
     * 默认关闭可避免两套调度器同时刷新同一日期区间。
     */
    @Value("${txgy.trade.portal-sales.fallback-enabled:false}")
    private boolean fallbackEnabled;

    @XxlJob("mallTenantSalesDailyRefreshJob")
    public String execute() {
        if (fallbackEnabled) {
            return "平台门户租户销量已配置由本地调度执行，本次 XXL-JOB 跳过";
        }
        int rows = service.refreshRecent(MallTenantSalesDailyService.DEFAULT_WINDOW_DAYS);
        return String.format("平台门户租户销量汇总 %s 天，生成 %s 行",
                MallTenantSalesDailyService.DEFAULT_WINDOW_DAYS, rows);
    }

    @Scheduled(cron = "${txgy.trade.portal-sales.cron:0 15 * * * *}")
    public void fallbackExecute() {
        if (!fallbackEnabled) {
            return;
        }
        int rows = service.refreshRecent(MallTenantSalesDailyService.DEFAULT_WINDOW_DAYS);
        log.info("[fallbackExecute][平台门户租户销量汇总完成，生成 {} 行]", rows);
    }
}
