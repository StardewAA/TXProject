package cn.iocoder.txgy.module.trade.service.checkout;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.member.api.platform.MemberPlatformAccountApi;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutFormalizationDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutPlanDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutPlanItemDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformFundAllocationDO;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutFormalizationMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutOrderMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutPlanItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutPlanMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformFundAllocationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_DATA_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_ACCOUNT_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_NOT_FOUND;

/**
 * 平台结算正式化准备边界。
 *
 * <p>这里故意不调用标准下单链路。标准链路会在单个租户内立即消费促销资源、清理购物车并创建
 * 独立支付单，无法作为跨租户聚合订单的原子子步骤。本服务只锁定并复核 DRAFT_ONLY 快照，
 * 以幂等方式记录仍未满足的正式化条件；不会写 {@code trade_order}、支付、库存或购物车事实。</p>
 */
@Service
public class PlatformCheckoutFormalizationService {

    static final String ATTEMPT_STATUS_BLOCKED = "BLOCKED";
    static final String GATE_REVISION = "platform-checkout-formalization-v1";

    private final PlatformCheckoutMapper checkoutMapper;
    private final PlatformCheckoutOrderMapper checkoutOrderMapper;
    private final PlatformFundAllocationMapper allocationMapper;
    private final PlatformCheckoutPlanMapper planMapper;
    private final PlatformCheckoutPlanItemMapper planItemMapper;
    private final PlatformCheckoutFormalizationMapper formalizationMapper;
    private final MemberPlatformAccountApi platformAccountApi;
    private final boolean featureEnabled;

    public PlatformCheckoutFormalizationService(
            PlatformCheckoutMapper checkoutMapper,
            PlatformCheckoutOrderMapper checkoutOrderMapper,
            PlatformFundAllocationMapper allocationMapper,
            PlatformCheckoutPlanMapper planMapper,
            PlatformCheckoutPlanItemMapper planItemMapper,
            PlatformCheckoutFormalizationMapper formalizationMapper,
            MemberPlatformAccountApi platformAccountApi,
            @Value("${txgy.trade.platform-checkout.formalization-enabled:false}") boolean featureEnabled) {
        this.checkoutMapper = checkoutMapper;
        this.checkoutOrderMapper = checkoutOrderMapper;
        this.allocationMapper = allocationMapper;
        this.planMapper = planMapper;
        this.planItemMapper = planItemMapper;
        this.formalizationMapper = formalizationMapper;
        this.platformAccountApi = platformAccountApi;
        this.featureEnabled = featureEnabled;
    }

