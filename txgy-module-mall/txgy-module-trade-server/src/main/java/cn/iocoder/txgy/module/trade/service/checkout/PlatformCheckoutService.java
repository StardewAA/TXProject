package cn.iocoder.txgy.module.trade.service.checkout;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.member.api.platform.MemberPlatformAccountApi;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAddressRespDTO;
import cn.iocoder.txgy.module.product.api.sku.ProductPublicSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuRespDTO;
import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutDetailRespVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutAddressRespVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutPreviewReqVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutPreviewRespVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutPageItemRespVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutSubmitReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.cart.PlatformCartDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutPlanDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutPlanItemDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformFundAllocationDO;
import cn.iocoder.txgy.module.trade.dal.mysql.cart.PlatformCartMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutOrderMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutPlanMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutPlanItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformFundAllocationMapper;
import cn.iocoder.txgy.module.trade.dal.redis.no.TradeNoRedisDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_ACCOUNT_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_ADDRESS_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_CART_EMPTY;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_CART_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_DATA_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_NOT_FOUND;

/**
 * 平台跨租户结算服务。
 *
 * <p>服务端重读商品归属，按租户和店铺核算运费并保存不可变快照；提交后由
 * {@link PlatformCheckoutOrderService} 生成正式子订单和统一支付单。客户端传入的租户、店铺和金额
 * 均不作为交易依据。</p>
 */
@Service
@RequiredArgsConstructor
public class PlatformCheckoutService {

    static final long FORMER_PUBLIC_TENANT_ID = 3000L;
    static final int PRODUCT_BATCH_SIZE = 200;
    static final String CURRENCY_CNY = "CNY";
    static final String DRAFT_AGGREGATE_STATUS = "DRAFT_ONLY";
    static final String DRAFT_PAY_STATUS = "NOT_PAYABLE";
    static final String DRAFT_PLAN_STATUS = "DRAFT_ONLY";
    static final String DRAFT_BLOCK_REASON =
            "等待生成正式店铺订单";

    private final PlatformCartMapper cartMapper;
    private final ProductPublicSkuApi publicSkuApi;
    private final MemberPlatformAccountApi platformAccountApi;
    private final PlatformCheckoutMapper checkoutMapper;
    private final PlatformCheckoutOrderMapper checkoutOrderMapper;
    private final PlatformFundAllocationMapper allocationMapper;
    private final PlatformCheckoutPlanMapper planMapper;
    private final PlatformCheckoutPlanItemMapper planItemMapper;
    private final TradeNoRedisDAO tradeNoRedisDAO;
    private final PlatformCheckoutFreightService freightService;
    private final PlatformCheckoutOrderService orderService;

    @Transactional(readOnly = true)
    public AppPlatformCheckoutPreviewRespVO preview(Long legacyMemberId, Long sourceTenantId,
                                                     AppPlatformCheckoutPreviewReqVO request) {
        ResolvedCheckoutContext context = resolveCheckoutContext(
                legacyMemberId, sourceTenantId, request.getAddressId());
        return buildPreview(context.platformAccountId(), request.getCartItemIds(), context.address());
    }

