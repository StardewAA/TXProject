package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class WmsShortageSenderTest {
    JdbcTemplate jdbc;
    WmsShortageService shortages;
    WmsShortageSender sender;
    MockEnvironment env;
    HttpServer server;
    TransactionTemplate transaction;
    AtomicInteger calls = new AtomicInteger();
    AtomicInteger httpStatus = new AtomicInteger(200);
    AtomicReference<String> response = new AtomicReference<>("{\"code\":0,\"data\":true}");
    AtomicReference<String> received = new AtomicReference<>();
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:shortage"+System.nanoTime()+";MODE=MYSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql")).execute(ds);
        jdbc=new JdbcTemplate(ds);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
        env=new MockEnvironment();
        shortages=new WmsShortageService();
        ReflectionTestUtils.setField(shortages,"jdbc",jdbc);
        ReflectionTestUtils.setField(shortages,"environment",env);
        sender=new WmsShortageSender();
        ReflectionTestUtils.setField(sender,"jdbc",jdbc);
        ReflectionTestUtils.setField(sender,"shortages",shortages);
        TenantContextHolder.setTenantId(0L);
        jdbc.update("INSERT INTO wms_shipment_order(id,no,type,order_time,status,source_module,biz_order_no,warehouse_id) VALUES(1,'WMS-MES-SEND-001',202,CURRENT_TIMESTAMP,0,'MES','SEND-001',1)");
        jdbc.update("INSERT INTO wms_shipment_order_detail(id,order_id,sku_id,warehouse_id,quantity,source_line_no) VALUES(1,1,1,1,3,1)");
        transaction.execute(s->shortages.check(1L));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/receive",exchange->{
            calls.incrementAndGet();
            received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body=response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(httpStatus.get(),body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
    }
    @AfterEach void cleanup() {
        server.stop(0); TenantContextHolder.clear(); jdbc.execute("SHUTDOWN");
    }
    String event() { return (String)shortages.list(1L).getFirst().get("eventId"); }
    boolean send(boolean manual) { return transaction.execute(s->sender.send(event(),manual)); }
    void configure() {
        env.setProperty("wms.shortage.targets.erp.0.url","http://127.0.0.1:"+server.getAddress().getPort()+"/receive");
        env.setProperty("wms.shortage.targets.erp.0.token","test-only-placeholder");
    }
    @Test void unconfiguredThenConfirmedReceivesStableEventOnlyOnce() {
        assertFalse(send(false)); assertEquals(0,calls.get());
        assertEquals("WAITING_INTEGRATION",shortages.list(1L).getFirst().get("notificationStatus"));
        configure();
        assertTrue(send(false)); assertTrue(send(true));
        assertEquals(1,calls.get()); assertTrue(received.get().contains(event()));
        assertEquals("SUCCEEDED",shortages.list(1L).getFirst().get("notificationStatus"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history",Integer.class));
    }
    @Test void serverFailureBacksOffAndStopsAfterFiveAttempts() {
        configure(); httpStatus.set(503);
        for(int i=0;i<5;i++) {
            jdbc.update("UPDATE wms_shortage_event SET next_attempt_time=CURRENT_TIMESTAMP");
            assertFalse(send(false));
            assertFalse(send(false));
        }
        assertEquals(5,calls.get());
        assertEquals(5,shortages.list(1L).getFirst().get("attempts"));
        assertNull(jdbc.queryForObject("SELECT next_attempt_time FROM wms_shortage_event",Object.class));
        httpStatus.set(200); assertTrue(send(true));
        assertEquals(6,calls.get());
    }
    @Test void authFailureAndBusinessRefusalNeverPretendDelivery() {
        configure(); httpStatus.set(403);
        assertFalse(send(false)); assertFalse(send(false)); assertEquals(1,calls.get());
        assertEquals("FAILED",shortages.list(1L).getFirst().get("notificationStatus"));
        httpStatus.set(200); response.set("{\"code\":0,\"data\":false}");
        assertFalse(send(true));
        assertEquals("FAILED",shortages.list(1L).getFirst().get("notificationStatus"));
        assertNull(jdbc.queryForObject("SELECT next_attempt_time FROM wms_shortage_event",Object.class));
    }
    @Test void simulationAndWrongTenantNeverSend() {
        configure(); jdbc.update("UPDATE wms_shipment_order SET simulation=1 WHERE id=1");
        assertFalse(send(true)); assertEquals(0,calls.get());
        String id=event();
        TenantContextHolder.setTenantId(999L);
        assertThrows(IllegalArgumentException.class,()->transaction.execute(s->sender.send(id,true)));
        assertEquals(0,calls.get());
    }
    @Test void newerResolvedVersionSupersedesUnsentOpenMessage() {
        configure();
        jdbc.update("INSERT INTO wms_inventory(id,sku_id,warehouse_id,quantity) VALUES(1,1,1,3)");
        transaction.execute(s->shortages.check(1L));
        assertTrue(send(false));
        assertTrue(received.get().contains("\"shortageStatus\":\"RESOLVED\""));
        assertTrue(received.get().contains("\"eventVersion\":2"));
        assertEquals(1,calls.get());
    }
    @Test void concurrentWorkersDeliverOneConfirmedVersion() throws Exception {
        configure();
        String id=event();
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> work=()->{
                TenantContextHolder.setTenantId(0L);
                try { return transaction.execute(s->sender.send(id,true)); }
                finally { TenantContextHolder.clear(); }
            };
            var first=workers.submit(work);
            var second=workers.submit(work);
            assertTrue(first.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(second.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,calls.get());
    }
    @Test void cancellationRetainsHistoryAndSendsCancelledVersion() {
        configure(); assertTrue(send(false));
        transaction.executeWithoutResult(s->shortages.cancel(1L));
        jdbc.update("UPDATE wms_shipment_order SET deleted=1 WHERE id=1");
        String id=jdbc.queryForObject("SELECT event_id FROM wms_shortage_event LIMIT 1",String.class);
        assertEquals(Boolean.TRUE,transaction.execute(s->sender.send(id,false)));
        assertTrue(received.get().contains("\"shortageStatus\":\"CANCELLED\""));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM wms_shortage_event",Integer.class));
    }
    @Test void olderUnconfiguredMessagesDoNotStarveOverdueConfiguredMessage() {
        configure();
        for(int i=0;i<100;i++) {
            jdbc.update("INSERT INTO wms_shortage_event(tenant_id,event_id,event_version,order_id,line_id,target_system,shortage_status,payload,notification_status,next_attempt_time,created_time)"
                    + " VALUES(0,?,1,1,?,'MES','OPEN','{}','WAITING_INTEGRATION',CURRENT_TIMESTAMP,?)",
                    "waiting-"+i,100+i,java.time.LocalDateTime.now().minusDays(2));
        }
        jdbc.update("UPDATE wms_shortage_event SET notification_status='PENDING',next_attempt_time=? WHERE event_id=?",
                java.time.LocalDateTime.now().minusDays(1),event());
        var scheduler=new WmsShortageScheduler();
        ReflectionTestUtils.setField(scheduler,"jdbc",jdbc);
        ReflectionTestUtils.setField(scheduler,"sender",sender);
        scheduler.deliver();
        assertEquals(1,calls.get());
    }
}
