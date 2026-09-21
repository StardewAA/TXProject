package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.promotion.api.point.PointActivityApi;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.service.order.handler.TradePointOrderHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradePointCancellationSafetyTest {
    PointActivityApi api;
    TradePointOrderHandler handler;
    TradeOrderDO order;
    TradeOrderItemDO item;

    @BeforeEach
    void setUp() {
        api = mock(PointActivityApi.class);
        handler = new TradePointOrderHandler();
        ReflectionTestUtils.setField(handler, "pointActivityApi", api);
        order = new TradeOrderDO().setId(280L).setType(4).setPointActivityId(1L);
        item = new TradeOrderItemDO().setId(275L).setOrderId(280L).setSkuId(920005L)
                .setCount(1).setAfterSaleStatus(0);
    }

    @Test
    void invalidActivityStopsAtReadOnlyPreflight() {
        when(api.validatePointStockReturn(1L, 920005L, 1, 275L))
                .thenReturn(CommonResult.error(400, "原活动商品关联不存在"));
        assertThrows(RuntimeException.class, () -> handler.beforeCancelOrder(order, List.of(item)));
        verify(api).validatePointStockReturn(1L, 920005L, 1, 275L);
        verifyNoMoreInteractions(api);
    }

    @Test
    void cancelledOrderAndItemRefundUseSamePersistedItemIdentity() {
        when(api.validatePointStockReturn(1L, 920005L, 1, 275L)).thenReturn(CommonResult.success(true));
        when(api.returnPointStock(1L, 920005L, 1, 275L)).thenReturn(CommonResult.success(true));
        handler.beforeCancelOrder(order, List.of(item));
        handler.afterCancelOrder(order, List.of(item));
        handler.beforeCancelOrderItem(order, item);
        handler.afterCancelOrderItem(order, item);
        verify(api, times(2)).returnPointStock(1L, 920005L, 1, 275L);
        verify(api, times(2)).validatePointStockReturn(1L, 920005L, 1, 275L);
        verifyNoMoreInteractions(api);
    }

    @Test
    void alreadyRefundedItemsAreNotReturnedAgainByWholeOrderHook() {
        item.setAfterSaleStatus(20);
        handler.beforeCancelOrder(order, List.of(item));
        handler.afterCancelOrder(order, List.of(item));
        verifyNoInteractions(api);
    }

    @Test
    void normalOrdersDoNotUsePointActivityApis() {
        order.setType(0);
        handler.beforeCancelOrder(order, List.of(item));
        handler.beforeCancelOrderItem(order, item);
        handler.afterCancelOrder(order, List.of(item));
        handler.afterCancelOrderItem(order, item);
        verifyNoInteractions(api);
    }
}
