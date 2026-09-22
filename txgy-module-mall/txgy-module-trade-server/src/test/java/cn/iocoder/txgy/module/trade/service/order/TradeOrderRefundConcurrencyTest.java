package cn.iocoder.txgy.module.trade.service.order;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.member.api.level.MemberLevelApi;
import cn.iocoder.txgy.module.member.api.point.MemberPointApi;
import cn.iocoder.txgy.module.member.enums.point.MemberPointBizTypeEnum;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.service.order.handler.TradeOrderHandler;
import cn.iocoder.txgy.module.trade.service.order.handler.TradeMemberPointOrderHandler;
import cn.iocoder.txgy.module.trade.service.order.handler.TradeProductSkuOrderHandler;
import cn.iocoder.txgy.module.trade.service.aftersale.AfterSaleService;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real row locks / SQL / transaction proxy, mapper adapters and remote side effects are test doubles. */
class TradeOrderRefundConcurrencyTest {
    private JdbcTemplate jdbc;
    private TradeOrderMapper orders;
    private TradeOrderItemMapper items;
    private TradeOrderUpdateServiceImpl service;
    private TradeOrderUpdateServiceImpl target;
    private TradeOrderHandler handler;
    private volatile CyclicBarrier hints;
    private boolean beforeDelivery;
    private final Map<Long, AtomicInteger> compensated = new ConcurrentHashMap<>();

