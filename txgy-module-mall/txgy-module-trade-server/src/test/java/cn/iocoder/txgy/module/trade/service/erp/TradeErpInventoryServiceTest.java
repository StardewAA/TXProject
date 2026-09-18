package cn.iocoder.txgy.module.trade.service.erp;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.*;
import static org.junit.jupiter.api.Assertions.*;

/** 使用正式DDL和真实SQL验证独立模拟账本，WMS和商品库存作为只读哨兵。 */
class TradeErpInventoryServiceTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    TradeErpInventoryService service;
    ErpInventoryProperties props;
    MockEnvironment env;
    TradeOrderDO order;
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:erp-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null&&!Files.exists(root.resolve("deploy/mysql/repair/2026-09-12-mall-erp-inventory.sql"))) root=root.getParent();
        assertNotNull(root);
        new ResourceDatabasePopulator(new FileSystemResource(root.resolve("deploy/mysql/repair/2026-09-12-mall-erp-inventory.sql"))).execute(ds);
        jdbc.execute("CREATE TABLE trade_order(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE product_sku(id BIGINT PRIMARY KEY,tenant_id BIGINT,stock INT,wms_sku_id BIGINT,wms_warehouse_id BIGINT,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE wms_inventory(id BIGINT PRIMARY KEY,quantity DECIMAL(18,3))");
        jdbc.update("INSERT INTO trade_order(id,tenant_id) VALUES(1,1),(2,1),(3,2)");
        jdbc.update("INSERT INTO product_sku VALUES(11,1,99,101,1,0),(12,1,88,102,1,0),(21,2,77,201,2,0)");
        jdbc.update("INSERT INTO wms_inventory VALUES(1,125.750)");
        props=new ErpInventoryProperties(); env=new MockEnvironment(); env.setActiveProfiles("test");
        service=new TradeErpInventoryService(jdbc,props,env);
        TenantContextHolder.setTenantId(1L);
        order=new TradeOrderDO().setId(1L).setNo("ERP-ORDER-1").setStatus(0);
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();jdbc.execute("DROP ALL OBJECTS");}
    void mockMode(){props.setMode("mock");props.setMockEnabled(true);}
    List<TradeOrderItemDO> items(int count){return List.of(new TradeOrderItemDO().setSkuId(11L).setCount(count));}
    Entry request(Operation op,String key,int count){return tx.execute(s->service.request(order,op,key,items(count),null,null));}
    void seed(int count){tx.executeWithoutResult(s->service.seed(new Seed(11L,count)));}
    int stock(String column){return jdbc.queryForObject("SELECT "+column+" FROM trade_erp_mock_stock WHERE tenant_id=1 AND sku_id=11",Integer.class);}
    int requests(){return jdbc.queryForObject("SELECT COUNT(*) FROM trade_erp_inventory_request",Integer.class);}
    void assertSourceUnchanged(){
        assertEquals(99,jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=11",Integer.class));
        assertEquals(0,new java.math.BigDecimal("125.750").compareTo(jdbc.queryForObject("SELECT quantity FROM wms_inventory WHERE id=1",java.math.BigDecimal.class)));
    }
    @Test void pendingPersistsWithoutPretendingSuccess(){
        var entry=request(Operation.RESERVE,"ORDER",3);
        assertEquals("PENDING",entry.status());assertFalse(service.paymentReady(1L));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM trade_erp_mock_stock",Integer.class));assertSourceUnchanged();
    }
    @Test void restartRetainsRequests(){
        var e=request(Operation.RESERVE,"ORDER",3);
        var restarted=new TradeErpInventoryService(jdbc,props,env);
        assertEquals(e.id(),restarted.list(1L).getFirst().id());
    }
    @Test void identicalRequestDoesNotRepeatOrChangeAttempts(){
        var e=request(Operation.RESERVE,"ORDER",3);
        assertEquals(e.id(),request(Operation.RESERVE,"ORDER",3).id());assertEquals(1,requests());
        assertThrows(ServiceException.class,()->request(Operation.RESERVE,"ORDER",4));assertEquals(1,requests());
    }
    @Test void mockFullLifecycleIsIsolatedAndIdempotent(){
        mockMode();seed(10);
        var e=request(Operation.RESERVE,"ORDER",5);assertTrue(e.confirmed());assertTrue(service.paymentReady(1L));
        request(Operation.RESERVE,"ORDER",5);assertEquals(5,stock("reserved"));
        request(Operation.RELEASE,"ITEM-1",2);assertEquals(3,stock("reserved"));
        request(Operation.OUTBOUND,"DELIVERY",3);assertEquals(7,stock("quantity"));assertEquals(0,stock("reserved"));
        request(Operation.OUTBOUND,"DELIVERY",3);assertEquals(7,stock("quantity"));
        request(Operation.RETURN,"AFTER-1",2);request(Operation.RETURN,"AFTER-1",2);assertEquals(9,stock("quantity"));
        assertSourceUnchanged();
    }
    @Test void seedReplayNeverRefillsConsumedStock(){
        mockMode();seed(10);request(Operation.RESERVE,"ORDER",3);request(Operation.OUTBOUND,"DELIVERY",3);
        seed(10);assertEquals(7,stock("quantity"));assertThrows(ServiceException.class,()->seed(20));
    }
    @Test void uninitializedStockDoesNotReadWmsAsMockStock(){
        mockMode();assertThrows(ServiceException.class,()->request(Operation.RESERVE,"ORDER",1));assertEquals(0,requests());assertSourceUnchanged();
    }
    @Test void insufficientMockStockRollsBackEverything(){
        mockMode();seed(1);assertThrows(ServiceException.class,()->request(Operation.RESERVE,"ORDER",2));
        assertEquals(0,requests());assertEquals(0,stock("reserved"));
    }
    @Test void multipleSkuFailureRollsBackEarlierSku(){
        mockMode();seed(10);
        assertThrows(ServiceException.class,()->tx.execute(s->service.request(order,Operation.RESERVE,"ORDER",
                List.of(new TradeOrderItemDO().setSkuId(11L).setCount(3),new TradeOrderItemDO().setSkuId(12L).setCount(1)),null,null)));
        assertEquals(0,stock("reserved"));assertEquals(0,requests());
    }
    @Test void excessReturnAndReleaseCannotInflateStock(){
        mockMode();seed(10);request(Operation.RESERVE,"ORDER",3);request(Operation.OUTBOUND,"DELIVERY",3);
        assertThrows(ServiceException.class,()->request(Operation.RETURN,"AFTER-1",4));
        assertThrows(ServiceException.class,()->request(Operation.RELEASE,"CANCEL",1));
        assertEquals(7,stock("quantity"));assertEquals(0,stock("reserved"));
    }
    @Test void tenantCannotReadRequestOrOperateForeignStock(){
        var e=request(Operation.RESERVE,"ORDER",3);
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class,()->service.get(e.id()));
        assertTrue(service.list(1L).isEmpty());
        assertThrows(ServiceException.class,()->request(Operation.RESERVE,"ORDER",3));
        mockMode();assertThrows(ServiceException.class,()->seed(10));
    }
    @Test void callbackFailsClosedAndFinalResultCannotChange(){
        assertThrows(ServiceException.class,()->service.authenticateCallback("anything"));
        props.setCallbackEnabled(true);props.setCallbackSecret("01234567890123456789012345678901");
        assertThrows(ServiceException.class,()->service.authenticateCallback("wrong"));
        service.authenticateCallback(props.getCallbackSecret());
        var e=request(Operation.RESERVE,"ORDER",3);
        var c=new Callback(e.id(),"CONFIRMED","ERP-123","ERP已完成预占");
        tx.executeWithoutResult(s->service.confirm(c));
        assertTrue(service.paymentReady(1L));
        tx.executeWithoutResult(s->service.confirm(c));
        assertThrows(ServiceException.class,()->tx.execute(s->service.confirm(new Callback(e.id(),"REJECTED","ERP-123","拒绝"))));
        assertSourceUnchanged();
    }
    @Test void formalCallbackCannotConfirmMockRequest(){
        mockMode();seed(10);var e=request(Operation.RESERVE,"ORDER",2);
        assertThrows(ServiceException.class,()->tx.execute(s->service.confirm(new Callback(e.id(),"CONFIRMED","ERP-X","结果"))));
    }
    @Test void retryKeepsBusinessIdAndDoesNotPretendDelivery(){
        var e=request(Operation.RESERVE,"ORDER",2);
        var retried=tx.execute(s->service.retry(e.id()));
        assertEquals(e.id(),retried.id());assertEquals(2,retried.attempts());assertEquals("PENDING",retried.status());
        tx.executeWithoutResult(s->service.confirm(new Callback(e.id(),"REJECTED","ERP-X","库存不足")));
        assertThrows(ServiceException.class,()->tx.execute(s->service.retry(e.id())));
    }
    @Test void productionAndUnknownProfilesCannotEnableMock(){
        mockMode();env.setActiveProfiles("prod");assertThrows(ServiceException.class,service::validateConfiguration);
        env.setActiveProfiles("local","prod");assertThrows(ServiceException.class,service::validateConfiguration);
        env.setActiveProfiles();assertThrows(ServiceException.class,service::validateConfiguration);
        env.setActiveProfiles("local");props.setMockEnabled(false);assertThrows(ServiceException.class,service::validateConfiguration);
        props.setMode("remote");assertThrows(ServiceException.class,service::validateConfiguration);
    }
    @Test void legacyOrdersNeverInventMockReservations(){
        mockMode();seed(10);var e=request(Operation.OUTBOUND,"DELIVERY",2);
        assertEquals("pending",e.mode());assertEquals("PENDING",e.status());assertEquals(10,stock("quantity"));
    }
    @Test void bindingChangesDoNotRedirectExistingOrder(){
        var e=request(Operation.RESERVE,"ORDER",2);
        jdbc.update("UPDATE product_sku SET wms_sku_id=999 WHERE id=11");
        var release=request(Operation.RELEASE,"CANCEL",2);
        assertEquals(e.items().getFirst().wmsSkuId(),release.items().getFirst().wmsSkuId());
    }
    @Test void changingModeCannotTurnPendingIntoMockSuccess(){
        request(Operation.RESERVE,"ORDER",2);mockMode();seed(10);
        assertThrows(ServiceException.class,()->request(Operation.OUTBOUND,"DELIVERY",2));
        assertEquals(10,stock("quantity"));
    }
    @Test void callbackAndOrderTransitionRollbackTogether(){
        var entry=request(Operation.RESERVE,"ORDER",2);
        props.setCallbackEnabled(true);props.setCallbackSecret("01234567890123456789012345678901");
        var query=org.mockito.Mockito.mock(cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryService.class);
        var update=org.mockito.Mockito.mock(cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService.class);
        org.mockito.Mockito.when(query.getOrder(1L)).thenReturn(order);
        org.mockito.Mockito.doThrow(new IllegalStateException("payment unavailable")).when(update).continueAfterErpReservation(1L);
        var results=new TradeErpResultService(service,query,update,
                org.mockito.Mockito.mock(cn.iocoder.txgy.module.trade.service.aftersale.AfterSaleService.class),
                org.mockito.Mockito.mock(cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper.class));
        assertThrows(IllegalStateException.class,()->tx.execute(s->results.receive(props.getCallbackSecret(),
                new Callback(entry.id(),"CONFIRMED","ERP-1","已预占"))));
        assertEquals("PENDING",service.get(entry.id()).status());
        assertFalse(service.paymentReady(1L));
    }
    @Test void concurrentReservationsCannotOversell() throws Exception {
        mockMode();seed(5);
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            List<Future<Boolean>> futures=new ArrayList<>();
            for(long id:List.of(1L,2L)){
                futures.add(pool.submit(()->{
                    TenantContextHolder.setTenantId(1L);start.await();
                    try{
                        tx.execute(s->service.request(new TradeOrderDO().setId(id).setNo("O-"+id),Operation.RESERVE,"ORDER",items(4),null,null));
                        return true;
                    }catch(ServiceException e){return false;}finally{TenantContextHolder.clear();}
                }));
            }
            start.countDown();int successes=0;
            for(var f:futures)if(f.get(20,TimeUnit.SECONDS))successes++;
            assertEquals(1,successes);assertEquals(4,stock("reserved"));assertEquals(1,requests());
        }
    }
}
