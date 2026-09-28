package cn.iocoder.txgy.module.crm.job.customer;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 客户自动掉入公海的本地定时任务（Spring 调度版）
 *
 * <p>与 {@link CrmCustomerAutoPutPoolJob}（XXL-Job Handler：customerAutoPutPoolJob）是同一套业务逻辑，
 * 区别只是触发方式：当环境里没有部署 XXL-Job 调度中心（或未登记该 Handler）时，
 * 用它来保证「客户长期无人跟进/未成交 → 自动掉入公海」仍然按点执行。</p>
 *
 * <p>开关与执行时间通过配置控制，默认关闭，避免与 XXL-Job 双跑：</p>
 * <pre>
 * txgy:
 *   crm:
 *     customer-pool:
 *       auto-enabled: true          # 是否启用本定时任务（默认 false）
 *       auto-cron: 0 30 2 * * *     # 每天 02:30 执行（默认值）
 * </pre>
 *
 * <p>{@link TenantJob} 由租户框架的切面处理：自动遍历所有租户，逐个租户执行一次，无需自己循环。</p>
 *
 * @author OPENLAB BS
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "txgy.crm.customer-pool.auto-enabled", havingValue = "true")
public class CrmCustomerAutoPutPoolScheduler {

    @Resource
    private CrmCustomerService customerService;

    @Scheduled(cron = "${txgy.crm.customer-pool.auto-cron:0 30 2 * * *}")
    @TenantJob
    public void execute() {
        int count = customerService.autoPutCustomerPool();
        log.info("[execute][客户自动掉入公海执行完成，本次掉入 {} 个]", count);
    }

}
