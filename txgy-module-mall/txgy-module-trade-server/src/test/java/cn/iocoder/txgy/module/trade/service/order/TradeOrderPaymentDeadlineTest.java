package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderDetailRespVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderPageItemRespVO;
import cn.iocoder.txgy.module.trade.convert.order.TradeOrderConvert;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeOrderPaymentDeadlineTest {
    TradeOrderProperties properties;
    PayOrderApi payApi;
    TradeOrderPaymentDeadlineService deadlines;
    TradeOrderDO order;
    LocalDateTime now;

    @BeforeEach
    void setup() {
        properties = new TradeOrderProperties();
        properties.setPayExpireTime(Duration.ofHours(2));
        payApi = mock(PayOrderApi.class);
        deadlines = new TradeOrderPaymentDeadlineService(properties, payApi);
        now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        order = new TradeOrderDO().setId(1L).setUserId(9L).setPayStatus(false).setStatus(0)
                .setPayPrice(100);
        order.setCreateTime(now.minusMinutes(30));
    }

    @Test
    void newOrderSnapshotsOnlyPaymentDuration() {
        deadlines.initialize(order, now);
        assertEquals(7200L, order.getPayExpireSeconds());
        assertNull(order.getErpWaitExpireTime());
        assertNull(order.getPayExpireTime());
    }

    @Test
    void fractionalSecondsAreNormalizedBeforePaymentDeadlineIsPersisted() {
        LocalDateTime fractional = LocalDateTime.of(2026, 9, 15, 12, 0, 0, 600_000_000);
        deadlines.initialize(order, fractional);
        assertEquals(7200L, order.getPayExpireSeconds());
        assertEquals(LocalDateTime.of(2026, 9, 15, 14, 0), deadlines.startPayment(order, fractional));
        assertEquals(0, order.getPayExpireTime().getNano());
    }

    @Test
    void paymentWindowStartsOnlyOnce() {
        deadlines.initialize(order, now);
        properties.setPayExpireTime(Duration.ofHours(12));
        LocalDateTime first = deadlines.startPayment(order, now.plusMinutes(40));
        assertEquals(now.plusMinutes(160), first);
        assertEquals(first, deadlines.startPayment(order, now.plusMinutes(60)));
    }

    @Test
    void invalidConfiguredDurationsAreRejectedBeforeOrderCreation() {
        properties.setPayExpireTime(Duration.ZERO);
        assertThrows(IllegalStateException.class, () -> deadlines.initialize(order, now));
    }

    @Test
    void missingPaymentOrderHasNoCountdownAndCannotPay() {
        deadlines.initialize(order, now);
        var response = new AppTradeOrderPageItemRespVO();
        deadlines.fill(order, response, false);
        assertEquals("PAY_ORDER_MISSING", response.getPaymentBlockReason());
        assertFalse(response.getCanPay());
        assertNull(response.getPayExpireTimestamp());
        assertNull(response.getErpWaitExpireTimestamp());
        assertTrue(Math.abs(System.currentTimeMillis() - response.getServerTime()) < 2000);
        verifyNoInteractions(payApi);
    }

    @Test
    void newOrderListReadsSnapshotWithoutPerRowRpc() {
        deadlines.initialize(order, now);
        deadlines.startPayment(order, now);
        order.setPayOrderId(2L);
        var response = new AppTradeOrderPageItemRespVO();
        deadlines.fill(order, response, false);
        assertTrue(response.getCanPay());
        assertEquals(TradeOrderPaymentDeadlineService.timestamp(order.getPayExpireTime()), response.getPayExpireTimestamp());
        verifyNoInteractions(payApi);
    }

    @Test
    void oldOrderUsesPersistedPaymentDeadlineNotCurrentConfiguration() {
        order.setPayOrderId(2L);
        LocalDateTime historicalDeadline = now.plusMinutes(10);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(historicalDeadline, 0)));
        properties.setPayExpireTime(Duration.ofDays(2));
        var response = new AppTradeOrderPageItemRespVO();
        deadlines.fill(order, response, false);
        assertEquals(historicalDeadline, response.getPayExpireTime());
        assertTrue(response.getCanPay());
        assertNull(order.getPayExpireTime()); // 只读兼容，不擅自回填旧单
        assertNull(order.getPayExpireSeconds());
    }

    @Test
    void unverifiedLegacyPaymentDoesNotFabricateDeadlineOrCanPay() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenThrow(new IllegalStateException("unavailable"));
        var response = new AppTradeOrderDetailRespVO();
        deadlines.fill(order, response, true);
        assertEquals("PAYMENT_UNCONFIRMED", response.getPaymentBlockReason());
        assertFalse(response.getCanPay());
        assertNull(response.getPayExpireTimestamp());
    }

    @Test
    void wrongMerchantOrPriceIsNotTrusted() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(now.plusMinutes(1), 0).setMerchantOrderId("999")));
        assertThrows(RuntimeException.class, () -> deadlines.getVerifiedPayOrder(order));
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(now.plusMinutes(1), 0).setPrice(101)));
        assertThrows(RuntimeException.class, () -> deadlines.getVerifiedPayOrder(order));
    }

    @Test
    void paidFactHasPriorityOverClientCountdownAndRpc() {
        order.setStatus(10).setPayStatus(true).setPayOrderId(2L).setPayExpireTime(now.minusHours(1));
        var response = new AppTradeOrderDetailRespVO();
        deadlines.fill(order, response, true);
        assertEquals("ORDER_NOT_UNPAID", response.getPaymentBlockReason());
        assertFalse(response.getCanPay());
        verifyNoInteractions(payApi);
    }

    @Test
    void delayedPaidFactIsDisplayedAsVerificationInsteadOfNewPayment() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(now.minusMinutes(1), 10)));
        var response = new AppTradeOrderDetailRespVO();
        deadlines.fill(order, response, true);
        assertEquals("PAYMENT_UNCONFIRMED", response.getPaymentBlockReason());
        assertFalse(response.getCanPay());
    }

    @Test
    void expiredPaymentDisablesBothResponseShapes() {
        deadlines.initialize(order, now.minusMinutes(5));
        order.setPayExpireTime(now.minusSeconds(1)).setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(order.getPayExpireTime(), 0)));
        var page = new AppTradeOrderPageItemRespVO();
        var detail = new AppTradeOrderDetailRespVO();
        deadlines.fill(order, page, false);
        deadlines.fill(order, detail, true);
        assertFalse(page.getCanPay());
        assertFalse(detail.getCanPay());
        assertEquals("PAY_EXPIRED", detail.getPaymentBlockReason());
        assertEquals(page.getPayExpireTimestamp(), detail.getPayExpireTimestamp());
    }

    @Test
    void legacyWithoutPayKeepsCreateTimeBasedLimitAndCannotRenew() {
        assertEquals(order.getCreateTime().plusHours(2), deadlines.startPayment(order, now));
        order.setPayExpireTime(null).setCreateTime(now.minusHours(3));
        assertThrows(RuntimeException.class, () -> deadlines.startPayment(order, now));
    }

    @Test
    void converterRequiresAndReusesFrozenPaymentDeadline() {
        var item = new TradeOrderItemDO().setSpuName("保温材料");
        assertThrows(IllegalStateException.class, () -> TradeOrderConvert.INSTANCE.convert(order, List.of(item), properties));
        order.setPayExpireTime(now.plusMinutes(2));
        assertEquals(order.getPayExpireTime(), TradeOrderConvert.INSTANCE.convert(order, List.of(item), properties).getExpireTime());
    }

    private PayOrderRespDTO pay(LocalDateTime expiry, int status) {
        return new PayOrderRespDTO().setId(2L).setMerchantOrderId("1").setPrice(100)
                .setExpireTime(expiry).setStatus(status);
    }
}
