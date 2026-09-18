package cn.iocoder.txgy.module.promotion.service.kefu.chat;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.trade.api.order.TradeOrderApi;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.Actor;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeService.*;

/** Server-owned immutable card snapshot. Caller must first lock and authorize the conversation. */
@Service
@RequiredArgsConstructor
public class KeFuChatCardService {
    public record Card(String type, long resourceId, long shopId, String title, String text,
                       Long spuId, Long orderId, String picUrl, Integer amount, Integer status) {}
    private final ProductSpuApi products;
    private final ProductShopApi shops;
    private final TradeOrderApi orders;

    public Card build(Session session, Actor buyer, String type, Long resourceId) {
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || TenantContextHolder.isIgnore() || session == null
                || session.tenantId() != tenant || buyer == null || buyer.type() != 1 || buyer.id() <= 0
                || buyer.id() != session.memberId()) throw exception(FORBIDDEN);
        if (resourceId == null || resourceId <= 0 || resourceId > 9007199254740991L
                || !("PRODUCT".equals(type) || "ORDER".equals(type))) throw exception(INVALID);
        if (!("SHOP".equals(session.scope()) && session.shopId() > 0
                || "PLATFORM".equals(session.scope()) && session.shopId() == 0)) throw exception(INVALID);
        if ("PRODUCT".equals(type)) {
            var list = products.validateSpuList(List.of(resourceId)).getCheckedData();
            if (list == null || list.size() != 1) throw exception(FORBIDDEN);
            var product = list.get(0);
            if (product == null || !Objects.equals(product.getId(), resourceId)
                    || !Boolean.TRUE.equals(product.getSaleable()) || !Integer.valueOf(1).equals(product.getStatus())) {
                throw exception(FORBIDDEN);
            }
            long shop = validateShop(session, product.getShopId(), true);
            return new Card(type, resourceId, shop, product.getName(), "商品信息快照，以商品详情实时信息为准",
                    resourceId, null, safeImage(product.getPicUrl()), nonNegative(product.getPrice()), product.getStatus());
        }
        var order = orders.getServiceCard(resourceId, buyer.id(), tenant).getCheckedData();
        if (order == null || !Objects.equals(order.getId(), resourceId) || !Objects.equals(order.getTenantId(), tenant)
                || !Objects.equals(order.getUserId(), buyer.id())) throw exception(FORBIDDEN);
        long shop = validateShop(session, order.getShopId(), false);
        return new Card(type, resourceId, shop, "订单 " + order.getNo(), "本人订单快照，不包含收货地址、手机号或支付凭据",
                null, resourceId, null, nonNegative(order.getPayPrice()), order.getStatus());
    }

    private long validateShop(Session session, Long shop, boolean requireActive) {
        if (shop == null || shop <= 0 || "SHOP".equals(session.scope()) && shop != session.shopId()) throw exception(FORBIDDEN);
        // A platform agent can help with the buyer's historical order even after its shop closes.
        if (requireActive) {
            var value = shops.getShop(shop).getCheckedData();
            if (value == null || !Objects.equals(value.getId(), shop) || !Integer.valueOf(0).equals(value.getStatus())) throw exception(FORBIDDEN);
        }
        return shop;
    }

    private Integer nonNegative(Integer amount) { return amount != null && amount >= 0 ? amount : null; }

    static String safeImage(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) return null;
        try {
            var uri = URI.create(value);
            if (uri.getUserInfo() != null || uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return null;
            return value;
        } catch (IllegalArgumentException ignored) { return null; }
    }
}
