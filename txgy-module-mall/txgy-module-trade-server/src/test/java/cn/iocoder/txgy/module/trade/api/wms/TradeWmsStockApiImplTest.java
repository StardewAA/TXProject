package cn.iocoder.txgy.module.trade.api.wms;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TradeWmsStockApiImplTest {

    private TradeOrderMapper orderMapper;
    private TradeOrderItemMapper orderItemMapper;
    private TradeWmsStockService stockService;
    private TradeWmsStockApiImpl api;

    @BeforeEach
    void setUp() {
        orderMapper = mock(TradeOrderMapper.class);
        orderItemMapper = mock(TradeOrderItemMapper.class);
        stockService = mock(TradeWmsStockService.class);
        api = new TradeWmsStockApiImpl(orderMapper, orderItemMapper, stockService);
    }

    @Test
    void rechecksWmsImmediatelyBeforePayment() {
        TradeOrderDO order = new TradeOrderDO().setId(1L).setUserId(2L).setPayOrderId(3L)
                .setStatus(0).setPayStatus(false);
        List<TradeOrderItemDO> items = List.of(new TradeOrderItemDO().setSkuId(4L).setCount(2));
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(1L)).thenReturn(items);

        assertEquals(Boolean.TRUE, api.validatePayment(1L, 3L, 2L).getData());
        verify(stockService).validateAvailable(items);
    }

    @Test
    void rejectsAnotherMembersPaymentBeforeReadingStock() {
        TradeOrderDO order = new TradeOrderDO().setId(1L).setUserId(9L).setPayOrderId(3L)
                .setStatus(0).setPayStatus(false);
        when(orderMapper.selectById(1L)).thenReturn(order);

        ServiceException ex = assertThrows(ServiceException.class, () -> api.validatePayment(1L, 3L, 2L));

        assertEquals(404, ex.getCode());
        verifyNoInteractions(orderItemMapper, stockService);
    }

    @Test
    void rejectsPaidOrderBeforeReadingStock() {
        TradeOrderDO order = new TradeOrderDO().setId(1L).setUserId(2L).setPayOrderId(3L)
                .setStatus(10).setPayStatus(true);
        when(orderMapper.selectById(1L)).thenReturn(order);

        ServiceException ex = assertThrows(ServiceException.class, () -> api.validatePayment(1L, 3L, 2L));

        assertEquals(400, ex.getCode());
        verifyNoInteractions(orderItemMapper, stockService);
    }
}
