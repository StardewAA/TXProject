package cn.iocoder.txgy.module.trade.service.comparison;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
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
import cn.iocoder.txgy.module.trade.controller.app.comparison.vo.AppProductComparisonQuoteRespVO.Item;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressTemplateService;
import cn.iocoder.txgy.module.trade.service.delivery.bo.DeliveryExpressTemplateRespBO;
import cn.iocoder.txgy.module.trade.service.checkout.PlatformCheckoutFreightService;
import cn.iocoder.txgy.module.trade.service.price.TradePriceService;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.springframework.stereotype.Service;
import java.util.*;

/** Read-only adapter around the existing calculator. It intentionally has no order/cart/ERP writer dependency. */
@Service
public class TradeProductComparisonQuoteService {
    private final ProductSkuApi skus;
    private final ProductSpuApi spus;
    private final ProductPublicSkuApi publicSkus;
    private final MemberAddressApi addresses;
    private final DeliveryExpressTemplateService templates;
    private final TradePriceService prices;
    private final PlatformCheckoutFreightService platformFreight;

    public TradeProductComparisonQuoteService(ProductSkuApi skus, ProductSpuApi spus, MemberAddressApi addresses,
                                              DeliveryExpressTemplateService templates, TradePriceService prices,
                                              ProductPublicSkuApi publicSkus,
                                              PlatformCheckoutFreightService platformFreight) {
        this.skus = skus; this.spus = spus; this.addresses = addresses; this.templates = templates; this.prices = prices;
        this.publicSkus = publicSkus; this.platformFreight = platformFreight;
    }

