package cn.iocoder.txgy.module.trade.service.comparison;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.api.address.MemberAddressApi;
import cn.iocoder.txgy.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.txgy.module.product.api.sku.ProductSkuApi;
import cn.iocoder.txgy.module.product.api.sku.ProductPublicSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuLogisticsRespDTO;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.comparison.vo.AppProductComparisonQuoteReqVO;
import cn.iocoder.txgy.module.trade.controller.app.comparison.vo.AppProductComparisonQuoteRespVO;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressTemplateService;
import cn.iocoder.txgy.module.trade.service.checkout.PlatformCheckoutFreightService;
import cn.iocoder.txgy.module.trade.service.delivery.bo.DeliveryExpressTemplateRespBO;
import cn.iocoder.txgy.module.trade.service.price.TradePriceService;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeProductComparisonQuoteServiceTest {
    ProductSkuApi skus=mock(ProductSkuApi.class);
    ProductSpuApi spus=mock(ProductSpuApi.class);
    MemberAddressApi addresses=mock(MemberAddressApi.class);
    DeliveryExpressTemplateService templates=mock(DeliveryExpressTemplateService.class);
    TradePriceService prices=mock(TradePriceService.class);
    ProductPublicSkuApi publicSkus=mock(ProductPublicSkuApi.class);
    PlatformCheckoutFreightService platformFreight=mock(PlatformCheckoutFreightService.class);
    TradeProductComparisonQuoteService service=new TradeProductComparisonQuoteService(skus,spus,addresses,templates,prices,publicSkus,platformFreight);
    ProductSkuRespDTO sku;
    ProductSpuRespDTO spu;
    MemberAddressRespDTO address;
    DeliveryExpressTemplateRespBO shipping;
    @BeforeEach void init(){
        TenantContextHolder.setTenantId(1L);TenantContextHolder.setIgnore(false);
        sku=new ProductSkuRespDTO().setId(201L).setSpuId(101L).setPrice(1000).setStock(1000).setWeight(2D).setVolume(1D);
        spu=new ProductSpuRespDTO().setId(101L).setShopId(41L).setStatus(1).setSaleable(true).setDeliveryTypes(List.of(1)).setDeliveryTemplateId(14L);
        address=new MemberAddressRespDTO().setId(91L).setUserId(71L).setAreaId(110101).setName("private-name").setMobile("private-phone").setDetailAddress("private-address");
        shipping=new DeliveryExpressTemplateRespBO().setChargeMode(1).setCharge(new DeliveryExpressTemplateRespBO.Charge()
                .setStartCount(1D).setStartPrice(100).setExtraCount(1D).setExtraPrice(0));
        when(skus.getSku(201L)).thenReturn(sku);when(spus.validateSpuList(List.of(101L))).thenReturn(CommonResult.success(List.of(spu)));
        when(addresses.getAddress(91L,71L)).thenReturn(CommonResult.success(address));
        when(templates.getExpressTemplateMapByIdsAndArea(List.of(14L),110101)).thenReturn(Map.of(14L,shipping));
        when(prices.calculateOrderPrice(any(TradePriceCalculateReqBO.class))).thenAnswer(call->{var r=call.<TradePriceCalculateReqBO>getArgument(0);return calculation(r.getItems().getFirst().getSkuId(),r.getItems().getFirst().getCount());});
    }
    @AfterEach void reset(){TenantContextHolder.clear();}
    AppProductComparisonQuoteReqVO request(){return new AppProductComparisonQuoteReqVO().setSkuIds(List.of(201L)).setQuantity(3).setAddressId(91L);}
    TradePriceCalculateRespBO calculation(Long id,int count){
        return new TradePriceCalculateRespBO().setType(0).setItems(List.of(new TradePriceCalculateRespBO.OrderItem()
                .setSkuId(id).setCount(count).setPrice(1000).setSelected(true))).setPrice(new TradePriceCalculateRespBO.Price()
                .setTotalPrice(1000*count).setDiscountPrice(100).setVipPrice(50).setDeliveryPrice(100).setCouponPrice(0).setPointPrice(0).setPayPrice(1000*count-50));
    }
    AppProductComparisonQuoteRespVO.Item quote(){return service.quote(71L,request()).getItems().getFirst();}
    @Test void quoteUsesCurrentMemberAndExplicitAddressWithoutCouponsPointsOrCart(){
        var result=service.quote(71L,request());var item=result.getItems().getFirst();
        assertEquals("AVAILABLE",item.getStatus());assertTrue(item.isAmountsComplete());assertEquals(3000L,item.getGoodsAmount());
        assertEquals(150L,item.getDiscountAmount());assertEquals(100L,item.getShippingFee());assertEquals(2950L,item.getTotalAmount());
        assertEquals("CHECKOUT_ESTIMATE",result.getPriceBasis());assertTrue(result.getCalculatedAt()>0);assertEquals(3,result.getWarnings().size());
        var captured=ArgumentCaptor.forClass(TradePriceCalculateReqBO.class);verify(prices).calculateOrderPrice(captured.capture());
        var input=captured.getValue();assertEquals(71L,input.getUserId());assertEquals(91L,input.getAddressId());assertFalse(input.getPointStatus());
        assertNull(input.getCouponId());assertNull(input.getItems().getFirst().getCartId());assertNull(input.getCombinationActivityId());assertNull(input.getSeckillActivityId());
        verify(addresses,never()).getDefaultAddress(any());verify(skus,never()).reserveSkuStock(any());verify(skus,never()).updateSkuStock(any());
        assertFalse(result.toString().contains("private-name"));assertFalse(result.toString().contains("private-phone"));assertFalse(result.toString().contains("private-address"));
    }
    @Test void noAddressKeepsShippingAndTotalUnknownWithoutDefaultAddressLookup(){
        var input=request().setAddressId(null);var item=service.quote(71L,input).getItems().getFirst();assertEquals("ADDRESS_REQUIRED",item.getStatus());
        assertEquals(3000L,item.getGoodsAmount());assertNull(item.getShippingFee());assertNull(item.getTotalAmount());assertFalse(item.isAmountsComplete());
        verifyNoInteractions(addresses,templates,prices);
    }
    @Test void foreignAddressFailsBeforeProductReads(){
        address.setUserId(72L);assertEquals("ADDRESS_UNAVAILABLE",quote().getStatus());verifyNoInteractions(skus,spus,templates,prices);
    }
    @Test void mismatchedAddressIdFailsClosed(){address.setId(92L);assertEquals("ADDRESS_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void missingAddressRegionFailsClosed(){address.setAreaId(null);assertEquals("ADDRESS_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void addressFailureNeverReflectsRemoteSecrets(){
        when(addresses.getAddress(any(),any())).thenThrow(new IllegalStateException("private-token"));var result=quote();
        assertEquals("ADDRESS_UNAVAILABLE",result.getStatus());assertFalse(result.toString().contains("private-token"));verifyNoInteractions(prices);
    }
    @Test void anonymousAndIgnoredTenantCannotUsePrivatePricing(){
        assertThrows(ServiceException.class,()->service.quote(null,request()));TenantContextHolder.setIgnore(true);
        assertThrows(ServiceException.class,()->service.quote(71L,request()));verifyNoInteractions(addresses,skus,spus,templates,prices);
    }
    @Test void missingTenantFailsBeforeRpc(){TenantContextHolder.clear();assertThrows(ServiceException.class,()->service.quote(71L,request()));verifyNoInteractions(addresses,prices);}
    @ParameterizedTest @ValueSource(ints={-1,0,1000,Integer.MAX_VALUE})
    void illegalQuantityCannotReachCalculator(int quantity){assertThrows(ServiceException.class,()->service.quote(71L,request().setQuantity(quantity)));verifyNoInteractions(prices,skus);}
    @Test void duplicateIdsAreRejected(){assertThrows(ServiceException.class,()->service.quote(71L,request().setSkuIds(List.of(201L,201L))));verifyNoInteractions(prices,skus);}
    @Test void emptyOrOverLimitIdsRejected(){
        assertThrows(ServiceException.class,()->service.quote(71L,request().setSkuIds(List.of())));
        assertThrows(ServiceException.class,()->service.quote(71L,request().setSkuIds(List.of(1L,2L,3L,4L,5L))));
        assertThrows(ServiceException.class,()->service.quote(71L,request().setSkuIds(Arrays.asList(201L,null))));verifyNoInteractions(prices,skus);
    }
    @Test void unreturnedSkuIsNotSilentlySkipped(){when(skus.getSku(201L)).thenReturn(null);assertEquals("PRODUCT_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void mismatchedSkuCannotBePriced(){sku.setId(202L);assertEquals("PRODUCT_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void crossTenantOrHiddenSpuFailsSameGenericWay(){
        when(spus.validateSpuList(any())).thenThrow(new IllegalStateException("other-tenant-hidden-title"));var item=quote();
        assertEquals("PRODUCT_UNAVAILABLE",item.getStatus());assertFalse(item.toString().contains("hidden-title"));assertNull(item.getUnitPrice());verifyNoInteractions(prices);
    }
    @Test void crossTenantPublicSkuShowsVerifiedGoodsAmountWithoutAddress(){
        when(skus.getSku(201L)).thenThrow(new IllegalStateException("different tenant"));
        when(publicSkus.getLogisticsList(List.of(201L))).thenReturn(List.of(new ProductPublicSkuLogisticsRespDTO()
                .setTenantId(2002L).setEnterpriseId(301L).setShopId(401L).setSpuId(101L).setSkuId(201L)
                .setPrice(1200).setStock(20).setSaleable(true)));
        var item=service.quote(71L,request().setAddressId(null).setQuantity(2)).getItems().getFirst();
        assertEquals("ADDRESS_REQUIRED",item.getStatus());assertEquals(Integer.valueOf(1200),item.getUnitPrice());
        assertEquals(2400L,item.getGoodsAmount());assertNull(item.getShippingFee());assertFalse(item.isAmountsComplete());
    }
    @Test void crossTenantPublicSkuUsesPlatformFreightForCompleteEstimate(){
        when(skus.getSku(201L)).thenThrow(new IllegalStateException("different tenant"));
        var publicSku=new ProductPublicSkuLogisticsRespDTO().setTenantId(2002L).setEnterpriseId(301L)
                .setShopId(401L).setSpuId(101L).setSkuId(201L).setPrice(1200).setStock(20).setSaleable(true);
        when(publicSkus.getLogisticsList(List.of(201L))).thenReturn(List.of(publicSku));
        var key=new PlatformCheckoutFreightService.ShopKey(2002L,401L);
        when(platformFreight.quote(eq(110101L),any())).thenReturn(Map.of(key,new PlatformCheckoutFreightService.FreightQuote(true,300L,null)));
        var item=service.quote(71L,request().setQuantity(2)).getItems().getFirst();
        assertEquals("AVAILABLE",item.getStatus());assertEquals(2400L,item.getGoodsAmount());
        assertEquals(0L,item.getDiscountAmount());assertEquals(300L,item.getShippingFee());assertEquals(2700L,item.getTotalAmount());
    }
    @Test void unauditedOrDisabledShopCannotQuote(){spu.setSaleable(false);assertEquals("PRODUCT_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void partialSpuResponseFailsClosed(){when(spus.validateSpuList(any())).thenReturn(CommonResult.success(List.of()));assertEquals("PRODUCT_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void insufficientStockDoesNotReserveOrCalculate(){sku.setStock(2);assertEquals("INSUFFICIENT_STOCK",quote().getStatus());verifyNoInteractions(prices);verify(skus,never()).reserveSkuStock(any());}
    @Test void grossOverflowRejectedBeforeSharedIntegerArithmetic(){sku.setPrice(Integer.MAX_VALUE);assertEquals("PRICE_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void deliveryNotSupportedIsUnknownNotFree(){spu.setDeliveryTypes(List.of(2));var item=quote();assertEquals("SHIPPING_UNAVAILABLE",item.getStatus());assertNull(item.getShippingFee());verifyNoInteractions(prices);}
    @Test void missingTemplateChargeIsNotZeroShipping(){shipping.setCharge(null);assertEquals("SHIPPING_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void missingTemplateForRegionIsNotZeroShipping(){when(templates.getExpressTemplateMapByIdsAndArea(any(),any())).thenReturn(Map.of());assertEquals("SHIPPING_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void missingWeightAndVolumeDoNotBecomeZeroCharge(){
        shipping.setChargeMode(2);sku.setWeight(0D);assertEquals("SHIPPING_UNAVAILABLE",quote().getStatus());
        shipping.setChargeMode(3);sku.setVolume(null);assertEquals("SHIPPING_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);
    }
    @Test void unsafeShippingArithmeticFailsBeforeCalculator(){shipping.getCharge().setExtraCount(0D);assertEquals("SHIPPING_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void shippingOverflowCannotWrapIntoCheapTotal(){shipping.getCharge().setExtraPrice(Integer.MAX_VALUE);assertEquals("SHIPPING_UNAVAILABLE",quote().getStatus());verifyNoInteractions(prices);}
    @Test void oneSkuFailureDoesNotDiscardOtherQuotes(){
        var result=service.quote(71L,request().setSkuIds(List.of(202L,201L)));assertEquals(2,result.getItems().size());
        assertEquals("PRODUCT_UNAVAILABLE",result.getItems().getFirst().getStatus());assertEquals("AVAILABLE",result.getItems().get(1).getStatus());verify(prices,times(1)).calculateOrderPrice(any());
    }
    @Test void pricingFailureIsSanitized(){when(prices.calculateOrderPrice(any())).thenThrow(new IllegalStateException("private-member-balance"));var item=quote();assertEquals("PRICE_UNAVAILABLE",item.getStatus());assertFalse(item.toString().contains("balance"));}
    @Test void missingOrWrongSkuInCalculationCannotProduceCompleteAmount(){
        when(prices.calculateOrderPrice(any())).thenReturn(calculation(202L,3));assertEquals("PRICE_UNAVAILABLE",quote().getStatus());
        when(prices.calculateOrderPrice(any())).thenReturn(calculation(201L,3).setItems(List.of()));assertEquals("PRICE_UNAVAILABLE",quote().getStatus());
    }
    @Test void changedUnitPriceRequiresRequote(){var calc=calculation(201L,3);calc.getItems().getFirst().setPrice(900);when(prices.calculateOrderPrice(any())).thenReturn(calc);assertEquals("PRICE_UNAVAILABLE",quote().getStatus());}
    @Test void unexpectedCouponOrPointUseIsRejected(){var calc=calculation(201L,3);calc.getPrice().setPointPrice(10);when(prices.calculateOrderPrice(any())).thenReturn(calc);assertEquals("PRICE_UNAVAILABLE",quote().getStatus());}
    @Test void negativeOrInconsistentTotalsAreNeverPresented(){
        var calc=calculation(201L,3);calc.getPrice().setPayPrice(-1);when(prices.calculateOrderPrice(any())).thenReturn(calc);assertEquals("PRICE_UNAVAILABLE",quote().getStatus());
        calc.getPrice().setPayPrice(1);assertEquals("PRICE_UNAVAILABLE",quote().getStatus());
    }
    @Test void validZeroShippingStillRequiresAddressAndCompleteTemplate(){
        shipping.getCharge().setStartPrice(0);var calc=calculation(201L,3);calc.getPrice().setDeliveryPrice(0).setPayPrice(2850);when(prices.calculateOrderPrice(any())).thenReturn(calc);
        var item=quote();assertEquals("AVAILABLE",item.getStatus());assertEquals(0L,item.getShippingFee());assertTrue(item.isAmountsComplete());
    }
}
