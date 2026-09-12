package cn.iocoder.txgy.module.pay.job.notify;

import cn.iocoder.txgy.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.pay.service.notify.PayNotifyServiceImpl;
import cn.iocoder.txgy.module.pay.dal.mysql.notify.PayNotifyTaskMapper;
import cn.iocoder.txgy.module.pay.dal.dataobject.notify.PayNotifyTaskDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 无 XXL-Job 的部署可显式启用；上线前必须审计遗留通知任务。 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "yudao.pay.local-notify-retry-enabled", havingValue = "true")
public class LocalPayNotifyScheduler {
    private final PayNotifyServiceImpl notifyService;
    private final PayNotifyTaskMapper taskMapper;
    private final TenantFrameworkService tenantService;
    @Value("${yudao.pay.local-notify-retry-min-id:9223372036854775807}")
    private long minTaskId;
    @Value("${yudao.pay.local-notify-retry-app-id:1}")
    private long appId;

    @Scheduled(fixedDelayString = "${yudao.pay.local-notify-retry-delay:15000}", initialDelay = 30000)
    public void retry() {
        for (Long tenantId : tenantService.getTenantIds()) {
            TenantUtils.execute(tenantId, () -> {
                try {
                    // 只重试审计边界之后的商城支付通知，不自动重放旧退款/转账任务。
                    for (PayNotifyTaskDO task : taskMapper.selectList(new LambdaQueryWrapper<PayNotifyTaskDO>()
                            .ge(PayNotifyTaskDO::getId, minTaskId).eq(PayNotifyTaskDO::getAppId, appId)
                            .eq(PayNotifyTaskDO::getType, 1).in(PayNotifyTaskDO::getStatus, 0, 20, 21)
                            .le(PayNotifyTaskDO::getNextNotifyTime, LocalDateTime.now())
                            .orderByAsc(PayNotifyTaskDO::getId).last("LIMIT 100"))) {
                        notifyService.executeNotify(task);
                    }
                } catch (Exception e) {
                    log.error("[retry][tenantId({}) 支付通知重试失败]", tenantId, e);
                }
            });
            if (Thread.currentThread().isInterrupted()) return;
        }
    }
}
