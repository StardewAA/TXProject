package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@EnableScheduling
@Slf4j
@ConditionalOnProperty(name = "erp.stock-alert.enabled", havingValue = "true")
public class ErpStockAlertScheduler {
    @Resource
    private ErpStockAlertService alertService;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Scheduled(initialDelay = 120000, fixedDelay = 3600000)
    public void scan() {
        // 定时线程无 HTTP 请求上下文，需先查出所有配置了安全库存的租户，再逐租户执行
        List<Long> tenantIds;
        try {
            tenantIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT tenant_id FROM erp_product " +
                    "WHERE material_type = 'RAW_MATERIAL' AND safety_stock IS NOT NULL " +
                    "AND status = 0 AND deleted = 0", Long.class);
        } catch (Exception e) {
            log.error("Stock alert scan failed to load tenant ids", e);
            return;
        }
        int totalCreated = 0;
        for (Long tenantId : tenantIds) {
            try {
                Integer created = TenantUtils.execute(tenantId, alertService::scanAndAlert);
                totalCreated += created == null ? 0 : created;
            } catch (Exception e) {
                log.error("Stock alert scan failed for tenant {}", tenantId, e);
            }
        }
        log.info("Stock alert scan completed across {} tenant(s), {} alerts created",
                tenantIds.size(), totalCreated);
    }
}
