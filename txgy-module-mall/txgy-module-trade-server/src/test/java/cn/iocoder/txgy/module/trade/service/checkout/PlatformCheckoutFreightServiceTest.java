package cn.iocoder.txgy.module.trade.service.checkout;

import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.sku.ProductPublicSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuLogisticsRespDTO;
import cn.iocoder.txgy.module.trade.service.price.calculator.TradeDeliveryPriceCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformCheckoutFreightServiceTest {

    private ProductPublicSkuApi publicSkuApi;
    private TradeDeliveryPriceCalculator calculator;
    private PlatformCheckoutFreightService service;

    @BeforeEach
    void setUp() {
        publicSkuApi = mock(ProductPublicSkuApi.class);
        calculator = mock(TradeDeliveryPriceCalculator.class);
        service = new PlatformCheckoutFreightService(publicSkuApi, calculator);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsControlledSnapshotInSourceContextAndCalculatesEachTargetTenantWithoutLeakingContext() {
        LoginUser loginUser = new LoginUser().setId(316L).setTenantId(2009L);
        var authentication = new UsernamePasswordAuthenticationToken(loginUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContextHolder.setTenantId(2009L);
        when(publicSkuApi.getLogisticsList(any())).thenAnswer(invocation -> {
            assertEquals(2009L, TenantContextHolder.getRequiredTenantId());
            assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
            return List.of(
                    logistics(2001L, 301L, 401L, 501L, 601L, 1200, 100, 701L),
                    logistics(2002L, 302L, 402L, 502L, 602L, 500, 100, 702L));
        });
        doAnswer(invocation -> {
            var price = invocation.getArgument(1,
                    cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateRespBO.class);
            long targetTenant = TenantContextHolder.getRequiredTenantId();
            assertTrue(targetTenant == 2001L || targetTenant == 2002L);
            assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
            int freight = targetTenant == 2001L ? 300 : 500;
            price.getItems().getFirst().setDeliveryPrice(freight)
                    .setPayPrice(price.getItems().getFirst().getPayPrice() + freight);
            price.getPrice().setDeliveryPrice(freight)
                    .setPayPrice(price.getPrice().getPayPrice() + freight);
            return null;
        }).when(calculator).calculateExpressForArea(anyInt(), any());

        Map<PlatformCheckoutFreightService.ShopKey, PlatformCheckoutFreightService.FreightQuote> result =
                service.quote(610100L, List.of(
                        item(2001L, 301L, 401L, 1L, 501L, 601L, 2, 1200),
                        item(2002L, 302L, 402L, 2L, 502L, 602L, 1, 500)));

        assertEquals(300L, result.get(new PlatformCheckoutFreightService.ShopKey(2001L, 401L)).freightAmount());
        assertEquals(500L, result.get(new PlatformCheckoutFreightService.ShopKey(2002L, 402L)).freightAmount());
        assertTrue(result.values().stream().allMatch(PlatformCheckoutFreightService.FreightQuote::ready));
        assertEquals(2009L, TenantContextHolder.getRequiredTenantId());
        assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void missingControlledSnapshotReturnsUnknownInsteadOfZeroFreight() {
        when(publicSkuApi.getLogisticsList(any())).thenReturn(List.of());

        var result = service.quote(610100L,
                List.of(item(2001L, 301L, 401L, 1L, 501L, 601L, 1, 1200)));

        var quote = result.get(new PlatformCheckoutFreightService.ShopKey(2001L, 401L));
        assertFalse(quote.ready());
        assertNull(quote.freightAmount());
        assertEquals(PlatformCheckoutFreightService.PRODUCT_LOGISTICS_INCOMPLETE, quote.failureReason());
        verifyNoInteractions(calculator);
    }

    @Test
    void mismatchedOwnershipSnapshotIsRejectedBeforeTemplateCalculation() {
        when(publicSkuApi.getLogisticsList(any())).thenReturn(List.of(
                logistics(2001L, 999L, 401L, 501L, 601L, 1200, 100, 701L)));

        var result = service.quote(610100L,
                List.of(item(2001L, 301L, 401L, 1L, 501L, 601L, 1, 1200)));

        var quote = result.get(new PlatformCheckoutFreightService.ShopKey(2001L, 401L));
        assertFalse(quote.ready());
        assertNull(quote.freightAmount());
        assertEquals(PlatformCheckoutFreightService.PRODUCT_LOGISTICS_INCOMPLETE, quote.failureReason());
        verifyNoInteractions(calculator);
    }

    @Test
    void controlledEndpointFailureReturnsUnknownForAllShops() {
        when(publicSkuApi.getLogisticsList(any())).thenThrow(new IllegalStateException("product unavailable"));

        var result = service.quote(610100L,
                List.of(item(2001L, 301L, 401L, 1L, 501L, 601L, 1, 1200)));

        var quote = result.get(new PlatformCheckoutFreightService.ShopKey(2001L, 401L));
        assertFalse(quote.ready());
        assertNull(quote.freightAmount());
        assertEquals(PlatformCheckoutFreightService.TENANT_LOGISTICS_UNAVAILABLE, quote.failureReason());
        verifyNoInteractions(calculator);
    }

    @Test
    void unsupportedAddressAreaReturnsUnknownWithoutCallingProductOrTemplateApis() {
        var result = service.quote((long) Integer.MAX_VALUE + 1,
                List.of(item(2001L, 301L, 401L, 1L, 501L, 601L, 1, 1200)));

        var quote = result.get(new PlatformCheckoutFreightService.ShopKey(2001L, 401L));
        assertFalse(quote.ready());
        assertNull(quote.freightAmount());
        assertEquals(PlatformCheckoutFreightService.ADDRESS_AREA_UNSUPPORTED, quote.failureReason());
        verifyNoInteractions(publicSkuApi, calculator);
    }

    private static ProductPublicSkuLogisticsRespDTO logistics(long tenantId, long enterpriseId, long shopId,
                                                               long spuId, long skuId, int price, int stock,
                                                               long templateId) {
        return new ProductPublicSkuLogisticsRespDTO().setTenantId(tenantId).setEnterpriseId(enterpriseId)
                .setShopId(shopId).setSpuId(spuId).setSkuId(skuId).setPrice(price).setStock(stock)
                .setSaleable(true).setDeliveryTypes(List.of(1)).setDeliveryTemplateId(templateId)
                .setWeight(1.0).setVolume(0.1);
    }

    private static PlatformCheckoutFreightService.FreightItem item(long tenantId, long enterpriseId, long shopId,
                                                                    long cartId, long spuId, long skuId,
                                                                    int quantity, int unitPrice) {
        return new PlatformCheckoutFreightService.FreightItem(tenantId, enterpriseId, shopId, cartId, spuId, skuId,
                quantity, unitPrice, (long) quantity * unitPrice);
    }

}
