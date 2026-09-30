package cn.iocoder.txgy.module.trade.controller.app.order;

import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.service.order.*;
import cn.iocoder.txgy.module.trade.service.checkout.PlatformCheckoutOrderService;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.item.AppTradeOrderItemCommentCreateReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class PlatformBuyerActionsTest {
    @Test void crossTenantMutationUsesAuthorizedOrderOwnerAndRestoresCallerTenant() {
        var controller = new AppTradeOrderController();
        var reader = mock(PlatformBuyerOrderQueryService.class);
        var writer = mock(TradeOrderUpdateService.class);
        var checkout = mock(PlatformCheckoutOrderService.class);
        ReflectionTestUtils.setField(controller, "platformBuyerOrders", reader);
        ReflectionTestUtils.setField(controller, "tradeOrderUpdateService", writer);
        ReflectionTestUtils.setField(controller, "platformCheckoutOrderService", checkout);
        var authorized = new PlatformBuyerOrderQueryService.AuthorizedOrder(new TradeOrderDO().setId(8L).setUserId(9L), 2024L);
        when(reader.getAuthorized(8L, 7L, 1L)).thenReturn(authorized);
        when(reader.getAuthorizedByItem(18L, 7L, 1L)).thenReturn(authorized);
        doAnswer(invocation -> { assertEquals(2024L, TenantContextHolder.getRequiredTenantId()); return null; })
                .when(writer).cancelOrderByMember(9L, 8L);
        when(writer.createOrderItemCommentByMember(eq(9L), any())).thenAnswer(invocation -> {
            assertEquals(2024L, TenantContextHolder.getRequiredTenantId()); return 88L;
        });
        TenantContextHolder.setTenantId(1L);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertTrue(controller.cancelOrder(8L).getData());
            var comment = new AppTradeOrderItemCommentCreateReqVO(); comment.setOrderItemId(18L);
            assertEquals(88L, controller.createOrderItemComment(comment).getData());
            assertEquals(1L, TenantContextHolder.getRequiredTenantId());
            assertThrows(RuntimeException.class, () -> controller.cancelOrder(99L));
            comment.setOrderItemId(99L);
            assertThrows(RuntimeException.class, () -> controller.createOrderItemComment(comment));
            verify(writer, times(1)).cancelOrderByMember(anyLong(), anyLong());
            verify(writer, times(1)).createOrderItemCommentByMember(anyLong(), any());
        } finally { TenantContextHolder.clear(); }
    }
}
