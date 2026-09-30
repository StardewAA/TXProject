package cn.iocoder.txgy.module.trade.service.order.handler;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.api.level.MemberLevelApi;
import cn.iocoder.txgy.module.member.api.point.MemberPointApi;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.service.aftersale.AfterSaleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeMemberPointOrderHandlerTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void afterCancelOrderItemUsesPaymentTenantForPlatformBuyerAssets() {
        MemberPointApi pointApi = mock(MemberPointApi.class);
        MemberLevelApi levelApi = mock(MemberLevelApi.class);
        PayOrderApi payOrderApi = mock(PayOrderApi.class);
        AfterSaleService afterSaleService = mock(AfterSaleService.class);
        when(payOrderApi.getOrderTenantId(940624L)).thenReturn(CommonResult.success(2009L));
        when(afterSaleService.getAfterSale(125L)).thenReturn(new AfterSaleDO().setRefundPrice(11000));
        when(levelApi.reduceExperience(anyLong(), anyInt(), anyInt(), anyString())).thenAnswer(invocation -> {
            assertEquals(2009L, TenantContextHolder.getTenantId());
            return CommonResult.success(true);
        });
        TradeMemberPointOrderHandler handler = new TradeMemberPointOrderHandler();
        ReflectionTestUtils.setField(handler, "memberPointApi", pointApi);
        ReflectionTestUtils.setField(handler, "memberLevelApi", levelApi);
        ReflectionTestUtils.setField(handler, "payOrderApi", payOrderApi);
        ReflectionTestUtils.setField(handler, "afterSaleService", afterSaleService);
        TenantContextHolder.setTenantId(2024L);

        handler.afterCancelOrderItem(new TradeOrderDO().setId(940728L).setPayOrderId(940624L).setUserId(300040123L),
                new TradeOrderItemDO().setId(940828L).setAfterSaleId(125L).setUsePoint(0).setGivePoint(0));

        assertEquals(2024L, TenantContextHolder.getTenantId());
        verify(levelApi).reduceExperience(eq(300040123L), eq(11000), anyInt(), eq("940828"));
        verifyNoInteractions(pointApi);
    }
}
