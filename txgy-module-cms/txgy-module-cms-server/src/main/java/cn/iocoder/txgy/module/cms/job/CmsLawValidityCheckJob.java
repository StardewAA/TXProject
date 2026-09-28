package cn.iocoder.txgy.module.cms.job;

import cn.iocoder.txgy.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.cms.service.validity.CmsLawValidityCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日核验已发布法律；只生成待办，不自动归档。 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "txgy.cms.validity-check", name = "enabled", havingValue = "true")
@Slf4j
public class CmsLawValidityCheckJob {

    private final TenantFrameworkService tenantFrameworkService;
    private final CmsLawValidityCheckService service;

    public CmsLawValidityCheckJob(TenantFrameworkService tenantFrameworkService,
                                  CmsLawValidityCheckService service) {
        this.tenantFrameworkService = tenantFrameworkService;
        this.service = service;
    }

    @Scheduled(cron = "${txgy.cms.validity-check.cron:0 20 3 * * ?}")
    public void execute() {
        tenantFrameworkService.getTenantIds().parallelStream().forEach(tenantId ->
                TenantUtils.execute(tenantId, () -> {
                    try {
                        service.checkCurrentTenant();
                    } catch (RuntimeException ex) {
                        log.error("[execute][tenantId={} 法律效力定时核验失败]", tenantId, ex);
                    }
                }));
    }
}
