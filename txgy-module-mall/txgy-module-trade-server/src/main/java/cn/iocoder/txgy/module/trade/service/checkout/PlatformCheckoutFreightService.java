package cn.iocoder.txgy.module.trade.service.checkout;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.product.api.sku.ProductPublicSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuLogisticsRespDTO;
import cn.iocoder.txgy.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateRespBO;
import cn.iocoder.txgy.module.trade.service.price.calculator.TradeDeliveryPriceCalculator;
import cn.iocoder.txgy.module.trade.service.price.calculator.TradePriceCalculatorHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 平台跨租户结算的只读运费核算器。
 *
 * <p>商品物流资料由商品服务的受控公开 SKU 接口按 SKU 编号解析；客户端不能提交或覆盖
 * 租户、店铺、价格、库存和运费模板。真实运费模板仍在对应经营租户中读取并按店铺独立核算。
 * 该服务没有订单、支付、库存写依赖，无法核定时绝不以 0 元代替未知运费。</p>
 */
@Service
@RequiredArgsConstructor
public class PlatformCheckoutFreightService {

    static final String ADDRESS_AREA_UNSUPPORTED = "收货地区编号不受支持，暂不能核定运费";
    static final String TENANT_LOGISTICS_UNAVAILABLE = "租户配送资料暂不可用，请稍后重试";
    static final String PRODUCT_LOGISTICS_INCOMPLETE = "商品配送模板、重量或体积资料不完整";
    static final String FREIGHT_CALCULATION_FAILED = "当前地址的运费暂不能核定，请联系店铺";

    private final ProductPublicSkuApi productPublicSkuApi;
    private final TradeDeliveryPriceCalculator deliveryPriceCalculator;

    public Map<ShopKey, FreightQuote> quote(Long areaId, Collection<FreightItem> items) {
        List<FreightItem> requestedItems = items == null ? List.of()
                : items.stream().filter(Objects::nonNull).toList();
        LinkedHashMap<ShopKey, List<FreightItem>> byShop = groupByShop(requestedItems);
        if (byShop.isEmpty()) {
            return Map.of();
        }
        if (areaId == null || areaId <= 0 || areaId > Integer.MAX_VALUE) {
            return failedQuotes(byShop.keySet(), ADDRESS_AREA_UNSUPPORTED);
        }
        LinkedHashSet<Long> skuIds = new LinkedHashSet<>();
        for (FreightItem item : requestedItems) {
            if (item.skuId() != null && item.skuId() > 0) {
                skuIds.add(item.skuId());
            }
        }
        Map<Long, ProductPublicSkuLogisticsRespDTO> logisticsMap;
        try {
            // 必须在源租户安全上下文中调用：Feign 透传的租户头与登录用户一致；商品服务的
            // @TenantIgnore 白名单端点负责受控跨租户解析，禁止通过清空 SecurityContext 绕过校验。
            logisticsMap = uniqueLogisticsMap(productPublicSkuApi.getLogisticsList(skuIds), skuIds);
        } catch (RuntimeException unavailable) {
            return failedQuotes(byShop.keySet(), TENANT_LOGISTICS_UNAVAILABLE);
        }
        LinkedHashMap<ShopKey, FreightQuote> result = new LinkedHashMap<>();
        for (Map.Entry<ShopKey, List<FreightItem>> entry : byShop.entrySet()) {
            ShopKey key = entry.getKey();
            try {
                // 只有本地运费模板查询在目标经营租户内执行；安全上下文由 TenantUtils 原样保留并恢复。
                result.put(key, TenantUtils.execute(key.tenantId(),
                        () -> quoteShop(areaId.intValue(), key, entry.getValue(), logisticsMap)));
            } catch (RuntimeException calculationFailure) {
                result.put(key, FreightQuote.failed(FREIGHT_CALCULATION_FAILED));
            }
        }
        return Map.copyOf(result);
    }

