package cn.iocoder.txgy.module.trade.service.checkout;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_DATA_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_ACCOUNT_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformCheckoutFormalizationServiceTest {

    private PlatformCheckoutMapper checkoutMapper;
    private PlatformCheckoutOrderMapper checkoutOrderMapper;
    private PlatformFundAllocationMapper allocationMapper;
    private PlatformCheckoutPlanMapper planMapper;
    private PlatformCheckoutPlanItemMapper planItemMapper;
    private PlatformCheckoutFormalizationMapper formalizationMapper;
    private MemberPlatformAccountApi platformAccountApi;
    private PlatformCheckoutFormalizationService service;
    private PlatformCheckoutDO checkout;
    private PlatformCheckoutPlanDO plan;
    private PlatformCheckoutPlanItemDO item;

    @BeforeEach
    void setUp() {
        checkoutMapper = mock(PlatformCheckoutMapper.class);
        checkoutOrderMapper = mock(PlatformCheckoutOrderMapper.class);
        allocationMapper = mock(PlatformFundAllocationMapper.class);
        planMapper = mock(PlatformCheckoutPlanMapper.class);
        planItemMapper = mock(PlatformCheckoutPlanItemMapper.class);
        formalizationMapper = mock(PlatformCheckoutFormalizationMapper.class);
        platformAccountApi = mock(MemberPlatformAccountApi.class);
        service = service(false);
        checkout = checkout();
        plan = plan();
        item = item();
        when(platformAccountApi.resolve(316L, 2009L)).thenReturn(CommonResult.success(
                new MemberPlatformAccountRespDTO().setPlatformAccountId(9001L)
                        .setLegacyMemberId(316L).setSourceTenantId(2009L)));
        stubDraftSnapshot();
    }

    @Test
    void preparePersistsOnlyBlockedReadinessAudit() {
        AtomicReference<PlatformCheckoutFormalizationDO> stored = storeAttempt();

        PlatformCheckoutFormalizationService.Preparation result =
                service.prepare(316L, 2009L, "pc202609211200001", "formalize-01");

        assertEquals(40L, result.attemptId());
        assertEquals("pc202609211200001", result.checkoutNo());
        assertEquals(64, result.snapshotDigest().length());
        assertFalse(result.ready());
        assertTrue(result.blockers().contains(
                PlatformCheckoutFormalizationService.Blocker.FEATURE_DISABLED));
        assertTrue(result.blockers().contains(
                PlatformCheckoutFormalizationService.Blocker.AGGREGATE_PAYMENT_UNAVAILABLE));
        assertEquals(PlatformCheckoutFormalizationService.ATTEMPT_STATUS_BLOCKED,
                stored.get().getAttemptStatus());

        verify(formalizationMapper).insertBlockedOrGetId(any(PlatformCheckoutFormalizationDO.class));
        verify(checkoutMapper, never()).insertDraftOrGetId(any(PlatformCheckoutDO.class));
        verify(checkoutOrderMapper, never()).insert(any(PlatformCheckoutOrderDO.class));
        verify(allocationMapper, never()).insert(any(PlatformFundAllocationDO.class));
        verify(planMapper, never()).insert(any(PlatformCheckoutPlanDO.class));
        verify(planItemMapper, never()).insert(any(PlatformCheckoutPlanItemDO.class));
    }

    @Test
    void prepareIsIdempotentForSameKeyAndSnapshot() {
        storeAttempt();

        var first = service.prepare(316L, 2009L, "pc202609211200001", "formalize-01");
        var second = service.prepare(316L, 2009L, "pc202609211200001", "formalize-01");

        assertEquals(first, second);
        verify(formalizationMapper, times(1))
                .insertBlockedOrGetId(any(PlatformCheckoutFormalizationDO.class));
    }

    @Test
    void prepareRejectsSameKeyWhenSnapshotDigestChanged() {
        PlatformCheckoutFormalizationDO oldAttempt = attempt("0".repeat(64));
        when(formalizationMapper.selectByCheckoutAndKey(10L, "formalize-01")).thenReturn(oldAttempt);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.prepare(316L, 2009L, "pc202609211200001", "formalize-01"));

        assertEquals(PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
        verify(formalizationMapper, never()).insertBlockedOrGetId(any());
    }

    @Test
    void prepareFailsClosedWhenDraftContainsPaymentFact() {
        checkout.setPlatformPayOrderId(88L).setPlatformPayOrderNo("PAY-88");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.prepare(316L, 2009L, "pc202609211200001", "formalize-01"));

        assertEquals(PLATFORM_CHECKOUT_DATA_INVALID.getCode(), error.getCode());
        verify(formalizationMapper, never()).selectByCheckoutAndKey(any(), any());
        verify(formalizationMapper, never()).insertBlockedOrGetId(any());
    }

    @Test
    void enablingConfigurationAloneStillCannotMakeDraftReady() {
        service = service(true);
        storeAttempt();

        var result = service.prepare(316L, 2009L, "pc202609211200001", "formalize-01");

        assertFalse(result.ready());
        assertFalse(result.blockers().contains(
                PlatformCheckoutFormalizationService.Blocker.FEATURE_DISABLED));
        assertTrue(result.blockers().contains(
                PlatformCheckoutFormalizationService.Blocker.ATOMIC_CHILD_ORDER_WRITER_UNAVAILABLE));
        assertTrue(result.blockers().contains(
                PlatformCheckoutFormalizationService.Blocker.INVENTORY_RESERVATION_UNAVAILABLE));
    }

    @Test
    void snapshotDigestCoversServerAmountsAndOwnership() {
        String original = PlatformCheckoutFormalizationService.snapshotDigest(
                checkout, List.of(plan), List.of(item));

        plan.setFreightAmount(301L).setPayAmount(2701L);
        checkout.setFreightAmount(301L).setPayAmount(2701L);
        String changed = PlatformCheckoutFormalizationService.snapshotDigest(
                checkout, List.of(plan), List.of(item));

        assertNotEquals(original, changed);
    }

    @Test
    void prepareRejectsForgedPlatformAccountResolution() {
        when(platformAccountApi.resolve(316L, 2009L)).thenReturn(CommonResult.success(
                new MemberPlatformAccountRespDTO().setPlatformAccountId(9001L)
                        .setLegacyMemberId(999L).setSourceTenantId(2009L)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.prepare(316L, 2009L, "pc202609211200001", "formalize-01"));

        assertEquals(PLATFORM_CHECKOUT_ACCOUNT_INVALID.getCode(), error.getCode());
        verify(checkoutMapper, never()).selectByNoAndMemberForUpdate(any(), any());
        verifyNoFormalizationWrites();
    }

    private PlatformCheckoutFormalizationService service(boolean enabled) {
        return new PlatformCheckoutFormalizationService(checkoutMapper, checkoutOrderMapper, allocationMapper,
                planMapper, planItemMapper, formalizationMapper, platformAccountApi, enabled);
    }

    private void stubDraftSnapshot() {
        when(checkoutMapper.selectByNoAndMemberForUpdate("pc202609211200001", 9001L)).thenReturn(checkout);
        when(checkoutOrderMapper.selectListByCheckout(10L, "pc202609211200001")).thenReturn(List.of());
        when(allocationMapper.selectListByCheckout(10L, "pc202609211200001")).thenReturn(List.of());
        when(planMapper.selectListByCheckout(10L, "pc202609211200001")).thenAnswer(ignored -> List.of(plan));
        when(planItemMapper.selectListByCheckout(10L, "pc202609211200001")).thenAnswer(ignored -> List.of(item));
    }

    private AtomicReference<PlatformCheckoutFormalizationDO> storeAttempt() {
        AtomicReference<PlatformCheckoutFormalizationDO> stored = new AtomicReference<>();
        when(formalizationMapper.selectByCheckoutAndKey(10L, "formalize-01"))
                .thenAnswer(ignored -> stored.get());
        doAnswer(invocation -> {
            PlatformCheckoutFormalizationDO value = invocation.getArgument(0);
            value.setId(40L);
            stored.set(value);
            return 1;
        }).when(formalizationMapper).insertBlockedOrGetId(any(PlatformCheckoutFormalizationDO.class));
        return stored;
    }

    private PlatformCheckoutFormalizationDO attempt(String snapshotDigest) {
        List<PlatformCheckoutFormalizationService.Blocker> blockers = List.of(
                PlatformCheckoutFormalizationService.Blocker.FEATURE_DISABLED,
                PlatformCheckoutFormalizationService.Blocker.LIVE_PRODUCT_REVALIDATION_UNAVAILABLE,
                PlatformCheckoutFormalizationService.Blocker.TARGET_MEMBER_MAPPING_UNAVAILABLE,
                PlatformCheckoutFormalizationService.Blocker.PROMOTION_COMMIT_UNAVAILABLE,
                PlatformCheckoutFormalizationService.Blocker.INVENTORY_RESERVATION_UNAVAILABLE,
                PlatformCheckoutFormalizationService.Blocker.AGGREGATE_PAYMENT_UNAVAILABLE,
                PlatformCheckoutFormalizationService.Blocker.ATOMIC_CHILD_ORDER_WRITER_UNAVAILABLE);
        String reason = blockers.stream().map(PlatformCheckoutFormalizationService.Blocker::getReason)
                .collect(java.util.stream.Collectors.joining("；"));
        return new PlatformCheckoutFormalizationDO().setId(40L).setCheckoutId(10L)
                .setCheckoutNo("pc202609211200001").setPlatformMemberId(9001L)
                .setIdempotencyKey("formalize-01").setSnapshotDigest(snapshotDigest)
                .setAttemptStatus(PlatformCheckoutFormalizationService.ATTEMPT_STATUS_BLOCKED)
                .setBlockerCodes(cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(
                        blockers.stream().map(Enum::name).toList()))
                .setBlockerReason(reason).setVersion(0L);
    }

    private void verifyNoFormalizationWrites() {
        verify(formalizationMapper, never()).insertBlockedOrGetId(any());
        verify(checkoutOrderMapper, never()).insert(any(PlatformCheckoutOrderDO.class));
        verify(allocationMapper, never()).insert(any(PlatformFundAllocationDO.class));
    }

    private static PlatformCheckoutDO checkout() {
        return new PlatformCheckoutDO().setId(10L).setCheckoutNo("pc202609211200001")
                .setIdempotencyKey("checkout-01").setRequestDigest("a".repeat(64))
                .setPlatformMemberId(9001L).setAddressId(99L).setAddressSourceTenantId(2009L)
                .setReceiverName("采购员").setReceiverMobile("13600000000").setReceiverAreaId(610100L)
                .setReceiverDetailAddress("陕西省西安市高新区 1 号")
                .setReceiverLongitude(new BigDecimal("108.953100"))
                .setReceiverLatitude(new BigDecimal("34.277800")).setReceiverCoordSystem("GCJ02")
                .setRemark("正式化边界测试").setCurrency("CNY")
                .setGoodsAmount(2400L).setFreightAmount(300L).setDiscountAmount(0L).setPayAmount(2700L)
                .setPaidAmount(0L).setRefundedAmount(0L).setPayStatus("NOT_PAYABLE")
                .setAggregateStatus("DRAFT_ONLY").setFailureReason("仅保存拆单规划")
                .setVersion(0L);
    }

    private static PlatformCheckoutPlanDO plan() {
        return new PlatformCheckoutPlanDO().setId(20L).setCheckoutId(10L)
                .setCheckoutNo("pc202609211200001").setPlanNo("pc202609211200001:2001:401")
                .setTenantId(2001L).setSellerEnterpriseId(301L).setShopId(401L)
                .setTenantName("科盾").setEnterpriseName("防腐企业").setShopName("防腐材料店")
                .setItemCount(1).setTotalQuantity(2).setGoodsAmount(2400L).setFreightAmount(300L)
                .setDiscountAmount(0L).setPayAmount(2700L).setPlanStatus("DRAFT_ONLY")
                .setBlockReason("尚未生成业务订单、支付单或库存占用").setVersion(0L);
    }

    private static PlatformCheckoutPlanItemDO item() {
        return new PlatformCheckoutPlanItemDO().setId(30L).setCheckoutId(10L)
                .setCheckoutNo("pc202609211200001").setPlanId(20L)
                .setPlanNo("pc202609211200001:2001:401").setCartItemId(1L)
                .setTenantId(2001L).setEnterpriseId(301L).setShopId(401L)
                .setSpuId(501L).setSkuId(601L).setSpuName("环氧防腐涂料")
                .setPropertiesJson("[]").setPicUrl("https://example.invalid/sku.png")
                .setQuantity(2).setUnitPrice(1200).setGoodsAmount(2400L);
    }
}
