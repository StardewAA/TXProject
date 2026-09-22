package cn.iocoder.txgy.module.trade.service.price.calculator;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.txgy.module.trade.service.config.TradeConfigService;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressTemplateService;
import cn.iocoder.txgy.module.trade.service.delivery.bo.DeliveryExpressTemplateRespBO;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeDeliveryPriceCalculatorTest {

    private DeliveryExpressTemplateService templates;
    private TradeConfigService config;
    private TradeDeliveryPriceCalculator calculator;

    @BeforeEach
    void setUp() {
        templates = mock(DeliveryExpressTemplateService.class);
        config = mock(TradeConfigService.class);
        calculator = new TradeDeliveryPriceCalculator();
        ReflectionTestUtils.setField(calculator, "deliveryExpressTemplateService", templates);
        ReflectionTestUtils.setField(calculator, "tradeConfigService", config);
    }

    @Test
    void calculatesCountTemplateWithStandardAlgorithm() {
        when(templates.getExpressTemplateMapByIdsAndArea(eq(java.util.Set.of(701L)), eq(610100)))
                .thenReturn(Map.of(701L, template(1, 1D, 100, 1D, 50)));
        TradePriceCalculateRespBO result = price(item(601L, 701L, 3, null, null, 1200));

        calculator.calculateExpressForArea(610100, result);

        assertEquals(200, result.getPrice().getDeliveryPrice());
        assertEquals(3800, result.getPrice().getPayPrice());
        assertEquals(200, result.getItems().getFirst().getDeliveryPrice());
    }

    @Test
    void rejectsWeightTemplateWhenSkuWeightIsMissing() {
        when(templates.getExpressTemplateMapByIdsAndArea(any(), eq(610100)))
                .thenReturn(Map.of(701L, template(2, 1D, 100, 1D, 50)));
        TradePriceCalculateRespBO result = price(item(601L, 701L, 1, null, null, 1200));

        assertThrows(ServiceException.class, () -> calculator.calculateExpressForArea(610100, result));
    }

    @Test
    void rejectsIncompleteTemplateMapInsteadOfTreatingMissingTemplateAsFree() {
        when(templates.getExpressTemplateMapByIdsAndArea(any(), eq(610100)))
                .thenReturn(Map.of(701L, template(1, 1D, 100, 1D, 50)));
        TradePriceCalculateRespBO result = price(
                item(601L, 701L, 1, null, null, 1200),
                item(602L, 702L, 1, null, null, 800));

        assertThrows(ServiceException.class, () -> calculator.calculateExpressForArea(610100, result));
    }

    @Test
    void appliesTenantGlobalFreeRuleBeforeTemplateCharge() {
        when(config.getTradeConfig()).thenReturn(new TradeConfigDO()
                .setDeliveryExpressFreeEnabled(true).setDeliveryExpressFreePrice(1000));
        TradePriceCalculateRespBO result = price(item(601L, 701L, 1, null, null, 1200));

        calculator.calculateExpressForArea(610100, result);

        assertEquals(0, result.getPrice().getDeliveryPrice());
        assertEquals(1200, result.getPrice().getPayPrice());
        verify(templates, never()).getExpressTemplateMapByIdsAndArea(any(), any());
    }

    private static TradePriceCalculateRespBO price(TradePriceCalculateRespBO.OrderItem... items) {
        int goods = java.util.Arrays.stream(items).mapToInt(item -> item.getPrice() * item.getCount()).sum();
        return new TradePriceCalculateRespBO().setItems(List.of(items)).setFreeDelivery(false)
                .setPrice(new TradePriceCalculateRespBO.Price().setTotalPrice(goods).setDiscountPrice(0)
                        .setDeliveryPrice(0).setCouponPrice(0).setPointPrice(0).setVipPrice(0)
                        .setPayPrice(goods));
    }

    private static TradePriceCalculateRespBO.OrderItem item(long skuId, long templateId, int count,
                                                             Double weight, Double volume, int price) {
        return new TradePriceCalculateRespBO.OrderItem().setSkuId(skuId).setCount(count).setSelected(true)
                .setPrice(price).setDiscountPrice(0).setDeliveryPrice(0).setCouponPrice(0)
                .setPointPrice(0).setVipPrice(0).setPayPrice(price * count)
                .setDeliveryTypes(List.of(1)).setDeliveryTemplateId(templateId)
                .setWeight(weight).setVolume(volume);
    }

    private static DeliveryExpressTemplateRespBO template(int mode, double startCount, int startPrice,
                                                           double extraCount, int extraPrice) {
        return new DeliveryExpressTemplateRespBO().setChargeMode(mode)
                .setCharge(new DeliveryExpressTemplateRespBO.Charge().setStartCount(startCount)
                        .setStartPrice(startPrice).setExtraCount(extraCount).setExtraPrice(extraPrice));
    }

}
