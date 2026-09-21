package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WmsShipmentNotificationSchedulerTest {

    @Test
    void scansDueExecutedErpAndMallShipments() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:shipmentNotify" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE wms_shipment_order (id BIGINT, tenant_id BIGINT, deleted INT, source_module VARCHAR, execution_status INT, simulation INT, notification_status VARCHAR, next_notification_time TIMESTAMP)");
        jdbc.execute("INSERT INTO wms_shipment_order VALUES "
                + "(1,2005,0,'ERP',1,0,NULL,NULL),(2,2006,0,'MALL',1,0,'FAILED',NULL),"
                + "(3,2005,0,'MES',1,0,NULL,NULL),(4,2005,0,'MALL',0,0,NULL,NULL),"
                + "(5,2005,0,'MALL',1,1,NULL,NULL),(6,2005,0,'MALL',1,0,'DELIVERED',NULL)");
        var service = mock(WmsShipmentExecutionService.class);
        List<String> routed = new ArrayList<>();
        when(service.notifyIfDue(anyLong())).thenAnswer(call -> {
            assertFalse(TenantContextHolder.isIgnore());
            routed.add(TenantContextHolder.getRequiredTenantId() + ":" + call.getArgument(0));
            return true;
        });
        var scheduler = new WmsShipmentNotificationScheduler();
        ReflectionTestUtils.setField(scheduler, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(scheduler, "executionService", service);

        scheduler.deliverPending();

        assertEquals(java.util.Set.of("2005:1", "2006:2"), new java.util.HashSet<>(routed));
        assertEquals(2, routed.size());
    }
}
