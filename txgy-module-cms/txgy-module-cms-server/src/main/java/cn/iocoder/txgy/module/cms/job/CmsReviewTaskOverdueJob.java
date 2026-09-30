package cn.iocoder.txgy.module.cms.job;

import cn.iocoder.txgy.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.cms.service.contentext.CmsContentExtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 审查任务超时扫描：超期未完成的审查任务标记为 OVERDUE，便于审核人优先处理。 */
@Component
@Slf4j
public class CmsReviewTaskOverdueJob {

    private final TenantFrameworkService tenantFrameworkService;
    private final CmsContentExtService service;

    public CmsReviewTaskOverdueJob(TenantFrameworkService tenantFrameworkService,
                                   CmsContentExtService service) {
        this.tenantFrameworkService = tenantFrameworkService;
        this.service = service;
    }

    @Scheduled(cron = "${txgy.cms.review-task.overdue-cron:0 */10 * * * ?}")
    public void execute() {
        tenantFrameworkService.getTenantIds().forEach(tenantId -> TenantUtils.execute(tenantId, () -> {
            try {
                int count = service.markOverdue();
                if (count > 0) {
                    log.info("[execute][tenantId={} 标记超时审查任务 {} 条]", tenantId, count);
                }
            } catch (RuntimeException ex) {
                log.error("[execute][tenantId={} 审查任务超时扫描失败]", tenantId, ex);
            }
        }));
    }
}