package cn.iocoder.txgy.module.trade.service.checkout;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.member.api.platform.MemberPlatformAccountApi;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAddressRespDTO;
import cn.iocoder.txgy.module.product.api.sku.ProductPublicSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutPreviewReqVO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_CART_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_ADDRESS_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_DATA_INVALID;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class PlatformCheckoutServiceTest {

    private PlatformCartMapper cartMapper;
    private ProductPublicSkuApi productApi;
    private MemberPlatformAccountApi accountApi;
    private PlatformCheckoutMapper checkoutMapper;
    private PlatformCheckoutOrderMapper checkoutOrderMapper;
    private PlatformFundAllocationMapper allocationMapper;
    private PlatformCheckoutPlanMapper planMapper;
    private PlatformCheckoutPlanItemMapper planItemMapper;
    private TradeNoRedisDAO tradeNoRedisDAO;
    private PlatformCheckoutFreightService freightService;
    private PlatformCheckoutOrderService orderService;
    private PlatformCheckoutService service;

    @BeforeEach
    void setUp() {
        cartMapper = mock(PlatformCartMapper.class);
        productApi = mock(ProductPublicSkuApi.class);
        accountApi = mock(MemberPlatformAccountApi.class);
        checkoutMapper = mock(PlatformCheckoutMapper.class);
        checkoutOrderMapper = mock(PlatformCheckoutOrderMapper.class);
        allocationMapper = mock(PlatformFundAllocationMapper.class);
        planMapper = mock(PlatformCheckoutPlanMapper.class);
        planItemMapper = mock(PlatformCheckoutPlanItemMapper.class);
        tradeNoRedisDAO = mock(TradeNoRedisDAO.class);
        freightService = mock(PlatformCheckoutFreightService.class);
        orderService = mock(PlatformCheckoutOrderService.class);
        service = new PlatformCheckoutService(cartMapper, productApi, accountApi,
                checkoutMapper, checkoutOrderMapper, allocationMapper, planMapper, planItemMapper, tradeNoRedisDAO,
                freightService, orderService);
        when(orderService.formalize(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(freightService.quote(any(), any())).thenAnswer(invocation -> {
            Collection<PlatformCheckoutFreightService.FreightItem> items = invocation.getArgument(1);
            Map<PlatformCheckoutFreightService.ShopKey, PlatformCheckoutFreightService.FreightQuote> quotes =
                    new LinkedHashMap<>();
            items.forEach(item -> quotes.put(item.shopKey(),
                    PlatformCheckoutFreightService.FreightQuote.ready(0L)));
            return quotes;
        });
        when(tradeNoRedisDAO.generate(TradeNoRedisDAO.PLATFORM_CHECKOUT_NO_PREFIX))
                .thenReturn("pc202609211200001");
        when(accountApi.resolve(316L, 2009L)).thenReturn(CommonResult.success(
                new MemberPlatformAccountRespDTO().setPlatformAccountId(9001L)
                        .setLegacyMemberId(316L).setSourceTenantId(2009L)));
        when(accountApi.resolveAddress(316L, 2009L, 99L)).thenReturn(CommonResult.success(address()));
    }

    @Test
    void previewUsesServerPricesAndGroupsByTenantThenShop() {
        PlatformCartDO first = cart(1L, 2001L, 301L, 401L, 501L, 601L, 2, true);
        PlatformCartDO second = cart(2L, 2001L, 302L, 402L, 502L, 602L, 3, true);
        PlatformCartDO third = cart(3L, 2002L, 303L, 403L, 503L, 603L, 1, true);
        when(cartMapper.selectListByMember(9001L)).thenReturn(List.of(first, second, third));
        when(productApi.getOwnershipList(List.of(601L, 602L, 603L))).thenReturn(List.of(
                product(601L, 2001L, 301L, 401L, 501L, 1200, 100),
                product(602L, 2001L, 302L, 402L, 502L, 500, 100),
                product(603L, 2002L, 303L, 403L, 503L, 1000, 100)));

        var result = service.preview(316L, 2009L, new AppPlatformCheckoutPreviewReqVO().setAddressId(99L));

        assertEquals(9001L, result.getPlatformAccountId());
        assertEquals(4900L, result.getGoodsAmount());
        assertEquals(0L, result.getFreightAmount());
        assertEquals(4900L, result.getPayAmount());
        assertTrue(result.getFreightReady());
        assertEquals(3, result.getValidItemCount());
        assertEquals(6, result.getTotalQuantity());
        assertTrue(result.getValidationPassed());
        assertTrue(result.getSubmittable());
        assertEquals(2, result.getTenantGroups().size());
        assertEquals(99L, result.getAddress().getAddressId());
        assertEquals(2, result.getTenantGroups().getFirst().getShops().size());
        assertEquals(2400L, result.getTenantGroups().getFirst().getShops().getFirst().getGoodsAmount());
        assertTrue(result.getBlockingReasons().isEmpty());
    }

    @Test
    void previewAggregatesServerFreightByTenantAndShop() {
        PlatformCartDO first = cart(1L, 2001L, 301L, 401L, 501L, 601L, 2, true);
        PlatformCartDO second = cart(2L, 2002L, 302L, 402L, 502L, 602L, 1, true);
        when(cartMapper.selectListByMember(9001L)).thenReturn(List.of(first, second));
        when(productApi.getOwnershipList(List.of(601L, 602L))).thenReturn(List.of(
                product(601L, 2001L, 301L, 401L, 501L, 1200, 100),
                product(602L, 2002L, 302L, 402L, 502L, 500, 100)));
        doReturn(Map.of(
                new PlatformCheckoutFreightService.ShopKey(2001L, 401L),
                PlatformCheckoutFreightService.FreightQuote.ready(300L),
                new PlatformCheckoutFreightService.ShopKey(2002L, 402L),
                PlatformCheckoutFreightService.FreightQuote.ready(500L)))
                .when(freightService).quote(org.mockito.ArgumentMatchers.eq(610100L), any());

        var result = service.preview(316L, 2009L,
                new AppPlatformCheckoutPreviewReqVO().setAddressId(99L));

        assertTrue(result.getFreightReady());
        assertEquals(800L, result.getFreightAmount());
        assertEquals(3700L, result.getPayAmount());
        assertEquals(300L, result.getTenantGroups().getFirst().getFreightAmount());
        assertEquals(500L, result.getTenantGroups().get(1).getShops().getFirst().getFreightAmount());
    }

    @Test
    void previewDoesNotRepresentUnknownFreightAsZero() {
        PlatformCartDO cart = cart(1L, 2001L, 301L, 401L, 501L, 601L, 1, true);
        when(cartMapper.selectListByMember(9001L)).thenReturn(List.of(cart));
        when(productApi.getOwnershipList(List.of(601L))).thenReturn(List.of(
                product(601L, 2001L, 301L, 401L, 501L, 1200, 100)));
        doReturn(Map.of(
                new PlatformCheckoutFreightService.ShopKey(2001L, 401L),
                PlatformCheckoutFreightService.FreightQuote.failed("商品配送模板不完整")))
                .when(freightService).quote(org.mockito.ArgumentMatchers.eq(610100L), any());

        var result = service.preview(316L, 2009L,
                new AppPlatformCheckoutPreviewReqVO().setAddressId(99L));

        assertFalse(result.getFreightReady());
        assertEquals(null, result.getFreightAmount());
        assertEquals(null, result.getPayAmount());
        assertFalse(result.getValidationPassed());
        assertEquals("商品配送模板不完整",
                result.getTenantGroups().getFirst().getShops().getFirst().getFreightFailureReason());
    }

    @Test
    void pageUsesResolvedPlatformAccountAndReturnsOnlyHistorySummary() {
        PlatformCheckoutDO draft = new PlatformCheckoutDO().setId(10L)
                .setCheckoutNo("PC202609210001").setPlatformMemberId(9001L).setCurrency("CNY")
                .setGoodsAmount(2400L).setFreightAmount(0L).setDiscountAmount(0L).setPayAmount(2400L)
                .setPaidAmount(0L).setRefundedAmount(0L).setPayStatus("NOT_PAYABLE")
                .setAggregateStatus("DRAFT_ONLY").setFailureReason("等待聚合支付接通")
                .setReceiverName("不应出现在列表").setReceiverMobile("13600000000")
                .setRemark("不应出现在列表");
        draft.setCreateTime(LocalDateTime.of(2026, 9, 21, 12, 0));
        PageParam pageParam = new PageParam().setPageNo(1).setPageSize(10);
        when(checkoutMapper.selectPageByMember(pageParam, 9001L))
                .thenReturn(new PageResult<>(List.of(draft), 1L));

        var result = service.page(316L, 2009L, pageParam);

        assertEquals(1L, result.getTotal());
        assertEquals("PC202609210001", result.getList().getFirst().getCheckoutNo());
        assertTrue(result.getList().getFirst().getPlanningOnly());
        assertFalse(result.getList().getFirst().getPayable());
        assertEquals("等待聚合支付接通", result.getList().getFirst().getFailureReason());
        verify(checkoutMapper).selectPageByMember(pageParam, 9001L);
    }

    @Test
    void pageDerivesPartiallyDeliveredFromCurrentChildOrderStatuses() {
        PlatformCheckoutDO checkout = completedCheckout(0L).setAggregateStatus("ALL_UNDELIVERED");
        PageParam pageParam = new PageParam().setPageNo(1).setPageSize(10);
        when(checkoutMapper.selectPageByMember(pageParam, 9001L))
                .thenReturn(new PageResult<>(List.of(checkout), 1L));
        when(checkoutOrderMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo()))
                .thenReturn(List.of(
                        childOrder(11L, 21L, 2001L, 401L, 1000L, 0L).setOrderStatus(20),
                        childOrder(12L, 22L, 2002L, 402L, 2000L, 0L).setOrderStatus(10)));

        var result = service.page(316L, 2009L, pageParam);

        assertEquals("PARTIALLY_DELIVERED", result.getList().getFirst().getAggregateStatus());
    }

    @Test
    void previewRejectsPublicTenantAndChangedOwnershipWithoutTrustingCartSnapshot() {
        PlatformCartDO publicTenant = cart(1L, 3000L, 301L, 401L, 501L, 601L, 1, true);
        PlatformCartDO changed = cart(2L, 2001L, 302L, 402L, 502L, 602L, 1, true);
        when(cartMapper.selectListByMember(9001L)).thenReturn(List.of(publicTenant, changed));
        when(productApi.getOwnershipList(any())).thenReturn(List.of(
                product(601L, 3000L, 301L, 401L, 501L, 1200, 100),
                product(602L, 2001L, 999L, 402L, 502L, 500, 100)));

        var result = service.preview(316L, 2009L, new AppPlatformCheckoutPreviewReqVO().setAddressId(99L));

        assertFalse(result.getValidationPassed());
        assertEquals(0, result.getValidItemCount());
        assertEquals(0L, result.getGoodsAmount());
        assertEquals(2, result.getFailedItems().size());
        var reasonByCartId = result.getFailedItems().stream().collect(
                java.util.stream.Collectors.toMap(item -> item.getCartItemId(), item -> item.getReason()));
        assertEquals("商品所属租户未开放", reasonByCartId.get(1L));
        assertEquals("商品经营归属已变更", reasonByCartId.get(2L));
    }

    @Test
    void submitPersistsAtomicNonPayablePlanWithoutCreatingOrderOrClearingCart() {
        PlatformCartDO cart = cart(1L, 2001L, 301L, 401L, 501L, 601L, 2, true);
        AtomicReference<PlatformCheckoutDO> stored = new AtomicReference<>();
        when(checkoutMapper.selectByIdempotencyKey(9001L, "checkout-20260921-01"))
                .thenAnswer(ignored -> stored.get());
        doAnswer(invocation -> {
            PlatformCheckoutDO value = invocation.getArgument(0);
            value.setId(10L).setCreateTime(LocalDateTime.now());
            stored.set(value);
            return 1;
        }).when(checkoutMapper).insertDraftOrGetId(any());
        AtomicLong planId = new AtomicLong(20L);
        List<PlatformCheckoutPlanDO> storedPlans = new ArrayList<>();
        doAnswer(invocation -> {
            PlatformCheckoutPlanDO value = invocation.getArgument(0);
            value.setId(planId.getAndIncrement());
            storedPlans.add(value);
            return 1;
        }).when(planMapper).insert(any(PlatformCheckoutPlanDO.class));
        AtomicLong itemId = new AtomicLong(30L);
        List<PlatformCheckoutPlanItemDO> storedItems = new ArrayList<>();
        doAnswer(invocation -> {
            PlatformCheckoutPlanItemDO value = invocation.getArgument(0);
            value.setId(itemId.getAndIncrement());
            storedItems.add(value);
            return 1;
        }).when(planItemMapper).insert(any(PlatformCheckoutPlanItemDO.class));
        when(planMapper.selectListByCheckout(10L, "pc202609211200001")).thenAnswer(ignored -> storedPlans);
        when(planItemMapper.selectListByCheckout(10L, "pc202609211200001")).thenAnswer(ignored -> storedItems);
        when(checkoutOrderMapper.selectListByCheckout(10L, "pc202609211200001")).thenReturn(List.of());
        when(allocationMapper.selectListByCheckout(10L, "pc202609211200001")).thenReturn(List.of());
        when(cartMapper.selectListByIdsAndMember(java.util.Set.of(1L), 9001L)).thenReturn(List.of(cart));
        when(productApi.getOwnershipList(List.of(601L))).thenReturn(List.of(
                product(601L, 2001L, 301L, 401L, 501L, 1200, 100)));
        doReturn(Map.of(
                new PlatformCheckoutFreightService.ShopKey(2001L, 401L),
                PlatformCheckoutFreightService.FreightQuote.ready(350L)))
                .when(freightService).quote(org.mockito.ArgumentMatchers.eq(610100L), any());
        AppPlatformCheckoutSubmitReqVO request = new AppPlatformCheckoutSubmitReqVO()
                .setCartItemIds(List.of(1L)).setAddressId(99L).setIdempotencyKey("checkout-20260921-01")
                .setRemark("  送货前联系  ");

        var result = service.submit(316L, 2009L, request);

        assertEquals("pc202609211200001", result.getCheckoutNo());
        assertEquals(2750L, result.getPayAmount());
        assertEquals(350L, stored.get().getFreightAmount());
        assertEquals("NOT_PAYABLE", result.getPayStatus());
        assertEquals("DRAFT_ONLY", result.getAggregateStatus());
        assertEquals("送货前联系", result.getRemark());
        assertTrue(result.getPlanningOnly());
        assertFalse(result.getPayable());
        assertEquals(1, result.getChildPlans().size());
        assertEquals(1, result.getChildPlans().getFirst().getItems().size());
        assertEquals(2, result.getChildPlans().getFirst().getTotalQuantity());
        assertEquals(350L, result.getChildPlans().getFirst().getFreightAmount());
        assertEquals(2400L, result.getChildPlans().getFirst().getItems().getFirst().getGoodsAmount());
        var replay = service.submit(316L, 2009L, request);
        assertEquals(result.getCheckoutNo(), replay.getCheckoutNo());
        assertEquals(1, replay.getChildPlans().size());
        verify(checkoutMapper, times(1)).insertDraftOrGetId(any(PlatformCheckoutDO.class));
        verify(planMapper, times(1)).insert(any(PlatformCheckoutPlanDO.class));
        verify(planItemMapper, times(1)).insert(any(PlatformCheckoutPlanItemDO.class));
        verify(checkoutOrderMapper, never()).insert(any(PlatformCheckoutOrderDO.class));
        verify(allocationMapper, never()).insert(any(PlatformFundAllocationDO.class));
        verify(cartMapper, never()).hardDeleteByIdsAndMember(any(), any());
    }

    @Test
    void submitRejectsUnknownFreightWithoutWritingDraft() {
        PlatformCartDO cart = cart(1L, 2001L, 301L, 401L, 501L, 601L, 1, true);
        when(cartMapper.selectListByIdsAndMember(java.util.Set.of(1L), 9001L)).thenReturn(List.of(cart));
        when(productApi.getOwnershipList(List.of(601L))).thenReturn(List.of(
                product(601L, 2001L, 301L, 401L, 501L, 1200, 100)));
        doReturn(Map.of(new PlatformCheckoutFreightService.ShopKey(2001L, 401L),
                PlatformCheckoutFreightService.FreightQuote.failed("商品配送模板不完整")))
                .when(freightService).quote(org.mockito.ArgumentMatchers.eq(610100L), any());

        ServiceException error = assertThrows(ServiceException.class, () -> service.submit(316L, 2009L,
                new AppPlatformCheckoutSubmitReqVO().setCartItemIds(List.of(1L)).setAddressId(99L)
                        .setIdempotencyKey("checkout-freight-unready")));

        assertEquals(PLATFORM_CHECKOUT_CART_INVALID.getCode(), error.getCode());
        verify(checkoutMapper, never()).insertDraftOrGetId(any(PlatformCheckoutDO.class));
        verify(planMapper, never()).insert(any(PlatformCheckoutPlanDO.class));
        verify(planItemMapper, never()).insert(any(PlatformCheckoutPlanItemDO.class));
        verify(checkoutOrderMapper, never()).insert(any(PlatformCheckoutOrderDO.class));
        verify(allocationMapper, never()).insert(any(PlatformFundAllocationDO.class));
        verify(cartMapper, never()).hardDeleteByIdsAndMember(any(), any());
    }

    @Test
    void submitPropagatesSnapshotFailureAndIsConfiguredForRollback() throws Exception {
        PlatformCartDO cart = cart(1L, 2001L, 301L, 401L, 501L, 601L, 1, true);
        AtomicReference<PlatformCheckoutDO> stored = new AtomicReference<>();
        when(checkoutMapper.selectByIdempotencyKey(9001L, "checkout-rollback-01"))
                .thenAnswer(ignored -> stored.get());
        doAnswer(invocation -> {
            PlatformCheckoutDO value = invocation.getArgument(0);
            value.setId(10L).setCreateTime(LocalDateTime.now());
            stored.set(value);
            return 1;
        }).when(checkoutMapper).insertDraftOrGetId(any());
        doAnswer(invocation -> {
            ((PlatformCheckoutPlanDO) invocation.getArgument(0)).setId(20L);
            return 1;
        }).when(planMapper).insert(any(PlatformCheckoutPlanDO.class));
        when(planItemMapper.insert(any(PlatformCheckoutPlanItemDO.class)))
                .thenThrow(new IllegalStateException("snapshot insert failed"));
        when(cartMapper.selectListByIdsAndMember(java.util.Set.of(1L), 9001L)).thenReturn(List.of(cart));
        when(productApi.getOwnershipList(List.of(601L))).thenReturn(List.of(
                product(601L, 2001L, 301L, 401L, 501L, 1200, 100)));

        assertThrows(IllegalStateException.class, () -> service.submit(316L, 2009L,
                new AppPlatformCheckoutSubmitReqVO().setCartItemIds(List.of(1L)).setAddressId(99L)
                        .setIdempotencyKey("checkout-rollback-01")));

        Transactional transactional = PlatformCheckoutService.class
                .getMethod("submit", Long.class, Long.class, AppPlatformCheckoutSubmitReqVO.class)
                .getAnnotation(Transactional.class);
        assertFalse(transactional.readOnly());
        assertTrue(List.of(transactional.rollbackFor()).contains(Exception.class));
        verify(checkoutOrderMapper, never()).insert(any(PlatformCheckoutOrderDO.class));
        verify(allocationMapper, never()).insert(any(PlatformFundAllocationDO.class));
        verify(cartMapper, never()).hardDeleteByIdsAndMember(any(), any());
    }

    @Test
    void submitReturnsSameValidatedCheckoutForExistingIdempotencyKey() {
        PlatformCheckoutDO checkout = new PlatformCheckoutDO().setId(10L).setCheckoutNo("PC202609210001")
                .setIdempotencyKey("checkout-20260921-01").setPlatformMemberId(9001L).setCurrency("CNY")
                .setRequestDigest(PlatformCheckoutService.requestDigest(9001L, List.of(1L), address(), null))
                .setAddressId(99L).setAddressSourceTenantId(2009L).setReceiverName("采购员")
                .setReceiverMobile("13600000000").setReceiverAreaId(610100L)
                .setReceiverDetailAddress("高新区 1 号").setReceiverLongitude(new BigDecimal("108.900000"))
                .setReceiverLatitude(new BigDecimal("34.200000")).setReceiverCoordSystem("GCJ02")
                .setGoodsAmount(2400L).setFreightAmount(100L).setDiscountAmount(0L).setPayAmount(2500L)
                .setPaidAmount(0L).setRefundedAmount(0L).setPayStatus("UNPAID")
                .setAggregateStatus("CREATED").setVersion(0L);
        checkout.setCreateTime(LocalDateTime.now());
        PlatformCheckoutOrderDO order = new PlatformCheckoutOrderDO().setId(11L).setCheckoutId(10L)
                .setCheckoutNo("PC202609210001").setOrderId(21L).setOrderNo("O202609210001")
                .setTenantId(2001L).setBuyerMemberId(316L).setSellerEnterpriseId(301L).setShopId(401L)
                .setGoodsAmount(2400L).setFreightAmount(100L).setDiscountAmount(0L).setPayAmount(2500L)
                .setRefundedAmount(0L).setOrderStatus(0).setAfterSaleStatus(0);
        PlatformFundAllocationDO allocation = new PlatformFundAllocationDO().setId(12L).setCheckoutId(10L)
                .setCheckoutNo("PC202609210001").setOrderId(21L).setTenantId(2001L).setShopId(401L)
                .setAllocatedAmount(2500L).setRefundedAmount(0L).setAllocationStatus("PENDING");
        when(checkoutMapper.selectByIdempotencyKey(9001L, "checkout-20260921-01")).thenReturn(checkout);
        when(checkoutOrderMapper.selectListByCheckout(10L, "PC202609210001")).thenReturn(List.of(order));
        when(allocationMapper.selectListByCheckout(10L, "PC202609210001")).thenReturn(List.of(allocation));
        when(planMapper.selectListByCheckout(10L, "PC202609210001")).thenReturn(List.of());
        when(planItemMapper.selectListByCheckout(10L, "PC202609210001")).thenReturn(List.of());
        AppPlatformCheckoutSubmitReqVO request = new AppPlatformCheckoutSubmitReqVO()
                .setCartItemIds(List.of(1L)).setAddressId(99L).setIdempotencyKey("checkout-20260921-01");

        var result = service.submit(316L, 2009L, request);

        assertEquals("PC202609210001", result.getCheckoutNo());
        assertEquals(2500L, result.getPayAmount());
        assertEquals(99L, result.getAddress().getAddressId());
        assertEquals("ALL_UNPAID", result.getAggregateStatus());
        assertEquals(316L, result.getChildOrders().getFirst().getBuyerMemberId());
        assertEquals(1, result.getChildOrders().size());
        assertEquals(1, result.getFundAllocations().size());
        assertFalse(result.getPayable());
        verify(cartMapper, never()).selectListByMember(any());
        verify(productApi, never()).getOwnershipList(any());
    }

    @Test
    void getAcceptsRefundWhenCheckoutOrdersAndAllocationsAreBalanced() {
        PlatformCheckoutDO checkout = completedCheckout(300L);
        List<PlatformCheckoutOrderDO> orders = List.of(
                childOrder(11L, 21L, 2001L, 401L, 1000L, 100L),
                childOrder(12L, 22L, 2002L, 402L, 2000L, 200L));
        List<PlatformFundAllocationDO> allocations = List.of(
                allocation(31L, 21L, 2001L, 401L, 1000L, 100L),
                allocation(32L, 22L, 2002L, 402L, 2000L, 200L));
        stubCompletedCheckout(checkout, orders, allocations);

        var result = service.get(316L, 2009L, checkout.getCheckoutNo());

        assertEquals(300L, result.getRefundedAmount());
        assertEquals(300L, result.getChildOrders().stream()
                .mapToLong(order -> order.getRefundedAmount()).sum());
        assertEquals(300L, result.getFundAllocations().stream()
                .mapToLong(allocation -> allocation.getRefundedAmount()).sum());
        assertEquals("PARTIALLY_REFUNDED", result.getAggregateStatus());
        assertFalse(result.getPayable());
    }

    @Test
    void getRejectsRefundWhenCheckoutAndChildOrderTotalsDiffer() {
        PlatformCheckoutDO checkout = completedCheckout(300L);
        List<PlatformCheckoutOrderDO> orders = List.of(
                childOrder(11L, 21L, 2001L, 401L, 1000L, 100L),
                childOrder(12L, 22L, 2002L, 402L, 2000L, 199L));
        List<PlatformFundAllocationDO> allocations = List.of(
                allocation(31L, 21L, 2001L, 401L, 1000L, 100L),
                allocation(32L, 22L, 2002L, 402L, 2000L, 199L));
        stubCompletedCheckout(checkout, orders, allocations);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.get(316L, 2009L, checkout.getCheckoutNo()));

        assertEquals(PLATFORM_CHECKOUT_DATA_INVALID.getCode(), error.getCode());
    }

    @Test
    void getRejectsRefundWhenChildOrderAndAllocationTotalsDiffer() {
        PlatformCheckoutDO checkout = completedCheckout(300L);
        List<PlatformCheckoutOrderDO> orders = List.of(
                childOrder(11L, 21L, 2001L, 401L, 1000L, 100L),
                childOrder(12L, 22L, 2002L, 402L, 2000L, 200L));
        List<PlatformFundAllocationDO> allocations = List.of(
                allocation(31L, 21L, 2001L, 401L, 1000L, 100L),
                allocation(32L, 22L, 2002L, 402L, 2000L, 199L));
        stubCompletedCheckout(checkout, orders, allocations);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.get(316L, 2009L, checkout.getCheckoutNo()));

        assertEquals(PLATFORM_CHECKOUT_DATA_INVALID.getCode(), error.getCode());
    }

    @Test
    void previewRejectsForeignCartIdsBeforeReadingProduct() {
        when(cartMapper.selectListByIdsAndMember(java.util.Set.of(1L, 2L), 9001L))
                .thenReturn(List.of(cart(1L, 2001L, 301L, 401L, 501L, 601L, 1, true)));
        AppPlatformCheckoutPreviewReqVO request = new AppPlatformCheckoutPreviewReqVO()
                .setCartItemIds(List.of(1L, 2L)).setAddressId(99L);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.preview(316L, 2009L, request));

        assertEquals(PLATFORM_CHECKOUT_CART_INVALID.getCode(), error.getCode());
        verify(productApi, never()).getOwnershipList(any());
    }

    @Test
    void submitRejectsSameIdempotencyKeyWhenCanonicalRequestDiffers() {
        PlatformCheckoutDO checkout = new PlatformCheckoutDO()
                .setId(10L).setCheckoutNo("PC202609210001")
                .setIdempotencyKey("checkout-20260921-01").setPlatformMemberId(9001L)
                .setRequestDigest(PlatformCheckoutService.requestDigest(9001L, List.of(2L), address(), null));
        when(checkoutMapper.selectByIdempotencyKey(9001L, "checkout-20260921-01")).thenReturn(checkout);
        AppPlatformCheckoutSubmitReqVO request = new AppPlatformCheckoutSubmitReqVO()
                .setCartItemIds(List.of(1L)).setAddressId(99L).setIdempotencyKey("checkout-20260921-01");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submit(316L, 2009L, request));

        assertEquals(PLATFORM_CHECKOUT_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
        verify(cartMapper, never()).selectListByIdsAndMember(any(), any());
    }

    @Test
    void previewRejectsAddressThatIsNotBoundToResolvedPlatformAccount() {
        when(accountApi.resolveAddress(316L, 2009L, 99L)).thenReturn(CommonResult.success(
                address().setPlatformAccountId(9999L)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.preview(316L, 2009L,
                        new AppPlatformCheckoutPreviewReqVO().setAddressId(99L)));

        assertEquals(PLATFORM_CHECKOUT_ADDRESS_INVALID.getCode(), error.getCode());
        verify(cartMapper, never()).selectListByMember(any());
    }

    @Test
    void previewAcceptsAddressOwnedByAnotherReadyLegacyLinkOfSamePlatformAccount() {
        when(accountApi.resolveAddress(316L, 2009L, 99L)).thenReturn(CommonResult.success(
                address().setLegacyMemberId(400L).setSourceTenantId(2001L)));
        PlatformCartDO cart = cart(1L, 2001L, 301L, 401L, 501L, 601L, 1, true);
        when(cartMapper.selectListByMember(9001L)).thenReturn(List.of(cart));
        when(productApi.getOwnershipList(List.of(601L))).thenReturn(List.of(
                product(601L, 2001L, 301L, 401L, 501L, 1200, 100)));

        var result = service.preview(316L, 2009L,
                new AppPlatformCheckoutPreviewReqVO().setAddressId(99L));

        assertEquals(2001L, result.getAddress().getSourceTenantId());
        assertTrue(result.getValidationPassed());
    }

    @Test
    void requestDigestIsStableForCartOrderAndChangesWithAddressSnapshot() {
        String first = PlatformCheckoutService.requestDigest(9001L, List.of(2L, 1L), address(), "  留言  ");
        String reordered = PlatformCheckoutService.requestDigest(9001L, List.of(1L, 2L), address(), "留言");
        String changed = PlatformCheckoutService.requestDigest(9001L, List.of(1L, 2L),
                address().setDetailAddress("高新区 2 号"), "留言");

        assertEquals(first, reordered);
        assertEquals(64, first.length());
        assertFalse(first.equals(changed));
    }

    private static PlatformCartDO cart(Long id, Long tenantId, Long enterpriseId, Long shopId,
                                       Long spuId, Long skuId, int quantity, boolean selected) {
        return new PlatformCartDO().setId(id).setPlatformMemberId(9001L).setTenantId(tenantId)
                .setEnterpriseId(enterpriseId).setShopId(shopId).setSpuId(spuId).setSkuId(skuId)
                .setQuantity(quantity).setSelected(selected);
    }

    private static ProductPublicSkuRespDTO product(Long skuId, Long tenantId, Long enterpriseId,
                                                    Long shopId, Long spuId, int price, int stock) {
        return new ProductPublicSkuRespDTO().setSkuId(skuId).setTenantId(tenantId)
                .setTenantName("租户 " + tenantId).setEnterpriseId(enterpriseId)
                .setEnterpriseName("企业 " + enterpriseId).setShopId(shopId)
                .setShopName("店铺 " + shopId).setSpuId(spuId).setSpuName("商品 " + spuId)
                .setPrice(price).setStock(stock).setSaleable(true).setProperties(List.of());
    }

    private void stubCompletedCheckout(PlatformCheckoutDO checkout,
                                       List<PlatformCheckoutOrderDO> orders,
                                       List<PlatformFundAllocationDO> allocations) {
        when(checkoutMapper.selectByNoAndMember(checkout.getCheckoutNo(), 9001L)).thenReturn(checkout);
        when(checkoutOrderMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo()))
                .thenReturn(orders);
        when(allocationMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo()))
                .thenReturn(allocations);
        when(planMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo())).thenReturn(List.of());
        when(planItemMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo())).thenReturn(List.of());
    }

    private static PlatformCheckoutDO completedCheckout(Long refundedAmount) {
        PlatformCheckoutDO checkout = new PlatformCheckoutDO().setId(10L).setCheckoutNo("PC202609210002")
                .setIdempotencyKey("checkout-20260921-refund").setPlatformMemberId(9001L).setCurrency("CNY")
                .setRequestDigest("0".repeat(64)).setAddressId(99L).setAddressSourceTenantId(2009L)
                .setReceiverName("采购员").setReceiverMobile("13600000000").setReceiverAreaId(610100L)
                .setReceiverDetailAddress("高新区 1 号").setGoodsAmount(3000L).setFreightAmount(0L)
                .setDiscountAmount(0L).setPayAmount(3000L).setPaidAmount(3000L)
                .setRefundedAmount(refundedAmount).setPayStatus("PAID").setAggregateStatus("CREATED")
                .setVersion(0L);
        checkout.setCreateTime(LocalDateTime.now());
        return checkout;
    }

    private static PlatformCheckoutOrderDO childOrder(Long id, Long orderId, Long tenantId, Long shopId,
                                                       Long payAmount, Long refundedAmount) {
        return new PlatformCheckoutOrderDO().setId(id).setCheckoutId(10L)
                .setCheckoutNo("PC202609210002").setOrderId(orderId).setOrderNo("O" + orderId)
                .setTenantId(tenantId).setBuyerMemberId(316L).setSellerEnterpriseId(tenantId + 100L)
                .setShopId(shopId).setGoodsAmount(payAmount).setFreightAmount(0L).setDiscountAmount(0L)
                .setPayAmount(payAmount).setRefundedAmount(refundedAmount).setOrderStatus(20).setAfterSaleStatus(0);
    }

    private static PlatformFundAllocationDO allocation(Long id, Long orderId, Long tenantId, Long shopId,
                                                        Long allocatedAmount, Long refundedAmount) {
        return new PlatformFundAllocationDO().setId(id).setCheckoutId(10L)
                .setCheckoutNo("PC202609210002").setOrderId(orderId).setTenantId(tenantId).setShopId(shopId)
                .setAllocatedAmount(allocatedAmount).setRefundedAmount(refundedAmount)
                .setAllocationStatus("ALLOCATED");
    }

    private static MemberPlatformAddressRespDTO address() {
        return new MemberPlatformAddressRespDTO().setPlatformAccountId(9001L)
                .setLegacyMemberId(316L).setSourceTenantId(2009L).setAddressId(99L)
                .setName("采购员").setMobile("13600000000").setAreaId(610100L)
                .setDetailAddress("高新区 1 号").setLongitude(new BigDecimal("108.900000"))
                .setLatitude(new BigDecimal("34.200000")).setCoordSystem("GCJ02");
    }

}
