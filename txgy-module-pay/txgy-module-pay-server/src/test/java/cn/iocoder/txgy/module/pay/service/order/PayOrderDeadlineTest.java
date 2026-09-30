package cn.iocoder.txgy.module.pay.service.order;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.pay.convert.order.PayOrderConvert;
import cn.iocoder.txgy.module.pay.dal.dataobject.app.PayAppDO;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import cn.iocoder.txgy.module.pay.dal.mysql.order.PayOrderMapper;
import cn.iocoder.txgy.module.pay.dal.mysql.order.PayOrderExtensionMapper;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.PayClient;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.txgy.module.pay.service.app.PayAppService;
import cn.iocoder.txgy.module.pay.service.channel.PayChannelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOrderDeadlineTest {
    @InjectMocks private PayOrderServiceImpl service;
    @Mock private PayOrderMapper orderMapper;
    @Mock private PayOrderExtensionMapper orderExtensionMapper;
    @Mock private PayAppService appService;
    @Mock private PayChannelService channelService;
    @Mock private PayClient<?> client;
    @Mock private PayOrderLifecycleService lifecycleService;

    private PayOrderDO order(int status, LocalDateTime expiry) {
        PayOrderDO order = new PayOrderDO().setId(7L).setAppId(3L).setMerchantOrderId("mall-8")
                .setStatus(status).setExpireTime(expiry);
        when(appService.validPayApp("mall")).thenReturn(new PayAppDO().setId(3L));
        when(orderMapper.selectById(7L)).thenReturn(order);
        return order;
    }

    private void channelAttempt() {
        when(orderExtensionMapper.selectListByOrderId(7L)).thenReturn(List.of(
                new PayOrderExtensionDO().setId(9L).setOrderId(7L).setChannelId(11L)
                        .setNo("attempt-9").setStatus(0).setSubmitState(1)));
        doReturn(client).when(channelService).getPayClient(11L);
    }

    @Test void unexpiredOrderNeverContactsChannel() {
        order(0, LocalDateTime.now().plusHours(1));
        assertFalse(service.expireOrder(7L, "mall", "mall-8"));
        verifyNoInteractions(orderExtensionMapper, channelService);
    }

    @Test void paidOrderCannotBeClosed() {
        order(10, LocalDateTime.now().minusHours(1));
        assertFalse(service.expireOrder(7L, "mall", "mall-8"));
        verifyNoInteractions(orderExtensionMapper);
    }

    @Test void refundedOrderCannotBeClosed() {
        order(20, LocalDateTime.now().minusHours(1));
        assertFalse(service.expireOrder(7L, "mall", "mall-8"));
        verifyNoInteractions(orderExtensionMapper);
    }

    @Test void closedOrderIsIdempotent() {
        order(30, LocalDateTime.now().minusHours(1));
        assertTrue(service.expireOrder(7L, "mall", "mall-8"));
        verifyNoInteractions(orderExtensionMapper);
    }

    @Test void wrongBusinessOrApplicationCannotCloseOrder() {
        PayOrderDO current = order(0, LocalDateTime.now().minusHours(1));
        assertThrows(ServiceException.class, () -> service.expireOrder(7L, "mall", "other-order"));
        current.setAppId(99L);
        assertThrows(ServiceException.class, () -> service.expireOrder(7L, "mall", "mall-8"));
        verifyNoInteractions(orderExtensionMapper);
    }

    @Test void missingDeadlineDoesNotGuessFromCurrentConfiguration() {
        order(0, null);
        assertFalse(service.expireOrder(7L, "mall", "mall-8"));
        verifyNoInteractions(orderExtensionMapper);
    }

    @Test void noAttemptExpiredOrderClosesWithCompareAndSet() {
        order(0, LocalDateTime.now().minusSeconds(1));
        when(orderExtensionMapper.selectListByOrderId(7L)).thenReturn(List.of());
        when(lifecycleService.closeIfAttemptsClosed(any())).thenReturn(true);
        assertTrue(service.expireOrder(7L, "mall", "mall-8"));
        verify(lifecycleService).closeIfAttemptsClosed(argThat(value -> value.getId() == 7L));
    }

    @Test void concurrentStatusUpdateIsNotOverwritten() {
        order(0, LocalDateTime.now().minusSeconds(1));
        when(orderExtensionMapper.selectListByOrderId(7L)).thenReturn(List.of());
        assertFalse(service.expireOrder(7L, "mall", "mall-8"));
    }

    @Test void channelStillWaitingDoesNotBecomeClosed() {
        order(0, LocalDateTime.now().minusSeconds(1)); channelAttempt();
        when(client.getOrder("attempt-9")).thenReturn(PayOrderRespDTO.waitingOf(null, null, "attempt-9", null));
        assertFalse(service.expireOrder(7L, "mall", "mall-8"));
        verify(orderMapper, never()).updateByIdAndStatus(anyLong(), anyInt(), any());
        verify(orderExtensionMapper, never()).updateByIdAndStatus(anyLong(), anyInt(), any());
    }

    @Test void unknownChannelResultDoesNotCancel() {
        order(0, LocalDateTime.now().minusSeconds(1)); channelAttempt();
        assertFalse(service.expireOrder(7L, "mall", "mall-8"));
        verify(orderMapper, never()).updateByIdAndStatus(anyLong(), anyInt(), any());
    }

    @Test void channelTimeoutDoesNotCancel() {
        order(0, LocalDateTime.now().minusSeconds(1)); channelAttempt();
        when(client.getOrder("attempt-9")).thenThrow(new IllegalStateException("Test channel timeout"));
        assertFalse(service.expireOrder(7L, "mall", "mall-8"));
        verify(orderMapper, never()).updateByIdAndStatus(anyLong(), anyInt(), any());
    }

    @Test void channelConfirmedClosedAllowsExpiry() {
        order(0, LocalDateTime.now().minusSeconds(1)); channelAttempt();
        when(client.getOrder("attempt-9")).thenReturn(PayOrderRespDTO.of(30, null, null, null, "attempt-9", null));
        when(orderExtensionMapper.updateVerifiedClosed(eq(9L), eq(0), anyString())).thenReturn(1);
        when(lifecycleService.closeIfAttemptsClosed(any())).thenReturn(true);
        assertTrue(service.expireOrder(7L, "mall", "mall-8"));
    }

    @Test void rpcResponsePreservesPersistedExpiry() {
        LocalDateTime expiry = LocalDateTime.of(2026, 9, 15, 12, 0);
        assertEquals(expiry, PayOrderConvert.INSTANCE.convert2(new PayOrderDO().setExpireTime(expiry)).getExpireTime());
    }

    @Test void manualCloseDoesNotRequireExpiryButStillUsesGate() {
        order(0, LocalDateTime.now().plusHours(1));
        when(orderExtensionMapper.selectListByOrderId(7L)).thenReturn(List.of());
        when(lifecycleService.closeIfAttemptsClosed(any())).thenReturn(true);
        assertTrue(service.closeOrder(7L, "mall", "mall-8"));
        verify(lifecycleService).closeIfAttemptsClosed(any());
    }

    @Test void manualCloseChecksBusinessOwnershipAndPaidStatus() {
        PayOrderDO current = order(10, LocalDateTime.now().plusHours(1));
        assertFalse(service.closeOrder(7L, "mall", "mall-8"));
        assertThrows(ServiceException.class, () -> service.closeOrder(7L, "mall", "other"));
        current.setAppId(99L);
        assertThrows(ServiceException.class, () -> service.closeOrder(7L, "mall", "mall-8"));
        verifyNoInteractions(orderExtensionMapper, lifecycleService);
    }

    @Test void preparedOrUnknownOrLegacyAttemptCannotCloseBasedOnChannelNotFound() {
        order(0, LocalDateTime.now().plusHours(1));
        doReturn(client).when(channelService).getPayClient(11L);
        when(client.getOrder("attempt-9")).thenReturn(PayOrderRespDTO.of(30, null, null, null, "attempt-9", null));
        for (Integer state : new Integer[]{0, 2, null}) {
            when(orderExtensionMapper.selectListByOrderId(7L)).thenReturn(List.of(
                    new PayOrderExtensionDO().setId(9L).setOrderId(7L).setChannelId(11L)
                            .setNo("attempt-9").setStatus(0).setSubmitState(state)));
            assertFalse(service.closeOrder(7L, "mall", "mall-8"));
        }
        verifyNoInteractions(lifecycleService);
        verify(orderExtensionMapper, never()).updateByIdAndStatus(anyLong(), anyInt(), any());
    }

    @Test void manualCloseWaitingOrNullChannelNeverCallsGate() {
        order(0, LocalDateTime.now().plusHours(1)); channelAttempt();
        assertFalse(service.closeOrder(7L, "mall", "mall-8"));
        when(client.getOrder("attempt-9")).thenReturn(PayOrderRespDTO.waitingOf(null, null, "attempt-9", null));
        assertFalse(service.closeOrder(7L, "mall", "mall-8"));
        verifyNoInteractions(lifecycleService);
    }

    @Test void dispatchedButProviderTradeNotExistCannotClose() {
        order(0, LocalDateTime.now().plusHours(1)); channelAttempt();
        when(client.getOrder("attempt-9")).thenReturn(PayOrderRespDTO.closedOf(
                "ACQ.TRADE_NOT_EXIST", "not created yet", "attempt-9", null));
        assertFalse(service.closeOrder(7L, "mall", "mall-8"));
        verify(orderExtensionMapper, never()).updateVerifiedClosed(anyLong(), anyInt(), anyString());
        verifyNoInteractions(lifecycleService);
    }

    @Test void dispatchedButProviderQueryErrorCannotClose() {
        order(0, LocalDateTime.now().plusHours(1)); channelAttempt();
        for (PayOrderRespDTO response : List.of(
                PayOrderRespDTO.closedOf("QUERY_FAILED", "temporary failure", "attempt-9", null),
                PayOrderRespDTO.closedOf(null, "unknown query result", "attempt-9", null))) {
            when(client.getOrder("attempt-9")).thenReturn(response);
            assertFalse(service.closeOrder(7L, "mall", "mall-8"));
        }
        verify(orderExtensionMapper, never()).updateVerifiedClosed(anyLong(), anyInt(), anyString());
        verifyNoInteractions(lifecycleService);
    }

    @Test void locallyClosedQueryErrorMustBeRecheckedAndCannotSkipToCloseGate() {
        order(0, LocalDateTime.now().plusHours(1));
        when(orderExtensionMapper.selectListByOrderId(7L)).thenReturn(List.of(
                new PayOrderExtensionDO().setId(9L).setOrderId(7L).setChannelId(11L).setNo("attempt-9")
                        .setStatus(30).setSubmitState(1).setChannelErrorCode("ACQ.TRADE_NOT_EXIST")));
        doReturn(client).when(channelService).getPayClient(11L);
        when(client.getOrder("attempt-9")).thenReturn(PayOrderRespDTO.closedOf(
                "ACQ.TRADE_NOT_EXIST", "not created yet", "attempt-9", null));
        assertFalse(service.closeOrder(7L, "mall", "mall-8"));
        verify(client).getOrder("attempt-9");
        verifyNoInteractions(lifecycleService);
    }

    @Test void locallyClosedErrorCanProceedOnlyAfterNewExplicitClosedProof() {
        order(0, LocalDateTime.now().plusHours(1));
        when(orderExtensionMapper.selectListByOrderId(7L)).thenReturn(List.of(
                new PayOrderExtensionDO().setId(9L).setOrderId(7L).setChannelId(11L).setNo("attempt-9")
                        .setStatus(30).setSubmitState(1).setChannelErrorCode("OLD_QUERY_ERROR")));
        doReturn(client).when(channelService).getPayClient(11L);
        when(client.getOrder("attempt-9")).thenReturn(PayOrderRespDTO.of(30, null, null, null, "attempt-9", null));
        when(orderExtensionMapper.updateVerifiedClosed(eq(9L), eq(30), anyString())).thenReturn(1);
        when(lifecycleService.closeIfAttemptsClosed(any())).thenReturn(true);
        assertTrue(service.closeOrder(7L, "mall", "mall-8"));
        verify(orderExtensionMapper).updateVerifiedClosed(eq(9L), eq(30), anyString());
    }
}
