package cn.iocoder.txgy.module.trade.service.delivery;

import cn.iocoder.txgy.module.trade.controller.app.order.AppTradeOrderController;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderLogisticsRespVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryServiceImpl;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeLogisticsCompatibilityTest {
    @Test void legacyBuyerAndStaffQueriesGoThroughSameGuardedService() {
        var queries = new TradeOrderQueryServiceImpl();
        var orders = mock(TradeOrderMapper.class); var logistics = mock(TradeOrderLogisticsService.class);
        ReflectionTestUtils.setField(queries, "tradeOrderMapper", orders);
        ReflectionTestUtils.setField(queries, "logistics", logistics);
        var order = new TradeOrderDO().setId(2L).setUserId(3L);
        when(orders.selectByIdAndUserId(2L, 3L)).thenReturn(order); when(orders.selectById(2L)).thenReturn(order);
        when(logistics.getForAuthorizedOrder(order)).thenReturn(new AppTradeOrderLogisticsRespVO().setEvents(List.of(
                new AppTradeOrderLogisticsRespVO.Event().setTime(LocalDateTime.of(2026, 9, 17, 12, 0)).setContent("fixture"))));
        assertEquals("fixture", queries.getExpressTrackList(2L, 3L).getFirst().getContent());
        assertEquals(1, queries.getExpressTrackList(2L).size());
        verify(logistics, times(2)).getForAuthorizedOrder(order);
    }
    @Test void missingBuyerOrderDoesNotReachGuardCacheOrProvider() {
        var queries = new TradeOrderQueryServiceImpl();
        var orders = mock(TradeOrderMapper.class); var logistics = mock(TradeOrderLogisticsService.class);
        ReflectionTestUtils.setField(queries, "tradeOrderMapper", orders);
        ReflectionTestUtils.setField(queries, "logistics", logistics);
        assertThrows(RuntimeException.class, () -> queries.getExpressTrackList(2L, 9L)); verifyNoInteractions(logistics);
    }
    @Test void newEndpointIsReadOnlyAndNotAnonymous() throws Exception {
        var method = AppTradeOrderController.class.getMethod("getLogistics", Long.class);
        assertArrayEquals(new String[]{"/get-logistics"}, method.getAnnotation(GetMapping.class).value());
        assertNull(method.getAnnotation(PermitAll.class)); assertNull(AppTradeOrderController.class.getAnnotation(PermitAll.class));
    }
}
