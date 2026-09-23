package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.txgy.module.trade.service.order.handler.TradeOrderHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeOrderCancellationDeadlineTest {
    TradeOrderMapper orders;
    TradeOrderItemMapper items;
    PayOrderApi payApi;
    TradeOrderProperties properties;
    TradeOrderPaymentDeadlineService deadlines;
    TradeOrderUpdateServiceImpl service;
    TradeOrderHandler handler;
    TradeOrderDO order;
    LocalDateTime now;

    @BeforeEach
    void setup() {
        now = LocalDateTime.now();
        orders = mock(TradeOrderMapper.class);
        items = mock(TradeOrderItemMapper.class);
        payApi = mock(PayOrderApi.class);
        handler = mock(TradeOrderHandler.class);
        properties = new TradeOrderProperties();
        properties.setPayExpireTime(Duration.ofHours(2));
        deadlines = new TradeOrderPaymentDeadlineService(properties, payApi,
                mock(cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutMapper.class),
                mock(cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutOrderMapper.class));
        service = new TradeOrderUpdateServiceImpl();
        ReflectionTestUtils.setField(service, "tradeOrderMapper", orders);
        ReflectionTestUtils.setField(service, "tradeOrderItemMapper", items);
        ReflectionTestUtils.setField(service, "payOrderApi", payApi);
        ReflectionTestUtils.setField(service, "tradeOrderProperties", properties);
        ReflectionTestUtils.setField(service, "paymentDeadline", deadlines);
        ReflectionTestUtils.setField(service, "tradeOrderHandlers", List.of(handler));
        order = new TradeOrderDO().setId(1L).setUserId(9L).setStatus(0).setPayStatus(false)
                .setPayPrice(100);
        order.setCreateTime(now.minusMinutes(20));
        when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        when(items.selectListByOrderId(1L)).thenReturn(List.of(new TradeOrderItemDO().setOrderId(1L).setSpuName("岩棉")));
        when(orders.updateByIdAndStatus(eq(1L), eq(0), any())).thenReturn(1);
    }

    @Test
    void freshPaidStateUnderLockOverridesStaleUnpaidCandidate() {
        order.setStatus(10).setPayStatus(true);
        assertFalse(service.cancelOrderBySystem(new TradeOrderDO().setId(1L).setStatus(0)));
        verifyNoInteractions(payApi, handler);
    }

    @Test
    void failedCancellationPreflightRunsNoCompensationOrOrderUpdate() {
        order.setPayExpireSeconds(null).setCreateTime(now.minusHours(3));
        doThrow(new IllegalStateException("historical activity product missing"))
                .when(handler).beforeCancelOrder(eq(order), anyList());
        assertThrows(IllegalStateException.class, () -> service.cancelOrderBySystem(order));
        verify(handler, never()).afterCancelOrder(any(), anyList());
        verify(orders, never()).updateByIdAndStatus(anyLong(), anyInt(), any());
        verifyNoInteractions(payApi);
    }

    @Test
    void everyHandlerIsValidatedBeforeAnyRemoteCompensation() {
        order.setPayExpireSeconds(null).setCreateTime(now.minusHours(3));
        TradeOrderHandler second = mock(TradeOrderHandler.class);
        ReflectionTestUtils.setField(service, "tradeOrderHandlers", List.of(handler, second));
        assertTrue(service.cancelOrderBySystem(order));
        var calls = inOrder(handler, second, orders);
        calls.verify(handler).beforeCancelOrder(eq(order), anyList());
        calls.verify(second).beforeCancelOrder(eq(order), anyList());
        calls.verify(orders).updateByIdAndStatus(eq(1L), eq(0), any());
        calls.verify(handler).afterCancelOrder(eq(order), anyList());
        calls.verify(second).afterCancelOrder(eq(order), anyList());
    }

    @Test
    void laterPreflightFailureDoesNotRunEarlierHandlersCompensation() {
        order.setPayExpireSeconds(null).setCreateTime(now.minusHours(3));
        TradeOrderHandler second = mock(TradeOrderHandler.class);
        ReflectionTestUtils.setField(service, "tradeOrderHandlers", List.of(handler, second));
        doThrow(new IllegalStateException("invalid point activity"))
                .when(second).beforeCancelOrder(eq(order), anyList());
        assertThrows(IllegalStateException.class, () -> service.cancelOrderBySystem(order));
        verify(handler, never()).afterCancelOrder(any(), anyList());
        verify(second, never()).afterCancelOrder(any(), anyList());
        verify(orders, never()).updateByIdAndStatus(anyLong(), anyInt(), any());
    }

    @Test
    void awaitingPaymentStatusIsVerifiedAndClosedBeforeCancellation() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(0)), CommonResult.success(pay(30)));
        when(payApi.expireOrder(2L, "mall", "1")).thenReturn(CommonResult.success(true));
        assertTrue(service.cancelOrderBySystem(order));
        verify(payApi).expireOrder(2L, "mall", "1");
        verify(handler).afterCancelOrder(eq(order), anyList());
    }

    @Test
    void unconfirmedCloseLeavesOrderAndInventoryUnchanged() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(0)));
        when(payApi.expireOrder(2L, "mall", "1")).thenReturn(CommonResult.success(false));
        assertFalse(service.cancelOrderBySystem(order));
        verify(orders, never()).updateByIdAndStatus(anyLong(), anyInt(), any());
        verifyNoInteractions(handler);
    }

    @Test
    void paymentBecomingPaidDuringCloseCannotBeCanceled() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(0)), CommonResult.success(pay(10)));
        when(payApi.expireOrder(2L, "mall", "1")).thenReturn(CommonResult.success(true));
        assertFalse(service.cancelOrderBySystem(order));
        verifyNoInteractions(handler);
    }

    @Test
    void missingPaymentOrRpcFailureCannotMeanUnpaid() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(null));
        assertThrows(RuntimeException.class, () -> service.cancelOrderBySystem(order));
        when(payApi.getOrder(2L)).thenThrow(new IllegalStateException("offline"));
        assertThrows(RuntimeException.class, () -> service.cancelOrderBySystem(order));
        verifyNoInteractions(handler);
    }

    @Test
    void alreadyPaidOrRefundedPaymentIsNeverCanceled() {
        order.setPayOrderId(2L);
        for (int status : new int[]{10, 20}) {
            when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(status)));
            assertFalse(service.cancelOrderBySystem(order));
        }
        verifyNoInteractions(handler);
        verify(payApi, never()).expireOrder(anyLong(), anyString(), anyString());
    }

    @Test
    void oldPaymentDeadlineIsPreservedDespiteNewConfiguration() {
        order.setPayOrderId(2L).setCreateTime(now.minusDays(2));
        properties.setPayExpireTime(Duration.ofSeconds(1));
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(0).setExpireTime(now.plusHours(1))));
        assertFalse(service.cancelOrderBySystem(order));
        verify(payApi, never()).expireOrder(anyLong(), anyString(), anyString());
        verifyNoInteractions(handler);
    }

    @Test
    void noPaymentLegacyOrderStillUsesOriginalCreateTimeRule() {
        order.setCreateTime(now.minusHours(3));
        assertTrue(service.cancelOrderBySystem(order));
        verify(orders).updateByIdAndStatus(eq(1L), eq(0), argThat(row -> row.getCancelType() == 10));
        verifyNoInteractions(payApi);
    }

    @Test
    void canceledRowIsNotCanceledTwice() {
        order.setStatus(40);
        assertFalse(service.cancelOrderBySystem(order));
        verifyNoInteractions(payApi, handler);
    }

    @Test
    void failedConditionalCancelDoesNotReleaseResources() {
        order.setPayExpireSeconds(null).setCreateTime(now.minusHours(3));
        when(orders.updateByIdAndStatus(eq(1L), eq(0), any())).thenReturn(0);
        assertThrows(RuntimeException.class, () -> service.cancelOrderBySystem(order));
        verify(handler).beforeCancelOrder(eq(order), anyList());
        verify(handler, never()).afterCancelOrder(any(), anyList());
        verifyNoMoreInteractions(handler);
    }

    private PayOrderRespDTO pay(int status) {
        return new PayOrderRespDTO().setId(2L).setMerchantOrderId("1").setPrice(100)
                .setStatus(status).setExpireTime(now.minusMinutes(1));
    }

    @Test void manualCancellationClosesUnexpiredPaymentBeforeReleasingResources() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(0).setExpireTime(now.plusHours(1))),
                CommonResult.success(pay(30).setExpireTime(now.plusHours(1))));
        when(payApi.closeOrder(2L,"mall","1")).thenReturn(CommonResult.success(true));
        service.cancelOrderByMember(9L,1L);
        var calls=inOrder(payApi,orders,handler);
        calls.verify(payApi).getOrder(2L);
        calls.verify(payApi).closeOrder(2L,"mall","1");
        calls.verify(payApi).getOrder(2L);
        calls.verify(handler).beforeCancelOrder(eq(order),anyList());
        calls.verify(orders).updateByIdAndStatus(eq(1L),eq(0),any());
        calls.verify(handler).afterCancelOrder(eq(order),anyList());
        verify(payApi,never()).expireOrder(anyLong(),anyString(),anyString());
    }

    @Test void manualCancellationDoesNotReleaseAnythingOnUnknownClose() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(0)));
        when(payApi.closeOrder(2L,"mall","1")).thenReturn(CommonResult.success(false));
        assertThrows(RuntimeException.class,()->service.cancelOrderByMember(9L,1L));
        verifyNoInteractions(handler);
        verify(orders,never()).updateByIdAndStatus(anyLong(),anyInt(),any());
    }

    @Test void manualCancellationRpcTimeoutCannotMeanClosed() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(0)));
        when(payApi.closeOrder(2L,"mall","1")).thenThrow(new IllegalStateException("simulated timeout"));
        assertThrows(RuntimeException.class,()->service.cancelOrderByMember(9L,1L));
        verifyNoInteractions(handler);
        verify(orders,never()).updateByIdAndStatus(anyLong(),anyInt(),any());
    }

    @Test void manualCancellationVerifiesFinalClosedStateInsteadOfTrustingCloseBoolean() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(0)),CommonResult.success(pay(10)));
        when(payApi.closeOrder(2L,"mall","1")).thenReturn(CommonResult.success(true));
        assertThrows(RuntimeException.class,()->service.cancelOrderByMember(9L,1L));
        verifyNoInteractions(handler);
    }

    @Test void manualCancellationOfAlreadyClosedPaymentDoesNotContactProviderAgain() {
        order.setPayOrderId(2L);
        when(payApi.getOrder(2L)).thenReturn(CommonResult.success(pay(30)));
        service.cancelOrderByMember(9L,1L);
        verify(payApi,never()).closeOrder(anyLong(),anyString(),anyString());
        verify(handler).afterCancelOrder(eq(order),anyList());
    }

    @Test void manualCancellationWithoutPaymentStillWorksBeforeErpConfirmation() {
        service.cancelOrderByMember(9L,1L);
        verifyNoInteractions(payApi);
        verify(handler).afterCancelOrder(eq(order),anyList());
    }

    @Test void manualCancellationWrongOwnerCannotProbePayment() {
        order.setPayOrderId(2L);
        assertThrows(RuntimeException.class,()->service.cancelOrderByMember(99L,1L));
        verifyNoInteractions(payApi,handler);
    }
}
