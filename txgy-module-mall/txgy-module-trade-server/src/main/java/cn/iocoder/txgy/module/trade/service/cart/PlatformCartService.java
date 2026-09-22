package cn.iocoder.txgy.module.trade.service.cart;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.member.api.platform.MemberPlatformAccountApi;
import cn.iocoder.txgy.module.product.api.sku.ProductPublicSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartAddReqVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartListRespVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartUpdateCountReqVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartUpdateSelectedReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.cart.PlatformCartDO;
import cn.iocoder.txgy.module.trade.dal.mysql.cart.PlatformCartMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CART_ITEM_NOT_FOUND;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CART_SKU_UNAVAILABLE;

/** 平台级跨租户购物车服务。 */
@Service
@RequiredArgsConstructor
public class PlatformCartService {

    static final int MAX_QUANTITY = 99999;
    static final int PRODUCT_BATCH_SIZE = 200;

    private final PlatformCartMapper cartMapper;
    private final ProductPublicSkuApi publicSkuApi;
    private final MemberPlatformAccountApi platformAccountApi;

    @Transactional(rollbackFor = Exception.class)
    public Long add(Long legacyMemberId, Long sourceTenantId, AppPlatformCartAddReqVO request) {
        Long platformMemberId = resolvePlatformMemberId(legacyMemberId, sourceTenantId);
        ProductPublicSkuRespDTO sku = requireSaleable(request.getSkuId(), request.getQuantity());
        PlatformCartDO existing = cartMapper.selectByMemberAndSkuId(platformMemberId, request.getSkuId());
        if (existing != null) {
            incrementExisting(existing, platformMemberId, request.getQuantity(), sku);
            return existing.getId();
        }

        PlatformCartDO row = new PlatformCartDO()
                .setPlatformMemberId(platformMemberId)
                .setTenantId(sku.getTenantId()).setEnterpriseId(sku.getEnterpriseId())
                .setShopId(sku.getShopId()).setSpuId(sku.getSpuId()).setSkuId(sku.getSkuId())
                .setQuantity(request.getQuantity()).setSelected(true).setInvalidReason(null);
        try {
            cartMapper.insert(row);
            return row.getId();
        } catch (DuplicateKeyException concurrentInsert) {
            // 同一账号并发加入同一 SKU 时，唯一键负责收口；重新读取后按增量合并。
            PlatformCartDO concurrent = cartMapper.selectByMemberAndSkuId(platformMemberId, request.getSkuId());
            if (concurrent == null) {
                throw concurrentInsert;
            }
            incrementExisting(concurrent, platformMemberId, request.getQuantity(), sku);
            return concurrent.getId();
        }
    }

    public void updateCount(Long legacyMemberId, Long sourceTenantId, AppPlatformCartUpdateCountReqVO request) {
        Long platformMemberId = resolvePlatformMemberId(legacyMemberId, sourceTenantId);
        PlatformCartDO cart = requireOwned(request.getId(), platformMemberId);
        ProductPublicSkuRespDTO sku = requireSaleable(cart.getSkuId(), request.getQuantity());
        requireOwnership(cart, sku);
        if (cartMapper.updateQuantity(cart.getId(), platformMemberId, request.getQuantity()) != 1) {
            throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
        }
    }

    public void updateSelected(Long legacyMemberId, Long sourceTenantId,
                               AppPlatformCartUpdateSelectedReqVO request) {
        Long platformMemberId = resolvePlatformMemberId(legacyMemberId, sourceTenantId);
        LinkedHashSet<Long> ids = normalizeIds(request.getIds());
        List<PlatformCartDO> carts = requireOwned(ids, platformMemberId);
        if (Boolean.TRUE.equals(request.getSelected())) {
            Map<Long, ProductPublicSkuRespDTO> products = productMap(carts.stream()
                    .map(PlatformCartDO::getSkuId).toList());
            String firstInvalid = null;
            for (PlatformCartDO cart : carts) {
                String reason = invalidReason(cart, products.get(cart.getSkuId()));
                persistInvalidReason(cart, platformMemberId, reason);
                if (firstInvalid == null && reason != null) {
                    firstInvalid = reason;
                }
            }
            if (firstInvalid != null) {
                throw exception(PLATFORM_CART_SKU_UNAVAILABLE, firstInvalid);
            }
        }
        if (cartMapper.updateSelected(platformMemberId, ids, request.getSelected()) != ids.size()) {
            throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
        }
    }

