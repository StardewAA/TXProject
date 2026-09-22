package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.module.product.api.inquiry.dto.InquiryAcceptedQuoteRespDTO;
import cn.iocoder.txgy.module.product.api.inquiry.ProductInquiryApi;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeInquiryOrderCreateReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateRespBO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeInquiryOrderPriceTest {

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void acceptedQuoteOverridesCatalogAndDisablesAllMarketing() {
        InquiryAcceptedQuoteRespDTO quote = quote(31L,
                quoteItem(101L, 1001L, 2, 1250),
                quoteItem(102L, 1002L, 3, 800));
        TradePriceCalculateRespBO calculated = calculated(
                orderItem(31L, 1001L, 2, 1800),
                orderItem(31L, 1002L, 3, 1200));
        calculated.setCouponId(99L);
        calculated.setUsePoint(100);
        calculated.setGivePoint(30);
        calculated.setFreeDelivery(true);
        calculated.setPromotions(new ArrayList<>(List.of(new TradePriceCalculateRespBO.Promotion())));
        calculated.setGiveCouponTemplateCounts(new LinkedHashMap<>(java.util.Map.of(8L, 1)));

        TradeOrderUpdateServiceImpl.applyAcceptedInquiryQuotePrice(316L, quote, calculated);

        assertEquals(1250, calculated.getItems().get(0).getPrice());
        assertEquals(800, calculated.getItems().get(1).getPrice());
        assertEquals(4900, calculated.getPrice().getTotalPrice());
        assertEquals(4900, calculated.getPrice().getPayPrice());
        assertEquals(0, calculated.getPrice().getDiscountPrice());
        assertEquals(0, calculated.getPrice().getCouponPrice());
        assertEquals(0, calculated.getPrice().getPointPrice());
        assertEquals(0, calculated.getPrice().getVipPrice());
        assertEquals(0, calculated.getPrice().getDeliveryPrice());
        assertEquals(0, calculated.getUsePoint());
        assertEquals(0, calculated.getGivePoint());
        assertTrue(calculated.getPromotions().isEmpty());
        assertTrue(calculated.getCoupons().isEmpty());
        assertTrue(calculated.getGiveCouponTemplateCounts().isEmpty());
    }

    @Test
    void quoteForAnotherBuyerIsRejected() {
        InquiryAcceptedQuoteRespDTO quote = quote(31L, quoteItem(101L, 1001L, 2, 1250));
        assertThrows(RuntimeException.class,
                () -> TradeOrderUpdateServiceImpl.applyAcceptedInquiryQuotePrice(999L, quote,
                        calculated(orderItem(31L, 1001L, 2, 1800))));
    }

    @Test
    void crossShopSkuIsRejected() {
        InquiryAcceptedQuoteRespDTO quote = quote(31L, quoteItem(101L, 1001L, 2, 1250));
        assertThrows(RuntimeException.class,
                () -> TradeOrderUpdateServiceImpl.applyAcceptedInquiryQuotePrice(316L, quote,
                        calculated(orderItem(32L, 1001L, 2, 1800))));
    }

    @Test
    void changedSkuCountIsRejected() {
        InquiryAcceptedQuoteRespDTO quote = quote(31L, quoteItem(101L, 1001L, 2, 1250));
        assertThrows(RuntimeException.class,
                () -> TradeOrderUpdateServiceImpl.applyAcceptedInquiryQuotePrice(316L, quote,
                        calculated(orderItem(31L, 1001L, 3, 1800))));
    }

    @Test
    void duplicateSkuRowsAreRejected() {
        InquiryAcceptedQuoteRespDTO quote = quote(31L,
                quoteItem(101L, 1001L, 1, 1250),
                quoteItem(102L, 1001L, 1, 1250));
        assertThrows(RuntimeException.class,
                () -> TradeOrderUpdateServiceImpl.validateAcceptedInquiryQuote(316L, 9001L, quote));
    }

    @Test
    void acceptedQuoteIsTheStableIdempotencyBoundaryAcrossRequestKeys() {
        TradeOrderMapper mapper = mock(TradeOrderMapper.class);
        ProductInquiryApi inquiryApi = mock(ProductInquiryApi.class);
        TradeOrderUpdateServiceImpl service = new TradeOrderUpdateServiceImpl();
        ReflectionTestUtils.setField(service, "tradeOrderMapper", mapper);
        ReflectionTestUtils.setField(service, "productInquiryApi", inquiryApi);
        TradeOrderDO existing = new TradeOrderDO().setId(7001L).setInquiryQuoteId(9001L)
                .setBuyerEnterpriseId(501L).setPayOrderId(6001L);
        when(inquiryApi.getAcceptedQuote(316L, 9001L)).thenReturn(success(quote(31L,
                quoteItem(101L, 1001L, 2, 1250))));
        when(mapper.selectByInquiryQuoteId(9001L)).thenReturn(existing);

        AppTradeInquiryOrderCreateReqVO first = createRequest("request-a");
        AppTradeInquiryOrderCreateReqVO retried = createRequest("request-b");
        assertSame(existing, service.createInquiryOrder(316L, first));
        assertSame(existing, service.createInquiryOrder(316L, retried));
        verify(mapper, times(2)).selectByInquiryQuoteId(9001L);
    }

    @Test
    void createRequestRequiresAddressAndRequestKey() {
        AppTradeInquiryOrderCreateReqVO request = new AppTradeInquiryOrderCreateReqVO();
        request.setQuoteId(9001L);
        request.setRequestKey(" ");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var invalidFields = factory.getValidator().validate(request).stream()
                    .map(violation -> violation.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
            assertTrue(invalidFields.contains("addressId"));
            assertTrue(invalidFields.contains("requestKey"));

            request.setAddressId(2001L);
            request.setRequestKey("rfq-order-9001");
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }

    private static AppTradeInquiryOrderCreateReqVO createRequest(String requestKey) {
        return new AppTradeInquiryOrderCreateReqVO().setQuoteId(9001L).setAddressId(2001L)
                .setRemark("按报价发货").setRequestKey(requestKey);
    }

    private static InquiryAcceptedQuoteRespDTO quote(Long shopId, InquiryAcceptedQuoteRespDTO.Item... items) {
        return new InquiryAcceptedQuoteRespDTO().setQuoteId(9001L).setInquiryId(8001L)
                .setBuyerEnterpriseId(501L).setBuyerMemberId(316L)
                .setShopId(shopId).setVersion(2L).setTitle("保温材料采购")
                .setItems(List.of(items));
    }

    private static InquiryAcceptedQuoteRespDTO.Item quoteItem(Long inquiryItemId, Long skuId,
                                                              int count, int unitPrice) {
        return new InquiryAcceptedQuoteRespDTO.Item().setInquiryItemId(inquiryItemId).setSkuId(skuId)
                .setCount(count).setUnitPrice(unitPrice);
    }

    private static TradePriceCalculateRespBO calculated(TradePriceCalculateRespBO.OrderItem... items) {
        TradePriceCalculateRespBO result = new TradePriceCalculateRespBO();
        result.setItems(new ArrayList<>(List.of(items)));
        result.setPrice(new TradePriceCalculateRespBO.Price());
        result.setCoupons(new ArrayList<>());
        result.setPromotions(new ArrayList<>());
        result.setGiveCouponTemplateCounts(new LinkedHashMap<>());
        return result;
    }

    private static TradePriceCalculateRespBO.OrderItem orderItem(Long shopId, Long skuId, int count, int price) {
        return new TradePriceCalculateRespBO.OrderItem().setShopId(shopId).setSkuId(skuId).setCount(count)
                .setSelected(true).setPrice(price).setPayPrice(price * count)
                .setDiscountPrice(100).setDeliveryPrice(50).setCouponPrice(20).setPointPrice(10)
                .setUsePoint(10).setGivePoint(20).setVipPrice(30)
                .setSpuId(skuId + 10000).setSpuName("商品 " + skuId).setPicUrl("/" + skuId + ".png");
    }

}
