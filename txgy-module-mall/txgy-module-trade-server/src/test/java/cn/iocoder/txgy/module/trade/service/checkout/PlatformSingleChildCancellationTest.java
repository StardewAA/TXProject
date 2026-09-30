package cn.iocoder.txgy.module.trade.service.checkout;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.api.platform.MemberPlatformAccountApi;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.*;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.*;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.*;
import cn.iocoder.txgy.module.trade.dal.mysql.cart.PlatformCartMapper;
import cn.iocoder.txgy.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.txgy.module.trade.service.order.handler.*;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsStockService;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class PlatformSingleChildCancellationTest {
    final PlatformCheckoutMapper checkouts = mock(PlatformCheckoutMapper.class);
    final PlatformCheckoutOrderMapper relations = mock(PlatformCheckoutOrderMapper.class);
    final TradeOrderMapper orders = mock(TradeOrderMapper.class);
    final PayOrderApi pays = mock(PayOrderApi.class);
    final MemberPlatformAccountApi accounts = mock(MemberPlatformAccountApi.class);
    final PlatformCheckoutDO checkout = new PlatformCheckoutDO().setId(5L).setCheckoutNo("pc5")
            .setPlatformMemberId(70L).setAddressSourceTenantId(1L).setPlatformPayOrderId(30L)
            .setPayAmount(17000L).setPayStatus("UNPAID");
    final PlatformCheckoutOrderDO relation = new PlatformCheckoutOrderDO().setId(6L).setCheckoutId(5L)
            .setCheckoutNo("pc5").setOrderId(8L).setTenantId(2024L).setBuyerMemberId(7L);
    final PayOrderRespDTO pay = new PayOrderRespDTO().setId(30L).setMerchantOrderId("-5").setPrice(17000).setStatus(0);
    final PlatformCheckoutOrderService service = new PlatformCheckoutOrderService(checkouts,
            mock(PlatformCheckoutPlanMapper.class), mock(PlatformCheckoutPlanItemMapper.class), relations,
            mock(PlatformFundAllocationMapper.class), mock(PlatformCartMapper.class), orders,
            mock(TradeOrderItemMapper.class), mock(TradeNoRedisDAO.class), mock(TradeWmsStockService.class),
            mock(TradeProductSkuOrderHandler.class), mock(TradeOrderWorkbenchHandler.class), pays,
            new TradeOrderProperties().setPayAppKey("mall"), accounts);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(relations.selectByOrder(8L)).thenReturn(relation);
        when(accounts.resolve(7L,1L)).thenReturn(CommonResult.success(new MemberPlatformAccountRespDTO()
                .setPlatformAccountId(70L).setLegacyMemberId(7L).setSourceTenantId(1L)));
        when(checkouts.selectByNoAndMemberForUpdate("pc5",70L)).thenReturn(checkout);
        when(relations.selectListByCheckout(5L,"pc5")).thenReturn(List.of(relation));
        when(orders.selectByIdForUpdate(8L)).thenReturn(new TradeOrderDO().setId(8L).setUserId(7L)
                .setStatus(0).setPayStatus(false).setPayOrderId(30L));
        when(pays.getOrder(30L)).thenAnswer(invocation -> { assertEquals(1L,TenantContextHolder.getRequiredTenantId()); return CommonResult.success(pay); });
        when(pays.closeOrder(30L,"mall","-5")).thenAnswer(invocation -> { pay.setStatus(30); return CommonResult.success(true); });
        when(orders.updateByIdAndStatus(eq(8L), eq(0), any())).thenReturn(1);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void closesAggregatePaymentUsingItsRealMerchantIdAndCancelsSingleChild() {
        assertTrue(service.cancelSingleChildByBuyer(8L,7L,1L));
        verify(pays).closeOrder(30L,"mall","-5");
        assertEquals("CLOSED", checkout.getPayStatus());
        assertEquals(1L,TenantContextHolder.getRequiredTenantId());
    }
    @Test void deniesOtherBuyerBeforePaymentAccess() {
        when(checkouts.selectByNoAndMemberForUpdate("pc5",70L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.cancelSingleChildByBuyer(8L,7L,1L));
        verifyNoInteractions(pays);
    }
    @Test void neverClosesSharedMultiChildPayment() {
        when(relations.selectListByCheckout(5L,"pc5")).thenReturn(List.of(relation, new PlatformCheckoutOrderDO().setOrderId(9L)));
        assertThrows(RuntimeException.class, () -> service.cancelSingleChildByBuyer(8L,7L,1L));
        verifyNoInteractions(pays);
    }
    @Test void paidOrMismatchedPaymentCannotCancel() {
        pay.setStatus(10);
        assertThrows(RuntimeException.class, () -> service.cancelSingleChildByBuyer(8L,7L,1L));
        pay.setStatus(0).setMerchantOrderId("8");
        assertThrows(RuntimeException.class, () -> service.cancelSingleChildByBuyer(8L,7L,1L));
        verify(pays,never()).closeOrder(anyLong(),anyString(),anyString());
        verify(orders,never()).updateByIdAndStatus(anyLong(),anyInt(),any());
    }
}