    private FreightQuote quoteShop(int areaId, ShopKey key, List<FreightItem> items,
                                   Map<Long, ProductPublicSkuLogisticsRespDTO> logisticsMap) {
        List<TradePriceCalculateRespBO.OrderItem> priceItems = new ArrayList<>(items.size());
        long expectedGoods = 0L;
        for (FreightItem item : items) {
            ProductPublicSkuLogisticsRespDTO logistics = logisticsMap.get(item.skuId());
            if (!validLogisticsSource(item, key, logistics)) {
                return FreightQuote.failed(PRODUCT_LOGISTICS_INCOMPLETE);
            }
            try {
                expectedGoods = Math.addExact(expectedGoods, item.goodsAmount());
            } catch (ArithmeticException overflow) {
                return FreightQuote.failed(PRODUCT_LOGISTICS_INCOMPLETE);
            }
            if (expectedGoods > Integer.MAX_VALUE) {
                return FreightQuote.failed(PRODUCT_LOGISTICS_INCOMPLETE);
            }
            priceItems.add(new TradePriceCalculateRespBO.OrderItem()
                    .setSpuId(item.spuId()).setShopId(item.shopId()).setSkuId(item.skuId())
                    .setCount(item.quantity()).setCartId(item.cartItemId()).setSelected(true)
                    .setPrice(item.unitPrice()).setDiscountPrice(0).setDeliveryPrice(0)
                    .setCouponPrice(0).setPointPrice(0).setUsePoint(0).setVipPrice(0)
                    .setGivePoint(0).setPayPrice(item.goodsAmount().intValue())
                    .setDeliveryTypes(logistics.getDeliveryTypes())
                    .setDeliveryTemplateId(logistics.getDeliveryTemplateId())
                    .setWeight(logistics.getWeight()).setVolume(logistics.getVolume()));
        }
        TradePriceCalculateRespBO price = new TradePriceCalculateRespBO()
                .setItems(priceItems).setPrice(new TradePriceCalculateRespBO.Price())
                .setFreeDelivery(false);
        TradePriceCalculatorHelper.recountAllPrice(price);
        if (!validBuiltPrice(price, items, expectedGoods)) {
            return FreightQuote.failed(PRODUCT_LOGISTICS_INCOMPLETE);
        }
        deliveryPriceCalculator.calculateExpressForArea(areaId, price);
        if (price.getPrice() == null || price.getPrice().getDeliveryPrice() == null
                || price.getPrice().getDeliveryPrice() < 0 || price.getPrice().getPayPrice() == null
                || price.getPrice().getPayPrice().longValue() != expectedGoods + price.getPrice().getDeliveryPrice()) {
            return FreightQuote.failed(FREIGHT_CALCULATION_FAILED);
        }
        return FreightQuote.ready(price.getPrice().getDeliveryPrice().longValue());
    }

    private static boolean validLogisticsSource(FreightItem item, ShopKey key,
                                                ProductPublicSkuLogisticsRespDTO logistics) {
        long expectedLine;
        try {
            expectedLine = Math.multiplyExact((long) item.unitPrice(), item.quantity());
        } catch (ArithmeticException overflow) {
            return false;
        }
        return item.valid() && Objects.equals(item.shopKey(), key)
                && logistics != null && Boolean.TRUE.equals(logistics.getSaleable())
                && logistics.getInvalidReason() == null
                && Objects.equals(logistics.getTenantId(), item.tenantId())
                && Objects.equals(logistics.getEnterpriseId(), item.enterpriseId())
                && Objects.equals(logistics.getShopId(), item.shopId())
                && Objects.equals(logistics.getSpuId(), item.spuId())
                && Objects.equals(logistics.getSkuId(), item.skuId())
                && Objects.equals(logistics.getPrice(), item.unitPrice())
                && logistics.getStock() != null && logistics.getStock() >= item.quantity()
                && logistics.getDeliveryTypes() != null
                && logistics.getDeliveryTypes().contains(DeliveryTypeEnum.EXPRESS.getType())
                && logistics.getDeliveryTemplateId() != null && logistics.getDeliveryTemplateId() > 0
                && finiteNonNegativeOrNull(logistics.getWeight())
                && finiteNonNegativeOrNull(logistics.getVolume())
                && expectedLine == item.goodsAmount();
    }