    /** 幂等保存拆单快照，并生成正式店铺子订单和统一支付单。 */
    @Transactional(rollbackFor = Exception.class)
    public AppPlatformCheckoutDetailRespVO submit(Long legacyMemberId, Long sourceTenantId,
                                                   AppPlatformCheckoutSubmitReqVO request) {
        List<Long> cartItemIds = normalizeSubmitCartItemIds(request.getCartItemIds());
        ResolvedCheckoutContext context = resolveCheckoutContext(
                legacyMemberId, sourceTenantId, request.getAddressId());
        Long platformAccountId = context.platformAccountId();
        String requestDigest = requestDigest(platformAccountId, cartItemIds, context.address(), request.getRemark());
        PlatformCheckoutDO existing = checkoutMapper.selectByIdempotencyKey(
                platformAccountId, request.getIdempotencyKey());
        if (existing != null) {
            if (existing.getRequestDigest() == null
                    || !MessageDigest.isEqual(existing.getRequestDigest().getBytes(StandardCharsets.US_ASCII),
                    requestDigest.getBytes(StandardCharsets.US_ASCII))) {
                throw exception(PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT);
            }
            return loadDetail(orderService.formalize(existing, legacyMemberId, sourceTenantId));
        }

        AppPlatformCheckoutPreviewRespVO preview = buildPreview(platformAccountId, cartItemIds, context.address());
        if (CollUtil.isNotEmpty(preview.getFailedItems())) {
            AppPlatformCheckoutPreviewRespVO.FailedItem first = preview.getFailedItems().getFirst();
            throw exception(PLATFORM_CHECKOUT_CART_INVALID,
                    "购物车项 " + first.getCartItemId() + "：" + first.getReason());
        }
        if (!Boolean.TRUE.equals(preview.getFreightReady()) || preview.getFreightAmount() == null
                || preview.getPayAmount() == null) {
            throw exception(PLATFORM_CHECKOUT_CART_INVALID, "当前地址的分店运费尚未全部核定");
        }
        PlatformCheckoutDO candidate = buildDraftCheckout(platformAccountId, request, requestDigest,
                context.address(), preview);
        checkoutMapper.insertDraftOrGetId(candidate);
        if (candidate.getId() == null || candidate.getId() <= 0) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "未取得平台结算草稿编号");
        }

        PlatformCheckoutDO claimed = checkoutMapper.selectByIdempotencyKey(
                platformAccountId, request.getIdempotencyKey());
        validateIdempotentClaim(claimed, platformAccountId, request.getIdempotencyKey(), requestDigest);
        // 并发请求输掉幂等键竞争后，只能读取赢家已完整提交的快照。
        if (!Objects.equals(candidate.getCheckoutNo(), claimed.getCheckoutNo())) {
            return loadDetail(claimed);
        }

        persistPlanSnapshot(claimed, preview);
        return loadDetail(orderService.formalize(claimed, legacyMemberId, sourceTenantId));
    }

    @Transactional(readOnly = true)
    public AppPlatformCheckoutDetailRespVO get(Long legacyMemberId, Long sourceTenantId, String checkoutNo) {
        Long platformAccountId = resolvePlatformAccountId(legacyMemberId, sourceTenantId);
        if (checkoutNo == null || checkoutNo.isBlank() || checkoutNo.length() > 40
                || !checkoutNo.matches("[A-Za-z0-9._:-]+")) {
            throw exception(PLATFORM_CHECKOUT_NOT_FOUND);
        }
        PlatformCheckoutDO checkout = checkoutMapper.selectByNoAndMember(checkoutNo, platformAccountId);
        if (checkout == null) {
            throw exception(PLATFORM_CHECKOUT_NOT_FOUND);
        }
        return loadDetail(checkout);
    }

    /**
     * 按平台账号查询结算历史。分页项刻意不返回地址、手机号、备注和幂等键；
     * 详情仍需通过带账号归属校验的 {@link #get(Long, Long, String)} 读取。
     */
    @Transactional(readOnly = true)
    public PageResult<AppPlatformCheckoutPageItemRespVO> page(Long legacyMemberId, Long sourceTenantId,
                                                               PageParam pageParam) {
        Long platformAccountId = resolvePlatformAccountId(legacyMemberId, sourceTenantId);
        PageResult<PlatformCheckoutDO> page = checkoutMapper.selectPageByMember(pageParam, platformAccountId);
        return new PageResult<>(page.getList().stream().map(PlatformCheckoutService::toPageItem).toList(),
                page.getTotal());
    }

    private AppPlatformCheckoutPreviewRespVO buildPreview(Long platformAccountId,
                                                           Collection<Long> requestedCartItemIds,
                                                           MemberPlatformAddressRespDTO address) {
        List<PlatformCartDO> carts = loadCarts(platformAccountId, requestedCartItemIds);
        Map<Long, ProductPublicSkuRespDTO> products = productMap(carts.stream()
                .map(PlatformCartDO::getSkuId).filter(Objects::nonNull).toList());

        Map<Long, TenantAccumulator> tenantGroups = new LinkedHashMap<>();
        List<AppPlatformCheckoutPreviewRespVO.FailedItem> failedItems = new ArrayList<>();
        List<PlatformCheckoutFreightService.FreightItem> freightItems = new ArrayList<>();
        long goodsAmount = 0L;
        int totalQuantity = 0;
        int validItemCount = 0;
        for (PlatformCartDO cart : carts) {
            ProductPublicSkuRespDTO product = products.get(cart.getSkuId());
            String invalidReason = invalidReason(cart, product);
            if (invalidReason != null) {
                failedItems.add(new AppPlatformCheckoutPreviewRespVO.FailedItem()
                        .setCartItemId(cart.getId()).setSkuId(cart.getSkuId()).setReason(invalidReason));
                continue;
            }
            long lineAmount;
            try {
                lineAmount = Math.multiplyExact((long) product.getPrice(), cart.getQuantity());
                goodsAmount = Math.addExact(goodsAmount, lineAmount);
                totalQuantity = Math.addExact(totalQuantity, cart.getQuantity());
            } catch (ArithmeticException overflow) {
                throw exception(PLATFORM_CHECKOUT_CART_INVALID, "商品金额或数量超出结算范围");
            }
            validItemCount++;
            freightItems.add(new PlatformCheckoutFreightService.FreightItem(product.getTenantId(),
                    product.getEnterpriseId(), product.getShopId(), cart.getId(), product.getSpuId(), product.getSkuId(),
                    cart.getQuantity(), product.getPrice(), lineAmount));
            TenantAccumulator tenant = tenantGroups.computeIfAbsent(product.getTenantId(), ignored ->
                    new TenantAccumulator(product.getTenantId(), product.getTenantName()));
            tenant.add(cart, product, lineAmount);
        }

        List<String> blockingReasons = new ArrayList<>();
        if (CollUtil.isNotEmpty(failedItems)) {
            blockingReasons.add("存在失效、库存不足、未选中或经营归属变更的购物车项");
        }
        Map<PlatformCheckoutFreightService.ShopKey, PlatformCheckoutFreightService.FreightQuote> freightQuotes =
                freightService.quote(address.getAreaId(), freightItems);
        tenantGroups.values().forEach(tenant -> tenant.applyFreight(freightQuotes));
        boolean freightReady = !tenantGroups.isEmpty()
                && tenantGroups.values().stream().allMatch(TenantAccumulator::freightReady);
        Long freightAmount = null;
        Long payAmount = null;
        if (freightReady) {
            try {
                freightAmount = tenantGroups.values().stream().mapToLong(TenantAccumulator::freightAmount)
                        .reduce(0L, Math::addExact);
                payAmount = Math.addExact(goodsAmount, freightAmount);
            } catch (ArithmeticException overflow) {
                throw exception(PLATFORM_CHECKOUT_CART_INVALID, "商品和运费汇总金额超出结算范围");
            }
        } else {
            blockingReasons.add("部分租户或店铺运费未能由服务端核定，请按明细处理");
        }
        return new AppPlatformCheckoutPreviewRespVO()
                .setPlatformAccountId(platformAccountId).setCurrency(CURRENCY_CNY)
                .setValidItemCount(validItemCount).setTotalQuantity(totalQuantity)
                .setGoodsAmount(goodsAmount).setFreightAmount(freightAmount).setDiscountAmount(0L)
                .setPayAmount(payAmount).setFreightReady(freightReady)
                .setValidationPassed(failedItems.isEmpty() && freightReady)
                .setAddress(toAddress(address))
                .setSubmittable(failedItems.isEmpty() && freightReady).setBlockingReasons(List.copyOf(blockingReasons))
                .setTenantGroups(tenantGroups.values().stream().map(TenantAccumulator::toResponse).toList())
                .setFailedItems(List.copyOf(failedItems));
    }

    private List<PlatformCartDO> loadCarts(Long platformAccountId, Collection<Long> requestedIds) {
        List<PlatformCartDO> carts;
        if (CollUtil.isEmpty(requestedIds)) {
            carts = cartMapper.selectListByMember(platformAccountId).stream()
                    .filter(row -> Boolean.TRUE.equals(row.getSelected())).toList();
        } else {
            LinkedHashSet<Long> ids = new LinkedHashSet<>(requestedIds);
            if (ids.size() != requestedIds.size() || ids.stream().anyMatch(id -> id == null || id <= 0)) {
                throw exception(PLATFORM_CHECKOUT_CART_INVALID, "购物车项编号重复或无效");
            }
            carts = cartMapper.selectListByIdsAndMember(ids, platformAccountId);
            if (carts.size() != ids.size()) {
                throw exception(PLATFORM_CHECKOUT_CART_INVALID, "部分购物车项不存在或不属于当前平台账号");
            }
        }
        if (CollUtil.isEmpty(carts)) {
            throw exception(PLATFORM_CHECKOUT_CART_EMPTY);
        }
        return carts.stream().sorted(Comparator.comparing(PlatformCartDO::getTenantId,
                        Comparator.nullsLast(Long::compareTo))
                .thenComparing(PlatformCartDO::getShopId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(PlatformCartDO::getId, Comparator.nullsLast(Long::compareTo))).toList();
    }

    private Map<Long, ProductPublicSkuRespDTO> productMap(Collection<Long> skuIds) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(skuIds);
        Map<Long, ProductPublicSkuRespDTO> result = new HashMap<>();
        List<Long> ordered = new ArrayList<>(uniqueIds);
        for (int from = 0; from < ordered.size(); from += PRODUCT_BATCH_SIZE) {
            int to = Math.min(from + PRODUCT_BATCH_SIZE, ordered.size());
            List<ProductPublicSkuRespDTO> batch = publicSkuApi.getOwnershipList(ordered.subList(from, to));
            if (batch == null) {
                throw exception(PLATFORM_CHECKOUT_CART_INVALID, "商品公开归属服务暂不可用");
            }
            for (ProductPublicSkuRespDTO product : batch) {
                if (product == null || product.getSkuId() == null || !uniqueIds.contains(product.getSkuId())
                        || result.putIfAbsent(product.getSkuId(), product) != null) {
                    throw exception(PLATFORM_CHECKOUT_CART_INVALID, "商品公开归属服务返回了非法结果");
                }
            }
        }
        return result;
    }

    private static String invalidReason(PlatformCartDO cart, ProductPublicSkuRespDTO product) {
        if (!Boolean.TRUE.equals(cart.getSelected())) {
            return "购物车项未选中";
        }
        if (cart.getTenantId() == null || cart.getTenantId() <= 0
                || Objects.equals(cart.getTenantId(), FORMER_PUBLIC_TENANT_ID)) {
            return "商品所属租户未开放";
        }
        if (product == null) {
            return "所属租户未开放或商品不存在";
        }
        if (product.getTenantId() == null || product.getTenantId() <= 0
                || Objects.equals(product.getTenantId(), FORMER_PUBLIC_TENANT_ID)
                || product.getEnterpriseId() == null || product.getEnterpriseId() <= 0
                || product.getShopId() == null || product.getShopId() <= 0
                || product.getSpuId() == null || product.getSpuId() <= 0
                || product.getSkuId() == null || product.getSkuId() <= 0) {
            return "商品经营归属无效";
        }
        if (!Objects.equals(cart.getTenantId(), product.getTenantId())
                || !Objects.equals(cart.getEnterpriseId(), product.getEnterpriseId())
                || !Objects.equals(cart.getShopId(), product.getShopId())
                || !Objects.equals(cart.getSpuId(), product.getSpuId())
                || !Objects.equals(cart.getSkuId(), product.getSkuId())) {
            return "商品经营归属已变更";
        }
        if (!Boolean.TRUE.equals(product.getSaleable())) {
            return product.getInvalidReason() == null || product.getInvalidReason().isBlank()
                    ? "商品当前不可购买" : product.getInvalidReason();
        }
        if (cart.getQuantity() == null || cart.getQuantity() <= 0) {
            return "商品数量无效";
        }
        if (product.getStock() == null || product.getStock() < cart.getQuantity()) {
            return "库存不足";
        }
        if (product.getPrice() == null || product.getPrice() <= 0) {
            return "商品价格无效";
        }
        return null;
    }

    private Long resolvePlatformAccountId(Long legacyMemberId, Long sourceTenantId) {
        if (legacyMemberId == null || legacyMemberId <= 0 || sourceTenantId == null || sourceTenantId <= 0) {
            throw exception(PLATFORM_CHECKOUT_ACCOUNT_INVALID);
        }
        var result = platformAccountApi.resolve(legacyMemberId, sourceTenantId);
        MemberPlatformAccountRespDTO account = result == null ? null : result.getCheckedData();
        if (account == null || account.getPlatformAccountId() == null || account.getPlatformAccountId() <= 0
                || !Objects.equals(account.getLegacyMemberId(), legacyMemberId)
                || !Objects.equals(account.getSourceTenantId(), sourceTenantId)) {
            throw exception(PLATFORM_CHECKOUT_ACCOUNT_INVALID);
        }
        return account.getPlatformAccountId();
    }

    private ResolvedCheckoutContext resolveCheckoutContext(Long legacyMemberId, Long sourceTenantId,
                                                            Long addressId) {
        Long platformAccountId = resolvePlatformAccountId(legacyMemberId, sourceTenantId);
        if (addressId == null || addressId <= 0) {
            throw exception(PLATFORM_CHECKOUT_ADDRESS_INVALID);
        }
        var result = platformAccountApi.resolveAddress(legacyMemberId, sourceTenantId, addressId);
        MemberPlatformAddressRespDTO address = result == null ? null : result.getCheckedData();
        validateResolvedAddress(address, platformAccountId, addressId);
        return new ResolvedCheckoutContext(platformAccountId, address);
    }

    private static void validateResolvedAddress(MemberPlatformAddressRespDTO address, Long platformAccountId,
                                                Long addressId) {
        if (address == null || !Objects.equals(address.getPlatformAccountId(), platformAccountId)
                || !Objects.equals(address.getAddressId(), addressId)
                || address.getLegacyMemberId() == null || address.getLegacyMemberId() <= 0
                || address.getSourceTenantId() == null || address.getSourceTenantId() <= 0
                || Objects.equals(address.getSourceTenantId(), FORMER_PUBLIC_TENANT_ID)
                || address.getName() == null || address.getName().isBlank()
                || address.getMobile() == null || address.getMobile().isBlank()
                || address.getAreaId() == null || address.getAreaId() <= 0
                || address.getDetailAddress() == null || address.getDetailAddress().isBlank()) {
            throw exception(PLATFORM_CHECKOUT_ADDRESS_INVALID);
        }
        if ((address.getLongitude() == null) != (address.getLatitude() == null)
                || !coordinateInRange(address.getLongitude(), "-180", "180")
                || !coordinateInRange(address.getLatitude(), "-90", "90")) {
            throw exception(PLATFORM_CHECKOUT_ADDRESS_INVALID);
        }
    }

    private static boolean coordinateInRange(BigDecimal coordinate, String minimum, String maximum) {
        return coordinate == null || coordinate.compareTo(new BigDecimal(minimum)) >= 0
                && coordinate.compareTo(new BigDecimal(maximum)) <= 0;
    }

    private static List<Long> normalizeSubmitCartItemIds(Collection<Long> requestedIds) {
        if (CollUtil.isEmpty(requestedIds) || requestedIds.size() > 200
                || requestedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw exception(PLATFORM_CHECKOUT_CART_EMPTY);
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw exception(PLATFORM_CHECKOUT_CART_INVALID, "购物车项编号重复");
        }
        return uniqueIds.stream().sorted().toList();
    }

    static String requestDigest(Long platformAccountId, Collection<Long> cartItemIds,
                                MemberPlatformAddressRespDTO address, String remark) {
        StringBuilder canonical = new StringBuilder("platform-checkout-v1");
        appendDigestField(canonical, "platformAccountId", platformAccountId);
        appendDigestField(canonical, "addressId", address.getAddressId());
        appendDigestField(canonical, "addressSourceTenantId", address.getSourceTenantId());
        appendDigestField(canonical, "receiverName", address.getName());
        appendDigestField(canonical, "receiverMobile", address.getMobile());
        appendDigestField(canonical, "receiverAreaId", address.getAreaId());
        appendDigestField(canonical, "receiverDetailAddress", address.getDetailAddress());
        appendDigestField(canonical, "receiverLongitude", decimalText(address.getLongitude()));
        appendDigestField(canonical, "receiverLatitude", decimalText(address.getLatitude()));
        appendDigestField(canonical, "receiverCoordSystem", address.getCoordSystem());
        appendDigestField(canonical, "cartItemIds", cartItemIds.stream().sorted()
                .map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(""));
        appendDigestField(canonical, "remark", remark == null ? "" : remark.strip());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(value & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static void appendDigestField(StringBuilder canonical, String name, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        canonical.append('|').append(name).append(':').append(text.getBytes(StandardCharsets.UTF_8).length)
                .append(':').append(text);
    }

    private static String decimalText(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private PlatformCheckoutDO buildDraftCheckout(Long platformAccountId,
                                                   AppPlatformCheckoutSubmitReqVO request,
                                                   String requestDigest,
                                                   MemberPlatformAddressRespDTO address,
                                                   AppPlatformCheckoutPreviewRespVO preview) {
        String checkoutNo = tradeNoRedisDAO.generate(TradeNoRedisDAO.PLATFORM_CHECKOUT_NO_PREFIX);
        if (checkoutNo == null || checkoutNo.isBlank() || checkoutNo.length() > 40
                || !checkoutNo.matches("[A-Za-z0-9._:-]+")) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "平台结算编号生成失败");
        }
        String operator = String.valueOf(platformAccountId);
        PlatformCheckoutDO checkout = new PlatformCheckoutDO()
                .setCheckoutNo(checkoutNo).setIdempotencyKey(request.getIdempotencyKey())
                .setRequestDigest(requestDigest).setPlatformMemberId(platformAccountId)
                .setAddressId(address.getAddressId()).setAddressSourceTenantId(address.getSourceTenantId())
                .setReceiverName(address.getName()).setReceiverMobile(address.getMobile())
                .setReceiverAreaId(address.getAreaId()).setReceiverDetailAddress(address.getDetailAddress())
                .setReceiverLongitude(address.getLongitude()).setReceiverLatitude(address.getLatitude())
                .setReceiverCoordSystem(address.getCoordSystem())
                .setRemark(request.getRemark() == null ? null : request.getRemark().strip())
                .setCurrency(CURRENCY_CNY).setGoodsAmount(preview.getGoodsAmount())
                .setFreightAmount(preview.getFreightAmount()).setDiscountAmount(0L)
                .setPayAmount(preview.getPayAmount())
                .setPaidAmount(0L).setRefundedAmount(0L).setPayStatus(DRAFT_PAY_STATUS)
                .setAggregateStatus(DRAFT_AGGREGATE_STATUS).setFailureReason(DRAFT_BLOCK_REASON)
                .setVersion(0L);
        checkout.setCreator(operator).setUpdater(operator).setCreateTime(LocalDateTime.now());
        return checkout;
    }

    private static void validateIdempotentClaim(PlatformCheckoutDO claimed, Long platformAccountId,
                                                String idempotencyKey, String requestDigest) {
        if (claimed == null || claimed.getId() == null || claimed.getId() <= 0
                || !Objects.equals(claimed.getPlatformMemberId(), platformAccountId)
                || !Objects.equals(claimed.getIdempotencyKey(), idempotencyKey)) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "平台结算幂等占用结果异常");
        }
        if (claimed.getRequestDigest() == null
                || !MessageDigest.isEqual(claimed.getRequestDigest().getBytes(StandardCharsets.US_ASCII),
                requestDigest.getBytes(StandardCharsets.US_ASCII))) {
            throw exception(PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT);
        }
    }

    private PlanSnapshot persistPlanSnapshot(PlatformCheckoutDO checkout,
                                             AppPlatformCheckoutPreviewRespVO preview) {
        List<PlatformCheckoutPlanDO> plans = new ArrayList<>();
        List<PlatformCheckoutPlanItemDO> items = new ArrayList<>();
        int sequence = 0;
        for (AppPlatformCheckoutPreviewRespVO.TenantGroup tenant : preview.getTenantGroups()) {
            for (AppPlatformCheckoutPreviewRespVO.ShopGroup shop : tenant.getShops()) {
                String planNo = checkout.getCheckoutNo() + "-" + String.format("%03d", ++sequence);
                int totalQuantity = shop.getItems().stream().map(AppPlatformCheckoutPreviewRespVO.Item::getQuantity)
                        .reduce(0, Math::addExact);
                PlatformCheckoutPlanDO plan = new PlatformCheckoutPlanDO()
                        .setCheckoutId(checkout.getId()).setCheckoutNo(checkout.getCheckoutNo())
                        .setPlanNo(planNo).setTenantId(tenant.getTenantId())
                        .setSellerEnterpriseId(shop.getEnterpriseId()).setShopId(shop.getShopId())
                        .setTenantName(tenant.getTenantName()).setEnterpriseName(shop.getEnterpriseName())
                        .setShopName(shop.getShopName()).setItemCount(shop.getItems().size())
                        .setTotalQuantity(totalQuantity).setGoodsAmount(shop.getGoodsAmount())
                        .setFreightAmount(shop.getFreightAmount()).setDiscountAmount(0L)
                        .setPayAmount(Math.addExact(shop.getGoodsAmount(), shop.getFreightAmount()))
                        .setPlanStatus(DRAFT_PLAN_STATUS).setBlockReason(DRAFT_BLOCK_REASON).setVersion(0L);
                if (planMapper.insert(plan) != 1 || plan.getId() == null || plan.getId() <= 0) {
                    throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单规划保存失败");
                }
                plans.add(plan);
                for (AppPlatformCheckoutPreviewRespVO.Item item : shop.getItems()) {
                    PlatformCheckoutPlanItemDO snapshot = new PlatformCheckoutPlanItemDO()
                            .setCheckoutId(checkout.getId()).setCheckoutNo(checkout.getCheckoutNo())
                            .setPlanId(plan.getId()).setPlanNo(planNo).setCartItemId(item.getCartItemId())
                            .setTenantId(item.getTenantId()).setEnterpriseId(item.getEnterpriseId())
                            .setShopId(item.getShopId()).setSpuId(item.getSpuId()).setSkuId(item.getSkuId())
                            .setSpuName(item.getSpuName())
                            .setPropertiesJson(JsonUtils.toJsonString(item.getProperties() == null
                                    ? List.of() : item.getProperties()))
                            .setPicUrl(item.getPicUrl()).setQuantity(item.getQuantity())
                            .setUnitPrice(item.getUnitPrice()).setGoodsAmount(item.getGoodsAmount());
                    if (planItemMapper.insert(snapshot) != 1 || snapshot.getId() == null || snapshot.getId() <= 0) {
                        throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单商品快照保存失败");
                    }
                    items.add(snapshot);
                }
            }
        }
        if (plans.isEmpty() || items.isEmpty()) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单规划不能为空");
        }
        return new PlanSnapshot(List.copyOf(plans), List.copyOf(items));
    }

    private AppPlatformCheckoutDetailRespVO loadDetail(PlatformCheckoutDO checkout) {
        List<PlatformCheckoutOrderDO> orders = checkoutOrderMapper.selectListByCheckout(
                checkout.getId(), checkout.getCheckoutNo());
        List<PlatformFundAllocationDO> allocations = allocationMapper.selectListByCheckout(
                checkout.getId(), checkout.getCheckoutNo());
        List<PlatformCheckoutPlanDO> plans = planMapper.selectListByCheckout(
                checkout.getId(), checkout.getCheckoutNo());
        List<PlatformCheckoutPlanItemDO> planItems = planItemMapper.selectListByCheckout(
                checkout.getId(), checkout.getCheckoutNo());
        return buildDetail(checkout, orders, allocations, plans, planItems);
    }

    private AppPlatformCheckoutDetailRespVO buildDetail(PlatformCheckoutDO checkout,
                                                         List<PlatformCheckoutOrderDO> orders,
                                                         List<PlatformFundAllocationDO> allocations,
                                                         List<PlatformCheckoutPlanDO> plans,
                                                         List<PlatformCheckoutPlanItemDO> planItems) {
        validateCheckoutHeader(checkout);
        validateCheckoutRelations(checkout, orders, allocations, plans, planItems);
        String derivedAggregateStatus;
        try {
            derivedAggregateStatus = PlatformCheckoutAggregateStatusResolver.resolve(checkout, orders);
        } catch (IllegalArgumentException invalidState) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, invalidState.getMessage());
        }
        Map<Long, List<PlatformCheckoutPlanItemDO>> itemsByPlan = new LinkedHashMap<>();
        for (PlatformCheckoutPlanItemDO item : planItems) {
            itemsByPlan.computeIfAbsent(item.getPlanId(), ignored -> new ArrayList<>()).add(item);
        }
        return new AppPlatformCheckoutDetailRespVO()
                .setCheckoutNo(checkout.getCheckoutNo()).setCurrency(checkout.getCurrency())
                .setGoodsAmount(checkout.getGoodsAmount()).setFreightAmount(checkout.getFreightAmount())
                .setDiscountAmount(checkout.getDiscountAmount()).setPayAmount(checkout.getPayAmount())
                .setPaidAmount(checkout.getPaidAmount()).setRefundedAmount(checkout.getRefundedAmount())
                .setPayStatus(checkout.getPayStatus()).setAggregateStatus(derivedAggregateStatus)
                .setFailureReason(checkout.getFailureReason()).setRemark(checkout.getRemark())
                .setPlatformPayOrderId(checkout.getPlatformPayOrderId())
                .setPlatformPayOrderNo(checkout.getPlatformPayOrderNo())
                .setAddress(toAddress(checkout)).setPayable(checkout.getPlatformPayOrderId() != null
                        && "UNPAID".equals(checkout.getPayStatus()))
                .setPlanningOnly(DRAFT_AGGREGATE_STATUS.equals(derivedAggregateStatus))
                .setCreateTime(checkout.getCreateTime())
                .setChildOrders(orders.stream().map(PlatformCheckoutService::toChildOrder).toList())
                .setFundAllocations(allocations.stream().map(PlatformCheckoutService::toAllocation).toList())
                .setChildPlans(plans.stream().map(plan -> toChildPlan(plan,
                        itemsByPlan.getOrDefault(plan.getId(), List.of()))).toList());
    }

    static void validateCheckoutHeader(PlatformCheckoutDO checkout) {
        if (checkout.getId() == null || checkout.getId() <= 0 || checkout.getCheckoutNo() == null
                || checkout.getCheckoutNo().isBlank() || checkout.getPlatformMemberId() == null
                || checkout.getPlatformMemberId() <= 0 || checkout.getIdempotencyKey() == null
                || checkout.getIdempotencyKey().isBlank() || checkout.getRequestDigest() == null
                || !checkout.getRequestDigest().matches("[0-9a-f]{64}")
                || checkout.getAddressId() == null || checkout.getAddressId() <= 0
                || checkout.getAddressSourceTenantId() == null || checkout.getAddressSourceTenantId() <= 0
                || Objects.equals(checkout.getAddressSourceTenantId(), FORMER_PUBLIC_TENANT_ID)
                || checkout.getReceiverName() == null || checkout.getReceiverName().isBlank()
                || checkout.getReceiverMobile() == null || checkout.getReceiverMobile().isBlank()
                || checkout.getReceiverAreaId() == null || checkout.getReceiverAreaId() <= 0
                || checkout.getReceiverDetailAddress() == null || checkout.getReceiverDetailAddress().isBlank()
                || !CURRENCY_CNY.equals(checkout.getCurrency())
                || checkout.getPayStatus() == null || checkout.getPayStatus().isBlank()
                || checkout.getAggregateStatus() == null || checkout.getAggregateStatus().isBlank()) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "结算单主信息不完整");
        }
        if ((checkout.getPlatformPayOrderId() == null) != (checkout.getPlatformPayOrderNo() == null)
                || checkout.getPlatformPayOrderId() != null && checkout.getPlatformPayOrderId() <= 0
                || checkout.getPlatformPayOrderNo() != null && checkout.getPlatformPayOrderNo().isBlank()
                || (checkout.getReceiverLongitude() == null) != (checkout.getReceiverLatitude() == null)
                || !coordinateInRange(checkout.getReceiverLongitude(), "-180", "180")
                || !coordinateInRange(checkout.getReceiverLatitude(), "-90", "90")) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "结算单支付或地址快照不完整");
        }
        requireNonNegative(checkout.getGoodsAmount(), "商品金额");
        requireNonNegative(checkout.getFreightAmount(), "运费金额");
        requireNonNegative(checkout.getDiscountAmount(), "优惠金额");
        requireNonNegative(checkout.getPayAmount(), "应付金额");
        requireNonNegative(checkout.getPaidAmount(), "实付金额");
        requireNonNegative(checkout.getRefundedAmount(), "退款金额");
        long expectedPay;
        try {
            expectedPay = Math.subtractExact(Math.addExact(checkout.getGoodsAmount(), checkout.getFreightAmount()),
                    checkout.getDiscountAmount());
        } catch (ArithmeticException overflow) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "结算单金额溢出");
        }
        if (expectedPay != checkout.getPayAmount() || checkout.getPaidAmount() > checkout.getPayAmount()
                || checkout.getRefundedAmount() > checkout.getPaidAmount()) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "结算单金额不守恒");
        }
    }

    static void validateCheckoutRelations(PlatformCheckoutDO checkout,
                                          List<PlatformCheckoutOrderDO> orders,
                                          List<PlatformFundAllocationDO> allocations,
                                          List<PlatformCheckoutPlanDO> plans,
                                          List<PlatformCheckoutPlanItemDO> planItems) {
        boolean planningOnly = DRAFT_AGGREGATE_STATUS.equals(checkout.getAggregateStatus());
        validatePlanRelations(checkout, plans, planItems, planningOnly);
        if (planningOnly && (!DRAFT_PAY_STATUS.equals(checkout.getPayStatus())
                || checkout.getPlatformPayOrderId() != null || checkout.getPlatformPayOrderNo() != null
                || checkout.getPaidAmount() != 0 || checkout.getRefundedAmount() != 0
                || CollUtil.isNotEmpty(orders) || CollUtil.isNotEmpty(allocations))) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "规划草稿错误包含订单或资金事实");
        }
        if (CollUtil.isEmpty(orders)) {
            // 失败/创建中记录允许没有子订单，但绝不能成为可支付结果。
            if (!planningOnly && (checkout.getPayAmount() != 0 || checkout.getPaidAmount() != 0
                    || CollUtil.isNotEmpty(allocations))) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "无子订单的结算单存在资金数据");
            }
            return;
        }
        long goods = 0L;
        long freight = 0L;
        long discount = 0L;
        long pay = 0L;
        long orderRefunded = 0L;
        Set<Long> orderIds = new LinkedHashSet<>();
        Map<Long, PlatformCheckoutOrderDO> orderMap = new HashMap<>();
        for (PlatformCheckoutOrderDO order : orders) {
            validateChildOrder(checkout, order);
            if (!orderIds.add(order.getOrderId())) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "子订单重复");
            }
            orderMap.put(order.getOrderId(), order);
            try {
                goods = Math.addExact(goods, order.getGoodsAmount());
                freight = Math.addExact(freight, order.getFreightAmount());
                discount = Math.addExact(discount, order.getDiscountAmount());
                pay = Math.addExact(pay, order.getPayAmount());
                orderRefunded = Math.addExact(orderRefunded, order.getRefundedAmount());
            } catch (ArithmeticException overflow) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "子订单金额溢出");
            }
        }
        if (goods != checkout.getGoodsAmount() || freight != checkout.getFreightAmount()
                || discount != checkout.getDiscountAmount() || pay != checkout.getPayAmount()) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "平台金额与子订单金额不一致");
        }

        long allocated = 0L;
        long allocationRefunded = 0L;
        Set<Long> allocatedOrders = new LinkedHashSet<>();
        for (PlatformFundAllocationDO allocation : allocations) {
            PlatformCheckoutOrderDO order = orderMap.get(allocation.getOrderId());
            if (order == null || !allocatedOrders.add(allocation.getOrderId())
                    || !Objects.equals(allocation.getCheckoutId(), checkout.getId())
                    || !Objects.equals(allocation.getCheckoutNo(), checkout.getCheckoutNo())
                    || !Objects.equals(allocation.getTenantId(), order.getTenantId())
                    || !Objects.equals(allocation.getShopId(), order.getShopId())
                    || !Objects.equals(allocation.getAllocatedAmount(), order.getPayAmount())) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "资金分摊归属或金额不一致");
            }
            requireNonNegative(allocation.getRefundedAmount(), "分摊退款金额");
            if (allocation.getRefundedAmount() > allocation.getAllocatedAmount()) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "资金分摊退款超过分摊金额");
            }
            try {
                allocated = Math.addExact(allocated, allocation.getAllocatedAmount());
                allocationRefunded = Math.addExact(allocationRefunded, allocation.getRefundedAmount());
            } catch (ArithmeticException overflow) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "资金分摊金额溢出");
            }
        }
        if (!allocations.isEmpty() && (allocatedOrders.size() != orders.size() || allocated != checkout.getPayAmount())) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "资金分摊未覆盖全部子订单");
        }
        if (checkout.getPaidAmount() > 0 && allocations.isEmpty()) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "已支付结算单缺少资金分摊");
        }
        if (orderRefunded != checkout.getRefundedAmount()
                || allocationRefunded != checkout.getRefundedAmount()) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "平台、子订单与资金分摊退款金额不一致");
        }
    }

    private static void validatePlanRelations(PlatformCheckoutDO checkout,
                                              List<PlatformCheckoutPlanDO> plans,
                                              List<PlatformCheckoutPlanItemDO> items,
                                              boolean required) {
        if (CollUtil.isEmpty(plans)) {
            if (required || CollUtil.isNotEmpty(items)) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "结算规划或商品快照缺失");
            }
            return;
        }
        if (CollUtil.isEmpty(items)) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "结算规划商品快照缺失");
        }
        Map<Long, PlatformCheckoutPlanDO> plansById = new LinkedHashMap<>();
        Set<String> planNos = new LinkedHashSet<>();
        Set<String> owners = new LinkedHashSet<>();
        long goods = 0L;
        long freight = 0L;
        long discount = 0L;
        long pay = 0L;
        for (PlatformCheckoutPlanDO plan : plans) {
            if (!Objects.equals(plan.getCheckoutId(), checkout.getId())
                    || !Objects.equals(plan.getCheckoutNo(), checkout.getCheckoutNo())
                    || plan.getId() == null || plan.getId() <= 0 || plan.getPlanNo() == null
                    || plan.getPlanNo().isBlank() || plan.getTenantId() == null || plan.getTenantId() <= 0
                    || Objects.equals(plan.getTenantId(), FORMER_PUBLIC_TENANT_ID)
                    || plan.getSellerEnterpriseId() == null || plan.getSellerEnterpriseId() <= 0
                    || plan.getShopId() == null || plan.getShopId() <= 0
                    || plan.getItemCount() == null || plan.getItemCount() <= 0
                    || plan.getTotalQuantity() == null || plan.getTotalQuantity() <= 0
                    || plan.getPlanStatus() == null || plan.getPlanStatus().isBlank()) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单规划归属不完整或重复");
            }
            if (!planNos.add(plan.getPlanNo())
                    || !owners.add(plan.getTenantId() + ":" + plan.getShopId())) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单规划归属不完整或重复");
            }
            if (plansById.putIfAbsent(plan.getId(), plan) != null) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单规划重复");
            }
            requireNonNegative(plan.getGoodsAmount(), "规划商品金额");
            requireNonNegative(plan.getFreightAmount(), "规划运费");
            requireNonNegative(plan.getDiscountAmount(), "规划优惠");
            requireNonNegative(plan.getPayAmount(), "规划应付金额");
            long expected;
            try {
                expected = Math.subtractExact(Math.addExact(plan.getGoodsAmount(), plan.getFreightAmount()),
                        plan.getDiscountAmount());
                goods = Math.addExact(goods, plan.getGoodsAmount());
                freight = Math.addExact(freight, plan.getFreightAmount());
                discount = Math.addExact(discount, plan.getDiscountAmount());
                pay = Math.addExact(pay, plan.getPayAmount());
            } catch (ArithmeticException overflow) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单规划金额溢出");
            }
            if (expected != plan.getPayAmount()) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单规划金额不守恒");
            }
        }

        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Integer> quantities = new HashMap<>();
        Map<Long, Long> itemAmounts = new HashMap<>();
        Set<Long> cartIds = new LinkedHashSet<>();
        for (PlatformCheckoutPlanItemDO item : items) {
            PlatformCheckoutPlanDO plan = plansById.get(item.getPlanId());
            if (plan == null || !Objects.equals(item.getCheckoutId(), checkout.getId())
                    || !Objects.equals(item.getCheckoutNo(), checkout.getCheckoutNo())
                    || !Objects.equals(item.getPlanNo(), plan.getPlanNo())
                    || item.getId() == null || item.getId() <= 0
                    || item.getCartItemId() == null || item.getCartItemId() <= 0 || !cartIds.add(item.getCartItemId())
                    || !Objects.equals(item.getTenantId(), plan.getTenantId())
                    || !Objects.equals(item.getEnterpriseId(), plan.getSellerEnterpriseId())
                    || !Objects.equals(item.getShopId(), plan.getShopId())
                    || item.getSpuId() == null || item.getSpuId() <= 0 || item.getSkuId() == null || item.getSkuId() <= 0
                    || item.getQuantity() == null || item.getQuantity() <= 0
                    || item.getUnitPrice() == null || item.getUnitPrice() <= 0
                    || item.getGoodsAmount() == null || item.getGoodsAmount() <= 0
                    || item.getPropertiesJson() == null) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单商品快照归属或内容无效");
            }
            long expected;
            try {
                expected = Math.multiplyExact((long) item.getUnitPrice(), item.getQuantity());
                counts.merge(plan.getId(), 1, Math::addExact);
                quantities.merge(plan.getId(), item.getQuantity(), Math::addExact);
                itemAmounts.merge(plan.getId(), item.getGoodsAmount(), Math::addExact);
            } catch (ArithmeticException overflow) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单商品快照金额或数量溢出");
            }
            if (expected != item.getGoodsAmount()) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单商品快照金额不守恒");
            }
        }
        for (PlatformCheckoutPlanDO plan : plans) {
            if (!Objects.equals(counts.get(plan.getId()), plan.getItemCount())
                    || !Objects.equals(quantities.get(plan.getId()), plan.getTotalQuantity())
                    || !Objects.equals(itemAmounts.get(plan.getId()), plan.getGoodsAmount())) {
                throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单规划与商品快照汇总不一致");
            }
        }
        if (goods != checkout.getGoodsAmount() || freight != checkout.getFreightAmount()
                || discount != checkout.getDiscountAmount() || pay != checkout.getPayAmount()) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "平台金额与拆单规划金额不一致");
        }
    }

    private static void validateChildOrder(PlatformCheckoutDO checkout, PlatformCheckoutOrderDO order) {
        if (!Objects.equals(order.getCheckoutId(), checkout.getId())
                || !Objects.equals(order.getCheckoutNo(), checkout.getCheckoutNo())
                || order.getOrderId() == null || order.getOrderId() <= 0 || order.getOrderNo() == null
                || order.getOrderNo().isBlank() || order.getTenantId() == null || order.getTenantId() <= 0
                || Objects.equals(order.getTenantId(), FORMER_PUBLIC_TENANT_ID)
                || order.getBuyerMemberId() == null || order.getBuyerMemberId() <= 0
                || order.getSellerEnterpriseId() == null || order.getSellerEnterpriseId() <= 0
                || order.getShopId() == null || order.getShopId() <= 0
                || order.getOrderStatus() == null || order.getAfterSaleStatus() == null) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "子订单归属不完整或包含原公共租户");
        }
        requireNonNegative(order.getGoodsAmount(), "子订单商品金额");
        requireNonNegative(order.getFreightAmount(), "子订单运费");
        requireNonNegative(order.getDiscountAmount(), "子订单优惠");
        requireNonNegative(order.getPayAmount(), "子订单应付金额");
        requireNonNegative(order.getRefundedAmount(), "子订单退款金额");
        long expected;
        try {
            expected = Math.subtractExact(Math.addExact(order.getGoodsAmount(), order.getFreightAmount()),
                    order.getDiscountAmount());
        } catch (ArithmeticException overflow) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "子订单金额溢出");
        }
        if (expected != order.getPayAmount() || order.getRefundedAmount() > order.getPayAmount()) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "子订单金额不守恒");
        }
    }

    private static void requireNonNegative(Long amount, String label) {
        if (amount == null || amount < 0) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, label + "无效");
        }
    }

    private static AppPlatformCheckoutDetailRespVO.ChildOrder toChildOrder(PlatformCheckoutOrderDO order) {
        return new AppPlatformCheckoutDetailRespVO.ChildOrder()
                .setOrderId(order.getOrderId()).setOrderNo(order.getOrderNo())
                .setTenantId(order.getTenantId()).setBuyerMemberId(order.getBuyerMemberId())
                .setBuyerEnterpriseId(order.getBuyerEnterpriseId())
                .setSellerEnterpriseId(order.getSellerEnterpriseId()).setShopId(order.getShopId())
                .setGoodsAmount(order.getGoodsAmount()).setFreightAmount(order.getFreightAmount())
                .setDiscountAmount(order.getDiscountAmount()).setPayAmount(order.getPayAmount())
                .setRefundedAmount(order.getRefundedAmount()).setOrderStatus(order.getOrderStatus())
                .setAfterSaleStatus(order.getAfterSaleStatus());
    }

    private static AppPlatformCheckoutPageItemRespVO toPageItem(PlatformCheckoutDO checkout) {
        boolean planningOnly = Objects.equals(DRAFT_AGGREGATE_STATUS, checkout.getAggregateStatus());
        boolean payable = !planningOnly && !Objects.equals(DRAFT_PAY_STATUS, checkout.getPayStatus())
                && checkout.getPlatformPayOrderId() != null && checkout.getPlatformPayOrderId() > 0;
        return new AppPlatformCheckoutPageItemRespVO()
                .setCheckoutNo(checkout.getCheckoutNo()).setCurrency(checkout.getCurrency())
                .setGoodsAmount(checkout.getGoodsAmount()).setFreightAmount(checkout.getFreightAmount())
                .setDiscountAmount(checkout.getDiscountAmount()).setPayAmount(checkout.getPayAmount())
                .setPaidAmount(checkout.getPaidAmount()).setRefundedAmount(checkout.getRefundedAmount())
                .setPayStatus(checkout.getPayStatus()).setAggregateStatus(checkout.getAggregateStatus())
                .setFailureReason(checkout.getFailureReason()).setPayable(payable)
                .setPlanningOnly(planningOnly).setCreateTime(checkout.getCreateTime());
    }

    private static AppPlatformCheckoutDetailRespVO.FundAllocation toAllocation(
            PlatformFundAllocationDO allocation) {
        return new AppPlatformCheckoutDetailRespVO.FundAllocation()
                .setOrderId(allocation.getOrderId()).setTenantId(allocation.getTenantId())
                .setShopId(allocation.getShopId()).setAllocatedAmount(allocation.getAllocatedAmount())
                .setRefundedAmount(allocation.getRefundedAmount())
                .setAllocationStatus(allocation.getAllocationStatus());
    }

    private static AppPlatformCheckoutDetailRespVO.ChildPlan toChildPlan(
            PlatformCheckoutPlanDO plan, List<PlatformCheckoutPlanItemDO> items) {
        return new AppPlatformCheckoutDetailRespVO.ChildPlan()
                .setPlanNo(plan.getPlanNo()).setTenantId(plan.getTenantId())
                .setSellerEnterpriseId(plan.getSellerEnterpriseId()).setShopId(plan.getShopId())
                .setTenantName(plan.getTenantName()).setEnterpriseName(plan.getEnterpriseName())
                .setShopName(plan.getShopName()).setItemCount(plan.getItemCount())
                .setTotalQuantity(plan.getTotalQuantity()).setGoodsAmount(plan.getGoodsAmount())
                .setFreightAmount(plan.getFreightAmount()).setDiscountAmount(plan.getDiscountAmount())
                .setPayAmount(plan.getPayAmount()).setPlanStatus(plan.getPlanStatus())
                .setBlockReason(plan.getBlockReason())
                .setItems(items.stream().map(PlatformCheckoutService::toPlanItem).toList());
    }

    private static AppPlatformCheckoutDetailRespVO.PlanItem toPlanItem(PlatformCheckoutPlanItemDO item) {
        List<ProductPropertyValueDetailRespDTO> properties;
        try {
            properties = JsonUtils.parseArray(item.getPropertiesJson(), ProductPropertyValueDetailRespDTO.class);
        } catch (RuntimeException malformed) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "拆单商品规格快照无法解析");
        }
        return new AppPlatformCheckoutDetailRespVO.PlanItem()
                .setCartItemId(item.getCartItemId()).setTenantId(item.getTenantId())
                .setEnterpriseId(item.getEnterpriseId()).setShopId(item.getShopId())
                .setSpuId(item.getSpuId()).setSkuId(item.getSkuId()).setSpuName(item.getSpuName())
                .setProperties(properties == null ? List.of() : properties).setPicUrl(item.getPicUrl())
                .setQuantity(item.getQuantity()).setUnitPrice(item.getUnitPrice())
                .setGoodsAmount(item.getGoodsAmount());
    }

    private static AppPlatformCheckoutAddressRespVO toAddress(MemberPlatformAddressRespDTO address) {
        return new AppPlatformCheckoutAddressRespVO()
                .setAddressId(address.getAddressId()).setSourceTenantId(address.getSourceTenantId())
                .setName(address.getName()).setMobile(address.getMobile()).setAreaId(address.getAreaId())
                .setDetailAddress(address.getDetailAddress()).setLongitude(address.getLongitude())
                .setLatitude(address.getLatitude()).setCoordSystem(address.getCoordSystem());
    }

    private static AppPlatformCheckoutAddressRespVO toAddress(PlatformCheckoutDO checkout) {
        return new AppPlatformCheckoutAddressRespVO()
                .setAddressId(checkout.getAddressId()).setSourceTenantId(checkout.getAddressSourceTenantId())
                .setName(checkout.getReceiverName()).setMobile(checkout.getReceiverMobile())
                .setAreaId(checkout.getReceiverAreaId()).setDetailAddress(checkout.getReceiverDetailAddress())
                .setLongitude(checkout.getReceiverLongitude()).setLatitude(checkout.getReceiverLatitude())
                .setCoordSystem(checkout.getReceiverCoordSystem());
    }

    private record ResolvedCheckoutContext(Long platformAccountId, MemberPlatformAddressRespDTO address) {
    }

    private record PlanSnapshot(List<PlatformCheckoutPlanDO> plans,
                                List<PlatformCheckoutPlanItemDO> items) {
    }

    private static final class TenantAccumulator {
        private final Long tenantId;
        private final String tenantName;
        private long goodsAmount;
        private final Map<Long, ShopAccumulator> shops = new LinkedHashMap<>();

        private TenantAccumulator(Long tenantId, String tenantName) {
            this.tenantId = tenantId;
            this.tenantName = tenantName;
        }

        private void add(PlatformCartDO cart, ProductPublicSkuRespDTO product, long lineAmount) {
            goodsAmount = Math.addExact(goodsAmount, lineAmount);
            ShopAccumulator shop = shops.computeIfAbsent(product.getShopId(), ignored ->
                    new ShopAccumulator(product.getEnterpriseId(), product.getEnterpriseName(),
                            product.getShopId(), product.getShopName()));
            if (!Objects.equals(shop.enterpriseId, product.getEnterpriseId())) {
                throw exception(PLATFORM_CHECKOUT_CART_INVALID, "同一店铺返回了冲突的企业归属");
            }
            shop.add(cart, product, lineAmount);
        }

        private void applyFreight(Map<PlatformCheckoutFreightService.ShopKey,
                PlatformCheckoutFreightService.FreightQuote> quotes) {
            shops.values().forEach(shop -> shop.applyFreight(tenantId, quotes));
        }

        private boolean freightReady() {
            return !shops.isEmpty() && shops.values().stream().allMatch(ShopAccumulator::freightReady);
        }

        private long freightAmount() {
            return shops.values().stream().mapToLong(ShopAccumulator::requiredFreightAmount)
                    .reduce(0L, Math::addExact);
        }

        private AppPlatformCheckoutPreviewRespVO.TenantGroup toResponse() {
            boolean ready = freightReady();
            return new AppPlatformCheckoutPreviewRespVO.TenantGroup()
                    .setTenantId(tenantId).setTenantName(tenantName == null ? "租户 " + tenantId : tenantName)
                    .setGoodsAmount(goodsAmount)
                    .setFreightReady(ready).setFreightAmount(ready ? freightAmount() : null)
                    .setShops(shops.values().stream().map(ShopAccumulator::toResponse).toList());
        }
    }

    private static final class ShopAccumulator {
        private final Long enterpriseId;
        private final String enterpriseName;
        private final Long shopId;
        private final String shopName;
        private long goodsAmount;
        private Long freightAmount;
        private String freightFailureReason;
        private final List<AppPlatformCheckoutPreviewRespVO.Item> items = new ArrayList<>();

        private ShopAccumulator(Long enterpriseId, String enterpriseName, Long shopId, String shopName) {
            this.enterpriseId = enterpriseId;
            this.enterpriseName = enterpriseName;
            this.shopId = shopId;
            this.shopName = shopName;
        }

        private void add(PlatformCartDO cart, ProductPublicSkuRespDTO product, long lineAmount) {
            goodsAmount = Math.addExact(goodsAmount, lineAmount);
            items.add(new AppPlatformCheckoutPreviewRespVO.Item()
                    .setCartItemId(cart.getId()).setTenantId(product.getTenantId())
                    .setEnterpriseId(product.getEnterpriseId()).setShopId(product.getShopId())
                    .setSpuId(product.getSpuId()).setSpuName(product.getSpuName()).setSkuId(product.getSkuId())
                    .setProperties(product.getProperties() == null ? List.of() : List.copyOf(product.getProperties()))
                    .setPicUrl(product.getPicUrl()).setQuantity(cart.getQuantity())
                    .setUnitPrice(product.getPrice()).setGoodsAmount(lineAmount));
        }

        private void applyFreight(Long tenantId,
                                  Map<PlatformCheckoutFreightService.ShopKey,
                                          PlatformCheckoutFreightService.FreightQuote> quotes) {
            PlatformCheckoutFreightService.FreightQuote quote = quotes.get(
                    new PlatformCheckoutFreightService.ShopKey(tenantId, shopId));
            if (quote == null || !quote.ready() || quote.freightAmount() == null
                    || quote.freightAmount() < 0) {
                freightAmount = null;
                freightFailureReason = quote == null || quote.failureReason() == null
                        ? PlatformCheckoutFreightService.TENANT_LOGISTICS_UNAVAILABLE : quote.failureReason();
                return;
            }
            freightAmount = quote.freightAmount();
            freightFailureReason = null;
        }

        private boolean freightReady() {
            return freightAmount != null && freightAmount >= 0 && freightFailureReason == null;
        }

        private long requiredFreightAmount() {
            if (!freightReady()) {
                throw new IllegalStateException("店铺运费尚未核定");
            }
            return freightAmount;
        }

        private AppPlatformCheckoutPreviewRespVO.ShopGroup toResponse() {
            return new AppPlatformCheckoutPreviewRespVO.ShopGroup()
                    .setEnterpriseId(enterpriseId)
                    .setEnterpriseName(enterpriseName == null ? "企业 " + enterpriseId : enterpriseName)
                    .setShopId(shopId).setShopName(shopName == null ? "店铺 " + shopId : shopName)
                    .setGoodsAmount(goodsAmount).setFreightReady(freightReady())
                    .setFreightAmount(freightAmount).setFreightFailureReason(freightFailureReason)
                    .setItems(List.copyOf(items));
        }
    }

}
