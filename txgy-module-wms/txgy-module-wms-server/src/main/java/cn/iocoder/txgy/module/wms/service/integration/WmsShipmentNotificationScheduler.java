package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Recovers notifications from committed execution state, including after a process crash. */
@Component
@EnableScheduling
@Slf4j
public class WmsShipmentNotificationScheduler {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private WmsShipmentExecutionService executionService;

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void deliverPending() {
        // This read-only global scan obtains only routing keys. All business reads and writes below are tenant-scoped.
        var pending = jdbcTemplate.query("SELECT tenant_id, id FROM wms_shipment_order WHERE deleted = 0 "
                        + "AND source_module IN ('ERP','MALL') AND execution_status = 1 AND (simulation = 0 OR simulation IS NULL) "
                        + "AND (notification_status IS NULL OR notification_status = '' OR notification_status IN ('PENDING','FAILED')) "
                        + "AND (next_notification_time IS NULL OR next_notification_time <= CURRENT_TIMESTAMP)",
                (rs, index) -> new Long[]{rs.getLong("tenant_id"), rs.getLong("id")});
        for (Long[] entry : pending) {
            try {
                TenantUtils.execute(entry[0], () -> { executionService.notifyIfDue(entry[1]); });
            } catch (Exception failure) {
                log.warn("Shipment notification deferred: tenant={}, shipment={}", entry[0], entry[1], failure);
            }
        }
    }
}
