package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderService;
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
    @Resource private WmsShipmentOrderService shipmentOrderService;

    /**
     * Recovers MALL drafts created before automatic outbound was enabled or left behind by an interrupted rollout.
     * New requests complete synchronously in {@code WmsIntegrationApiImpl}; this is only the safety net.
     */
    @Scheduled(initialDelay = 10000, fixedDelay = 60000)
    public void completePendingMallShipments() {
        var pending = jdbcTemplate.query("SELECT tenant_id, id FROM wms_shipment_order WHERE deleted = 0 "
                        + "AND source_module = 'MALL' AND status = 0 AND approval_status = -1 "
                        + "AND execution_status = 0 AND (execution_error IS NULL OR execution_error = '')",
                (rs, index) -> new Long[]{rs.getLong("tenant_id"), rs.getLong("id")});
        for (Long[] entry : pending) {
            try {
                TenantUtils.execute(entry[0], () -> shipmentOrderService.autoCompleteMallShipment(entry[1]));
            } catch (Exception failure) {
                String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                jdbcTemplate.update("UPDATE wms_shipment_order SET execution_error = ?, update_time = CURRENT_TIMESTAMP "
                                + "WHERE tenant_id = ? AND id = ? AND status = 0 AND execution_status = 0",
                        message.substring(0, Math.min(message.length(), 512)), entry[0], entry[1]);
                log.warn("Automatic MALL shipment failed: tenant={}, shipment={}", entry[0], entry[1], failure);
            }
        }
    }

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
