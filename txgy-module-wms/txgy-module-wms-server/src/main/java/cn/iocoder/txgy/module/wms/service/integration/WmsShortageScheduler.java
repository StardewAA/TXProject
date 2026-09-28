package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
@ConditionalOnProperty(name="wms.shortage.sender-enabled",havingValue="true")
public class WmsShortageScheduler {
    @Resource private JdbcTemplate jdbc;
    @Resource private WmsShortageSender sender;
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void deliver() {
        // Cross-tenant scan only obtains routing keys; all payload access occurs inside tenant context.
        var keys=jdbc.query("SELECT e.tenant_id,e.event_id FROM wms_shortage_event e WHERE"
                + " ((e.notification_status IN ('PENDING','FAILED') AND e.attempts<5"
                + " AND e.next_attempt_time<=CURRENT_TIMESTAMP) OR (e.notification_status='WAITING_INTEGRATION'"
                + " AND e.next_attempt_time<=CURRENT_TIMESTAMP))"
                + WmsShortageService.LATEST + " ORDER BY e.next_attempt_time,e.created_time,e.event_id LIMIT 100",
                (rs,i)->new Object[]{rs.getLong(1),rs.getString(2)});
        for(var key:keys) {
            try { TenantUtils.execute((Long)key[0],()-> { sender.send((String)key[1],false); }); }
            catch(Exception failure) { log.warn("Shortage delivery deferred: tenant={}, event={}",key[0],key[1]); }
        }
    }
}
