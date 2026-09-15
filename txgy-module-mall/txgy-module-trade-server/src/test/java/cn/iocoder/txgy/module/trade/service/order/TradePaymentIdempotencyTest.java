package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradePaymentIdempotencyTest {
    @Test
    void duplicatePaidNotificationDoesNotRunSideEffects() {
        TradeOrderMapper mapper = mock(TradeOrderMapper.class);
        TradeOrderUpdateServiceImpl service = new TradeOrderUpdateServiceImpl();
        ReflectionTestUtils.setField(service, "tradeOrderMapper", mapper);
        when(mapper.selectByIdForUpdate(1L)).thenReturn(new TradeOrderDO().setId(1L)
                .setStatus(10).setPayStatus(true).setPayOrderId(2L));
        assertDoesNotThrow(() -> service.updateOrderPaid(1L, 2L));
        verify(mapper).selectByIdForUpdate(1L);
        verifyNoMoreInteractions(mapper);
    }
    @Test
    void cancelledUnpaidOrderMustNotAcknowledgePayment() {
        TradeOrderMapper mapper = mock(TradeOrderMapper.class);
        TradeOrderUpdateServiceImpl service = new TradeOrderUpdateServiceImpl();
        ReflectionTestUtils.setField(service, "tradeOrderMapper", mapper);
        when(mapper.selectByIdForUpdate(1L)).thenReturn(new TradeOrderDO().setId(1L)
                .setStatus(40).setPayStatus(false).setPayOrderId(2L));
        assertThrows(RuntimeException.class, () -> service.updateOrderPaid(1L, 2L));
    }
}