    public void delete(Long legacyMemberId, Long sourceTenantId, Collection<Long> requestedIds) {
        Long platformMemberId = resolvePlatformMemberId(legacyMemberId, sourceTenantId);
        LinkedHashSet<Long> ids = normalizeIds(requestedIds);
        requireOwned(ids, platformMemberId);
        if (cartMapper.deleteByIdsAndMember(ids, platformMemberId) != ids.size()) {
            throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
        }
    }

    public AppPlatformCartListRespVO list(Long legacyMemberId, Long sourceTenantId) {
        Long platformMemberId = resolvePlatformMemberId(legacyMemberId, sourceTenantId);
        List<PlatformCartDO> carts = cartMapper.selectListByMember(platformMemberId);
        if (CollUtil.isEmpty(carts)) {
            return new AppPlatformCartListRespVO().setItemCount(0).setTotalQuantity(0)
                    .setSelectedQuantity(0).setTenantGroups(List.of());
        }
        Map<Long, ProductPublicSkuRespDTO> products = productMap(carts.stream()
                .map(PlatformCartDO::getSkuId).toList());

        Map<Long, TenantAccumulator> tenants = new LinkedHashMap<>();
        int totalQuantity = 0;
        int selectedQuantity = 0;
        for (PlatformCartDO cart : carts) {
            ProductPublicSkuRespDTO product = products.get(cart.getSkuId());
            String reason = invalidReason(cart, product);
            persistInvalidReason(cart, platformMemberId, reason);
            AppPlatformCartListRespVO.Item item = toItem(cart, product, reason);
            totalQuantity = Math.addExact(totalQuantity, cart.getQuantity());
            if (reason == null && Boolean.TRUE.equals(cart.getSelected())) {
                selectedQuantity = Math.addExact(selectedQuantity, cart.getQuantity());
            }

            TenantAccumulator tenant = tenants.computeIfAbsent(cart.getTenantId(), ignored ->
                    new TenantAccumulator(cart.getTenantId(), product == null ? null : product.getTenantName()));
            tenant.add(cart, product, item);
        }
        return new AppPlatformCartListRespVO()
                .setItemCount(carts.size()).setTotalQuantity(totalQuantity)
                .setSelectedQuantity(selectedQuantity)
                .setTenantGroups(tenants.values().stream().map(TenantAccumulator::response).toList());
    }

    private ProductPublicSkuRespDTO requireSaleable(Long skuId, int quantity) {
        ProductPublicSkuRespDTO sku = productMap(Set.of(skuId)).get(skuId);
        if (sku == null) {
            throw exception(PLATFORM_CART_SKU_UNAVAILABLE, "所属租户未开放或商品不存在");
        }
        if (!Boolean.TRUE.equals(sku.getSaleable())) {
            throw exception(PLATFORM_CART_SKU_UNAVAILABLE,
                    Objects.requireNonNullElse(sku.getInvalidReason(), "商品当前不可购买"));
        }
        validateResolvedOwnership(sku);
        validateQuantity(quantity, sku);
        return sku;
    }

    private void incrementExisting(PlatformCartDO cart, Long platformMemberId, int delta,
                                   ProductPublicSkuRespDTO sku) {
        requireOwnership(cart, sku);
        if (cart.getQuantity() == null || cart.getQuantity() < 1) {
            throw exception(PLATFORM_CART_SKU_UNAVAILABLE, "购物车商品数量无效");
        }
        int maxQuantity = Math.min(MAX_QUANTITY, sku.getStock());
        if (cart.getQuantity() > maxQuantity - delta
                || cartMapper.incrementQuantityAndSelect(cart.getId(), platformMemberId,
                delta, maxQuantity) != 1) {
            throw exception(PLATFORM_CART_SKU_UNAVAILABLE, "库存不足或商品数量已达上限");
        }
    }