    /**
     * 在结算主记录行锁内生成正式化 readiness 记录。
     *
     * <p>即使配置误开，本版本仍保留专用子订单写入器等硬阻断，绝不会把规划草稿标记成正式订单。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Preparation prepare(Long legacyMemberId, Long sourceTenantId,
                               String checkoutNo, String idempotencyKey) {
        Long platformMemberId = resolvePlatformAccountId(legacyMemberId, sourceTenantId);
        return prepareResolved(platformMemberId, checkoutNo, idempotencyKey);
    }

    private Preparation prepareResolved(Long platformMemberId, String checkoutNo, String idempotencyKey) {
        validateRequest(platformMemberId, checkoutNo, idempotencyKey);
        PlatformCheckoutDO checkout = checkoutMapper.selectByNoAndMemberForUpdate(checkoutNo, platformMemberId);
        if (checkout == null) {
            throw exception(PLATFORM_CHECKOUT_NOT_FOUND);
        }
        List<PlatformCheckoutOrderDO> orders = nullToEmpty(checkoutOrderMapper.selectListByCheckout(
                checkout.getId(), checkout.getCheckoutNo()));
        List<PlatformFundAllocationDO> allocations = nullToEmpty(allocationMapper.selectListByCheckout(
                checkout.getId(), checkout.getCheckoutNo()));
        List<PlatformCheckoutPlanDO> plans = nullToEmpty(planMapper.selectListByCheckout(
                checkout.getId(), checkout.getCheckoutNo()));
        List<PlatformCheckoutPlanItemDO> items = nullToEmpty(planItemMapper.selectListByCheckout(
                checkout.getId(), checkout.getCheckoutNo()));
        validateDraftSnapshot(checkout, orders, allocations, plans, items);

        List<Blocker> blockers = blockers();
        String blockerCodes = JsonUtils.toJsonString(blockers.stream().map(Enum::name).toList());
        String blockerReason = blockers.stream().map(Blocker::getReason)
                .collect(java.util.stream.Collectors.joining("；"));
        String snapshotDigest = snapshotDigest(checkout, plans, items);

        PlatformCheckoutFormalizationDO existing = formalizationMapper.selectByCheckoutAndKey(
                checkout.getId(), idempotencyKey);
        if (existing != null) {
            validateIdempotentAttempt(existing, checkout, idempotencyKey, snapshotDigest,
                    blockerCodes, blockerReason);
            return new Preparation(existing.getId(), checkout.getCheckoutNo(), existing.getSnapshotDigest(),
                    false, List.copyOf(blockers), existing.getBlockerReason());
        }

        PlatformCheckoutFormalizationDO candidate = new PlatformCheckoutFormalizationDO()
                .setCheckoutId(checkout.getId()).setCheckoutNo(checkout.getCheckoutNo())
                .setPlatformMemberId(checkout.getPlatformMemberId()).setIdempotencyKey(idempotencyKey)
                .setSnapshotDigest(snapshotDigest).setAttemptStatus(ATTEMPT_STATUS_BLOCKED)
                .setBlockerCodes(blockerCodes).setBlockerReason(blockerReason).setVersion(0L);
        formalizationMapper.insertBlockedOrGetId(candidate);
        if (candidate.getId() == null || candidate.getId() <= 0) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "未取得正式化准备记录编号");
        }
        PlatformCheckoutFormalizationDO claimed = formalizationMapper.selectByCheckoutAndKey(
                checkout.getId(), idempotencyKey);
        validateIdempotentAttempt(claimed, checkout, idempotencyKey, snapshotDigest,
                blockerCodes, blockerReason);
        return new Preparation(claimed.getId(), checkout.getCheckoutNo(), claimed.getSnapshotDigest(),
                false, List.copyOf(blockers), claimed.getBlockerReason());
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

    private List<Blocker> blockers() {
        List<Blocker> blockers = new ArrayList<>();
        if (!featureEnabled) {
            blockers.add(Blocker.FEATURE_DISABLED);
        }
        // 以下条件尚无安全实现。即使误将配置打开，也必须继续 fail-closed。
        blockers.add(Blocker.LIVE_PRODUCT_REVALIDATION_UNAVAILABLE);
        blockers.add(Blocker.TARGET_MEMBER_MAPPING_UNAVAILABLE);
        blockers.add(Blocker.PROMOTION_COMMIT_UNAVAILABLE);
        blockers.add(Blocker.INVENTORY_RESERVATION_UNAVAILABLE);
        blockers.add(Blocker.AGGREGATE_PAYMENT_UNAVAILABLE);
        blockers.add(Blocker.ATOMIC_CHILD_ORDER_WRITER_UNAVAILABLE);
        return List.copyOf(blockers);
    }

    private static void validateRequest(Long platformMemberId, String checkoutNo, String idempotencyKey) {
        if (platformMemberId == null || platformMemberId <= 0
                || checkoutNo == null || checkoutNo.isBlank() || checkoutNo.length() > 40
                || !checkoutNo.matches("[A-Za-z0-9._:-]+")) {
            throw exception(PLATFORM_CHECKOUT_NOT_FOUND);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64
                || !idempotencyKey.matches("[A-Za-z0-9._:-]+")) {
            throw exception(PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT);
        }
    }

    private static void validateDraftSnapshot(PlatformCheckoutDO checkout,
                                              List<PlatformCheckoutOrderDO> orders,
                                              List<PlatformFundAllocationDO> allocations,
                                              List<PlatformCheckoutPlanDO> plans,
                                              List<PlatformCheckoutPlanItemDO> items) {
        PlatformCheckoutService.validateCheckoutHeader(checkout);
        PlatformCheckoutService.validateCheckoutRelations(checkout, orders, allocations, plans, items);
        if (!PlatformCheckoutService.DRAFT_AGGREGATE_STATUS.equals(checkout.getAggregateStatus())
                || !PlatformCheckoutService.DRAFT_PAY_STATUS.equals(checkout.getPayStatus())
                || plans.stream().anyMatch(plan -> !PlatformCheckoutService.DRAFT_PLAN_STATUS.equals(
                        plan.getPlanStatus()) || plan.getBlockReason() == null || plan.getBlockReason().isBlank())) {
            throw exception(PLATFORM_CHECKOUT_DATA_INVALID, "仅可准备完整且不可支付的 DRAFT_ONLY 规划");
        }
    }

    private static void validateIdempotentAttempt(PlatformCheckoutFormalizationDO attempt,
                                                  PlatformCheckoutDO checkout,
                                                  String idempotencyKey,
                                                  String snapshotDigest,
                                                  String blockerCodes,
                                                  String blockerReason) {
        if (attempt == null || attempt.getId() == null || attempt.getId() <= 0
                || !Objects.equals(attempt.getCheckoutId(), checkout.getId())
                || !Objects.equals(attempt.getCheckoutNo(), checkout.getCheckoutNo())
                || !Objects.equals(attempt.getPlatformMemberId(), checkout.getPlatformMemberId())
                || !Objects.equals(attempt.getIdempotencyKey(), idempotencyKey)
                || !Objects.equals(attempt.getSnapshotDigest(), snapshotDigest)
                || !Objects.equals(attempt.getAttemptStatus(), ATTEMPT_STATUS_BLOCKED)
                || !Objects.equals(attempt.getBlockerCodes(), blockerCodes)
                || !Objects.equals(attempt.getBlockerReason(), blockerReason)
                || attempt.getVersion() == null || attempt.getVersion() < 0) {
            throw exception(PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT);
        }
    }

    static String snapshotDigest(PlatformCheckoutDO checkout,
                                 List<PlatformCheckoutPlanDO> plans,
                                 List<PlatformCheckoutPlanItemDO> items) {
        StringBuilder canonical = new StringBuilder(1024);
        append(canonical, GATE_REVISION);
        append(canonical, checkout.getId(), checkout.getCheckoutNo(), checkout.getRequestDigest(),
                checkout.getPlatformMemberId(), checkout.getAddressId(), checkout.getAddressSourceTenantId(),
                checkout.getReceiverName(), checkout.getReceiverMobile(), checkout.getReceiverAreaId(),
                checkout.getReceiverDetailAddress(), checkout.getReceiverLongitude(), checkout.getReceiverLatitude(),
                checkout.getReceiverCoordSystem(), checkout.getRemark(), checkout.getCurrency(),
                checkout.getGoodsAmount(), checkout.getFreightAmount(), checkout.getDiscountAmount(),
                checkout.getPayAmount(), checkout.getPaidAmount(), checkout.getRefundedAmount(),
                checkout.getPayStatus(), checkout.getAggregateStatus(), checkout.getPlatformPayOrderId(),
                checkout.getPlatformPayOrderNo(), checkout.getVersion());
        plans.stream().sorted(Comparator.comparing(PlatformCheckoutPlanDO::getTenantId)
                        .thenComparing(PlatformCheckoutPlanDO::getShopId)
                        .thenComparing(PlatformCheckoutPlanDO::getId))
                .forEach(plan -> append(canonical, "PLAN", plan.getId(), plan.getPlanNo(), plan.getTenantId(),
                        plan.getSellerEnterpriseId(), plan.getShopId(), plan.getItemCount(), plan.getTotalQuantity(),
                        plan.getGoodsAmount(), plan.getFreightAmount(), plan.getDiscountAmount(), plan.getPayAmount(),
                        plan.getPlanStatus(), plan.getVersion()));
        items.stream().sorted(Comparator.comparing(PlatformCheckoutPlanItemDO::getPlanId)
                        .thenComparing(PlatformCheckoutPlanItemDO::getId))
                .forEach(item -> append(canonical, "ITEM", item.getId(), item.getPlanId(), item.getPlanNo(),
                        item.getCartItemId(), item.getTenantId(), item.getEnterpriseId(), item.getShopId(),
                        item.getSpuId(), item.getSkuId(), item.getSpuName(), item.getPropertiesJson(), item.getPicUrl(),
                        item.getQuantity(), item.getUnitPrice(), item.getGoodsAmount()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void append(StringBuilder target, Object... values) {
        for (Object value : values) {
            String normalized = value == null ? "<null>" : value.toString();
            target.append(normalized.length()).append(':').append(normalized).append('|');
        }
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Preparation(Long attemptId, String checkoutNo, String snapshotDigest,
                              boolean ready, List<Blocker> blockers, String reason) {
    }

    public enum Blocker {
        FEATURE_DISABLED("正式子订单功能开关默认关闭"),
        LIVE_PRODUCT_REVALIDATION_UNAVAILABLE("正式提交前尚未重新核验商品价格、库存、经营归属与运费"),
        TARGET_MEMBER_MAPPING_UNAVAILABLE("平台账号尚未映射到各目标租户的实际会员编号"),
        PROMOTION_COMMIT_UNAVAILABLE("跨租户优惠券、积分与促销资源尚无统一提交或补偿边界"),
        INVENTORY_RESERVATION_UNAVAILABLE("WMS 当前只读，尚无幂等库存预占与释放能力"),
        AGGREGATE_PAYMENT_UNAVAILABLE("聚合支付及子订单资金分摊尚未接通"),
        ATOMIC_CHILD_ORDER_WRITER_UNAVAILABLE("尚无延迟外部副作用的原子子订单专用写入器");

        private final String reason;

        Blocker(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}
