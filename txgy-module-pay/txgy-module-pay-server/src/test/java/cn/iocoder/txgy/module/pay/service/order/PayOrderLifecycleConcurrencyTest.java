package cn.iocoder.txgy.module.pay.service.order;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import cn.iocoder.txgy.module.pay.dal.mysql.order.PayOrderMapper;
import cn.iocoder.txgy.module.pay.dal.mysql.order.PayOrderExtensionMapper;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.txgy.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO;
import cn.iocoder.txgy.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.txgy.module.pay.dal.redis.no.PayNoRedisDAO;
import cn.iocoder.txgy.module.pay.framework.pay.config.PayProperties;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.PayClient;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.txgy.module.pay.service.app.PayAppService;
import cn.iocoder.txgy.module.pay.service.channel.PayChannelService;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual database row locks, independent JDBC connections and Spring transaction advice. No remote writes. */
class PayOrderLifecycleConcurrencyTest {
    private JdbcTemplate jdbc;
    private PayOrderMapper orders;
    private PayOrderExtensionMapper attempts;
    private PayOrderLifecycleService service;
    private DataSourceTransactionManager transactions;
    private final AtomicLong nextId = new AtomicLong(10);
    private volatile Thread heldThread;
    private volatile CountDownLatch locked;
    private volatile CountDownLatch release;