    public AppProductComparisonQuoteRespVO quote(Long memberId, AppProductComparisonQuoteReqVO input) {
        if (memberId == null || memberId <= 0) throw new ServiceException(401, "请登录后估算费用");
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || TenantContextHolder.isIgnore()) throw new ServiceException(403, "当前商城会话不可用");
        validate(input);
        MemberAddressRespDTO address = null;
        if (input.getAddressId() != null) {
            try {
                address = addresses.getAddress(input.getAddressId(), memberId).getCheckedData();
                if (address == null || !Objects.equals(address.getId(), input.getAddressId())
                        || !Objects.equals(address.getUserId(), memberId) || address.getAreaId() == null || address.getAreaId() <= 0) {
                    address = null;
                }
            } catch (RuntimeException unavailable) { address = null; }
        }
        List<Item> items = new ArrayList<>();
        for (Long skuId : input.getSkuIds()) {
            Item item = new Item().setSkuId(skuId).setQuantity(input.getQuantity());
            if (input.getAddressId() != null && address == null) {
                items.add(state(item, "ADDRESS_UNAVAILABLE", "所选收货地址不可用，请重新选择本人的地址"));
                continue;
            }
            items.add(quoteOne(memberId, skuId, input.getQuantity(), address, item));
        }
        return new AppProductComparisonQuoteRespVO().setQuantity(input.getQuantity()).setAddressId(input.getAddressId())
                .setCalculatedAt(System.currentTimeMillis()).setItems(items);
    }

    private Item quoteOne(Long memberId, Long skuId, int quantity, MemberAddressRespDTO address, Item item) {
        ProductSkuRespDTO sku;
        ProductSpuRespDTO spu;
        try {
            sku = skus.getSku(skuId);
            if (sku == null || !Objects.equals(sku.getId(), skuId) || sku.getSpuId() == null) {
                return quotePublic(skuId, quantity, address, item);
            }
            List<ProductSpuRespDTO> visible = spus.validateSpuList(List.of(sku.getSpuId())).getCheckedData();
            if (visible == null || visible.size() != 1 || !Objects.equals(visible.getFirst().getId(), sku.getSpuId())
                    || !Boolean.TRUE.equals(visible.getFirst().getSaleable())) {
                return quotePublic(skuId, quantity, address, item);
            }
            spu = visible.getFirst();
        } catch (RuntimeException unavailable) { return quotePublic(skuId, quantity, address, item); }
        if (sku.getStock() == null || sku.getStock() < quantity) return state(item, "INSUFFICIENT_STOCK", "当前库存不足，调整数量后重试");
        if (sku.getPrice() == null || sku.getPrice() <= 0 || (long) sku.getPrice() * quantity > Integer.MAX_VALUE) {
            return state(item, "PRICE_UNAVAILABLE", "当前金额无法安全估算，请调整数量或联系店铺");
        }
        long goods = (long) sku.getPrice() * quantity;
        item.setUnitPrice(sku.getPrice()).setGoodsAmount(goods);
        if (address == null) return state(item, "ADDRESS_REQUIRED", "请选择本人的收货地址后估算运费和到手金额");
        if (spu.getDeliveryTypes() == null || !spu.getDeliveryTypes().contains(1) || spu.getDeliveryTemplateId() == null) {
            return state(item, "SHIPPING_UNAVAILABLE", "配送资料不完整，暂不能确定运费");
        }
        try {
            Map<Long, DeliveryExpressTemplateRespBO> applicable = templates.getExpressTemplateMapByIdsAndArea(
                    List.of(spu.getDeliveryTemplateId()), address.getAreaId());
            DeliveryExpressTemplateRespBO template = applicable == null ? null : applicable.get(spu.getDeliveryTemplateId());
            if (!safeShipping(template, sku, quantity, goods)) return state(item, "SHIPPING_UNAVAILABLE", "配送资料不完整，暂不能确定运费");
        } catch (RuntimeException unavailable) { return state(item, "SHIPPING_UNAVAILABLE", "暂时无法核实运费，请稍后重试"); }
        try {
            TradePriceCalculateReqBO request = new TradePriceCalculateReqBO().setUserId(memberId).setPointStatus(false)
                    .setDeliveryType(1).setAddressId(address.getId()).setItems(List.of(new TradePriceCalculateReqBO.Item()
                            .setSkuId(skuId).setCount(quantity).setSelected(true)));
            TradePriceCalculateRespBO result = prices.calculateOrderPrice(request);
            if (!safePrice(result, skuId, quantity, sku.getPrice(), goods)) return state(item, "PRICE_UNAVAILABLE", "计价结果已变化或不完整，请重新估算");
            var price = result.getPrice();
            return item.setStatus("AVAILABLE").setMessage("本人当前地址的独立商品结算预估，下单时重新核价")
                    .setAmountsComplete(true).setDiscountAmount((long) price.getDiscountPrice() + price.getVipPrice())
                    .setShippingFee(price.getDeliveryPrice().longValue()).setTotalAmount(price.getPayPrice().longValue());
        } catch (RuntimeException unavailable) { return state(item, "PRICE_UNAVAILABLE", "当前暂不能完成计价，请稍后重试或进入结算页核实"); }
    }

    /** Cross-tenant portal products are resolved only from the controlled public SKU boundary. */
    private Item quotePublic(Long skuId, int quantity, MemberAddressRespDTO address, Item item) {
        ProductPublicSkuLogisticsRespDTO sku;
        try {
            List<ProductPublicSkuLogisticsRespDTO> rows = publicSkus.getLogisticsList(List.of(skuId));
            if (rows == null || rows.size() != 1 || !Objects.equals(rows.getFirst().getSkuId(), skuId)) {
                return state(item, "PRODUCT_UNAVAILABLE", "商品暂不可用，请重新选择");
            }
            sku = rows.getFirst();
        } catch (RuntimeException unavailable) {
            return state(item, "PRODUCT_UNAVAILABLE", "商品暂不可用，请重新选择");
        }
        if (!Boolean.TRUE.equals(sku.getSaleable()) || sku.getInvalidReason() != null
                || sku.getTenantId() == null || sku.getEnterpriseId() == null || sku.getShopId() == null
                || sku.getSpuId() == null || sku.getPrice() == null || sku.getPrice() <= 0) {
            return state(item, "PRODUCT_UNAVAILABLE", "商品暂不可用，请重新选择");
        }
        if (sku.getStock() == null || sku.getStock() < quantity) {
            return state(item, "INSUFFICIENT_STOCK", "当前库存不足，调整数量后重试");
        }
        long goods;
        try { goods = Math.multiplyExact((long) sku.getPrice(), quantity); }
        catch (ArithmeticException overflow) { return state(item, "PRICE_UNAVAILABLE", "当前金额无法安全估算，请调整数量或联系店铺"); }
        if (goods > Integer.MAX_VALUE) return state(item, "PRICE_UNAVAILABLE", "当前金额无法安全估算，请调整数量或联系店铺");
        item.setUnitPrice(sku.getPrice()).setGoodsAmount(goods);
        if (address == null) return state(item, "ADDRESS_REQUIRED", "商品金额已核验；请选择本人的收货地址后估算运费和到手金额");
        try {
            var key = new PlatformCheckoutFreightService.ShopKey(sku.getTenantId(), sku.getShopId());
            var freightItem = new PlatformCheckoutFreightService.FreightItem(sku.getTenantId(), sku.getEnterpriseId(),
                    sku.getShopId(), skuId, sku.getSpuId(), skuId, quantity, sku.getPrice(), goods);
            var quote = platformFreight.quote(address.getAreaId().longValue(), List.of(freightItem)).get(key);
            if (quote == null || !quote.ready() || quote.freightAmount() == null) {
                return state(item, "SHIPPING_UNAVAILABLE", quote == null || quote.failureReason() == null
                        ? "配送资料不完整，暂不能确定运费" : quote.failureReason());
            }
            long total = Math.addExact(goods, quote.freightAmount());
            if (total <= 0 || total > Integer.MAX_VALUE) return state(item, "PRICE_UNAVAILABLE", "当前金额无法安全估算，请调整数量或联系店铺");
            return item.setStatus("AVAILABLE").setMessage("跨租户公开商品费用预估，下单时重新核验价格、库存与运费")
                    .setAmountsComplete(true).setDiscountAmount(0L).setShippingFee(quote.freightAmount()).setTotalAmount(total);
        } catch (RuntimeException unavailable) {
            return state(item, "SHIPPING_UNAVAILABLE", "当前地址的运费暂不能核定，请稍后重试");
        }
    }

    /** The shared calculator can treat a missing charge/weight as zero. Never label that as a known complete quote. */
    private static boolean safeShipping(DeliveryExpressTemplateRespBO template, ProductSkuRespDTO sku, int quantity, long goods) {
        if (template == null || template.getChargeMode() == null || template.getCharge() == null) return false;
        double count;
        switch (template.getChargeMode()) {
            case 1 -> count = quantity;
            case 2 -> { if (!positive(sku.getWeight())) return false; count = sku.getWeight() * quantity; }
            case 3 -> { if (!positive(sku.getVolume())) return false; count = sku.getVolume() * quantity; }
            default -> { return false; }
        }
        var charge = template.getCharge();
        if (!Double.isFinite(count) || charge.getStartCount() == null || !Double.isFinite(charge.getStartCount())
                || charge.getStartCount() < 0 || !positive(charge.getExtraCount()) || !nonnegative(charge.getStartPrice())
                || !nonnegative(charge.getExtraPrice())) return false;
        var free = template.getFree();
        if (free != null && (!nonnegative(free.getFreeCount()) || !nonnegative(free.getFreePrice()))) return false;
        double extra = Math.max(0, Math.ceil((count - charge.getStartCount()) / charge.getExtraCount()));
        if (!Double.isFinite(extra) || extra > Integer.MAX_VALUE) return false;
        long maximumCharge = charge.getStartPrice().longValue() + (long) extra * charge.getExtraPrice();
        return maximumCharge <= Integer.MAX_VALUE - goods;
    }

    private static boolean safePrice(TradePriceCalculateRespBO value, Long skuId, int quantity, Integer unitPrice, long goods) {
        if (value == null || value.getItems() == null || value.getItems().size() != 1 || value.getPrice() == null) return false;
        var item = value.getItems().getFirst();
        if (item == null || !Objects.equals(item.getSkuId(), skuId) || !Objects.equals(item.getCount(), quantity)
                || !Objects.equals(item.getPrice(), unitPrice) || !Boolean.TRUE.equals(item.getSelected())) return false;
        var p = value.getPrice();
        if (!nonnegative(p.getTotalPrice()) || !nonnegative(p.getDiscountPrice()) || !nonnegative(p.getVipPrice())
                || !nonnegative(p.getDeliveryPrice()) || !nonnegative(p.getPayPrice())
                || !Objects.equals(p.getCouponPrice(), 0) || !Objects.equals(p.getPointPrice(), 0)
                || p.getTotalPrice().longValue() != goods) return false;
        long discount = (long) p.getDiscountPrice() + p.getVipPrice();
        long total = goods - discount + p.getDeliveryPrice();
        return discount <= goods && total > 0 && total <= Integer.MAX_VALUE && total == p.getPayPrice();
    }

    private static boolean positive(Double n) { return n != null && Double.isFinite(n) && n > 0; }
    private static boolean nonnegative(Integer n) { return n != null && n >= 0; }
    private static Item state(Item item, String status, String message) {
        return item.setStatus(status).setMessage(message).setAmountsComplete(false).setDiscountAmount(null).setShippingFee(null).setTotalAmount(null);
    }
    private static void validate(AppProductComparisonQuoteReqVO input) {
        if (input == null || input.getSkuIds() == null || input.getSkuIds().isEmpty() || input.getSkuIds().size() > 4
                || input.getSkuIds().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(input.getSkuIds()).size() != input.getSkuIds().size()
                || input.getQuantity() == null || input.getQuantity() < 1 || input.getQuantity() > 999
                || (input.getAddressId() != null && input.getAddressId() <= 0)) throw new ServiceException(400, "请选择一至四个不同商品，并填写一至九百九十九的采购数量");
    }
}
