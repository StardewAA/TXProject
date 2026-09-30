package cn.iocoder.txgy.module.pay.service.order;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO;
import cn.iocoder.txgy.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import cn.iocoder.txgy.module.pay.dal.mysql.order.PayOrderExtensionMapper;
import cn.iocoder.txgy.module.pay.dal.mysql.order.PayOrderMapper;
import cn.iocoder.txgy.module.pay.dal.redis.no.PayNoRedisDAO;
import cn.iocoder.txgy.module.pay.framework.pay.config.PayProperties;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.PayClient;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.txgy.module.pay.service.app.PayAppService;
import cn.iocoder.txgy.module.pay.service.channel.PayChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOrderSubmitRecoveryTest {
    @InjectMocks private PayOrderServiceImpl service;
    @Mock private PayOrderMapper orders;
    @Mock private PayOrderExtensionMapper attempts;
    @Mock private PayOrderLifecycleService lifecycle;
    @Mock private PayChannelService channels;
    @Mock private PayAppService apps;
    @Mock private PayNoRedisDAO numbers;
    @Mock private PayProperties properties;
    @Mock private PayClient<?> client;
    private PayOrderDO stored;
    private PayChannelDO channel;

    @BeforeEach void setup() {
        stored = new PayOrderDO().setId(7L).setAppId(3L).setStatus(0).setPrice(1000)
                .setExpireTime(LocalDateTime.now().plusHours(1));
        channel = new PayChannelDO().setId(9L).setCode("wallet");
        when(orders.selectById(7L)).thenAnswer(call -> stored);
        when(channels.validPayChannel(3L, "wallet")).thenReturn(channel);
        doReturn(client).when(channels).getPayClient(9L);
        when(properties.getOrderNotifyUrl()).thenReturn("http://localhost");
        when(numbers.generate(any())).thenReturn("attempt-11");
        doAnswer(call -> { ((PayOrderExtensionDO) call.getArgument(1)).setId(11L); return null; })
                .when(lifecycle).prepare(any(), any());
    }

    private PayOrderSubmitReqVO request() { return new PayOrderSubmitReqVO().setId(7L).setChannelCode("wallet"); }
    private PayOrderRespDTO paid() { return PayOrderRespDTO.successOf("wallet-transaction", "316",
            LocalDateTime.now(), "attempt-11", null); }

    @Test void knownWalletSuccessStillNotifiesWhenDispatchMarkerFails() {
        PayOrderServiceImpl receiver = mock(PayOrderServiceImpl.class);
        when(client.unifiedOrder(any())).thenReturn(paid());
        doThrow(new IllegalStateException("marker write failed")).when(lifecycle).finishDispatch(any(), eq(11L), eq(true));
        doAnswer(call -> { stored.setStatus(10).setExtensionId(11L); return null; }).when(receiver).notifyOrder(eq(channel), any());
        try (var beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(PayOrderServiceImpl.class)).thenReturn(receiver);
            assertEquals(10, service.submitOrder(request(), "127.0.0.1").getStatus());
        }
        verify(receiver).notifyOrder(eq(channel), argThat(result -> result.getStatus() == 10));
        var sequence = inOrder(receiver, lifecycle);
        sequence.verify(receiver).notifyOrder(eq(channel), any());
        sequence.verify(lifecycle).finishDispatch(any(), eq(11L), eq(true));
        verify(lifecycle, never()).finishDispatch(any(), anyLong(), eq(false));
    }

    @Test void notificationFailureKeepsPreparedAndDoesNotAttemptMarkerPromotion() {
        PayOrderServiceImpl receiver = mock(PayOrderServiceImpl.class);
        IllegalArgumentException notify = new IllegalArgumentException("notify failed");
        when(client.unifiedOrder(any())).thenReturn(paid());
        doThrow(notify).when(receiver).notifyOrder(eq(channel), any());
        try (var beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(PayOrderServiceImpl.class)).thenReturn(receiver);
            ServiceException result = assertThrows(ServiceException.class, () -> service.submitOrder(request(), "127.0.0.1"));
            assertTrue(Arrays.asList(result.getSuppressed()).contains(notify));
        }
        assertEquals(0, stored.getStatus());
        verify(receiver).notifyOrder(eq(channel), any());
        verify(lifecycle, never()).finishDispatch(any(), anyLong(), anyBoolean());
    }

    @Test void waitingResponseDoesNotUseSuccessRecoveryWhenMarkerFails() {
        when(client.unifiedOrder(any())).thenReturn(PayOrderRespDTO.waitingOf("url", "synthetic", "attempt-11", null));
        doThrow(new IllegalStateException("marker write failed")).when(lifecycle).finishDispatch(any(), eq(11L), eq(true));
        try (var beans = mockStatic(SpringUtil.class)) {
            assertThrows(IllegalStateException.class, () -> service.submitOrder(request(), "127.0.0.1"));
            beans.verifyNoInteractions();
        }
        assertEquals(0, stored.getStatus());
    }

    @Test void nullResponseDoesNotInventSuccessIfUnknownMarkerFails() {
        doThrow(new IllegalStateException("marker write failed")).when(lifecycle).finishDispatch(any(), eq(11L), eq(false));
        try (var beans = mockStatic(SpringUtil.class)) {
            assertThrows(IllegalStateException.class, () -> service.submitOrder(request(), "127.0.0.1"));
            beans.verifyNoInteractions();
        }
        assertEquals(0, stored.getStatus());
    }

    @Test void nullResponseMarksUnknownAndRemainsUnpaid() {
        try (var beans = mockStatic(SpringUtil.class)) {
            assertEquals(0, service.submitOrder(request(), "127.0.0.1").getStatus());
            beans.verifyNoInteractions();
        }
        verify(lifecycle).finishDispatch(any(), eq(11L), eq(false));
    }

    @Test void failedNotificationCannotReturnSuccessOrReleasePreparedAttempt() {
        PayOrderServiceImpl receiver = mock(PayOrderServiceImpl.class);
        when(client.unifiedOrder(any())).thenReturn(paid());
        doThrow(new IllegalStateException("notify failed")).when(receiver).notifyOrder(eq(channel), any());
        try (var beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(PayOrderServiceImpl.class)).thenReturn(receiver);
            assertThrows(ServiceException.class, () -> service.submitOrder(request(), "127.0.0.1"));
        }
        assertEquals(0, stored.getStatus());
        verify(lifecycle, never()).finishDispatch(any(), anyLong(), anyBoolean());
    }

    @Test void notificationReturningWithoutPersistedPaidStateIsNotReportedAsSuccess() {
        PayOrderServiceImpl receiver = mock(PayOrderServiceImpl.class);
        when(client.unifiedOrder(any())).thenReturn(paid());
        try (var beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(PayOrderServiceImpl.class)).thenReturn(receiver);
            assertThrows(ServiceException.class, () -> service.submitOrder(request(), "127.0.0.1"));
        }
        assertEquals(0, stored.getStatus());
        verify(lifecycle, never()).finishDispatch(any(), anyLong(), anyBoolean());
    }

    @Test void persistedPaidStateFromAnotherAttemptDoesNotConfirmThisPayment() {
        PayOrderServiceImpl receiver = mock(PayOrderServiceImpl.class);
        when(client.unifiedOrder(any())).thenReturn(paid());
        doAnswer(call -> { stored.setStatus(10).setExtensionId(99L); return null; }).when(receiver).notifyOrder(eq(channel), any());
        try (var beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(PayOrderServiceImpl.class)).thenReturn(receiver);
            assertThrows(ServiceException.class, () -> service.submitOrder(request(), "127.0.0.1"));
        }
    }
}