    @BeforeEach void setup() {
        String mysql = System.getenv("MALL_PAY_LIFECYCLE_TEST_JDBC");
        DriverManagerDataSource source = new DriverManagerDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                connection.setTransactionIsolation(mysql == null ? Connection.TRANSACTION_READ_COMMITTED
                        : Connection.TRANSACTION_REPEATABLE_READ);
                return connection;
            }
        };
        if (mysql != null) {
            assertTrue(mysql.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/mall_pay_lifecycle_test_20260917(?:\\?.*)?"),
                    "Only the exact disposable payment lifecycle test database is permitted");
            source.setUrl(mysql);
            source.setUsername(System.getenv("MALL_PAY_LIFECYCLE_TEST_USER"));
            source.setPassword(System.getenv("MALL_PAY_LIFECYCLE_TEST_PASSWORD"));
        } else source.setUrl("jdbc:h2:mem:pay-lifecycle-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS lifecycle_test_order (id BIGINT PRIMARY KEY,app_id BIGINT NOT NULL,"
                + "merchant_order_id VARCHAR(60) NOT NULL,status INT NOT NULL,price INT NOT NULL,expiry TIMESTAMP NOT NULL,extension_id BIGINT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS lifecycle_test_attempt (id BIGINT PRIMARY KEY,order_id BIGINT NOT NULL,"
                + "status INT NOT NULL,submit_state INT NULL)");
        clear();
        jdbc.update("INSERT INTO lifecycle_test_order(id,app_id,merchant_order_id,status,price,expiry) VALUES(1,3,'mall-8',0,1000,?)",
                LocalDateTime.now().plusHours(1).withNano(0));
        orders = mock(PayOrderMapper.class);
        attempts = mock(PayOrderExtensionMapper.class);
        when(orders.selectByIdForUpdate(anyLong())).thenAnswer(call -> {
            assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                    org.springframework.jdbc.datasource.DataSourceUtils.getConnection(source).getTransactionIsolation());
            PayOrderDO value = read(call.getArgument(0), true);
            if (Thread.currentThread() == heldThread) {
                locked.countDown();
                assertTrue(release.await(8, TimeUnit.SECONDS), "test must release held SQL row lock");
            }
            return value;
        });
        when(orders.selectById(anyLong())).thenAnswer(call -> read(call.getArgument(0), false));
        when(orders.updateByIdAndStatus(anyLong(), anyInt(), any())).thenAnswer(call ->
                jdbc.update("UPDATE lifecycle_test_order SET status=COALESCE(?,status),price=COALESCE(?,price) WHERE id=? AND status=?",
                        ((PayOrderDO) call.getArgument(2)).getStatus(), ((PayOrderDO) call.getArgument(2)).getPrice(),
                        call.getArgument(0), call.getArgument(1)));
        when(attempts.selectListByOrderId(anyLong())).thenAnswer(call ->
                jdbc.query("SELECT * FROM lifecycle_test_attempt WHERE order_id=?", (rs, row) ->
                        new PayOrderExtensionDO().setId(rs.getLong("id")).setOrderId(rs.getLong("order_id"))
                                .setStatus(rs.getInt("status")).setSubmitState((Integer) rs.getObject("submit_state")),
                        call.getArgument(0, Long.class)));
        when(attempts.insert(any(PayOrderExtensionDO.class))).thenAnswer(call -> {
            PayOrderExtensionDO value = call.getArgument(0);
            value.setId(nextId.incrementAndGet());
            return jdbc.update("INSERT INTO lifecycle_test_attempt VALUES(?,?,?,?)",
                    value.getId(), value.getOrderId(), value.getStatus(), value.getSubmitState());
        });
        when(attempts.updateSubmitStateIfPrepared(anyLong(), anyLong(), anyInt())).thenAnswer(call ->
                jdbc.update("UPDATE lifecycle_test_attempt SET submit_state=? WHERE id=? AND order_id=? AND submit_state=0",
                        call.getArgument(2), call.getArgument(0), call.getArgument(1)));
        PayOrderLifecycleService target = new PayOrderLifecycleService();
        ReflectionTestUtils.setField(target, "orderMapper", orders);
        ReflectionTestUtils.setField(target, "orderExtensionMapper", attempts);
        transactions = new DataSourceTransactionManager(source);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (PayOrderLifecycleService) proxy.getProxy();
    }

    @AfterEach void cleanup() {
        heldThread = null;
        if (release != null) release.countDown();
        clear();
    }

    private void clear() {
        jdbc.update("DELETE FROM lifecycle_test_attempt");
        jdbc.update("DELETE FROM lifecycle_test_order");
    }

    private PayOrderDO read(Long id, boolean lock) {
        var values = jdbc.query("SELECT * FROM lifecycle_test_order WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new PayOrderDO().setId(rs.getLong("id")).setAppId(rs.getLong("app_id"))
                        .setMerchantOrderId(rs.getString("merchant_order_id")).setStatus(rs.getInt("status"))
                        .setPrice(rs.getInt("price")).setExpireTime(rs.getTimestamp("expiry").toLocalDateTime())
                        .setExtensionId(rs.getObject("extension_id", Long.class)), id);
        return values.isEmpty() ? null : values.getFirst();
    }

    private PayOrderExtensionDO attempt() {
        return new PayOrderExtensionDO().setOrderId(1L).setStatus(0).setChannelId(9L).setNo("test");
    }

    private void concurrent(Callable<?> first, Callable<?> second, boolean secondThrows) throws Exception {
        locked = new CountDownLatch(1);
        release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<?> a = workers.submit(() -> { heldThread = Thread.currentThread(); return first.call(); });
            assertTrue(locked.await(8, TimeUnit.SECONDS));
            Future<?> b = workers.submit(() -> { secondStarted.countDown(); return second.call(); });
            assertTrue(secondStarted.await(8, TimeUnit.SECONDS));
            try {
                assertThrows(TimeoutException.class, () -> b.get(150, TimeUnit.MILLISECONDS), "second SQL gate must wait for first row lock");
            } finally { release.countDown(); }
            a.get(10, TimeUnit.SECONDS);
            if (secondThrows) assertInstanceOf(ServiceException.class,
                    assertThrows(ExecutionException.class, () -> b.get(10, TimeUnit.SECONDS)).getCause());
            else b.get(10, TimeUnit.SECONDS);
        } finally { heldThread = null; release.countDown(); }
    }

    @Test void closingFirstRejectsLatePrepareAfterRealLockWait() throws Exception {
        PayOrderDO stale = read(1L, false);
        concurrent(() -> { assertTrue(service.closeIfAttemptsClosed(stale)); return null; },
                () -> { service.prepare(stale, attempt()); return null; }, true);
        assertEquals(30, read(1L, false).getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt", Integer.class));
    }

    @Test void preparingFirstMakesConcurrentCloseFailAfterRealLockWait() throws Exception {
        PayOrderDO stale = read(1L, false);
        concurrent(() -> { service.prepare(stale, attempt()); return null; },
                () -> { assertFalse(service.closeIfAttemptsClosed(stale)); return null; }, false);
        assertEquals(0, read(1L, false).getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt WHERE submit_state=0", Integer.class));
    }

    @Test void concurrentSecondPrepareSeesCommittedPreparedAttemptAfterRealLockWait() throws Exception {
        PayOrderDO stale = read(1L, false);
        concurrent(() -> { service.prepare(stale, attempt()); return null; },
                () -> { service.prepare(stale, attempt()); return null; }, true);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt", Integer.class));
    }

    @Test void unknownAndLegacyNullAlsoRejectFreshPrepareWithoutNewAttempt() {
        PayOrderDO expected = read(1L, false);
        PayOrderExtensionDO first = attempt();
        service.prepare(expected, first);
        service.finishDispatch(expected, first.getId(), false);
        assertThrows(ServiceException.class, () -> service.prepare(expected, attempt()));
        jdbc.update("UPDATE lifecycle_test_attempt SET submit_state=NULL");
        assertThrows(ServiceException.class, () -> service.prepare(expected, attempt()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt", Integer.class));
    }

    @Test void oldCallerSnapshotCannotHideNewlyCommittedAttempt() {
        PayOrderDO stale = read(1L, false);
        TransactionTemplate outer = new TransactionTemplate(transactions);
        outer.setIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
        outer.executeWithoutResult(status -> {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt", Integer.class));
            service.prepare(stale, attempt()); // requires-new commits independently
            assertFalse(service.closeIfAttemptsClosed(stale)); // must not inherit caller's old snapshot
            status.setRollbackOnly();
        });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt", Integer.class));
    }

    @Test void preparedAttemptCannotCloseEvenIfChannelWasNotFound() {
        PayOrderDO expected = read(1L, false);
        service.prepare(expected, attempt());
        jdbc.update("UPDATE lifecycle_test_attempt SET status=30");
        assertFalse(service.closeIfAttemptsClosed(expected));
    }

    @Test void unknownAndLegacyNullRemainUnconfirmedEvenIfClosed() {
        PayOrderDO expected = read(1L, false);
        PayOrderExtensionDO attempt = attempt();
        service.prepare(expected, attempt);
        service.finishDispatch(expected, attempt.getId(), false);
        jdbc.update("UPDATE lifecycle_test_attempt SET status=30");
        assertFalse(service.closeIfAttemptsClosed(expected));
        jdbc.update("UPDATE lifecycle_test_attempt SET submit_state=NULL");
        assertFalse(service.closeIfAttemptsClosed(expected));
    }

    @Test void normalDispatchAndExplicitClosedMayCloseButWaitingCannot() {
        PayOrderDO expected = read(1L, false);
        PayOrderExtensionDO attempt = attempt();
        service.prepare(expected, attempt);
        service.finishDispatch(expected, attempt.getId(), true);
        assertFalse(service.closeIfAttemptsClosed(expected));
        jdbc.update("UPDATE lifecycle_test_attempt SET status=30");
        assertTrue(service.closeIfAttemptsClosed(expected));
        assertTrue(service.closeIfAttemptsClosed(expected));
    }

    @Test void newAttemptBetweenChannelCheckAndFinalCloseIsNotMissed() {
        PayOrderDO expected = read(1L, false);
        PayOrderExtensionDO first = attempt();
        service.prepare(expected, first);
        service.finishDispatch(expected, first.getId(), true);
        jdbc.update("UPDATE lifecycle_test_attempt SET status=30");
        service.prepare(expected, attempt());
        assertFalse(service.closeIfAttemptsClosed(expected));
    }

    @Test void changedPriceOrBusinessOrExpiredOrderRejectsPrepare() {
        PayOrderDO expected = read(1L, false);
        jdbc.update("UPDATE lifecycle_test_order SET price=2000");
        assertThrows(ServiceException.class, () -> service.prepare(expected, attempt()));
        jdbc.update("UPDATE lifecycle_test_order SET price=1000,app_id=99");
        assertThrows(ServiceException.class, () -> service.prepare(expected, attempt()));
        jdbc.update("UPDATE lifecycle_test_order SET app_id=3,merchant_order_id='wrong'");
        assertThrows(ServiceException.class, () -> service.prepare(expected, attempt()));
        jdbc.update("UPDATE lifecycle_test_order SET merchant_order_id='mall-8',expiry=?", LocalDateTime.now().minusSeconds(1));
        assertThrows(ServiceException.class, () -> service.prepare(read(1L, false), attempt()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt", Integer.class));
    }

    @Test void priceChangeOrSuccessfulCallbackCannotBeOverwrittenByClose() {
        PayOrderDO expected = read(1L, false);
        jdbc.update("UPDATE lifecycle_test_order SET price=2000");
        assertFalse(service.closeIfAttemptsClosed(expected));
        jdbc.update("UPDATE lifecycle_test_order SET price=1000,status=10");
        assertFalse(service.closeIfAttemptsClosed(expected));
        assertEquals(10, read(1L, false).getStatus());
    }

    @Test void attemptSurvivesOuterTransactionFailureAndUnknownMarkerCannotBePromoted() {
        PayOrderDO expected = read(1L, false);
        PayOrderExtensionDO attempt = attempt();
        TransactionTemplate outer = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> outer.executeWithoutResult(status -> {
            service.prepare(expected, attempt);
            throw new IllegalStateException("simulated external failure");
        }));
        service.finishDispatch(expected, attempt.getId(), false);
        assertEquals(2, jdbc.queryForObject("SELECT submit_state FROM lifecycle_test_attempt", Integer.class));
        assertThrows(ServiceException.class, () -> service.finishDispatch(expected, attempt.getId(), true));
        assertFalse(service.closeIfAttemptsClosed(expected));
    }

    @Test void failedMarkerUpdateLeavesPreparedAndPreventsClose() {
        PayOrderDO expected = read(1L, false);
        PayOrderExtensionDO attempt = attempt();
        service.prepare(expected, attempt);
        assertThrows(ServiceException.class, () -> service.finishDispatch(expected, 999L, true));
        assertEquals(0, jdbc.queryForObject("SELECT submit_state FROM lifecycle_test_attempt", Integer.class));
        assertFalse(service.closeIfAttemptsClosed(expected));
    }

    @Test void walletSuccessPausedBeforeNotificationKeepsSecondPreparedSubmitBlocked() throws Exception {
        PayOrderDO staleBeforeFirstSubmit = read(1L, false);
        PayOrderServiceImpl submission = new PayOrderServiceImpl();
        PayOrderServiceImpl notification = mock(PayOrderServiceImpl.class);
        PayChannelService channels = mock(PayChannelService.class);
        PayClient<?> walletClient = mock(PayClient.class);
        PayProperties properties = mock(PayProperties.class);
        PayNoRedisDAO numbers = mock(PayNoRedisDAO.class);
        when(properties.getOrderNotifyUrl()).thenReturn("http://localhost");
        when(numbers.generate(any())).thenReturn("attempt-11");
        when(channels.validPayChannel(3L, "wallet")).thenReturn(new PayChannelDO().setId(9L).setCode("wallet"));
        doReturn(walletClient).when(channels).getPayClient(9L);
        AtomicInteger walletResponses = new AtomicInteger();
        when(walletClient.unifiedOrder(any())).thenAnswer(call -> {
            walletResponses.incrementAndGet();
            return PayOrderRespDTO.successOf("synthetic-wallet-debit", "316", LocalDateTime.now(), "attempt-11", null);
        });
        ReflectionTestUtils.setField(submission, "orderMapper", orders);
        ReflectionTestUtils.setField(submission, "orderExtensionMapper", attempts);
        ReflectionTestUtils.setField(submission, "lifecycleService", service);
        ReflectionTestUtils.setField(submission, "channelService", channels);
        ReflectionTestUtils.setField(submission, "appService", mock(PayAppService.class));
        ReflectionTestUtils.setField(submission, "payProperties", properties);
        ReflectionTestUtils.setField(submission, "noRedisDAO", numbers);
        CountDownLatch notifyEntered = new CountDownLatch(1);
        CountDownLatch finishNotify = new CountDownLatch(1);
        doAnswer(call -> {
            notifyEntered.countDown();
            assertTrue(finishNotify.await(8, TimeUnit.SECONDS));
            jdbc.update("UPDATE lifecycle_test_attempt SET status=10 WHERE id=11");
            jdbc.update("UPDATE lifecycle_test_order SET status=10,extension_id=11 WHERE id=1");
            return null;
        }).when(notification).notifyOrder(any(PayChannelDO.class), any());
        var worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> result = worker.submit(() -> {
                try (var beans = mockStatic(SpringUtil.class)) {
                    beans.when(() -> SpringUtil.getBean(PayOrderServiceImpl.class)).thenReturn(notification);
                    assertEquals(10, submission.submitOrder(new PayOrderSubmitReqVO().setId(1L)
                            .setChannelCode("wallet"), "127.0.0.1").getStatus());
                }
            });
            assertTrue(notifyEntered.await(8, TimeUnit.SECONDS));
            assertEquals(0, jdbc.queryForObject("SELECT submit_state FROM lifecycle_test_attempt WHERE id=11", Integer.class));
            assertEquals(0, read(1L, false).getStatus());
            // B 已在 A 之前预检完成：只重演 prepare，仍须被当前 PREPARED 数据挡住。
            assertThrows(ServiceException.class, () -> service.prepare(staleBeforeFirstSubmit, attempt()));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt", Integer.class));
            finishNotify.countDown();
            result.get(10, TimeUnit.SECONDS);
            assertEquals(1, walletResponses.get());
            assertEquals(10, read(1L, false).getStatus());
            assertEquals(1, jdbc.queryForObject("SELECT submit_state FROM lifecycle_test_attempt WHERE id=11", Integer.class));
        } finally {
            finishNotify.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test void priceUpdateWinsLockAndRejectsAlreadyValidatedOldPricePrepare() throws Exception {
        PayOrderDO stale = read(1L, false);
        concurrent(() -> { service.updatePrice(1L, 2000); return null; },
                () -> { service.prepare(stale, attempt()); return null; }, true);
        assertEquals(2000, read(1L, false).getPrice());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt", Integer.class));
        service.prepare(read(1L, false), attempt()); // 刷新价格后的正常提交仍合法
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_test_attempt", Integer.class));
    }

    @Test void prepareWinsLockAndPriceCannotChangeAfterPaymentStarts() throws Exception {
        PayOrderDO stale = read(1L, false);
        concurrent(() -> { service.prepare(stale, attempt()); return null; },
                () -> { service.updatePrice(1L, 2000); return null; }, true);
        assertEquals(1000, read(1L, false).getPrice());
    }

    @Test void anyAttemptStatusAndAnySubmitStatePreventChangingPrice() {
        for (int status : new int[]{0, 10, 20, 30}) {
            for (Integer state : new Integer[]{null, 0, 1, 2}) {
                jdbc.update("DELETE FROM lifecycle_test_attempt");
                jdbc.update("INSERT INTO lifecycle_test_attempt VALUES(11,1,?,?)", status, state);
                assertThrows(ServiceException.class, () -> service.updatePrice(1L, 2000));
                assertEquals(1000, read(1L, false).getPrice());
            }
        }
    }

    @Test void onlyWaitingOrdersAllowSamePriceIdempotencyEvenWithAttempts() {
        jdbc.update("INSERT INTO lifecycle_test_attempt VALUES(11,1,0,NULL)");
        service.updatePrice(1L, 1000);
        verify(orders, never()).updateByIdAndStatus(anyLong(), anyInt(), any());
        for (int status : new int[]{10, 20, 30}) {
            jdbc.update("UPDATE lifecycle_test_order SET status=?", status);
            assertThrows(ServiceException.class, () -> service.updatePrice(1L, 1000));
            assertThrows(ServiceException.class, () -> service.updatePrice(1L, 2000));
        }
    }

    @Test void priceRequiresPositiveCentsAndExistingOrderAndPreservesDataOnRejection() {
        for (Integer value : new Integer[]{null, 0, -1}) assertThrows(ServiceException.class, () -> service.updatePrice(1L, value));
        assertThrows(ServiceException.class, () -> service.updatePrice(999L, 2000));
        assertThrows(ServiceException.class, () -> service.updatePrice(null, 2000));
        assertThrows(ServiceException.class, () -> service.updatePrice(0L, 2000));
        assertEquals(1000, read(1L, false).getPrice());
        service.updatePrice(1L, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, read(1L, false).getPrice());
    }
}