    private static boolean finiteNonNegativeOrNull(Double value) {
        return value == null || Double.isFinite(value) && value >= 0;
    }

    private static boolean validBuiltPrice(TradePriceCalculateRespBO price, List<FreightItem> source,
                                           long expectedGoods) {
        if (price == null || price.getPrice() == null || price.getItems() == null
                || price.getItems().size() != source.size()
                || price.getPrice().getTotalPrice() == null
                || price.getPrice().getTotalPrice().longValue() != expectedGoods) {
            return false;
        }
        Map<Long, FreightItem> sourceByCart = new HashMap<>();
        for (FreightItem item : source) {
            if (item.cartItemId() == null || sourceByCart.putIfAbsent(item.cartItemId(), item) != null) {
                return false;
            }
        }
        for (TradePriceCalculateRespBO.OrderItem built : price.getItems()) {
            FreightItem item = sourceByCart.get(built.getCartId());
            if (item == null || !Objects.equals(built.getSkuId(), item.skuId())
                    || !Objects.equals(built.getCount(), item.quantity())
                    || !Objects.equals(built.getPrice(), item.unitPrice())
                    || !Boolean.TRUE.equals(built.getSelected())) {
                return false;
            }
        }
        return true;
    }

    private static Map<Long, ProductPublicSkuLogisticsRespDTO> uniqueLogisticsMap(
            List<ProductPublicSkuLogisticsRespDTO> rows, Collection<Long> expectedIds) {
        if (rows == null) {
            throw new IllegalStateException("public SKU logistics unavailable");
        }
        Map<Long, ProductPublicSkuLogisticsRespDTO> result = new HashMap<>();
        for (ProductPublicSkuLogisticsRespDTO row : rows) {
            if (row == null || row.getSkuId() == null || !expectedIds.contains(row.getSkuId())
                    || result.putIfAbsent(row.getSkuId(), row) != null) {
                throw new IllegalStateException("public SKU logistics response invalid");
            }
        }
        return result;
    }

    private static LinkedHashMap<ShopKey, List<FreightItem>> groupByShop(Collection<FreightItem> items) {
        LinkedHashMap<ShopKey, List<FreightItem>> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (FreightItem item : items) {
            if (item == null || item.tenantId() == null || item.shopId() == null) {
                continue;
            }
            result.computeIfAbsent(item.shopKey(), ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private static Map<ShopKey, FreightQuote> failedQuotes(Collection<ShopKey> keys, String reason) {
        LinkedHashMap<ShopKey, FreightQuote> result = new LinkedHashMap<>();
        keys.forEach(key -> result.put(key, FreightQuote.failed(reason)));
        return Map.copyOf(result);
    }

    public record ShopKey(Long tenantId, Long shopId) {
    }

    public record FreightItem(Long tenantId, Long enterpriseId, Long shopId, Long cartItemId, Long spuId, Long skuId,
                              Integer quantity, Integer unitPrice, Long goodsAmount) {
        ShopKey shopKey() {
            return new ShopKey(tenantId, shopId);
        }

        boolean valid() {
            return tenantId != null && tenantId > 0 && enterpriseId != null && enterpriseId > 0
                    && shopId != null && shopId > 0
                    && cartItemId != null && cartItemId > 0 && spuId != null && spuId > 0
                    && skuId != null && skuId > 0 && quantity != null && quantity > 0
                    && unitPrice != null && unitPrice > 0 && goodsAmount != null && goodsAmount > 0;
        }
    }

    public record FreightQuote(boolean ready, Long freightAmount, String failureReason) {
        static FreightQuote ready(long freightAmount) {
            if (freightAmount < 0) {
                throw new IllegalArgumentException("freightAmount");
            }
            return new FreightQuote(true, freightAmount, null);
        }

        static FreightQuote failed(String reason) {
            return new FreightQuote(false, null, reason);
        }
    }

}