    @BeforeEach void setup() {
        String mysql = System.getenv("MALL_REFUND_TEST_JDBC");
        DriverManagerDataSource source = new DriverManagerDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                // H2 RR aborts a changed-snapshot lock with 40001 instead of MySQL's current-read
                // semantics. H2 verifies SQL/locking at its normal RC level; the isolated MySQL
                // run deliberately uses RR and the pre-lock snapshot barrier below.
                connection.setTransactionIsolation(mysql == null ? Connection.TRANSACTION_READ_COMMITTED
                        : Connection.TRANSACTION_REPEATABLE_READ);
                return connection;
            }
        };
        if (mysql != null) {
            assertTrue(mysql.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/mall_trade_refund_test_20260917(?:\\?.*)?"),
                    "Refund regression is only allowed on the exact disposable database");
            source.setUrl(mysql);
            source.setUsername(System.getenv("MALL_REFUND_TEST_USER"));
            source.setPassword(System.getenv("MALL_REFUND_TEST_PASSWORD"));
        } else {
            source.setUrl("jdbc:h2:mem:refund-" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        }
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS refund_test_order (tenant_id BIGINT NOT NULL,id BIGINT NOT NULL,"
                + "pay_price INT NOT NULL,refund_price INT NOT NULL,refund_point INT NOT NULL,refund_status INT NOT NULL,"
                + "status INT NOT NULL,PRIMARY KEY(tenant_id,id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS refund_test_item (tenant_id BIGINT NOT NULL,id BIGINT NOT NULL,"
                + "order_id BIGINT NOT NULL,pay_price INT NOT NULL,use_point INT NOT NULL,after_sale_status INT NOT NULL,"
                + "PRIMARY KEY(tenant_id,id))");
        clear();
        jdbc.update("INSERT INTO refund_test_order VALUES(1,1,10000,0,0,0,30)");
        jdbc.update("INSERT INTO refund_test_item VALUES(1,11,1,4000,4,10),(1,12,1,6000,6,10)");
        orders = mock(TradeOrderMapper.class);
        items = mock(TradeOrderItemMapper.class);
        handler = mock(TradeOrderHandler.class);
        when(items.selectById(anyLong())).thenAnswer(call -> {
            var list = queryItems("id=?", call.getArgument(0), false);
            CyclicBarrier wait = hints;
            if (wait != null) wait.await(8, TimeUnit.SECONDS);
            return list.isEmpty() ? null : list.getFirst();
        });
        when(orders.selectByIdForUpdate(anyLong())).thenAnswer(call -> queryOrder(call.getArgument(0), true));
        when(orders.selectById(anyLong())).thenAnswer(call -> queryOrder(call.getArgument(0), false));
        when(items.selectListByOrderIdForUpdate(anyLong())).thenAnswer(call -> queryItems("order_id=?", call.getArgument(0), true));
        when(items.selectListByOrderId(anyLong())).thenAnswer(call -> queryItems("order_id=?", call.getArgument(0), false));
        when(items.updateAfterSaleStatus(anyLong(), anyInt(), anyInt(), nullable(Long.class))).thenAnswer(call ->
                jdbc.update("UPDATE refund_test_item SET after_sale_status=? WHERE tenant_id=? AND id=? AND after_sale_status=?",
                        call.getArgument(2), tenant(), call.getArgument(0), call.getArgument(1)));
        when(orders.updateById(any(TradeOrderDO.class))).thenAnswer(call -> {
            TradeOrderDO value = call.getArgument(0);
            return jdbc.update("UPDATE refund_test_order SET refund_price=COALESCE(?,refund_price),"
                            + "refund_point=COALESCE(?,refund_point),refund_status=COALESCE(?,refund_status),status=COALESCE(?,status)"
                            + " WHERE tenant_id=? AND id=?", value.getRefundPrice(), value.getRefundPoint(),
                    value.getRefundStatus(), value.getStatus(), tenant(), value.getId());
        });
        doAnswer(call -> {
            TradeOrderItemDO value = call.getArgument(1);
            compensated.computeIfAbsent(value.getId(), key -> new AtomicInteger()).incrementAndGet();
            return null;
        }).when(handler).afterCancelOrderItem(any(), any());
        target = new TradeOrderUpdateServiceImpl();
        ReflectionTestUtils.setField(target, "tradeOrderMapper", orders);
        ReflectionTestUtils.setField(target, "tradeOrderItemMapper", items);
        ReflectionTestUtils.setField(target, "tradeOrderHandlers", List.of(handler));
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        service = (TradeOrderUpdateServiceImpl) factory.getProxy();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach void cleanup() {
        hints = null;
        TenantContextHolder.clear();
        clear();
    }

    private void clear() {
        jdbc.update("DELETE FROM refund_test_item");
        jdbc.update("DELETE FROM refund_test_order");
    }

    private long tenant() { return TenantContextHolder.getRequiredTenantId(); }

    private TradeOrderDO queryOrder(Long id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM refund_test_order WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new TradeOrderDO().setId(rs.getLong("id")).setUserId(100L).setPayPrice(rs.getInt("pay_price"))
                        .setRefundPrice(rs.getInt("refund_price")).setRefundPoint(rs.getInt("refund_point"))
                        .setRefundStatus(rs.getInt("refund_status")).setStatus(rs.getInt("status"))
                        .setPayStatus(true).setDeliveryTime(beforeDelivery ? null : LocalDateTime.now()), tenant(), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<TradeOrderItemDO> queryItems(String where, Long value, boolean lock) {
        return jdbc.query("SELECT * FROM refund_test_item WHERE tenant_id=? AND " + where
                        + " ORDER BY id" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new TradeOrderItemDO().setId(rs.getLong("id")).setOrderId(rs.getLong("order_id"))
                        .setPayPrice(rs.getInt("pay_price")).setUsePoint(rs.getInt("use_point"))
                        .setGivePoint(0).setAfterSaleId(rs.getLong("id"))
                        .setAfterSaleStatus(rs.getInt("after_sale_status")), tenant(), value);
    }

    private void refund(long tenant, long item, int amount) {
        TenantContextHolder.setTenantId(tenant);
        // getSelf() resolves to the same real transactional service, independently in each worker.
        try (var beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(TradeOrderUpdateServiceImpl.class)).thenReturn(service);
            service.updateOrderItemWhenAfterSaleSuccess(item, amount);
        } finally { TenantContextHolder.clear(); }
    }

    private void concurrent(long firstId, int firstAmount, long secondId, int secondAmount) throws Exception {
        hints = new CyclicBarrier(2); // Both transactions create their snapshot before either parent lock.
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> a = pool.submit(() -> refund(1, firstId, firstAmount));
            Future<?> b = pool.submit(() -> refund(1, secondId, secondAmount));
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally {
            hints = null;
            TenantContextHolder.setTenantId(1L);
        }
    }

    @Test void differentItemsConcurrentFullRefundAddsEveryCentAndPoint() throws Exception {
        concurrent(11, 4000, 12, 6000);
        TradeOrderDO order = queryOrder(1L, false);
        assertEquals(10000, order.getRefundPrice());
        assertEquals(10, order.getRefundPoint());
        assertEquals(20, order.getRefundStatus());
        assertEquals(40, order.getStatus());
        assertEquals(1, compensated.get(11L).get());
        assertEquals(1, compensated.get(12L).get());
        verify(handler).afterCancelOrder(argThat(value -> value.getRefundPrice()==10000
                && value.getRefundPoint()==10 && value.getRefundStatus()==20), anyList());
    }

    @Test void fullRefundCancellationUsesCurrentItemsAfterEarlierSnapshotSawOtherItemAsNone() throws Exception {
        beforeDelivery = true;
        jdbc.update("UPDATE refund_test_order SET status=10 WHERE id=1");
        jdbc.update("UPDATE refund_test_item SET after_sale_status=0 WHERE id=12");
        MemberPointApi points = mock(MemberPointApi.class);
        MemberLevelApi levels = mock(MemberLevelApi.class);
        AfterSaleService afterSales = mock(AfterSaleService.class);
        when(points.addPoint(anyLong(),anyInt(),anyInt(),anyString())).thenReturn(CommonResult.success(true));
        when(points.reducePoint(anyLong(),anyInt(),anyInt(),anyString())).thenReturn(CommonResult.success(true));
        when(levels.reduceExperience(anyLong(),anyInt(),anyInt(),anyString())).thenReturn(CommonResult.success(true));
        when(levels.addExperience(anyLong(),anyInt(),anyInt(),anyString())).thenReturn(CommonResult.success(true));
        when(afterSales.getAfterSale(anyLong())).thenAnswer(call -> new AfterSaleDO()
                .setRefundPrice(Objects.equals(call.getArgument(0),11L) ? 4000 : 6000));
        var pointHandler = new TradeMemberPointOrderHandler();
        ReflectionTestUtils.setField(pointHandler,"memberPointApi",points);
        ReflectionTestUtils.setField(pointHandler,"memberLevelApi",levels);
        ReflectionTestUtils.setField(pointHandler,"afterSaleService",afterSales);
        var inventoryHandler = new TradeProductSkuOrderHandler();
        ReflectionTestUtils.setField(target,"tradeOrderHandlers",List.of(handler,pointHandler,inventoryHandler));

        CountDownLatch originalSnapshot = new CountDownLatch(1), otherRefundCommitted = new CountDownLatch(1);
        when(items.selectById(11L)).thenAnswer(call -> {
            TradeOrderItemDO own = queryItems("id=?",11L,false).getFirst();
            assertEquals(0, queryItems("id=?",12L,false).getFirst().getAfterSaleStatus());
            originalSnapshot.countDown();
            assertTrue(otherRefundCommitted.await(10,TimeUnit.SECONDS));
            return own;
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?> lastRefund = pool.submit(() -> refund(1,11,4000));
            assertTrue(originalSnapshot.await(10,TimeUnit.SECONDS));
            // A different connection applies and finishes item 12 AFTER item 11's RR snapshot exists.
            jdbc.update("UPDATE refund_test_item SET after_sale_status=10 WHERE id=12");
            refund(1,12,6000);
            otherRefundCommitted.countDown();
            lastRefund.get(15,TimeUnit.SECONDS);
        } finally { otherRefundCommitted.countDown(); TenantContextHolder.setTenantId(1L); }

        assertEquals(10000,queryOrder(1L,false).getRefundPrice());
        assertEquals(10,queryOrder(1L,false).getRefundPoint());
        assertEquals(40,queryOrder(1L,false).getStatus());
        // Exercise the actual production handlers: exactly the two item compensations, no ORDER/CANCEL key.
        verify(points).addPoint(100L,4,MemberPointBizTypeEnum.ORDER_USE_CANCEL_ITEM.getType(),"11");
        verify(points).addPoint(100L,6,MemberPointBizTypeEnum.ORDER_USE_CANCEL_ITEM.getType(),"12");
        verify(points,never()).addPoint(anyLong(),anyInt(),eq(MemberPointBizTypeEnum.ORDER_USE_CANCEL.getType()),anyString());
        verify(points,times(2)).addPoint(anyLong(),anyInt(),anyInt(),anyString());
        // WMS 尚未提供预占，逐项退款和整单取消都不能发送库存释放请求。
        verify(levels,never()).addExperience(anyLong(),anyInt(),anyInt(),anyString());
        verify(handler).afterCancelOrder(argThat(value -> value.getRefundPrice()==10000
                && value.getRefundPoint()==10 && value.getRefundStatus()==20),
                argThat(values -> values.stream().allMatch(value -> value.getAfterSaleStatus()==20)));
        verify(items,never()).selectListByOrderId(anyLong());
    }

    @Test void differentItemsConcurrentPartialCashRefundKeepsExactTotal() throws Exception {
        concurrent(11, 1200, 12, 800);
        TradeOrderDO order = queryOrder(1L, false);
        assertEquals(2000, order.getRefundPrice());
        assertEquals(10, order.getRefundPoint());
        assertEquals(20, order.getRefundStatus());
        assertEquals(30, order.getStatus());
    }

    @Test void repeatedSameItemNeverRepeatsRemoteCompensation() {
        refund(1, 11, 4000);
        assertThrows(RuntimeException.class, () -> refund(1, 11, 4000));
        TenantContextHolder.setTenantId(1L);
        assertEquals(4000, queryOrder(1L, false).getRefundPrice());
        assertEquals(4, queryOrder(1L, false).getRefundPoint());
        assertEquals(1, compensated.get(11L).get());
    }

    @Test void racingDuplicateItemHasExactlyOneWinner() throws Exception {
        hints = new CyclicBarrier(2);
        AtomicInteger success = new AtomicInteger(), rejected = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> task = () -> {
                try { refund(1, 11, 4000); success.incrementAndGet(); }
                catch (RuntimeException expected) { rejected.incrementAndGet(); }
                return null;
            };
            Future<Void> a = pool.submit(task), b = pool.submit(task);
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
        } finally { hints = null; TenantContextHolder.setTenantId(1L); }
        assertEquals(1, success.get()); assertEquals(1, rejected.get());
        assertEquals(4000, queryOrder(1L, false).getRefundPrice());
        assertEquals(1, compensated.get(11L).get());
    }

    @Test void parentLockPrecedesEveryItemWriteAndCurrentRead() {
        refund(1, 11, 4000);
        var order = inOrder(items, orders);
        order.verify(items).selectById(11L);
        order.verify(orders).selectByIdForUpdate(1L);
        order.verify(items).selectListByOrderIdForUpdate(1L);
        order.verify(items).updateAfterSaleStatus(11L, 10, 20, null);
        verify(orders, never()).selectById(anyLong());
    }

    @Test void amountAboveItemOrOrderCannotRunSideEffects() {
        assertThrows(RuntimeException.class, () -> refund(1, 11, 4001));
        jdbc.update("UPDATE refund_test_order SET refund_price=8000 WHERE id=1");
        assertThrows(RuntimeException.class, () -> refund(1, 11, 4000));
        TenantContextHolder.setTenantId(1L);
        assertEquals(8000, queryOrder(1L, false).getRefundPrice());
        assertEquals(10, queryItems("id=?", 11L, false).getFirst().getAfterSaleStatus());
        verifyNoInteractions(handler);
    }

    @Test void centsAndPointsOverflowRejectBeforeCompensation() {
        jdbc.update("UPDATE refund_test_order SET pay_price=2147483647,refund_price=2147483600 WHERE id=1");
        assertThrows(ArithmeticException.class, () -> refund(1, 11, 100));
        jdbc.update("UPDATE refund_test_order SET refund_price=0,refund_point=2147483647 WHERE id=1");
        assertThrows(ArithmeticException.class, () -> refund(1, 11, 100));
        verifyNoInteractions(handler);
    }

    @Test void hookFailureRollsBackItemAndAggregateSoRetryCanFinish() {
        doThrow(new IllegalStateException("remote unavailable")).doNothing().when(handler).beforeCancelOrderItem(any(), any());
        assertThrows(IllegalStateException.class, () -> refund(1, 11, 4000));
        TenantContextHolder.setTenantId(1L);
        assertEquals(0, queryOrder(1L, false).getRefundPrice());
        assertEquals(10, queryItems("id=?", 11L, false).getFirst().getAfterSaleStatus());
        assertTrue(compensated.isEmpty());
        refund(1, 11, 4000);
        assertEquals(1, compensated.get(11L).get());
    }

    @Test void tenantBoundaryCannotLocateOtherTenantsItem() {
        assertThrows(RuntimeException.class, () -> refund(2, 11, 4000));
        verifyNoInteractions(handler);
        TenantContextHolder.setTenantId(1L);
        assertEquals(0, queryOrder(1L, false).getRefundPrice());
    }

    @Test void negativeCashRefundIsRejectedBeforeItemMutation() {
        assertThrows(RuntimeException.class, () -> refund(1, 11, -1));
        verifyNoInteractions(handler);
        TenantContextHolder.setTenantId(1L);
        assertEquals(10, queryItems("id=?", 11L, false).getFirst().getAfterSaleStatus());
    }

    @Test void zeroCashRefundPreservesAmountsAndAddsOnlyItemPoints() {
        refund(1, 11, 0);
        TenantContextHolder.setTenantId(1L);
        assertEquals(0, queryOrder(1L, false).getRefundPrice());
        assertEquals(4, queryOrder(1L, false).getRefundPoint());
        assertEquals(10, queryOrder(1L, false).getRefundStatus());
    }
}