    private void validateQuantity(int quantity, ProductPublicSkuRespDTO sku) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw exception(PLATFORM_CART_SKU_UNAVAILABLE, "商品数量无效");
        }
        if (sku.getStock() == null || sku.getStock() < quantity) {
            throw exception(PLATFORM_CART_SKU_UNAVAILABLE, "库存不足");
        }
    }

    private static void validateResolvedOwnership(ProductPublicSkuRespDTO sku) {
        if (sku.getTenantId() == null || sku.getTenantId() <= 0 || Objects.equals(sku.getTenantId(), 3000L)
                || sku.getEnterpriseId() == null || sku.getEnterpriseId() <= 0
                || sku.getShopId() == null || sku.getShopId() <= 0
                || sku.getSpuId() == null || sku.getSpuId() <= 0
                || sku.getSkuId() == null || sku.getSkuId() <= 0) {
            throw exception(PLATFORM_CART_SKU_UNAVAILABLE, "商品经营归属无效");
        }
    }

    private static void requireOwnership(PlatformCartDO cart, ProductPublicSkuRespDTO sku) {
        validateResolvedOwnership(sku);
        if (!Objects.equals(cart.getTenantId(), sku.getTenantId())
                || !Objects.equals(cart.getEnterpriseId(), sku.getEnterpriseId())
                || !Objects.equals(cart.getShopId(), sku.getShopId())
                || !Objects.equals(cart.getSpuId(), sku.getSpuId())
                || !Objects.equals(cart.getSkuId(), sku.getSkuId())) {
            throw exception(PLATFORM_CART_SKU_UNAVAILABLE, "商品经营归属已变更");
        }
    }

    private static String invalidReason(PlatformCartDO cart, ProductPublicSkuRespDTO sku) {
        if (sku == null) {
            return "所属租户未开放或商品不存在";
        }
        try {
            requireOwnership(cart, sku);
        } catch (ServiceException ignored) {
            return "商品经营归属已变更";
        }
        if (!Boolean.TRUE.equals(sku.getSaleable())) {
            return Objects.requireNonNullElse(sku.getInvalidReason(), "商品当前不可购买");
        }
        if (cart.getQuantity() == null || cart.getQuantity() < 1 || cart.getQuantity() > MAX_QUANTITY) {
            return "商品数量无效";
        }
        if (sku.getStock() == null || sku.getStock() < cart.getQuantity()) {
            return "库存不足";
        }
        return null;
    }

    private void persistInvalidReason(PlatformCartDO cart, Long platformMemberId, String reason) {
        if (!Objects.equals(cart.getInvalidReason(), reason)) {
            cartMapper.updateInvalidReason(cart.getId(), platformMemberId, reason);
            cart.setInvalidReason(reason);
        }
    }

    private Map<Long, ProductPublicSkuRespDTO> productMap(Collection<Long> skuIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>(skuIds);
        Map<Long, ProductPublicSkuRespDTO> result = new LinkedHashMap<>();
        List<Long> ordered = new ArrayList<>(ids);
        for (int from = 0; from < ordered.size(); from += PRODUCT_BATCH_SIZE) {
            int to = Math.min(from + PRODUCT_BATCH_SIZE, ordered.size());
            List<ProductPublicSkuRespDTO> batch = publicSkuApi.getOwnershipList(ordered.subList(from, to));
            if (batch == null) {
                throw new IllegalStateException("商品公开归属服务返回空结果");
            }
            for (ProductPublicSkuRespDTO sku : batch) {
                if (sku == null || sku.getSkuId() == null || !ids.contains(sku.getSkuId())
                        || result.putIfAbsent(sku.getSkuId(), sku) != null) {
                    throw new IllegalStateException("商品公开归属服务返回非法或重复 SKU");
                }
            }
        }
        return result;
    }

    private PlatformCartDO requireOwned(Long id, Long platformMemberId) {
        PlatformCartDO cart = cartMapper.selectByIdAndMember(id, platformMemberId);
        if (cart == null) {
            throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
        }
        return cart;
    }

    private List<PlatformCartDO> requireOwned(Collection<Long> ids, Long platformMemberId) {
        List<PlatformCartDO> carts = cartMapper.selectListByIdsAndMember(ids, platformMemberId);
        if (carts.size() != ids.size()) {
            throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
        }
        return carts;
    }

    private static LinkedHashSet<Long> normalizeIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
            }
            normalized.add(id);
        }
        return normalized;
    }

    private Long resolvePlatformMemberId(Long legacyMemberId, Long sourceTenantId) {
        if (legacyMemberId == null || legacyMemberId <= 0 || sourceTenantId == null || sourceTenantId <= 0) {
            throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
        }
        var result = platformAccountApi.resolve(legacyMemberId, sourceTenantId);
        if (result == null) {
            throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
        }
        var account = result.getCheckedData();
        if (account == null || account.getPlatformAccountId() == null || account.getPlatformAccountId() <= 0
                || !Objects.equals(account.getLegacyMemberId(), legacyMemberId)
                || !Objects.equals(account.getSourceTenantId(), sourceTenantId)) {
            throw exception(PLATFORM_CART_ITEM_NOT_FOUND);
        }
        return account.getPlatformAccountId();
    }

    private static AppPlatformCartListRespVO.Item toItem(PlatformCartDO cart,
                                                          ProductPublicSkuRespDTO product,
                                                          String reason) {
        boolean publiclyDisplayable = reason == null && product != null
                && Boolean.TRUE.equals(product.getSaleable());
        return new AppPlatformCartListRespVO.Item()
                .setId(cart.getId()).setTenantId(cart.getTenantId())
                .setEnterpriseId(cart.getEnterpriseId()).setShopId(cart.getShopId())
                .setSpuId(cart.getSpuId()).setSkuId(cart.getSkuId())
                .setSpuName(publiclyDisplayable ? product.getSpuName() : null)
                .setProperties(!publiclyDisplayable || product.getProperties() == null
                        ? List.of() : List.copyOf(product.getProperties()))
                .setPicUrl(publiclyDisplayable ? product.getPicUrl() : null)
                .setPrice(publiclyDisplayable ? product.getPrice() : null)
                .setQuantity(cart.getQuantity()).setSelected(cart.getSelected())
                .setValid(reason == null).setInvalidReason(reason);
    }

    private static final class TenantAccumulator {
        private final Long tenantId;
        private String tenantName;
        private final Map<Long, ShopAccumulator> shops = new LinkedHashMap<>();

        private TenantAccumulator(Long tenantId, String tenantName) {
            this.tenantId = tenantId;
            this.tenantName = tenantName;
        }

        private void add(PlatformCartDO cart, ProductPublicSkuRespDTO product,
                         AppPlatformCartListRespVO.Item item) {
            if (tenantName == null && product != null) {
                tenantName = product.getTenantName();
            }
            shops.computeIfAbsent(cart.getShopId(), ignored -> new ShopAccumulator(cart, product))
                    .items.add(item);
        }

        private AppPlatformCartListRespVO.TenantGroup response() {
            return new AppPlatformCartListRespVO.TenantGroup().setTenantId(tenantId)
                    .setTenantName(tenantName == null ? "租户 " + tenantId : tenantName)
                    .setShops(shops.values().stream().map(ShopAccumulator::response).toList());
        }
    }

    private static final class ShopAccumulator {
        private final Long enterpriseId;
        private final String enterpriseName;
        private final Long shopId;
        private final String shopName;
        private final List<AppPlatformCartListRespVO.Item> items = new ArrayList<>();

        private ShopAccumulator(PlatformCartDO cart, ProductPublicSkuRespDTO product) {
            enterpriseId = cart.getEnterpriseId();
            enterpriseName = product == null || product.getEnterpriseName() == null
                    ? "企业 " + cart.getEnterpriseId() : product.getEnterpriseName();
            shopId = cart.getShopId();
            shopName = product == null || product.getShopName() == null
                    ? "店铺 " + cart.getShopId() : product.getShopName();
        }

        private AppPlatformCartListRespVO.ShopGroup response() {
            return new AppPlatformCartListRespVO.ShopGroup()
                    .setEnterpriseId(enterpriseId).setEnterpriseName(enterpriseName)
                    .setShopId(shopId).setShopName(shopName).setItems(List.copyOf(items));
        }
    }

}
