package cn.iocoder.txgy.module.promotion.service.point;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.config.TenantProperties;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.txgy.module.promotion.dal.mysql.point.PointActivityMapper;
import cn.iocoder.txgy.module.promotion.dal.mysql.point.PointProductMapper;
import cn.iocoder.txgy.module.promotion.dal.mysql.point.PointStockReturnMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.txgy.module.promotion.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 正式 Mapper、租户拦截器、返库 DDL 和事务代理在独立 H2 中的回归测试，无外部服务调用。 */
class PointStockReturnTest {

    private JdbcTemplate jdbc;
    private PointActivityMapper activities;
    private PointProductMapper products;
    private PointActivityService service;

    @BeforeEach
    void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:point-return-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc = new JdbcTemplate(source);
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        String ddl = "deploy/mysql/repair/2026-09-15-promotion-point-stock-return.sql";
        while (root != null && !Files.exists(root.resolve(ddl))) {
            root = root.getParent();
        }
        assertNotNull(root);
        new ResourceDatabasePopulator(new FileSystemResource(root.resolve(ddl))).execute(source);
        String audit = "create_time TIMESTAMP,update_time TIMESTAMP,creator VARCHAR(64),updater VARCHAR(64),"
                + "deleted BOOLEAN DEFAULT FALSE,tenant_id BIGINT NOT NULL";
        jdbc.execute("CREATE TABLE promotion_point_activity(id BIGINT PRIMARY KEY,spu_id BIGINT,"
                + "status INT,remark VARCHAR(255),sort INT,stock INT,total_stock INT," + audit + ")");
        jdbc.execute("CREATE TABLE promotion_point_product(id BIGINT PRIMARY KEY,activity_id BIGINT,"
                + "spu_id BIGINT,sku_id BIGINT,count INT,point INT,price INT,stock INT,activity_status INT," + audit + ")");
        jdbc.update("INSERT INTO promotion_point_activity(id,stock,total_stock,status,tenant_id) VALUES(1,10,20,0,1),(2,30,40,0,2)");
        jdbc.update("INSERT INTO promotion_point_product(id,activity_id,sku_id,stock,activity_status,tenant_id) VALUES(11,1,1,10,0,1),(22,2,2,30,0,2)");

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PointActivityMapper.class);
        configuration.addMapper(PointProductMapper.class);
        configuration.addMapper(PointStockReturnMapper.class);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setPlugins(interceptor);
        var sessions = new SqlSessionTemplate(factory.getObject());
        activities = spy(sessions.getMapper(PointActivityMapper.class));
        products = spy(sessions.getMapper(PointProductMapper.class));
        var target = new PointActivityServiceImpl();
        ReflectionTestUtils.setField(target, "pointActivityMapper", activities);
        ReflectionTestUtils.setField(target, "pointProductMapper", products);
        ReflectionTestUtils.setField(target, "pointStockReturnMapper", sessions.getMapper(PointStockReturnMapper.class));
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        service = (PointActivityService) proxy.getProxy();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void historicalOrderWithSkuOutsideActivityFailsClearlyWithoutAnyWrite() {
        assertError(POINT_ACTIVITY_RETURN_PRODUCT_NOT_EXISTS,
                () -> service.validatePointStockReturn(1L, 920005L, 1, 280L));
        assertError(POINT_ACTIVITY_RETURN_PRODUCT_NOT_EXISTS,
                () -> service.returnPointStock(1L, 920005L, 1, 280L));
        assertState(10, 10, 0);
    }

    @Test
    void missingActivityDoesNotPretendSuccessfulReturn() {
        assertError(POINT_ACTIVITY_NOT_EXISTS, () -> service.validatePointStockReturn(99L, 1L, 2, 280L));
        assertError(POINT_ACTIVITY_NOT_EXISTS, () -> service.returnPointStock(99L, 1L, 2, 280L));
        assertState(10, 10, 0);
    }

    @Test
    void nullOrNonPositiveRequestIsRejectedBeforeTouchingStock() {
        for (Integer count : new Integer[]{null, 0, -1}) {
            assertError(POINT_ACTIVITY_RETURN_INVALID_REQUEST, () -> service.returnPointStock(1L, 1L, count, 280L));
            assertError(POINT_ACTIVITY_RETURN_INVALID_REQUEST, () -> service.validatePointStockReturn(1L, 1L, count, 280L));
        }
        assertError(POINT_ACTIVITY_RETURN_INVALID_REQUEST, () -> service.returnPointStock(null, 1L, 2, 280L));
        assertError(POINT_ACTIVITY_RETURN_INVALID_REQUEST, () -> service.returnPointStock(1L, null, 2, 280L));
        assertError(POINT_ACTIVITY_RETURN_INVALID_REQUEST, () -> service.returnPointStock(1L, 1L, 2, null));
        assertError(POINT_ACTIVITY_RETURN_INVALID_REQUEST, () -> service.returnPointStock(0L, 1L, 2, 280L));
        assertError(POINT_ACTIVITY_RETURN_INVALID_REQUEST, () -> service.returnPointStock(1L, 0L, 2, 280L));
        assertError(POINT_ACTIVITY_RETURN_INVALID_REQUEST, () -> service.returnPointStock(1L, 1L, 2, 0L));
        assertState(10, 10, 0);
    }

    @Test
    void legacyApiCannotInflateStockWithoutBusinessIdentifier() {
        assertError(POINT_ACTIVITY_RETURN_ORDER_ITEM_REQUIRED, () -> service.updatePointStockIncr(1L, 1L, 2));
        assertError(POINT_ACTIVITY_RETURN_ORDER_ITEM_REQUIRED, () -> service.updatePointStockIncr(1L, 1L, 2));
        assertState(10, 10, 0);
    }

    @Test
    void preflightDoesNotChangeStockOrCreateLedger() {
        service.validatePointStockReturn(1L, 1L, 2, 280L);
        assertState(10, 10, 0);
    }

    @Test
    void disabledAndSoldOutActivityCanStillReturnPreviouslyReservedStock() {
        jdbc.update("UPDATE promotion_point_activity SET stock=0,status=1 WHERE id=1");
        jdbc.update("UPDATE promotion_point_product SET stock=0,activity_status=1 WHERE id=11");
        service.validatePointStockReturn(1L, 1L, 2, 280L);
        service.returnPointStock(1L, 1L, 2, 280L);
        assertState(2, 2, 1);
    }

    @Test
    void exactRetryIncludingCancellationAfterItemRefundReturnsOnlyOnce() {
        service.returnPointStock(1L, 1L, 2, 280L);
        service.returnPointStock(1L, 1L, 2, 280L);
        service.validatePointStockReturn(1L, 1L, 2, 280L);
        assertState(12, 12, 1);
    }

    @Test
    void retryCannotChangeActivitySkuOrCount() {
        service.returnPointStock(1L, 1L, 2, 280L);
        assertError(POINT_ACTIVITY_RETURN_REQUEST_CONFLICT, () -> service.returnPointStock(1L, 1L, 3, 280L));
        assertError(POINT_ACTIVITY_RETURN_REQUEST_CONFLICT, () -> service.returnPointStock(1L, 7L, 2, 280L));
        assertError(POINT_ACTIVITY_RETURN_REQUEST_CONFLICT, () -> service.returnPointStock(9L, 1L, 2, 280L));
        assertError(POINT_ACTIVITY_RETURN_REQUEST_CONFLICT, () -> service.validatePointStockReturn(1L, 1L, 3, 280L));
        assertState(12, 12, 1);
    }

    @Test
    void committedLedgerRemainsAuthoritativeAfterActivityAndProductDeletion() {
        service.returnPointStock(1L, 1L, 2, 280L);
        jdbc.update("DELETE FROM promotion_point_product WHERE id=11");
        jdbc.update("DELETE FROM promotion_point_activity WHERE id=1");
        service.validatePointStockReturn(1L, 1L, 2, 280L);
        service.returnPointStock(1L, 1L, 2, 280L);
        assertEquals(1, ledgerCount());
    }

    @Test
    void activityUpdateFailureRollsBackProductIncrementAndLedgerInsert() {
        doReturn(0).when(activities).updateStockIncr(1L, 2);
        assertError(POINT_ACTIVITY_RETURN_STOCK_FAIL, () -> service.returnPointStock(1L, 1L, 2, 280L));
        assertState(10, 10, 0);
        doCallRealMethod().when(activities).updateStockIncr(1L, 2);
        service.returnPointStock(1L, 1L, 2, 280L);
        assertState(12, 12, 1);
    }

    @Test
    void differentTenantCannotRestoreForeignActivityOrConsumeItsLedger() {
        service.returnPointStock(1L, 1L, 2, 280L);
        TenantContextHolder.setTenantId(2L);
        assertError(POINT_ACTIVITY_NOT_EXISTS, () -> service.returnPointStock(1L, 1L, 2, 280L));
        service.returnPointStock(2L, 2L, 3, 280L);
        assertEquals(33, jdbc.queryForObject("SELECT stock FROM promotion_point_activity WHERE id=2", Integer.class));
        assertState(12, 12, 2);
    }

    @Test
    void concurrentIdenticalRetriesProduceOneReturnAndOneLedger() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentReturn(start));
            var second = executor.submit(() -> concurrentReturn(start));
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertState(12, 12, 1);
    }

    @Test
    void stockDecrementLocksActivityBeforeProductMutationLikeStockReturn() {
        service.updatePointStockDecr(1L, 1L, 3);
        var lockOrder = inOrder(activities, products);
        lockOrder.verify(activities).selectByIdForUpdate(1L);
        lockOrder.verify(products).selectListByActivityIdAndSkuId(1L, 1L);
        lockOrder.verify(products).updateStockDecr(11L, 3);
        lockOrder.verify(activities).updateStockDecr(1L, 3);
        assertState(7, 7, 0);
    }

    @Test
    void returnWaitsForActivityLockHeldByConcurrentDecrement() throws Exception {
        var decrementBeforeProduct = new CountDownLatch(1);
        var releaseDecrement = new CountDownLatch(1);
        var returnAttempted = new CountDownLatch(1);
        var returnAcquiredActivity = new CountDownLatch(1);
        var returnThread = new AtomicReference<Thread>();
        doAnswer(invocation -> {
            decrementBeforeProduct.countDown();
            assertTrue(releaseDecrement.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(products).updateStockDecr(11L, 3);
        doAnswer(invocation -> {
            boolean returning = Thread.currentThread() == returnThread.get();
            if (returning) {
                returnAttempted.countDown();
            }
            Object activity = invocation.callRealMethod();
            if (returning) {
                returnAcquiredActivity.countDown();
            }
            return activity;
        }).when(activities).selectByIdForUpdate(1L);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var decrement = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try {
                    service.updatePointStockDecr(1L, 1L, 3);
                } finally {
                    TenantContextHolder.clear();
                }
            });
            assertTrue(decrementBeforeProduct.await(5, TimeUnit.SECONDS));
            var returned = executor.submit(() -> {
                returnThread.set(Thread.currentThread());
                concurrentReturn(new CountDownLatch(0));
            });
            boolean waitedForActivity;
            try {
                assertTrue(returnAttempted.await(5, TimeUnit.SECONDS));
                waitedForActivity = !returnAcquiredActivity.await(200, TimeUnit.MILLISECONDS);
            } finally {
                releaseDecrement.countDown();
            }
            decrement.get(15, TimeUnit.SECONDS);
            returned.get(15, TimeUnit.SECONDS);
            assertTrue(waitedForActivity, "返库不得在扣库完成前越过同一活动行锁");
        }
        assertState(9, 9, 1);
    }

    @Test
    void failedActivityDecrementRollsBackProductAndDoesNotBlockSubsequentReturn() {
        doReturn(0).when(activities).updateStockDecr(1L, 3);
        assertError(POINT_ACTIVITY_UPDATE_STOCK_FAIL, () -> service.updatePointStockDecr(1L, 1L, 3));
        assertState(10, 10, 0);
        service.returnPointStock(1L, 1L, 2, 280L);
        assertState(12, 12, 1);
    }

    @Test
    void decrementRejectsInvalidRequestMissingActivityAndNullStockWithoutMutation() {
        for (Integer count : new Integer[]{null, 0, -1}) {
            assertError(POINT_ACTIVITY_UPDATE_STOCK_FAIL, () -> service.updatePointStockDecr(1L, 1L, count));
        }
        assertError(POINT_ACTIVITY_NOT_EXISTS, () -> service.updatePointStockDecr(99L, 1L, 2));
        assertError(POINT_ACTIVITY_UPDATE_STOCK_FAIL, () -> service.updatePointStockDecr(1L, 920005L, 2));
        assertState(10, 10, 0);
        jdbc.update("UPDATE promotion_point_product SET stock=NULL WHERE id=11");
        assertError(POINT_ACTIVITY_UPDATE_STOCK_FAIL, () -> service.updatePointStockDecr(1L, 1L, 2));
        assertEquals(10, jdbc.queryForObject("SELECT stock FROM promotion_point_activity WHERE id=1", Integer.class));
        assertEquals(0, ledgerCount());
    }

    @Test
    void invalidOrOverflowingStockFailsWithoutPartialReturn() {
        jdbc.update("UPDATE promotion_point_product SET stock=NULL WHERE id=11");
        assertError(POINT_ACTIVITY_RETURN_STOCK_FAIL, () -> service.returnPointStock(1L, 1L, 2, 280L));
        jdbc.update("UPDATE promotion_point_product SET stock=? WHERE id=11", Integer.MAX_VALUE);
        assertError(POINT_ACTIVITY_RETURN_STOCK_FAIL, () -> service.returnPointStock(1L, 1L, 2, 280L));
        jdbc.update("UPDATE promotion_point_product SET stock=10 WHERE id=11");
        jdbc.update("UPDATE promotion_point_activity SET stock=-1 WHERE id=1");
        assertError(POINT_ACTIVITY_RETURN_STOCK_FAIL, () -> service.returnPointStock(1L, 1L, 2, 280L));
        assertEquals(0, ledgerCount());
        assertEquals(10, jdbc.queryForObject("SELECT stock FROM promotion_point_product WHERE id=11", Integer.class));
    }

    private void concurrentReturn(CountDownLatch start) {
        TenantContextHolder.setTenantId(1L);
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            service.returnPointStock(1L, 1L, 2, 280L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void assertState(int activityStock, int productStock, int records) {
        assertEquals(activityStock, jdbc.queryForObject("SELECT stock FROM promotion_point_activity WHERE id=1", Integer.class));
        assertEquals(productStock, jdbc.queryForObject("SELECT stock FROM promotion_point_product WHERE id=11", Integer.class));
        assertEquals(records, ledgerCount());
    }

    private int ledgerCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_point_stock_return", Integer.class);
    }

    private void assertError(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected.getCode(), assertThrows(ServiceException.class, action).getCode());
    }
}
