package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderUpdatePriceReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.framework.order.core.utils.TradeOrderLogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual local SQL rollback when the payment price guard rejects a mall adjustment. No business database. */
class TradeOrderPriceRollbackTest {
    private JdbcTemplate jdbc;
    private PayOrderApi payment;
    private TradeOrderUpdateServiceImpl service;

    @BeforeEach void setup() {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:price-rollback-"
                + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE fixture_order(id BIGINT PRIMARY KEY,pay_price INT NOT NULL,adjust_price INT NOT NULL)");
        jdbc.execute("CREATE TABLE fixture_item(id BIGINT PRIMARY KEY,pay_price INT NOT NULL,adjust_price INT NOT NULL)");
        jdbc.update("INSERT INTO fixture_order VALUES(1,1000,0)");
        jdbc.update("INSERT INTO fixture_item VALUES(11,600,0),(12,400,0)");
        TradeOrderMapper orders = mock(TradeOrderMapper.class);
        TradeOrderItemMapper items = mock(TradeOrderItemMapper.class);
        payment = mock(PayOrderApi.class);
        when(orders.selectById(1L)).thenAnswer(call -> jdbc.queryForObject("SELECT * FROM fixture_order WHERE id=1",
                (rs, row) -> new TradeOrderDO().setId(1L).setPayOrderId(7L).setPayStatus(false).setStatus(0)
                        .setPayPrice(rs.getInt("pay_price")).setAdjustPrice(rs.getInt("adjust_price"))));
        when(orders.updateById(any(TradeOrderDO.class))).thenAnswer(call -> {
            TradeOrderDO update = call.getArgument(0);
            return jdbc.update("UPDATE fixture_order SET pay_price=?,adjust_price=? WHERE id=?",
                    update.getPayPrice(), update.getAdjustPrice(), update.getId());
        });
        when(items.selectListByOrderId(1L)).thenAnswer(call -> jdbc.query("SELECT * FROM fixture_item ORDER BY id",
                (rs, row) -> new TradeOrderItemDO().setId(rs.getLong("id")).setOrderId(1L)
                        .setPayPrice(rs.getInt("pay_price")).setAdjustPrice(rs.getInt("adjust_price"))));
        when(items.updateBatch(anyCollection())).thenAnswer(call -> {
            Collection<TradeOrderItemDO> updates = call.getArgument(0);
            for (TradeOrderItemDO update : updates) jdbc.update("UPDATE fixture_item SET pay_price=?,adjust_price=? WHERE id=?",
                    update.getPayPrice(), update.getAdjustPrice(), update.getId());
            return true;
        });
        TradeOrderUpdateServiceImpl target = new TradeOrderUpdateServiceImpl();
        ReflectionTestUtils.setField(target, "tradeOrderMapper", orders);
        ReflectionTestUtils.setField(target, "tradeOrderItemMapper", items);
        ReflectionTestUtils.setField(target, "payOrderApi", payment);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        service = (TradeOrderUpdateServiceImpl) proxy.getProxy();
    }

    private void adjust() {
        try (var logs = mockStatic(TradeOrderLogUtils.class)) {
            service.updateOrderPrice(new TradeOrderUpdatePriceReqVO().setId(1L).setAdjustPrice(200));
        }
    }

    private void assertAmounts(int price, int adjustment) {
        assertEquals(price, jdbc.queryForObject("SELECT pay_price FROM fixture_order WHERE id=1", Integer.class));
        assertEquals(adjustment, jdbc.queryForObject("SELECT adjust_price FROM fixture_order WHERE id=1", Integer.class));
        assertEquals(price, jdbc.queryForObject("SELECT SUM(pay_price) FROM fixture_item", Integer.class));
        assertEquals(adjustment, jdbc.queryForObject("SELECT SUM(adjust_price) FROM fixture_item", Integer.class));
    }

    @Test void rejectedPaymentPriceChangeRollsBackOrderAndItems() {
        when(payment.updatePayOrderPrice(7L, 1200)).thenAnswer(call -> {
            assertAmounts(1200, 200); // writes really happened in the current local transaction
            return CommonResult.error(1_007_002_008, "Existing payment attempt");
        });
        assertThrows(ServiceException.class, this::adjust);
        assertAmounts(1000, 0);
    }

    @Test void failedPaymentRpcRollsBackOrderAndItems() {
        when(payment.updatePayOrderPrice(7L, 1200)).thenAnswer(call -> {
            assertAmounts(1200, 200);
            throw new IllegalStateException("Synthetic RPC failure");
        });
        assertThrows(IllegalStateException.class, this::adjust);
        assertAmounts(1000, 0);
    }

    @Test void acceptedPriceChangeCommitsOrderAndItems() {
        when(payment.updatePayOrderPrice(7L, 1200)).thenReturn(CommonResult.success(true));
        adjust();
        assertAmounts(1200, 200);
    }
}
