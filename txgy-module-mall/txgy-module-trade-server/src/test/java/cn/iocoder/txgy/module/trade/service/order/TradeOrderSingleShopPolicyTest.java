package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.member.api.address.MemberAddressApi;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO;
import cn.iocoder.txgy.module.trade.service.cart.CartService;
import cn.iocoder.txgy.module.trade.service.price.TradePriceService;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateRespBO;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsStockService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeOrderSingleShopPolicyTest {

    @Test
    void oneEnterpriseShopOrPlatformShopIsAllowed() {
        assertEquals(18L, TradeOrderUpdateServiceImpl.requireSingleShop(priceItems(18L,18L)));
        assertEquals(0L, TradeOrderUpdateServiceImpl.requireSingleShop(priceItems(null,0L)));
    }

    @Test
    void twoEnterpriseShopsAndPlatformMixedWithEnterpriseAreRejected() {
        assertThrows(ServiceException.class,
                () -> TradeOrderUpdateServiceImpl.requireSingleShop(priceItems(18L,19L)));
        assertThrows(ServiceException.class,
                () -> TradeOrderUpdateServiceImpl.requireSingleShop(priceItems(0L,19L)));
        assertThrows(ServiceException.class,
                () -> TradeOrderUpdateServiceImpl.requireSingleShop(priceItems(null,19L)));
    }

    @Test
    void settlementUsesTrustedCalculatedShopSnapshotsInsteadOfClientSkuPayload() {
        TradeOrderUpdateServiceImpl service = new TradeOrderUpdateServiceImpl();
        MemberAddressApi addressApi = mock(MemberAddressApi.class);
        CartService cartService = mock(CartService.class);
        TradePriceService priceService = mock(TradePriceService.class);
        TradeWmsStockService stockService = mock(TradeWmsStockService.class);
        ReflectionTestUtils.setField(service,"addressApi",addressApi);
        ReflectionTestUtils.setField(service,"cartService",cartService);
        ReflectionTestUtils.setField(service,"tradePriceService",priceService);
        ReflectionTestUtils.setField(service,"wmsStockService",stockService);
        when(addressApi.getDefaultAddress(7L)).thenReturn(CommonResult.success(null));
        when(cartService.getCartList(eq(7L),anySet())).thenReturn(List.of());
        when(priceService.calculateOrderPrice(any())).thenReturn(priceItems(18L,19L));
        AppTradeOrderSettlementReqVO request = new AppTradeOrderSettlementReqVO()
                .setItems(List.of(item(101L),item(102L))).setPointStatus(false).setDeliveryType(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.settlementOrder(7L,request));

        assertTrue(error.getMessage().contains("分别结算"));
        verifyNoInteractions(stockService);
    }

    private static AppTradeOrderSettlementReqVO.Item item(Long skuId) {
        return new AppTradeOrderSettlementReqVO.Item().setSkuId(skuId).setCount(1);
    }

    private static TradePriceCalculateRespBO priceItems(Long... shopIds) {
        TradePriceCalculateRespBO result = new TradePriceCalculateRespBO();
        result.setItems(java.util.Arrays.stream(shopIds).map(shopId -> {
            TradePriceCalculateRespBO.OrderItem item = new TradePriceCalculateRespBO.OrderItem();
            item.setShopId(shopId);
            item.setSkuId(shopId == null ? 1L : shopId + 100L);
            item.setCount(1);
            return item;
        }).toList());
        return result;
    }
}
