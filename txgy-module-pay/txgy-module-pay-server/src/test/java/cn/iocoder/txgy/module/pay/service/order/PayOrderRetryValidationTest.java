package cn.iocoder.txgy.module.pay.service.order;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import cn.iocoder.txgy.module.pay.dal.mysql.order.PayOrderExtensionMapper;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.PayClient;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.txgy.module.pay.service.channel.PayChannelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.txgy.module.pay.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOrderRetryValidationTest {
    @InjectMocks private PayOrderServiceImpl service;
    @Mock private PayOrderExtensionMapper orderExtensionMapper;
    @Mock private PayChannelService channelService;
    @Mock private PayClient<?> client;

    private PayOrderExtensionDO attempt(long id, int status) {
        return new PayOrderExtensionDO().setId(id).setOrderId(1L).setChannelId(44L)
                .setNo("RETRY-" + id).setStatus(status);
    }

    private void waitingAttempt() {
        when(orderExtensionMapper.selectListByOrderId(1L)).thenReturn(List.of(attempt(2L, 0)));
        doReturn(client).when(channelService).getPayClient(44L);
    }

    @Test
    void closedAttemptDoesNotContactUnavailableChannel() {
        when(orderExtensionMapper.selectListByOrderId(1L)).thenReturn(List.of(attempt(2L, 30)));
        assertDoesNotThrow(() -> service.validateOrderActuallyPaid(1L));
        verifyNoInteractions(channelService);
    }

    @Test
    void anotherPaidAttemptStillBlocksRetryEvenIfFirstIsClosed() {
        when(orderExtensionMapper.selectListByOrderId(1L)).thenReturn(List.of(attempt(2L, 30), attempt(3L, 10)));
        assertEquals(PAY_ORDER_EXTENSION_IS_PAID.getCode(),
                assertThrows(ServiceException.class, () -> service.validateOrderActuallyPaid(1L)).getCode());
    }

    @Test
    void queryFailureIsBusinessErrorAndDoesNotPermitAnotherDebit() {
        waitingAttempt();
        when(client.getOrder("RETRY-2")).thenThrow(new IllegalStateException("Sandbox TLS unavailable"));
        assertEquals(PAY_ORDER_PREVIOUS_ATTEMPT_UNCONFIRMED.getCode(),
                assertThrows(ServiceException.class, () -> service.validateOrderActuallyPaid(1L)).getCode());
    }

    @Test
    void nullResultDoesNotPermitAnotherDebit() {
        waitingAttempt();
        assertEquals(PAY_ORDER_PREVIOUS_ATTEMPT_UNCONFIRMED.getCode(),
                assertThrows(ServiceException.class, () -> service.validateOrderActuallyPaid(1L)).getCode());
    }

    @Test
    void missingClientDoesNotPermitAnotherDebit() {
        when(orderExtensionMapper.selectListByOrderId(1L)).thenReturn(List.of(attempt(2L, 0)));
        assertEquals(PAY_ORDER_PREVIOUS_ATTEMPT_UNCONFIRMED.getCode(),
                assertThrows(ServiceException.class, () -> service.validateOrderActuallyPaid(1L)).getCode());
    }

    @Test
    void confirmedUnpaidAttemptAllowsAnotherChannel() {
        waitingAttempt();
        when(client.getOrder("RETRY-2")).thenReturn(PayOrderRespDTO.waitingOf(null, null, "RETRY-2", null));
        assertDoesNotThrow(() -> service.validateOrderActuallyPaid(1L));
    }

    @Test
    void remoteSuccessBlocksRetry() {
        waitingAttempt();
        when(client.getOrder("RETRY-2")).thenReturn(PayOrderRespDTO.of(10, null, null, null, "RETRY-2", null));
        assertEquals(PAY_ORDER_EXTENSION_IS_PAID.getCode(),
                assertThrows(ServiceException.class, () -> service.validateOrderActuallyPaid(1L)).getCode());
    }
}
