package cn.iocoder.txgy.module.trade.service.settlement;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.supplier.ProductSupplierApi;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.trade.service.settlement.SettlementModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual settlement service SQL and its Spring transaction proxy; no production accounts or RPC calls. */
class ShopSettlementConcurrencyTest {
    JdbcTemplate jdbc;
    ShopSettlementService service;
    TransactionTemplate otherBusiness;
    volatile Runnable beforeOrderLock, afterOrderLock;
    final Queue<Integer> observedIsolation = new ConcurrentLinkedQueue<>();

    @BeforeEach void setup() {
        String mysql = System.getenv("MALL_SETTLEMENT_TEST_JDBC");
        DriverManagerDataSource source = new DriverManagerDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                // Use MySQL's usual default deliberately. The real service proxy MUST switch to RC.
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                return connection;
            }
        };
        if (mysql == null) source.setUrl("jdbc:h2:mem:settlement-concurrent-"+UUID.randomUUID()
                +";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=4000");
        else {
            assertTrue(mysql.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/mall_shop_settlement_test_20260917(?:\\?.*)?"));
            source.setUrl(mysql); source.setUsername(System.getenv("MALL_SETTLEMENT_TEST_USER"));
            source.setPassword(System.getenv("MALL_SETTLEMENT_TEST_PASSWORD"));
        }
        jdbc = new JdbcTemplate(source) {
            @Override public List<Map<String,Object>> queryForList(String sql,Object... args) {
                boolean parent = sql.startsWith("SELECT pay_price,refund_price,status FROM trade_order ")
                        || sql.startsWith("SELECT o.id,o.no,o.pay_price");
                if (parent && beforeOrderLock != null) beforeOrderLock.run();
                List<Map<String,Object>> result = super.queryForList(sql,args);
                if (parent) {
                    observedIsolation.add(Objects.requireNonNull(execute((org.springframework.jdbc.core.ConnectionCallback<Integer>)
                            Connection::getTransactionIsolation)));
                    if (afterOrderLock != null) afterOrderLock.run();
                }
                return result;
            }
        };
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null&&!Files.exists(root.resolve("deploy/mysql/repair/2026-09-12-mall-shop-settlement.sql")))root=root.getParent();
        assertNotNull(root);
        new ResourceDatabasePopulator(new FileSystemResource(root.resolve("deploy/mysql/repair/2026-09-12-mall-shop-settlement.sql"))).execute(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS trade_order(id BIGINT PRIMARY KEY,tenant_id BIGINT,shop_id BIGINT,no VARCHAR(64),status INT,pay_status INT,pay_price BIGINT,delivery_price BIGINT,refund_price BIGINT,receive_time TIMESTAMP,finish_time TIMESTAMP,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS trade_order_item(id BIGINT PRIMARY KEY,tenant_id BIGINT,order_id BIGINT,shop_id BIGINT,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS trade_after_sale(id BIGINT PRIMARY KEY,tenant_id BIGINT,order_id BIGINT,status INT,deleted INT DEFAULT 0)");
        clear();
        jdbc.update("INSERT INTO trade_order VALUES(1,1,10,'FIXTURE-ONLY',30,1,10000,0,0,'2020-01-15 12:00:00',NULL,0)");
        jdbc.update("INSERT INTO trade_order_item VALUES(11,1,1,10,0)");
        jdbc.update("INSERT INTO trade_shop_settlement_lock(tenant_id,shop_id) VALUES(1,10)");
        jdbc.update("INSERT INTO trade_shop_settlement_bill(id,tenant_id,shop_id,shop_name,bill_month,no,commission_rate,order_count,order_pay_amount,commission_amount,settlement_amount) VALUES(1,1,10,'隔离结算夹具','2020-01','SET-FIXTURE',500,1,10000,500,9500)");
        jdbc.update("INSERT INTO trade_shop_settlement_item(tenant_id,bill_id,order_id,order_no,kind,original_order_id,pay_amount,delivery_amount,refund_before,refund_total,commission_rate,commission_amount,amount) VALUES(1,1,1,'FIXTURE-ONLY',1,1,10000,0,0,0,500,500,9500)");
        var target=new ShopSettlementService();
        ReflectionTestUtils.setField(target,"jdbc",jdbc);
        var auth=mock(ProductSupplierApi.class);
        when(auth.validateShop(5L,10L)).thenReturn(success(true));
        when(auth.validateShop(5L,20L)).thenReturn(success(true));
        ReflectionTestUtils.setField(target,"authorization",auth);
        ReflectionTestUtils.setField(target,"shops",mock(ProductShopApi.class));
        var manager=new DataSourceTransactionManager(source);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        service=(ShopSettlementService)proxy.getProxy();
        otherBusiness=new TransactionTemplate(manager);
    }
    void clear() {
        for(String table:List.of("trade_shop_settlement_log","trade_shop_settlement_item","trade_shop_settlement_bill",
                "trade_shop_settlement_lock","trade_after_sale","trade_order_item","trade_order"))jdbc.update("DELETE FROM "+table);
    }
    @AfterEach void cleanup(){beforeOrderLock=null;afterOrderLock=null;TenantContextHolder.clear();clear();}
    <T>T as(long tenant,boolean member,Supplier<T> task){
        TenantContextHolder.setTenantId(tenant);
        try(var security=mockStatic(SecurityFrameworkUtils.class)){
            security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(new LoginUser().setId(member?5L:1L)
                    .setUserType(member?1:2).setTenantId(tenant));
            return task.get();
        }finally{TenantContextHolder.clear();}
    }
    void audit(){as(1,false,()->{service.audit(new Audit(1L,1,true,"隔离核验"));return null;});}
    static void await(CountDownLatch latch){try{assertTrue(latch.await(8,TimeUnit.SECONDS));}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}}
    void assertDraft(){assertEquals(10,jdbc.queryForObject("SELECT status FROM trade_shop_settlement_bill WHERE id=1",Integer.class));}
    void assertRC(){assertFalse(observedIsolation.isEmpty());assertTrue(observedIsolation.stream().allMatch(value->value==Connection.TRANSACTION_READ_COMMITTED));}

    @Test void uncommittedRefundAndSettlementDoNotDeadlockOrApproveAnActiveAfterSale() throws Exception {
        jdbc.update("INSERT INTO trade_after_sale VALUES(101,1,1,40,0)");
        CountDownLatch saleLocked=new CountDownLatch(1),parentLocked=new CountDownLatch(1);
        afterOrderLock=parentLocked::countDown;
        try(var pool=Executors.newFixedThreadPool(2)){
            Future<?> refund=pool.submit(()->otherBusiness.executeWithoutResult(tx->{
                jdbc.update("UPDATE trade_after_sale SET status=50 WHERE id=101"); // Real AS X lock.
                saleLocked.countDown();await(parentLocked);
                jdbc.queryForObject("SELECT id FROM trade_order WHERE id=1 FOR UPDATE",Long.class);
                jdbc.update("UPDATE trade_order SET refund_price=1000 WHERE id=1");
            }));
            await(saleLocked);
            Future<?> settlement=pool.submit(()->assertThrows(ServiceException.class,this::audit));
            settlement.get(10,TimeUnit.SECONDS);refund.get(10,TimeUnit.SECONDS);
        }
        assertDraft();assertRC();
        assertEquals(1000L,jdbc.queryForObject("SELECT refund_price FROM trade_order WHERE id=1",Long.class));
    }

    @Test void pendingTerminalStatusIsReadWithoutWaitingForItsExclusiveRowLock() throws Exception {
        jdbc.update("INSERT INTO trade_after_sale VALUES(101,1,1,40,0)");
        CountDownLatch locked=new CountDownLatch(1),allowCommit=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Future<?> closing=pool.submit(()->otherBusiness.executeWithoutResult(tx->{
                jdbc.update("UPDATE trade_after_sale SET status=50 WHERE id=101");locked.countDown();await(allowCommit);
            }));
            await(locked);
            try{pool.submit(()->assertThrows(ServiceException.class,this::audit)).get(4,TimeUnit.SECONDS);}
            finally{allowCommit.countDown();}
            closing.get(10,TimeUnit.SECONDS);
        }
        assertDraft();assertRC();
    }

    @Test void creationCommittedWhileWaitingForParentIsVisibleToSettlement() throws Exception {
        CountDownLatch inserted=new CountDownLatch(1),settlementAttempt=new CountDownLatch(1);
        beforeOrderLock=settlementAttempt::countDown;
        try(var pool=Executors.newFixedThreadPool(2)){
            Future<?> create=pool.submit(()->otherBusiness.executeWithoutResult(tx->{
                // Exactly the production creation order: parent lock BEFORE inserting the after-sale.
                jdbc.queryForObject("SELECT id FROM trade_order WHERE id=1 FOR UPDATE",Long.class);
                jdbc.update("INSERT INTO trade_after_sale VALUES(101,1,1,10,0)");inserted.countDown();await(settlementAttempt);
            }));
            await(inserted);
            Future<?> settlement=pool.submit(()->assertThrows(ServiceException.class,this::audit));
            create.get(10,TimeUnit.SECONDS);settlement.get(10,TimeUnit.SECONDS);
        }
        assertDraft();assertRC();
    }

    @Test void committedRefundChangeRejectsPreviouslyGeneratedAmount() throws Exception {
        jdbc.update("INSERT INTO trade_after_sale VALUES(101,1,1,40,0)");
        CountDownLatch changed=new CountDownLatch(1),settlementAttempt=new CountDownLatch(1);
        beforeOrderLock=settlementAttempt::countDown;
        try(var pool=Executors.newFixedThreadPool(2)){
            Future<?> refund=pool.submit(()->otherBusiness.executeWithoutResult(tx->{
                jdbc.queryForObject("SELECT id FROM trade_order WHERE id=1 FOR UPDATE",Long.class);
                jdbc.update("UPDATE trade_order SET refund_price=1000 WHERE id=1");
                jdbc.update("UPDATE trade_after_sale SET status=50 WHERE id=101");changed.countDown();await(settlementAttempt);
            }));
            await(changed);
            Future<?> settlement=pool.submit(()->assertThrows(ServiceException.class,this::audit));
            refund.get(10,TimeUnit.SECONDS);settlement.get(10,TimeUnit.SECONDS);
        }
        assertDraft();assertRC();
    }

    @Test void otherTenantOrOtherAuthorizedShopCannotConfirmThisBill(){
        jdbc.update("UPDATE trade_shop_settlement_bill SET status=20 WHERE id=1");
        assertThrows(ServiceException.class,()->as(2,true,()->{service.confirm(new Confirm(10L,1L,1));return null;}));
        assertThrows(ServiceException.class,()->as(1,true,()->{service.confirm(new Confirm(20L,1L,1));return null;}));
        assertEquals(20,jdbc.queryForObject("SELECT status FROM trade_shop_settlement_bill WHERE id=1",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_log",Integer.class));
    }

    @Test void activeAfterSaleFromAnotherTenantOrOrderDoesNotBlockOwnBill(){
        jdbc.update("INSERT INTO trade_after_sale VALUES(101,2,1,40,0),(102,1,2,40,0)");
        audit();assertRC();assertEquals(20,jdbc.queryForObject("SELECT status FROM trade_shop_settlement_bill WHERE id=1",Integer.class));
    }

    @Test void concurrentConfirmationOnlyAppendsOneConfirmationAndKeepsOwnership() throws Exception {
        jdbc.update("UPDATE trade_shop_settlement_bill SET status=20 WHERE id=1");
        try(var pool=Executors.newFixedThreadPool(2)){
            Runnable action=()->as(1,true,()->{service.confirm(new Confirm(10L,1L,1));return null;});
            Future<?> first=pool.submit(action),second=pool.submit(action);
            first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }
        assertEquals(30,jdbc.queryForObject("SELECT status FROM trade_shop_settlement_bill WHERE id=1",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_log WHERE action='SHOP_CONFIRM'",Integer.class));
        assertRC();
    }

    private String fixtureProof() throws Exception {
        var output=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",output);
        return "data:image/png;base64,"+Base64.getEncoder().encodeToString(output.toByteArray());
    }

    @Test void duplicatePaymentRegistrationIsIdempotentUnderConcurrentRealTransactions() throws Exception {
        jdbc.update("UPDATE trade_shop_settlement_bill SET status=30 WHERE id=1");
        var request=new Pay(1L,1,9500L,"ISOLATED-NOT-REAL-PAYMENT",LocalDateTime.now(),fixtureProof());
        try(var pool=Executors.newFixedThreadPool(2)){
            Runnable action=()->as(1,false,()->{service.pay(request);return null;});
            Future<?> first=pool.submit(action),second=pool.submit(action);
            first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }
        assertEquals(40,jdbc.queryForObject("SELECT status FROM trade_shop_settlement_bill WHERE id=1",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_log WHERE action='OFFLINE_PAID'",Integer.class));
        assertRC();
    }

    @Test void twoShopsCannotRegisterTheSamePaymentReferenceConcurrently() throws Exception {
        jdbc.update("UPDATE trade_shop_settlement_bill SET status=30 WHERE id=1");
        jdbc.update("INSERT INTO trade_order VALUES(2,1,20,'FIXTURE-2',30,1,10000,0,0,'2020-01-15 12:00:00',NULL,0)");
        jdbc.update("INSERT INTO trade_order_item VALUES(12,1,2,20,0)");
        jdbc.update("INSERT INTO trade_shop_settlement_bill(id,tenant_id,shop_id,shop_name,bill_month,no,status,commission_rate,settlement_amount) VALUES(2,1,20,'隔离第二店铺','2020-01','SET-FIXTURE-2',30,500,9500)");
        jdbc.update("INSERT INTO trade_shop_settlement_item(tenant_id,bill_id,order_id,order_no,kind,original_order_id,pay_amount,delivery_amount,refund_before,refund_total,commission_rate,commission_amount,amount) VALUES(1,2,2,'FIXTURE-2',1,2,10000,0,0,0,500,500,9500)");
        String proof=fixtureProof();AtomicInteger accepted=new AtomicInteger(),rejected=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)){
            List<Future<?>> futures=new ArrayList<>();
            for(long id:List.of(1L,2L))futures.add(pool.submit(()->{
                try{as(1,false,()->{service.pay(new Pay(id,1,9500L,"ISOLATED-SHARED-REFERENCE",LocalDateTime.now(),proof));return null;});accepted.incrementAndGet();}
                catch(RuntimeException expected){rejected.incrementAndGet();}
            }));
            for(var future:futures)future.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1,accepted.get());assertEquals(1,rejected.get());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_bill WHERE status=40",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_log WHERE action='OFFLINE_PAID'",Integer.class));
    }

    @Test void allFiveSettlementWritersDeclareReadCommittedOnTheirOwnMethods() throws Exception {
        Map<String,Class<?>[]> methods=Map.of("generate",new Class<?>[]{Generate.class},"audit",new Class<?>[]{Audit.class},
                "confirm",new Class<?>[]{Confirm.class},"pay",new Class<?>[]{Pay.class},"carry",new Class<?>[]{Long.class,int.class});
        for(var entry:methods.entrySet())assertEquals(Isolation.READ_COMMITTED,
                ShopSettlementService.class.getMethod(entry.getKey(),entry.getValue()).getAnnotation(Transactional.class).isolation());
    }
}
