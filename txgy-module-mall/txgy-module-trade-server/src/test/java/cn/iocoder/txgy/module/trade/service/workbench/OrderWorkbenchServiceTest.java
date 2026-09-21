package cn.iocoder.txgy.module.trade.service.workbench;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.txgy.module.product.api.supplier.ProductSupplierApi;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsOutboundService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderWorkbenchServiceTest {
    JdbcTemplate jdbc;TransactionTemplate tx;OrderWorkbenchJournal journal;OrderWorkbenchService service;
    ProductSupplierApi suppliers;SecurityFrameworkService security;PayOrderApi pay;
    Path ddl;
    @BeforeEach void setup(){
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:workbench-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("deploy/mysql/repair/2026-09-15-mall-order-workbench.sql")))root=root.getParent();
        assertNotNull(root);ddl=root.resolve("deploy/mysql/repair/2026-09-15-mall-order-workbench.sql");
        new ResourceDatabasePopulator(new FileSystemResource(ddl)).execute(ds);
        jdbc.execute("CREATE TABLE trade_order(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted INT DEFAULT 0,no VARCHAR(64),user_id BIGINT,shop_id BIGINT,status INT,pay_status BIT,pay_order_id BIGINT,pay_expire_time DATETIME,pay_expire_seconds BIGINT,pay_price INT,delivery_type INT,refund_status INT,comment_status BIT,user_remark VARCHAR(200),type INT,pay_channel_code VARCHAR(30),terminal INT,logistics_id BIGINT,pick_up_store_id BIGINT,pick_up_verify_code VARCHAR(50),create_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE trade_order_item(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted INT DEFAULT 0,order_id BIGINT,shop_id BIGINT,after_sale_status INT DEFAULT 0,after_sale_id BIGINT)");
        jdbc.execute("CREATE TABLE trade_after_sale(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted INT DEFAULT 0,no VARCHAR(64),order_id BIGINT,order_item_id BIGINT,user_id BIGINT,shop_id BIGINT,status INT,way INT,pay_refund_id BIGINT)");
        jdbc.execute("CREATE TABLE trade_erp_inventory_request(id VARCHAR(64) PRIMARY KEY,tenant_id BIGINT,order_id BIGINT,operation VARCHAR(20),business_key VARCHAR(64),status VARCHAR(20),message VARCHAR(200))");
        jdbc.execute("""
                CREATE TABLE trade_wms_shipment(
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    order_id BIGINT NOT NULL,
                    order_no VARCHAR(64) NOT NULL,
                    shop_id BIGINT NOT NULL DEFAULT 0,
                    warehouse_id BIGINT NOT NULL,
                    warehouse_code VARCHAR(20),
                    source_order_no VARCHAR(56) NOT NULL,
                    request_id VARCHAR(64) NOT NULL,
                    target_order_no VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    event_version BIGINT NOT NULL DEFAULT 1,
                    retry_count INT NOT NULL DEFAULT 0,
                    last_error VARCHAR(1000),
                    request_json LONGTEXT,
                    requested_at DATETIME,
                    accepted_at DATETIME,
                    last_synced_at DATETIME,
                    creator VARCHAR(64) DEFAULT '',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updater VARCHAR(64) DEFAULT '',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BIT NOT NULL DEFAULT FALSE,
                    UNIQUE(tenant_id,order_id,shop_id,warehouse_id),
                    UNIQUE(tenant_id,source_order_no),
                    UNIQUE(tenant_id,request_id)
                )
                """);
        journal=new OrderWorkbenchJournal(jdbc);suppliers=mock(ProductSupplierApi.class);security=mock(SecurityFrameworkService.class);pay=mock(PayOrderApi.class);
        when(suppliers.validateShop(anyLong(),eq(1L))).thenReturn(CommonResult.success(true));when(suppliers.validateShop(anyLong(),eq(2L))).thenReturn(CommonResult.success(false));
        when(security.hasPermission(anyString())).thenReturn(true);
        when(pay.getOrder(anyLong())).thenAnswer(inv->{long id=inv.getArgument(0);assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"支付RPC必须在事务/时钟锁之前");
            return CommonResult.success(new PayOrderRespDTO().setId(id).setMerchantOrderId(String.valueOf(id-1000)).setPrice(100).setStatus(0).setExpireTime(LocalDateTime.now().plusMinutes(20)));});
        service=new OrderWorkbenchService(jdbc,journal,new OrderWorkbenchSource(jdbc,pay),new OrderWorkbenchCountService(jdbc,mock(MemberUserApi.class)),suppliers,security,manager);
        login(1,7,1);
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();SecurityContextHolder.clearContext();jdbc.execute("DROP ALL OBJECTS");}
    void login(long tenant,long user,int type){TenantContextHolder.setTenantId(tenant);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(user).setUserType(type).setTenantId(tenant),null,List.of()));}
    void order(long id,long member,long shop,int status){jdbc.update("INSERT INTO trade_order(id,tenant_id,no,user_id,shop_id,status,pay_status,pay_order_id,pay_expire_time,pay_expire_seconds,pay_price,delivery_type,refund_status,comment_status) VALUES(?,1,?,?,?,?,?,?,?,1200,100,1,0,0)",
            id,"ORDER-"+id,member,shop,status,status!=0,id+1000,LocalDateTime.now().plusMinutes(20));jdbc.update("INSERT INTO trade_order_item(id,tenant_id,order_id,shop_id) VALUES(?,1,?,?)",id,id,shop);}
    Summary summary(Role role,String cursor){return service.summary(role,role==Role.SUPPLIER?1L:null,cursor,List.of(1L,2L),List.of(),new TradeOrderPageReqVO(),null,role==Role.ADMIN);}
    void change(long id,String update){tx.executeWithoutResult(s->{jdbc.update(update);journal.changed(id);});}
    void sale(long id,long order,int status){jdbc.update("INSERT INTO trade_after_sale(id,tenant_id,no,order_id,order_item_id,user_id,shop_id,status,way) VALUES(?,1,?,?,?,7,1,?,20)",id,"AS-"+id,order,order,status);
        jdbc.update("UPDATE trade_order_item SET after_sale_status=10,after_sale_id=? WHERE id=?",id,order);}
    void shipment(long id,long order,long shop,String status){jdbc.update("INSERT INTO trade_wms_shipment(id,tenant_id,order_id,order_no,shop_id,warehouse_id,source_order_no,request_id,status) VALUES(?,1,?,?,?,?,'MALL-SOURCE-'||?,'MALL-REQUEST-'||?,?)",
            id,order,"ORDER-"+order,shop,id,id,id,status);}
    @Test void firstLoadCountsEntireScopeAndDoesNotBroadcastHistory(){for(long id=1;id<=27;id++)order(id,7,1,20);var s=summary(Role.BUYER,null);assertEquals(27,s.pendingCount());assertEquals(27,s.unreadCount());assertEquals(20,s.entries().size());assertTrue(s.hasMore());assertTrue(s.resetRequired());assertEquals(0,s.newCount());assertEquals(27,s.orderStatusCounts().deliveredCount());}
    @Test void readIsPersistentBetweenClientsButDoesNotProcessBusiness(){order(1,7,1,20);var s=summary(Role.BUYER,null);service.read(new Read(Role.BUYER,null,List.of(s.entries().getFirst().eventId())),false);
        var next=summary(Role.BUYER,s.cursor());assertEquals(1,next.pendingCount());assertEquals(0,next.unreadCount());assertEquals(20,jdbc.queryForObject("SELECT status FROM trade_order WHERE id=1",Integer.class));assertTrue(next.rowEntries().isEmpty());}
    @Test void differentSupplierReadDoesNotClearOtherPerson(){order(1,7,1,10);shipment(101,1,1,TradeWmsOutboundService.COMPLETED);login(1,316,1);var first=summary(Role.SUPPLIER,null);service.read(new Read(Role.SUPPLIER,1L,List.of(first.entries().getFirst().eventId())),false);
        login(1,317,1);assertEquals(1,summary(Role.SUPPLIER,null).unreadCount());login(1,316,1);assertEquals(0,summary(Role.SUPPLIER,null).unreadCount());}
    @Test void updateByOtherWorkerRemovesPendingEvenIfUnread(){order(1,7,1,10);shipment(101,1,1,TradeWmsOutboundService.COMPLETED);login(1,316,1);var first=summary(Role.SUPPLIER,null);change(1,"UPDATE trade_order SET status=20 WHERE id=1");assertEquals(0,summary(Role.SUPPLIER,first.cursor()).pendingCount());}
    @Test void unpaidAndErpWaitingNeverTellSupplierToDeliver(){order(1,7,1,0);login(1,316,1);assertEquals(0,summary(Role.SUPPLIER,null).pendingCount());jdbc.update("UPDATE trade_order SET pay_expire_time=NULL WHERE id=1");login(1,7,1);assertEquals(0,summary(Role.BUYER,null).pendingCount());}
    @Test void wmsPendingBlocksRepeatedDeliveryReminder(){order(1,7,1,10);shipment(101,1,1,TradeWmsOutboundService.PENDING);login(1,316,1);assertEquals(0,summary(Role.SUPPLIER,null).pendingCount());}
    @Test void wmsCompletedOrderCreatesDeliveryReminderForAuthorizedPlatform(){order(1,7,1,10);shipment(101,1,1,TradeWmsOutboundService.COMPLETED);login(1,1,2);
        var result=summary(Role.ADMIN,null);assertEquals(1,result.pendingCount());assertEquals(1,result.unreadCount());
        assertEquals(1,result.todoCounts().get(Action.DELIVERY));assertEquals("WMS 出库已完成，待登记物流并确认发货",result.entries().getFirst().reason());
        when(security.hasPermission("trade:order:query")).thenReturn(false);assertEquals(0,summary(Role.ADMIN,null).todoCounts().get(Action.DELIVERY));}
    @Test void refundBelongsToAuthorizedPlatformNotSupplier(){order(1,7,1,20);sale(11,1,40);login(1,316,1);assertEquals(0,summary(Role.SUPPLIER,null).pendingCount());login(1,1,2);assertEquals(1,summary(Role.ADMIN,null).todoCounts().get(Action.PLATFORM_REFUND));
        when(security.hasPermission("trade:after-sale:refund")).thenReturn(false);assertEquals(0,summary(Role.ADMIN,null).pendingCount());}
    @Test void newAfterSaleRequestUpdatesAuthorizedAdminUnread(){order(1,7,1,20);sale(11,1,10);login(1,1,2);
        var result=summary(Role.ADMIN,null);assertEquals(1,result.unreadCount());assertEquals(1,result.todoCounts().get(Action.AFTER_AUDIT));
        assertEquals("新售后退款申请待审核",result.entries().getFirst().reason());
        when(security.hasPermission("trade:after-sale:query")).thenReturn(false);assertEquals(0,summary(Role.ADMIN,null).pendingCount());}
    @Test void refundRequestAlreadySubmittedIsNotAnotherRefundTodo(){order(1,7,1,20);sale(11,1,40);jdbc.update("UPDATE trade_after_sale SET pay_refund_id=888 WHERE id=11");login(1,1,2);assertEquals(0,summary(Role.ADMIN,null).pendingCount());}
    @Test void afterSaleSpecialistSeesOnlyAuthorizedRefundWithoutOrderCounts(){order(1,7,1,20);sale(11,1,40);login(1,1,2);
        when(security.hasPermission("trade:order:query")).thenReturn(false);
        var s=summary(Role.ADMIN,null);assertEquals(1,s.pendingCount());assertNull(s.orderStatusCounts());assertEquals(0,s.todoCounts().get(Action.ERP_EXCEPTION));
        service.read(new Read(Role.ADMIN,null,List.of(s.entries().getFirst().eventId())),true);assertEquals(0,summary(Role.ADMIN,null).unreadCount());
        when(security.hasPermission("trade:after-sale:query")).thenReturn(false);assertThrows(ServiceException.class,()->summary(Role.ADMIN,null));
    }
    @Test void buyerReturnRemainsVisibleButSupplierReceiveWaitsForWmsReturnIntegration(){order(1,7,1,20);sale(11,1,20);assertEquals(1,summary(Role.BUYER,null).todoCounts().get(Action.BUYER_RETURN));change(1,"UPDATE trade_after_sale SET status=30 WHERE id=11");login(1,316,1);assertEquals(0,summary(Role.SUPPLIER,null).todoCounts().get(Action.AFTER_RECEIVE));
        tx.executeWithoutResult(s->{jdbc.update("INSERT INTO trade_erp_inventory_request VALUES('ret1',1,1,'RETURN','11','PENDING','待ERP')");journal.changed(1L);});assertEquals(0,summary(Role.SUPPLIER,null).pendingCount());}
    @Test void duplicatePollingAndBusinessCallbackDoNotCreateDuplicateEvents(){order(1,7,1,20);var first=summary(Role.BUYER,null);change(1,"UPDATE trade_order SET status=20 WHERE id=1");var next=summary(Role.BUYER,first.cursor());assertEquals(1,next.pendingCount());assertEquals(0,next.newCount());assertEquals(first.entries().getFirst().eventId(),next.entries().getFirst().eventId());}
    @Test void sameActionReentryBetweenPollsCreatesNewEvent(){order(1,7,1,10);shipment(101,1,1,TradeWmsOutboundService.COMPLETED);login(1,316,1);var first=summary(Role.SUPPLIER,null);service.read(new Read(Role.SUPPLIER,1L,List.of(first.entries().getFirst().eventId())),false);
        tx.executeWithoutResult(s->{sale(11,1,10);journal.changed(1L,Action.DELIVERY);});
        tx.executeWithoutResult(s->{jdbc.update("UPDATE trade_after_sale SET status=61 WHERE id=11");jdbc.update("UPDATE trade_order_item SET after_sale_status=0 WHERE order_id=1");journal.changed(1L);});
        var next=summary(Role.SUPPLIER,first.cursor());assertEquals(1,next.newCount());assertEquals(1,next.unreadCount());assertNotEquals(first.entries().getFirst().eventId(),next.entries().getFirst().eventId());}
    @Test void recordOnTransactionRollbackCannotAppear(){order(1,7,1,20);summary(Role.BUYER,null);assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{jdbc.update("UPDATE trade_order SET status=30 WHERE id=1");journal.changed(1L);throw new IllegalStateException("business failed");}));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM trade_order_workbench_change",Integer.class));assertEquals(1,summary(Role.BUYER,null).todoCounts().get(Action.RECEIVE));}
    @Test void journalFailureRollsBackBusinessInsteadOfLosingEvents(){order(1,7,1,20);jdbc.execute("DROP TABLE trade_order_workbench_change");assertThrows(RuntimeException.class,()->change(1,"UPDATE trade_order SET status=30 WHERE id=1"));assertEquals(20,jdbc.queryForObject("SELECT status FROM trade_order WHERE id=1",Integer.class));}
    @Test void tenantAndUserTypeAndShopMustAllMatch(){order(1,7,1,20);var s=summary(Role.BUYER,null);login(2,7,1);assertEquals(0,summary(Role.BUYER,null).pendingCount());assertThrows(ServiceException.class,()->service.read(new Read(Role.BUYER,null,List.of(s.entries().getFirst().eventId())),false));
        login(1,7,1);assertThrows(ServiceException.class,()->service.summary(Role.SUPPLIER,2L,null,List.of(),List.of(),null,null,false));assertThrows(ServiceException.class,()->summary(Role.ADMIN,null));login(1,1,2);assertThrows(ServiceException.class,()->summary(Role.BUYER,null));}
    @Test void stateTabsKeepSearchButGlobalTodosIgnoreIt(){order(1,7,1,20);order(2,7,1,30);var filters=new TradeOrderPageReqVO();filters.setNo("ORDER-1");filters.setStatus(40);
        var s=service.summary(Role.BUYER,null,null,List.of(),List.of(),filters,null,false);assertEquals(2,s.pendingCount());assertEquals(1,s.orderStatusCounts().allCount());assertEquals(1,s.orderStatusCounts().deliveredCount());assertEquals(0,s.orderStatusCounts().canceledCount());}
    @Test void pageCountsBeyondCurrentPageAndFiltersActualActions(){for(long id=1;id<=21;id++)order(id,7,1,20);order(22,7,1,30);var result=service.page(Role.BUYER,null,2,20,Action.RECEIVE,false,false);assertEquals(21,result.getTotal());assertEquals(1,result.getList().size());}
    @Test void corruptCursorAndOversizedIdsDoNotClearReadProgress(){assertThrows(ServiceException.class,()->summary(Role.BUYER,"bad"));assertThrows(ServiceException.class,()->service.read(new Read(Role.BUYER,null,List.of("-1")),false));assertTrue(summary(Role.BUYER,"99999999").resetRequired());}
    @Test void oldPaymentClosedOrPaidIsNeverShownAsPayable(){order(1,7,1,0);jdbc.update("UPDATE trade_order SET pay_expire_time=NULL,pay_expire_seconds=NULL WHERE id=1");
        when(pay.getOrder(1001L)).thenReturn(CommonResult.success(new PayOrderRespDTO().setId(1001L).setMerchantOrderId("1").setPrice(100).setStatus(30).setExpireTime(LocalDateTime.now().plusHours(1))));assertEquals(0,summary(Role.BUYER,null).todoCounts().get(Action.PAYMENT));
        when(pay.getOrder(1001L)).thenReturn(CommonResult.success(new PayOrderRespDTO().setId(1001L).setMerchantOrderId("1").setPrice(100).setStatus(10).setExpireTime(LocalDateTime.now().plusHours(1))));assertEquals(0,summary(Role.BUYER,null).pendingCount());}
    @Test void verifiedOldPaymentStillWorksWithoutRewritingItsDeadline(){order(1,7,1,0);jdbc.update("UPDATE trade_order SET pay_expire_time=NULL,pay_expire_seconds=NULL WHERE id=1");assertEquals(1,summary(Role.BUYER,null).todoCounts().get(Action.PAYMENT));assertNull(jdbc.queryForObject("SELECT pay_expire_time FROM trade_order WHERE id=1",java.sql.Timestamp.class));}
    @Test void paymentVerificationFailureIsErrorNotFakeZero(){order(1,7,1,0);when(pay.getOrder(1001L)).thenThrow(new IllegalStateException("RPC unavailable"));assertThrows(ServiceException.class,()->summary(Role.BUYER,null));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM trade_order_workbench_event",Integer.class));}
    @Test void invalidPaymentMerchantOrAmountFailsClosed(){order(1,7,1,0);when(pay.getOrder(1001L)).thenReturn(CommonResult.success(new PayOrderRespDTO().setId(1001L).setMerchantOrderId("other").setPrice(100).setStatus(0).setExpireTime(LocalDateTime.now().plusHours(1))));assertEquals(0,summary(Role.BUYER,null).pendingCount());}
    @Test void tradeDeadlineCrossingWhileVerifyingPaymentFailsClosed(){order(1,7,1,0);
        when(pay.getOrder(1001L)).thenAnswer(inv->{jdbc.update("UPDATE trade_order SET pay_expire_time=? WHERE id=1",LocalDateTime.now().minusSeconds(1));return CommonResult.success(new PayOrderRespDTO().setId(1001L).setMerchantOrderId("1").setPrice(100).setStatus(0).setExpireTime(LocalDateTime.now().plusHours(1)));});
        assertEquals(0,summary(Role.BUYER,null).pendingCount());
    }
    @Test void storeScopeProjectionCannotEraseBuyerPayment(){order(1,7,1,0);assertEquals(1,summary(Role.BUYER,null).pendingCount());login(1,316,1);summary(Role.SUPPLIER,null);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM trade_order_workbench_event WHERE role_name='BUYER' AND active=1",Integer.class));}
    @Test void paymentCreatedAfterPreparationIsNotPermanentlySkippedByCursor(){
        summary(Role.BUYER,null);tx.executeWithoutResult(s->{order(1,7,1,0);journal.changed(1L);});
        tx.executeWithoutResult(s->service.synchronize(new Scope(1,1,7,Role.BUYER,0,EnumSet.of(Action.PAYMENT,Action.RECEIVE,Action.COMMENT,Action.BUYER_RETURN)),Map.of()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM trade_order_workbench_event",Integer.class));
        assertEquals(1,summary(Role.BUYER,null).todoCounts().get(Action.PAYMENT));
    }
    @Test void legacyErpFailuresDoNotCreateWorkbenchTodosInWmsDirectFlow(){order(1,7,1,40);jdbc.update("INSERT INTO trade_erp_inventory_request VALUES('r1',1,1,'RESERVE','ORDER','REJECTED','库存不足')");login(1,1,2);assertEquals(0,summary(Role.ADMIN,null).pendingCount());
        tx.executeWithoutResult(s->{jdbc.update("INSERT INTO trade_erp_inventory_request VALUES('rel1',1,1,'RELEASE','CANCEL','REJECTED','释放失败')");journal.changed(1L);});assertEquals(0,summary(Role.ADMIN,null).todoCounts().get(Action.ERP_EXCEPTION));}
    @Test void journalCoalescesSameOrderPerTransaction(){order(1,7,1,20);tx.executeWithoutResult(s->{journal.changed(1L);journal.changed(1L);journal.changed(1L,Action.DELIVERY);});assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM trade_order_workbench_change",Integer.class));}
    @Test void sourceAndRealSupplierDeliveryGateAgreeAfterWmsCompletionAcrossStatusMatrix(){
        order(1,7,1,10);shipment(101,1,1,TradeWmsOutboundService.COMPLETED);var source=new OrderWorkbenchSource(jdbc,pay);
        for(int status:List.of(0,10,20,30,40))for(boolean paid:List.of(false,true))for(int delivery:List.of(1,2))for(int refund:List.of(0,1))for(int after:List.of(0,10)) {
            jdbc.update("UPDATE trade_order SET status=?,pay_status=?,delivery_type=?,refund_status=? WHERE id=1",status,paid,delivery,refund);
            jdbc.update("UPDATE trade_order_item SET after_sale_status=? WHERE id=1",after);
            var order=new cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO().setStatus(status).setPayStatus(paid).setDeliveryType(delivery).setRefundStatus(refund);
            var items=List.of(new cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO().setAfterSaleStatus(after));
            boolean fromSource=source.candidates(1,List.of(1L),Map.of()).stream().anyMatch(c->c.action()==Action.DELIVERY);
            assertEquals(OrderActionPolicy.deliveryBlock(order,items,List.of())==null,fromSource,"Source与真实发货门槛不一致");
        }
    }
    @Test void sequenceCannotOvertakeLateCommit() throws Exception {
        order(1,7,1,20);order(2,7,1,20);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{TenantContextHolder.setTenantId(1L);tx.executeWithoutResult(s->{journal.changed(1L);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void beforeCommit(boolean readOnly){locked.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}});});TenantContextHolder.clear();});
            assertTrue(locked.await(5,TimeUnit.SECONDS));var second=pool.submit(()->{TenantContextHolder.setTenantId(1L);tx.executeWithoutResult(s->journal.changed(2L));TenantContextHolder.clear();});
            assertThrows(TimeoutException.class,()->second.get(150,TimeUnit.MILLISECONDS));release.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }
        assertEquals(List.of(1L,2L),jdbc.queryForList("SELECT order_id FROM trade_order_workbench_change ORDER BY sequence_no",Long.class));
    }
}
