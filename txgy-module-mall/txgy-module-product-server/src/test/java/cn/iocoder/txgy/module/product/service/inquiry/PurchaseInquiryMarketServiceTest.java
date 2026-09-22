package cn.iocoder.txgy.module.product.service.inquiry;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryQuoteSaveReqVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryQuoteMessageSendReqVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryPublicDetailRespVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryPublicSummaryRespVO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.EnterpriseApplicationDO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.*;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.*;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.framework.web.LegacyMemberSalesProperties;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import cn.iocoder.txgy.module.product.service.audit.ProductBusinessOperationAuditService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseInquiryMarketServiceTest {

    @Mock private InquiryMapper inquiryMapper;
    @Mock private InquiryItemMapper inquiryItemMapper;
    @Mock private InquiryQuoteMapper quoteMapper;
    @Mock private InquiryQuoteItemMapper quoteItemMapper;
    @Mock private InquiryQuoteMessageMapper messageMapper;
    @Mock private ProductEnterpriseMapper enterpriseMapper;
    @Mock private ProductShopMapper shopMapper;
    @Mock private ProductSkuMapper skuMapper;
    @Mock private ProductSpuMapper spuMapper;
    @Mock private EnterpriseMembershipService membership;
    @Mock private ProductAdminBusinessScopeService adminScope;
    @Mock private ProductBusinessOperationAuditService operationAuditService;
    @Mock private InquiryInvitedShopMapper invitedShopMapper;
    @Mock private InquiryCrossTenantMapper crossTenantMapper;
    @Mock private LegacyMemberSalesProperties legacyMemberSalesProperties;
    @InjectMocks private PurchaseInquiryMarketService service;

    @BeforeEach
    void defaultBuyerIdentity() {
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        // App 买家与后台供应商已拆分；只有 buyer 身份可进入采购侧报价视图。
        lenient().when(membership.mine(7L)).thenReturn(buyer(100L));
        lenient().when(membership.requireBuyerHistory(7L)).thenReturn(buyer(100L));
        lenient().when(membership.requireSupplierHistory(8L)).thenReturn(supplier(200L));
        lenient().when(membership.requireSupplierShopHistory(8L, 200L, 20L))
                .thenReturn(supplier(200L));
        // 既有测试覆盖旧供应链路本身；默认关闭的兼容边界由独立用例验证。
        lenient().when(legacyMemberSalesProperties.isEnabled()).thenReturn(true);
        // 既有报价用例默认代表经营类目匹配；类目越权由独立用例覆盖。
        lenient().when(spuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        // 既有报价用例默认代表逐项品类与配送范围均匹配；变更后失配由独立用例覆盖。
        lenient().when(crossTenantMapper.countEligibleQuoteSkuForItem(anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), anyLong(), anyBoolean())).thenReturn(1L);
    }

    @AfterEach
    void clearTenantContext() {
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.clear();
    }

    @Test
    void publicDeliveryAreaNeverReturnsStreetAddress() {
        assertEquals("浙江省", PurchaseInquiryMarketService.maskDeliveryArea("浙江省杭州市西湖区文三路88号"));
        assertEquals("北京市", PurchaseInquiryMarketService.maskDeliveryArea("北京市朝阳区建国路1号"));
        assertEquals("成交后可见", PurchaseInquiryMarketService.maskDeliveryArea("杭州市西湖区文三路88号"));
        assertEquals("成交后可见", PurchaseInquiryMarketService.maskDeliveryArea("测试市建设路1号"));
        assertEquals("成交后可见", PurchaseInquiryMarketService.maskDeliveryArea("文三路88号3单元"));
        assertEquals("成交后可见", PurchaseInquiryMarketService.maskDeliveryArea("13800138000市建设路1号"));
    }

    @Test
    void obfuscatedContactFormatsAreSanitized() {
        List<String> values = List.of("138.0013.8000", "一三八〇〇一三八〇〇〇",
                "a @ b . com", "V信 abc123", "1\u200B3800138000");
        for (String value : values) {
            String sanitized = PurchaseInquiryMarketService.sanitizePublicText(value);
            assertNotEquals(value, sanitized, value);
            assertTrue(sanitized.contains("[联系方式已隐藏]"), value);
        }
    }

    @Test
    void publicSummaryTypeCannotExposePrivateOwnerFields() {
        Set<String> fields = Arrays.stream(InquiryPublicSummaryRespVO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
        assertTrue(Set.of("contactName", "contactMobile", "address", "memberId").stream()
                .noneMatch(fields::contains));
    }

    @Test
    void publicGetMasksContactsFromEveryPublicUserInputField() {
        long future = System.currentTimeMillis() + 86_400_000L;
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1); inquiry.setDeadline(future);
        inquiry.setNo("XJ-PUBLIC-10");
        inquiry.setVisibility("PLATFORM_PUBLIC");
        inquiry.setTitle("钢材采购 13800138000 sales@example.com");
        inquiry.setRemark("公开说明请联系 １ ３ ５ ０ ０ １ ３ ５ ０ ０ ０");
        inquiry.setAddress("浙江省杭州市西湖区文三路88号");
        InquiryItemDO requested = new InquiryItemDO();
        requested.setId(11L); requested.setInquiryId(10L);
        requested.setName("螺纹钢 13900139000"); requested.setCode("QQ: 1 2 3 4 5 6 7 8");
        requested.setCategory("资料 https://attacker.example/contact"); requested.setSpecification("电话010-87654321");
        requested.setUnit("吨 13600136000");
        requested.setRemark("行备注 微信: buyer_123");
        requested.setPicUrl("https://attacker.example/qr-code.png");
        ProductEnterpriseDO enterprise = approvedEnterprise(100L);
        enterprise.setName("采购企业 021-12345678");
        when(crossTenantMapper.selectPlatformPublicInquiry(10L))
                .thenReturn(opportunityRef(10L, 1L, "PLATFORM_PUBLIC"));
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(requested));
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise);
        when(quoteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        InquiryPublicDetailRespVO response = service.publicGet(10L);

        String publicText = response.getOrder().getTitle() + response.getOrder().getEnterpriseName()
                + response.getOrder().getItemSummary() + response.getRemark() + response.getItems().getFirst().getName()
                + response.getItems().getFirst().getCode() + response.getItems().getFirst().getCategory()
                + response.getItems().getFirst().getSpecification() + response.getItems().getFirst().getUnit()
                + response.getItems().getFirst().getRemark();
        assertFalse(publicText.contains("13800138000"));
        assertFalse(publicText.contains("13900139000"));
        assertFalse(publicText.contains("sales@example.com"));
        assertFalse(publicText.contains("buyer@example.com"));
        assertFalse(publicText.contains("1 2 3 4 5 6 7 8"));
        assertFalse(publicText.contains("010-87654321"));
        assertFalse(publicText.contains("13600136000"));
        assertFalse(publicText.contains("１ ３ ５ ０ ０ １ ３ ５ ０ ０ ０"));
        assertFalse(publicText.contains("buyer_123"));
        assertFalse(publicText.contains("attacker.example"));
        assertTrue(publicText.contains("[联系方式已隐藏]"));
        assertNull(response.getRemark());
        assertNull(response.getItems().getFirst().getRemark());
        assertNull(response.getItems().getFirst().getPicUrl());
    }

    @Test
    void closedInquiryRemainsAvailableAsReadOnlyPublicArchive() {
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(3);
        inquiry.setVisibility("PLATFORM_PUBLIC"); inquiry.setTitle("已关闭采购");
        inquiry.setNo("XJ-ARCHIVE-10");
        ProductEnterpriseDO enterprise = approvedEnterprise(100L); enterprise.setName("采购企业");
        when(crossTenantMapper.selectPlatformPublicInquiry(10L))
                .thenReturn(opportunityRef(10L, 1L, "PLATFORM_PUBLIC"));
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise);
        when(quoteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        InquiryPublicDetailRespVO response = service.publicGet(10L);

        assertEquals(3, response.getOrder().getStatus());
        assertFalse(response.getOrder().getCanQuote());
    }

    @Test
    void publicGetRejectsInquiryAfterBuyerEnterpriseQualificationIsRevoked() {
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        inquiry.setVisibility("PLATFORM_PUBLIC");
        inquiry.setNo("XJ-PUBLIC-10");
        ProductEnterpriseDO revoked = approvedEnterprise(100L); revoked.setAuditStatus(0);
        when(crossTenantMapper.selectPlatformPublicInquiry(10L))
                .thenReturn(opportunityRef(10L, 1L, "PLATFORM_PUBLIC"));
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(enterpriseMapper.selectById(100L)).thenReturn(revoked);

        assertThrows(ServiceException.class, () -> service.publicGet(10L));
    }

    @Test
    void closedDraftNeverBecomesPublicWithoutPublicationNumber() {
        assertThrows(ServiceException.class, () -> service.publicGet(10L));

        var page = service.publicPage(new cn.iocoder.txgy.framework.common.pojo.PageParam(), "ended", null);
        assertTrue(page.getList().isEmpty());
        assertEquals(0L, page.getTotal());
        verify(inquiryMapper, never()).selectById(10L);
        verifyNoInteractions(inquiryItemMapper, enterpriseMapper, quoteMapper);
    }

    @Test
    void tenantPublicOpportunityRequiresSupplierCategoryMatch() {
        InquiryDO inquiry = publishedInquiry(10L, 100L, "TENANT_PUBLIC");
        inquiry.setTenantId(1L);
        when(adminScope.requireMerchantShop(9L, 20L)).thenReturn(enabledShop(20L, 200L));
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);

        assertThrows(ServiceException.class, () -> service.adminOpportunityGet(9L, 20L, 10L));

        InquiryCrossTenantMapper.OpportunityRef ref = opportunityRef(10L, 1L, "TENANT_PUBLIC");
        when(crossTenantMapper.selectVisibleOpportunity(1L, 200L, 20L, 10L)).thenReturn(ref);
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        when(quoteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertEquals(10L, service.adminOpportunityGet(9L, 20L, 10L).getOrder().getId());
    }

    @Test
    void directedOpportunityAllowsOnlyInvitedShopWithoutCategoryMatch() {
        InquiryDO inquiry = publishedInquiry(10L, 100L, "INVITED_SHOPS");
        inquiry.setTenantId(1L);
        when(adminScope.requireMerchantShop(9L, 20L)).thenReturn(enabledShop(20L, 200L));
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(crossTenantMapper.selectVisibleOpportunity(1L, 200L, 20L, 10L))
                .thenReturn(opportunityRef(10L, 1L, "INVITED_SHOPS"));
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        when(quoteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertEquals(10L, service.adminOpportunityGet(9L, 20L, 10L).getOrder().getId());
    }

    @Test
    void platformPublicOpportunityCanCrossTenantOnlyThroughWhitelistedReference() {
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(2009L);
        InquiryDO inquiry = publishedInquiry(10L, 100L, "PLATFORM_PUBLIC");
        inquiry.setTenantId(1L);
        inquiry.setRemark("供应商可见的包装要求");
        when(adminScope.requireMerchantShop(9L, 20L)).thenReturn(enabledShop(20L, 200L));
        when(crossTenantMapper.selectVisibleOpportunity(2009L, 200L, 20L, 10L))
                .thenReturn(opportunityRef(10L, 1L, "PLATFORM_PUBLIC"));
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        InquiryItemDO item = new InquiryItemDO();
        item.setId(11L); item.setInquiryId(10L); item.setName("保温板"); item.setRemark("供应商可见的裁切要求");
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        when(quoteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        InquiryPublicDetailRespVO result = service.adminOpportunityGet(9L, 20L, 10L);

        assertEquals(10L, result.getOrder().getId());
        assertEquals(1L, result.getOrder().getInquiryTenantId());
        assertEquals("PLATFORM_PUBLIC", result.getOrder().getVisibility());
        assertEquals("供应商可见的包装要求", result.getRemark());
        assertEquals("供应商可见的裁切要求", result.getItems().getFirst().getRemark());
        assertEquals(2009L, cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
    }

    @Test
    void publicSearchCannotProbeRawContactsOrUseSqlWildcards() {
        long future = System.currentTimeMillis() + 86_400_000L;
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1); inquiry.setDeadline(future);
        inquiry.setNo("XJ-PUBLIC-10");
        inquiry.setVisibility("PLATFORM_PUBLIC");
        inquiry.setTitle("钢材 sales@example.com");
        InquiryItemDO item = new InquiryItemDO(); item.setId(11L); item.setInquiryId(10L);
        item.setName("螺纹钢 13800138000");
        ProductEnterpriseDO enterprise = approvedEnterprise(100L); enterprise.setName("采购企业");
        when(crossTenantMapper.selectPlatformPublicInquiries(eq("open"), anyLong(), eq("sales"), eq(0L), eq(10)))
                .thenReturn(List.of());
        when(crossTenantMapper.countPlatformPublicInquiries(eq("open"), anyLong(), eq("sales"))).thenReturn(0L);
        when(crossTenantMapper.selectPlatformPublicInquiries(eq("open"), anyLong(), eq("钢材"), eq(0L), eq(10)))
                .thenReturn(List.of(opportunityRef(10L, 1L, "PLATFORM_PUBLIC")));
        when(crossTenantMapper.countPlatformPublicInquiries(eq("open"), anyLong(), eq("钢材"))).thenReturn(1L);
        when(inquiryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(inquiry));
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(quoteMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(enterpriseMapper.selectBatchIds(anyCollection())).thenReturn(List.of(enterprise));
        var page = new cn.iocoder.txgy.framework.common.pojo.PageParam();

        assertEquals(0L, service.publicPage(page, "open", "sales").getTotal());
        assertEquals(1L, service.publicPage(page, "open", "钢材").getTotal());
        assertThrows(ServiceException.class, () -> service.publicPage(page, "open", "13800138000"));
        assertThrows(ServiceException.class, () -> service.publicPage(page, "open", "%钢材_"));
        verify(crossTenantMapper, times(2))
                .selectPlatformPublicInquiries(eq("open"), anyLong(), anyString(), eq(0L), eq(10));
    }

    @Test
    void platformPublicPageReadsEachResourceInItsRealTenantAndRestoresCallerContext() {
        TenantContextHolder.setTenantId(7777L);
        TenantContextHolder.setIgnore(true);
        InquiryDO first = publishedInquiry(10L, 100L, "PLATFORM_PUBLIC");
        first.setTenantId(1L);
        InquiryDO second = publishedInquiry(20L, 200L, "PLATFORM_PUBLIC");
        second.setTenantId(2009L);
        when(crossTenantMapper.selectPlatformPublicInquiries(eq("open"), anyLong(), isNull(), eq(0L), eq(10)))
                .thenReturn(List.of(opportunityRef(10L, 1L, "PLATFORM_PUBLIC"),
                        opportunityRef(20L, 2009L, "PLATFORM_PUBLIC")));
        when(crossTenantMapper.countPlatformPublicInquiries(eq("open"), anyLong(), isNull())).thenReturn(2L);
        when(inquiryMapper.selectList(any(Wrapper.class))).thenAnswer(invocation ->
                Objects.equals(TenantContextHolder.getRequiredTenantId(), 1L) ? List.of(first) : List.of(second));
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(quoteMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(enterpriseMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            Long tenantId = TenantContextHolder.getRequiredTenantId();
            ProductEnterpriseDO enterprise = approvedEnterprise(Objects.equals(tenantId, 1L) ? 100L : 200L);
            enterprise.setName("租户" + tenantId + "采购企业");
            return List.of(enterprise);
        });

        var result = service.publicPage(new cn.iocoder.txgy.framework.common.pojo.PageParam(), "open", null);

        assertEquals(2L, result.getTotal());
        assertEquals(List.of(1L, 2009L), result.getList().stream()
                .map(InquiryPublicSummaryRespVO::getInquiryTenantId).toList());
        assertEquals(7777L, TenantContextHolder.getRequiredTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test
    void decimalDemandCanUseIndependentIntegerPackagingCountThroughSubmitAndAccept() {
        long future = System.currentTimeMillis() + 86_400_000L;
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L);
        inquiry.setStatus(1); inquiry.setDeadline(future);
        InquiryItemDO requested = new InquiryItemDO();
        requested.setId(11L); requested.setInquiryId(10L);
        requested.setQuantity(new BigDecimal("1.500")); requested.setUnit("吨");

        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L); quote.setEnterpriseId(200L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_DRAFT); quote.setVersion(1L);
        quote.setGoodsPrice(3_000L); quote.setFreightPrice(100); quote.setTotalPrice(3_100L);
        quote.setDeliveryDate(future); quote.setValidUntil(future);
        InquiryQuoteItemDO line = new InquiryQuoteItemDO();
        line.setQuoteId(50L); line.setInquiryItemId(11L); line.setSkuId(31L);
        line.setProductName("商城袋装材料"); line.setSkuName("商城袋装材料 包装：500kg/包");
        line.setSkuSpecification("包装：500kg/包"); line.setSpuContentVersion(3L);
        line.setCount(3); line.setUnitPrice(1_000); line.setTotalPrice(3_000L);
        ProductSkuDO sku = new ProductSkuDO(); sku.setId(31L); sku.setSpuId(41L);
        sku.setProperties(List.of(new ProductSkuDO.Property(1L, "包装", 2L, "500kg/包")));
        ProductSpuDO spu = new ProductSpuDO();
        spu.setId(41L); spu.setShopId(20L); spu.setName("商城袋装材料");
        spu.setStatus(1); spu.setReviewStatus(20); spu.setContentVersion(3L); spu.setApprovedVersion(3L);

        when(membership.requireSupplier(8L)).thenReturn(supplier(200L));
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(enterpriseMapper.selectOne(any(Wrapper.class))).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(enabledShop(20L, 200L));
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(requested));
        when(quoteItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line));
        when(skuMapper.selectById(31L)).thenReturn(sku);
        when(spuMapper.selectById(41L)).thenReturn(spu);

        service.submitQuote(8L, 50L, 1L);
        assertEquals(PurchaseInquiryMarketService.QUOTE_SUBMITTED, quote.getStatus());
        assertEquals(2L, quote.getVersion());

        service.acceptQuote(7L, 50L, 2L);
        assertEquals(PurchaseInquiryMarketService.QUOTE_ACCEPTED, quote.getStatus());
        assertEquals(3L, quote.getVersion());
        assertEquals(4, inquiry.getStatus());
        assertEquals(50L, inquiry.getSelectedQuoteId());
    }

    @Test
    void saveQuoteCoversEveryItemAndCalculatesMoneyOnServer() {
        long future = System.currentTimeMillis() + 86_400_000L;
        EnterpriseApplicationDO application = new EnterpriseApplicationDO();
        application.setEnterpriseId(200L);
        when(membership.requireSupplier(7L)).thenReturn(application);

        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1); inquiry.setDeadline(future);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(enabledShop(20L, 200L));
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        InquiryItemDO requested = new InquiryItemDO();
        requested.setId(11L); requested.setInquiryId(10L); requested.setQuantity(new BigDecimal("2.000"));
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(requested));
        ProductSkuDO sku = new ProductSkuDO(); sku.setId(31L); sku.setSpuId(41L);
        sku.setProperties(List.of(new ProductSkuDO.Property(1L, "直径", 2L, "20mm")));
        when(skuMapper.selectById(31L)).thenReturn(sku);
        ProductSpuDO spu = new ProductSpuDO();
        spu.setId(41L); spu.setShopId(20L); spu.setStatus(1); spu.setReviewStatus(20);
        spu.setName("商城螺纹钢"); spu.setContentVersion(3L); spu.setApprovedVersion(3L);
        when(spuMapper.selectById(41L)).thenReturn(spu);
        doAnswer(invocation -> {
            InquiryQuoteDO quote = invocation.getArgument(0);
            quote.setId(50L);
            return 1;
        }).when(quoteMapper).insert(any(InquiryQuoteDO.class));

        InquiryQuoteSaveReqVO.Item input = new InquiryQuoteSaveReqVO.Item();
        input.setInquiryItemId(11L); input.setSkuId(31L); input.setCount(2); input.setUnitPrice(1_000);
        InquiryQuoteSaveReqVO request = new InquiryQuoteSaveReqVO();
        request.setInquiryId(10L); request.setShopId(20L); request.setVersion(0L);
        request.setRequestKey("quote-save-001"); request.setFreightPrice(100);
        request.setDeliveryDate(future); request.setValidUntil(future); request.setItems(List.of(input));

        assertEquals(50L, service.saveQuote(7L, request));
        verify(quoteMapper).insert(ArgumentMatchers.<InquiryQuoteDO>argThat(quote -> quote.getGoodsPrice() == 2_000L
                && quote.getFreightPrice() == 100 && quote.getTotalPrice() == 2_100L
                && quote.getStatus() == PurchaseInquiryMarketService.QUOTE_DRAFT && quote.getVersion() == 1L));
        verify(quoteItemMapper).insert(ArgumentMatchers.<InquiryQuoteItemDO>argThat(item -> item.getCount() == 2
                && item.getUnitPrice() == 1_000 && item.getTotalPrice() == 2_000L
                && "商城螺纹钢".equals(item.getProductName())
                && "商城螺纹钢 直径：20mm".equals(item.getSkuName())
                && "直径：20mm".equals(item.getSkuSpecification())
                && Long.valueOf(3L).equals(item.getSpuContentVersion())));
    }

    @Test
    void adminCanSavePlatformPublicQuoteAcrossTenantWithBothTenantOwnersRecorded() {
        long future = System.currentTimeMillis() + 86_400_000L;
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(2009L);
        ProductShopDO supplierShop = enabledShop(20L, 200L);
        when(adminScope.requireMerchantShop(9L, 20L)).thenReturn(supplierShop);
        when(crossTenantMapper.selectVisibleOpportunity(2009L, 200L, 20L, 10L))
                .thenReturn(opportunityRef(10L, 1L, "PLATFORM_PUBLIC"));

        InquiryDO inquiry = publishedInquiry(10L, 100L, "PLATFORM_PUBLIC");
        inquiry.setTenantId(1L);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        InquiryItemDO requested = new InquiryItemDO();
        requested.setId(11L); requested.setInquiryId(10L); requested.setQuantity(BigDecimal.ONE);
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(requested));

        ProductSkuDO sku = new ProductSkuDO(); sku.setId(31L); sku.setSpuId(41L);
        sku.setProperties(List.of(new ProductSkuDO.Property(1L, "厚度", 2L, "50mm")));
        when(skuMapper.selectById(31L)).thenReturn(sku);
        ProductSpuDO spu = new ProductSpuDO();
        spu.setId(41L); spu.setShopId(20L); spu.setStatus(1); spu.setReviewStatus(20);
        spu.setName("跨租户保温板"); spu.setContentVersion(2L); spu.setApprovedVersion(2L);
        when(spuMapper.selectById(41L)).thenReturn(spu);
        when(quoteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            InquiryQuoteDO quote = invocation.getArgument(0);
            quote.setId(50L);
            return 1;
        }).when(quoteMapper).insert(any(InquiryQuoteDO.class));

        InquiryQuoteSaveReqVO.Item input = new InquiryQuoteSaveReqVO.Item();
        input.setInquiryItemId(11L); input.setSkuId(31L); input.setCount(1); input.setUnitPrice(1_000);
        InquiryQuoteSaveReqVO request = new InquiryQuoteSaveReqVO();
        request.setInquiryId(10L); request.setShopId(20L); request.setVersion(0L);
        request.setRequestKey("cross-tenant-quote-001"); request.setFreightPrice(100);
        request.setDeliveryDate(future); request.setValidUntil(future); request.setItems(List.of(input));

        assertEquals(50L, service.adminSaveQuote(9L, request));

        verify(quoteMapper).insert(argThat((InquiryQuoteDO quote) -> Long.valueOf(1L).equals(quote.getTenantId())
                && Long.valueOf(2009L).equals(quote.getSupplierTenantId())
                && Long.valueOf(200L).equals(quote.getEnterpriseId())
                && Long.valueOf(20L).equals(quote.getShopId())));
        assertEquals(2009L, cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
    }

    @Test
    void crossTenantDraftCannotBeSubmittedAfterDeliveryCoverageIsRemoved() {
        long future = System.currentTimeMillis() + 86_400_000L;
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(2009L);
        ProductShopDO supplierShop = enabledShop(20L, 200L);
        when(adminScope.requireMerchantShop(9L, 20L)).thenReturn(supplierShop);
        when(crossTenantMapper.selectVisibleOpportunity(2009L, 200L, 20L, 10L))
                .thenReturn(opportunityRef(10L, 1L, "PLATFORM_PUBLIC"));

        InquiryDO inquiry = publishedInquiry(10L, 100L, "PLATFORM_PUBLIC");
        inquiry.setTenantId(1L);
        inquiry.setDeliveryAreaId(110101);
        inquiry.setDeliveryCityId(110100);
        inquiry.setDeliveryProvinceId(110000);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        InquiryItemDO requested = new InquiryItemDO();
        requested.setId(11L); requested.setInquiryId(10L); requested.setQuantity(BigDecimal.ONE);
        requested.setCategory("保温材料");
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(requested));

        ProductSkuDO sku = new ProductSkuDO(); sku.setId(31L); sku.setSpuId(41L);
        sku.setProperties(List.of(new ProductSkuDO.Property(1L, "厚度", 2L, "50mm")));
        when(skuMapper.selectById(31L)).thenReturn(sku);
        ProductSpuDO spu = new ProductSpuDO();
        spu.setId(41L); spu.setShopId(20L); spu.setStatus(1); spu.setReviewStatus(20);
        spu.setName("跨租户保温板"); spu.setContentVersion(2L); spu.setApprovedVersion(2L);
        when(spuMapper.selectById(41L)).thenReturn(spu);
        when(quoteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        InquiryQuoteDO[] savedQuote = new InquiryQuoteDO[1];
        InquiryQuoteItemDO[] savedItem = new InquiryQuoteItemDO[1];
        doAnswer(invocation -> {
            InquiryQuoteDO quote = invocation.getArgument(0);
            quote.setId(50L); savedQuote[0] = quote; return 1;
        }).when(quoteMapper).insert(any(InquiryQuoteDO.class));
        doAnswer(invocation -> {
            savedItem[0] = invocation.getArgument(0); return 1;
        }).when(quoteItemMapper).insert(any(InquiryQuoteItemDO.class));
        when(crossTenantMapper.countEligibleQuoteSkuForItem(
                2009L, 1L, 10L, 11L, 20L, 31L, true)).thenReturn(1L, 0L);

        InquiryQuoteSaveReqVO.Item input = new InquiryQuoteSaveReqVO.Item();
        input.setInquiryItemId(11L); input.setSkuId(31L); input.setCount(1); input.setUnitPrice(1_000);
        InquiryQuoteSaveReqVO request = new InquiryQuoteSaveReqVO();
        request.setInquiryId(10L); request.setShopId(20L); request.setVersion(0L);
        request.setRequestKey("cross-tenant-delivery-recheck"); request.setFreightPrice(100);
        request.setDeliveryDate(future); request.setValidUntil(future); request.setItems(List.of(input));
        assertEquals(50L, service.adminSaveQuote(9L, request));

        InquiryCrossTenantMapper.QuoteRef quoteRef = new InquiryCrossTenantMapper.QuoteRef();
        quoteRef.setQuoteId(50L); quoteRef.setInquiryTenantId(1L); quoteRef.setSupplierTenantId(2009L);
        quoteRef.setInquiryId(10L); quoteRef.setEnterpriseId(200L); quoteRef.setShopId(20L);
        when(crossTenantMapper.selectSupplierQuoteForTenant(2009L, 50L)).thenReturn(quoteRef);
        when(crossTenantMapper.selectSupplierQuote(2009L, 200L, 20L, 50L)).thenReturn(quoteRef);
        when(quoteMapper.selectById(50L)).thenReturn(savedQuote[0]);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(savedQuote[0]);
        when(quoteItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(savedItem[0]));
        when(enterpriseMapper.selectOne(any(Wrapper.class))).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(supplierShop);

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.adminSubmitQuote(9L, 50L, 1L));
        assertTrue(failure.getMessage().contains("运费模板"));
        assertEquals(PurchaseInquiryMarketService.QUOTE_DRAFT, savedQuote[0].getStatus());
        verify(crossTenantMapper, times(2)).countEligibleQuoteSkuForItem(
                2009L, 1L, 10L, 11L, 20L, 31L, true);
        verify(quoteMapper, never()).updateById(any(InquiryQuoteDO.class));
    }

    @Test
    void saveQuoteRejectsMappingDifferentInquiryLinesToTheSameSku() {
        long future = System.currentTimeMillis() + 86_400_000L;
        EnterpriseApplicationDO application = new EnterpriseApplicationDO();
        application.setEnterpriseId(200L);
        when(membership.requireSupplier(7L)).thenReturn(application);

        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1); inquiry.setDeadline(future);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(enabledShop(20L, 200L));
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        InquiryItemDO first = new InquiryItemDO();
        first.setId(11L); first.setInquiryId(10L); first.setQuantity(BigDecimal.ONE);
        InquiryItemDO second = new InquiryItemDO();
        second.setId(12L); second.setInquiryId(10L); second.setQuantity(BigDecimal.ONE);
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));

        InquiryQuoteSaveReqVO.Item firstInput = new InquiryQuoteSaveReqVO.Item();
        firstInput.setInquiryItemId(11L); firstInput.setSkuId(31L); firstInput.setCount(1); firstInput.setUnitPrice(1_000);
        InquiryQuoteSaveReqVO.Item secondInput = new InquiryQuoteSaveReqVO.Item();
        secondInput.setInquiryItemId(12L); secondInput.setSkuId(31L); secondInput.setCount(1); secondInput.setUnitPrice(2_000);
        InquiryQuoteSaveReqVO request = new InquiryQuoteSaveReqVO();
        request.setInquiryId(10L); request.setShopId(20L); request.setVersion(0L);
        request.setRequestKey("quote-save-duplicate-sku"); request.setFreightPrice(0);
        request.setDeliveryDate(future); request.setValidUntil(future);
        request.setItems(List.of(firstInput, secondInput));

        assertThrows(ServiceException.class, () -> service.saveQuote(7L, request));
        verifyNoInteractions(skuMapper, quoteItemMapper);
    }

    @Test
    void saveQuoteRejectsInquiryWhoseBuyerEnterpriseWasRevoked() {
        long future = System.currentTimeMillis() + 86_400_000L;
        when(membership.requireSupplier(7L)).thenReturn(supplier(200L));
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1); inquiry.setDeadline(future);
        ProductEnterpriseDO revoked = approvedEnterprise(100L); revoked.setAuditStatus(0);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(enterpriseMapper.selectById(100L)).thenReturn(revoked);
        InquiryQuoteSaveReqVO request = new InquiryQuoteSaveReqVO();
        request.setInquiryId(10L); request.setShopId(20L);

        assertThrows(ServiceException.class, () -> service.saveQuote(7L, request));
        verifyNoInteractions(quoteMapper, quoteItemMapper, skuMapper, spuMapper);
    }

    @Test
    void saveQuoteIdempotencyCannotReturnPriorEnterpriseQuoteAfterShopReassignment() {
        long future = System.currentTimeMillis() + 86_400_000L;
        when(membership.requireSupplier(8L)).thenReturn(supplier(999L));
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1); inquiry.setDeadline(future);
        InquiryQuoteDO previous = new InquiryQuoteDO();
        previous.setId(50L); previous.setInquiryId(10L); previous.setShopId(20L);
        previous.setEnterpriseId(200L); previous.setRequestKey("reused-key");
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(enabledShop(20L, 999L));
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(previous);
        InquiryQuoteSaveReqVO request = new InquiryQuoteSaveReqVO();
        request.setInquiryId(10L); request.setShopId(20L); request.setRequestKey("reused-key");

        assertThrows(ServiceException.class, () -> service.saveQuote(8L, request));
        verifyNoInteractions(quoteItemMapper, skuMapper);
    }

    @Test
    void saveQuoteLocksAndRechecksCurrentShopEnterprise() {
        long future = System.currentTimeMillis() + 86_400_000L;
        when(membership.requireSupplier(8L)).thenReturn(supplier(200L));
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1); inquiry.setDeadline(future);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(enabledShop(20L, 999L));
        InquiryQuoteSaveReqVO request = new InquiryQuoteSaveReqVO();
        request.setInquiryId(10L); request.setShopId(20L);

        assertThrows(ServiceException.class, () -> service.saveQuote(8L, request));

        ArgumentCaptor<Wrapper<ProductShopDO>> shopQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(shopMapper).selectOne(shopQuery.capture());
        assertTrue(Objects.toString(org.springframework.test.util.ReflectionTestUtils
                .getField(shopQuery.getValue(), "lastSql"), "").contains("FOR UPDATE"));
        verify(quoteMapper, never()).insert(any(InquiryQuoteDO.class));
    }

    @Test
    void reassignedShopWithForeignEnterpriseQuoteHistoryCannotBeReused() {
        long future = System.currentTimeMillis() + 86_400_000L;
        when(membership.requireSupplier(8L)).thenReturn(supplier(200L));
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1); inquiry.setDeadline(future);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(enterpriseMapper.selectById(100L)).thenReturn(approvedEnterprise(100L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(enabledShop(20L, 200L));
        when(quoteMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        InquiryQuoteSaveReqVO request = new InquiryQuoteSaveReqVO();
        request.setInquiryId(10L); request.setShopId(20L);

        assertThrows(ServiceException.class, () -> service.saveQuote(8L, request));
        verify(quoteMapper, never()).insert(any(InquiryQuoteDO.class));
        verifyNoInteractions(quoteItemMapper, skuMapper);
    }

    @Test
    void reassignedShopCannotExposeOrOperatePriorEnterpriseQuotes() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L);
        quote.setEnterpriseId(200L); quote.setStatus(PurchaseInquiryMarketService.QUOTE_DRAFT);
        quote.setVersion(3L);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        inquiry.setDeadline(System.currentTimeMillis() + 86_400_000L);
        when(membership.requireSupplier(8L)).thenReturn(supplier(999L));
        doThrow(new ServiceException(403, "报价不属于当前供应企业或店铺")).when(membership)
                .requireSupplierShopHistory(8L, 200L, 20L);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        assertThrows(ServiceException.class, () -> service.get(8L, 50L));
        assertThrows(ServiceException.class, () -> service.mine(8L, 10L, 20L));
        assertThrows(ServiceException.class, () -> service.supplierPage(8L, 20L, null,
                new cn.iocoder.txgy.framework.common.pojo.PageParam()));
        assertThrows(ServiceException.class, () -> service.submitQuote(8L, 50L, 3L));
        verify(quoteMapper, never()).updateById(any(InquiryQuoteDO.class));
        verifyNoInteractions(quoteItemMapper);
    }

    @Test
    void acceptQuoteSelectsOneWinnerAndRejectsOtherSubmittedQuotes() {
        long future = System.currentTimeMillis() + 86_400_000L;
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        quote.setEnterpriseId(200L); quote.setShopId(20L); quote.setVersion(7L); quote.setValidUntil(future);
        quote.setDeliveryDate(future);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(enterpriseMapper.selectOne(any(Wrapper.class))).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(enabledShop(20L, 200L));
        stubStoredQuoteLine(quote, 3L);

        service.acceptQuote(7L, 50L, 7L);

        assertEquals(PurchaseInquiryMarketService.QUOTE_ACCEPTED, quote.getStatus());
        assertEquals(8L, quote.getVersion());
        assertEquals(4, inquiry.getStatus());
        assertEquals(50L, inquiry.getSelectedQuoteId());
        verify(quoteMapper).update(isNull(), any(Wrapper.class));
        verify(inquiryMapper).updateById(inquiry);
    }

    @Test
    void crossTenantQuoteCannotBeAcceptedBeforeTenantOrderSplitExists() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setTenantId(1L); quote.setInquiryId(10L);
        quote.setEnterpriseId(200L); quote.setShopId(20L);
        quote.setSupplierTenantId(2009L); quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        quote.setVersion(1L);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setMemberId(7L);
        inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.acceptQuote(7L, 50L, 1L));

        assertTrue(error.getMessage().contains("跨租户"));
        verify(quoteMapper, never()).updateById(any(InquiryQuoteDO.class));
        verify(inquiryMapper, never()).updateById(any(InquiryDO.class));
    }

    @Test
    void crossTenantAcceptedSnapshotCannotReachOrderServiceEvenForLegacyOrCorruptData() {
        InquiryQuoteDO quote = acceptedQuote();
        quote.setTenantId(1L);
        quote.setSupplierTenantId(2009L);
        InquiryDO inquiry = acceptedInquiry();
        inquiry.setTenantId(1L);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getAcceptedQuote(7L, 50L));

        assertTrue(error.getMessage().contains("跨租户"));
        verifyNoInteractions(enterpriseMapper, shopMapper, quoteItemMapper);
    }

    @Test
    void buyerCannotSendCrossTenantMessageAfterPlatformScopeIsClosed() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setTenantId(1L); quote.setInquiryId(10L);
        quote.setEnterpriseId(200L); quote.setShopId(20L); quote.setSupplierTenantId(2009L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L);
        inquiry.setStatus(1); inquiry.setDeadline(System.currentTimeMillis() + 86_400_000L);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        // 显式白名单查询返回空，代表租户停用、门户关闭、资质撤销或可见范围变化。
        when(crossTenantMapper.selectSupplierQuote(2009L, 200L, 20L, 50L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.sendMessage(7L,
                message(50L, "cross-tenant-message", "不应继续发送")));

        assertTrue(error.getMessage().contains("范围已关闭") || error.getMessage().contains("资质已失效"));
        verifyNoInteractions(messageMapper);
    }

    @Test
    void crossTenantQuoteAndMessagesRemainReadableAfterPortalScopeCloses() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setTenantId(1L); quote.setInquiryId(10L);
        quote.setEnterpriseId(200L); quote.setShopId(20L); quote.setSupplierTenantId(2009L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L);
        inquiry.setStatus(3); inquiry.setNo("XJ-HISTORY-10"); inquiry.setTitle("历史询价");
        ProductShopDO closedShop = enabledShop(20L, 200L);
        closedShop.setName("供应店铺");
        InquiryQuoteMessageDO message = new InquiryQuoteMessageDO();
        message.setId(90L); message.setQuoteId(50L); message.setSenderRole("SUPPLIER");
        message.setContent("历史交期说明");
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(shopMapper.selectById(20L)).thenReturn(closedShop);
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(quoteItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(message));
        // ACTIVE 查询保持返回空，模拟门户关闭；历史读取不应依赖该结果。
        when(crossTenantMapper.selectSupplierQuote(2009L, 200L, 20L, 50L)).thenReturn(null);

        var detail = service.get(7L, 50L);
        var messages = service.messages(7L, 50L, 0L, 50);

        assertEquals(50L, detail.getId());
        assertFalse(detail.getCanMessage());
        assertEquals("历史交期说明", messages.getFirst().getContent());
    }

    @Test
    void adminHistoricalReadUsesImmutableQuoteScopeButNotActivePortalScope() {
        TenantContextHolder.setTenantId(2009L);
        InquiryCrossTenantMapper.QuoteRef ref = new InquiryCrossTenantMapper.QuoteRef();
        ref.setQuoteId(50L); ref.setInquiryTenantId(1L); ref.setSupplierTenantId(2009L);
        ref.setInquiryId(10L); ref.setEnterpriseId(200L); ref.setShopId(20L);
        ProductShopDO closedShop = enabledShop(20L, 200L);
        closedShop.setTenantId(2009L); closedShop.setStatus(1); closedShop.setName("历史店铺");
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setTenantId(1L); quote.setSupplierTenantId(2009L);
        quote.setInquiryId(10L); quote.setEnterpriseId(200L); quote.setShopId(20L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_REJECTED);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setTenantId(1L); inquiry.setEnterpriseId(100L);
        InquiryQuoteMessageDO message = new InquiryQuoteMessageDO();
        message.setId(90L); message.setQuoteId(50L); message.setContent("历史消息");
        when(crossTenantMapper.selectHistoricalSupplierQuoteForTenant(2009L, 50L)).thenReturn(ref);
        when(adminScope.requireMerchantShopHistory(9L, 20L)).thenReturn(closedShop);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(shopMapper.selectById(20L)).thenReturn(closedShop);
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(quoteItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(message));

        assertEquals(50L, service.adminGet(9L, 50L).getId());
        assertEquals("历史消息", service.adminMessages(9L, 50L, 0L, 50).getFirst().getContent());
        verify(crossTenantMapper, times(2)).selectHistoricalSupplierQuoteForTenant(2009L, 50L);
        verify(crossTenantMapper, never()).selectSupplierQuoteForTenant(anyLong(), anyLong());
        assertEquals(2009L, TenantContextHolder.getRequiredTenantId());
    }

    @Test
    void sameEnterpriseCollaboratorCanAcceptAndActualMemberIsAudited() {
        long future = System.currentTimeMillis() + 86_400_000L;
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        quote.setEnterpriseId(200L); quote.setShopId(20L); quote.setVersion(7L);
        quote.setValidUntil(future); quote.setDeliveryDate(future);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        when(membership.requireBuyer(9L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(enterpriseMapper.selectOne(any(Wrapper.class))).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(enabledShop(20L, 200L));
        stubStoredQuoteLine(quote, 3L);

        service.acceptQuote(9L, 50L, 7L);

        assertEquals(4, inquiry.getStatus());
        verify(operationAuditService).success(100L, 20L, "MEMBER", 9L,
                "INQUIRY_QUOTE_ACCEPT", "INQUIRY_QUOTE", 50L,
                "inquiryId=10,version=8");
    }

    @Test
    void acceptedSnapshotRejectsForeignBuyer() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setStatus(PurchaseInquiryMarketService.QUOTE_ACCEPTED);
        quote.setValidUntil(System.currentTimeMillis() - 1L);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L);
        inquiry.setStatus(4); inquiry.setSelectedQuoteId(50L);
        when(membership.requireBuyer(anyLong())).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);

        assertThrows(ServiceException.class, () -> service.getAcceptedQuote(8L, 50L));
        verifyNoInteractions(quoteItemMapper);
    }

    @Test
    void acceptedSnapshotRemainsUsableAfterQuotedValidityExpires() {
        InquiryQuoteDO quote = acceptedQuote();
        quote.setValidUntil(System.currentTimeMillis() - 1L);
        InquiryDO inquiry = acceptedInquiry();
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(enterpriseMapper.selectById(200L)).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectById(20L)).thenReturn(enabledShop(20L, 200L));
        stubStoredQuoteLine(quote, 3L);

        assertEquals(50L, service.getAcceptedQuote(7L, 50L).getQuoteId());
    }

    @Test
    void acceptedSnapshotContainsOnlyAcceptedLineValues() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L); quote.setVersion(8L);
        quote.setEnterpriseId(200L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_ACCEPTED);
        quote.setValidUntil(System.currentTimeMillis() + 86_400_000L);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setTitle("年度钢材采购");
        inquiry.setEnterpriseId(100L); inquiry.setStatus(4); inquiry.setSelectedQuoteId(50L);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(enterpriseMapper.selectById(200L)).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectById(20L)).thenReturn(enabledShop(20L, 200L));
        stubStoredQuoteLine(quote, 3L);

        var snapshot = service.getAcceptedQuote(7L, 50L);

        assertEquals(8L, snapshot.getVersion());
        assertEquals("年度钢材采购", snapshot.getTitle());
        assertEquals(31L, snapshot.getItems().getFirst().getSkuId());
        assertEquals(2, snapshot.getItems().getFirst().getCount());
        assertEquals(1_000, snapshot.getItems().getFirst().getUnitPrice());
    }

    @Test
    void acceptedQuoteCannotBeUsedAfterProductContentVersionChanges() {
        InquiryQuoteDO quote = acceptedQuote();
        InquiryDO inquiry = acceptedInquiry();
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(enterpriseMapper.selectById(200L)).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectById(20L)).thenReturn(enabledShop(20L, 200L));
        stubStoredQuoteLine(quote, 4L);

        assertThrows(ServiceException.class, () -> service.getAcceptedQuote(7L, 50L));
    }

    @Test
    void acceptQuoteRejectsDisabledOrMismatchedSupplierShop() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setEnterpriseId(200L); quote.setShopId(20L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED); quote.setVersion(7L);
        quote.setValidUntil(System.currentTimeMillis() + 86_400_000L);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        ProductShopDO disabled = enabledShop(20L, 200L); disabled.setStatus(1);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(enterpriseMapper.selectOne(any(Wrapper.class))).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(disabled);

        assertThrows(ServiceException.class, () -> service.acceptQuote(7L, 50L, 7L));

        verify(quoteMapper, never()).updateById(any(InquiryQuoteDO.class));
        verify(inquiryMapper, never()).updateById(any(InquiryDO.class));
    }

    @Test
    void acceptQuoteRejectsExpiredDeliveryDate() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setEnterpriseId(200L); quote.setShopId(20L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED); quote.setVersion(7L);
        quote.setValidUntil(System.currentTimeMillis() + 86_400_000L);
        quote.setDeliveryDate(System.currentTimeMillis() - 1L);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(enterpriseMapper.selectOne(any(Wrapper.class))).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(enabledShop(20L, 200L));

        assertThrows(ServiceException.class, () -> service.acceptQuote(7L, 50L, 7L));
        verifyNoInteractions(quoteItemMapper);
        verify(quoteMapper, never()).updateById(any(InquiryQuoteDO.class));
    }

    @Test
    void acceptedSnapshotRequiresCurrentBuyerQualification() {
        doThrow(new ServiceException(403, "企业认证已失效")).when(membership).requireBuyer(7L);

        assertThrows(ServiceException.class, () -> service.getAcceptedQuote(7L, 50L));

        verifyNoInteractions(quoteMapper, inquiryMapper, quoteItemMapper);
    }

    @Test
    void acceptedSnapshotRejectsBuyerEnterpriseReassignment() {
        InquiryQuoteDO quote = acceptedQuote();
        InquiryDO inquiry = acceptedInquiry();
        when(membership.requireBuyer(7L)).thenReturn(buyer(999L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);

        assertThrows(ServiceException.class, () -> service.getAcceptedQuote(7L, 50L));

        verifyNoInteractions(enterpriseMapper, shopMapper, quoteItemMapper);
    }

    @Test
    void acceptedSnapshotRejectsRevokedSupplierEnterprise() {
        InquiryQuoteDO quote = acceptedQuote();
        InquiryDO inquiry = acceptedInquiry();
        ProductEnterpriseDO revoked = approvedEnterprise(200L); revoked.setAuditStatus(0);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(enterpriseMapper.selectById(200L)).thenReturn(revoked);
        when(shopMapper.selectById(20L)).thenReturn(enabledShop(20L, 200L));

        assertThrows(ServiceException.class, () -> service.getAcceptedQuote(7L, 50L));

        verifyNoInteractions(quoteItemMapper);
    }

    @Test
    void quoteResponseUsesImmutableProductSnapshotAndReturnsAuditTimes() {
        LocalDateTime created = LocalDateTime.of(2026, 9, 20, 9, 0);
        LocalDateTime updated = created.plusHours(2);
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L);
        quote.setEnterpriseId(200L); quote.setStatus(PurchaseInquiryMarketService.QUOTE_DRAFT);
        quote.setDeliveryDate(System.currentTimeMillis() + 86_400_000L);
        quote.setValidUntil(System.currentTimeMillis() + 86_400_000L);
        quote.setCreateTime(created); quote.setUpdateTime(updated);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setStatus(1);
        inquiry.setDeadline(System.currentTimeMillis() + 86_400_000L);
        InquiryItemDO requested = new InquiryItemDO();
        requested.setId(11L); requested.setInquiryId(10L); requested.setName("询价物料名");
        requested.setSpecification("询价规格"); requested.setQuantity(BigDecimal.TWO); requested.setUnit("吨");
        InquiryQuoteItemDO line = new InquiryQuoteItemDO();
        line.setQuoteId(50L); line.setInquiryItemId(11L); line.setSkuId(31L);
        line.setProductName("商城螺纹钢"); line.setSkuName("商城螺纹钢 直径：20mm");
        line.setSkuSpecification("直径：20mm"); line.setSpuContentVersion(3L);
        line.setCount(2); line.setUnitPrice(1_000); line.setTotalPrice(2_000L);
        ProductShopDO shop = enabledShop(20L, 200L); shop.setName("钢材旗舰店");
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(shopMapper.selectById(20L)).thenReturn(shop);
        when(enterpriseMapper.selectById(200L)).thenReturn(approvedEnterprise(200L));
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(requested));
        when(quoteItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line));

        var response = service.get(8L, 50L);

        var item = response.getItems().getFirst();
        assertEquals("商城螺纹钢", item.getProductName());
        assertEquals("商城螺纹钢 直径：20mm", item.getSkuName());
        assertEquals("直径：20mm", item.getSkuSpecification());
        assertEquals(item.getProductName(), item.getName());
        assertEquals(item.getSkuSpecification(), item.getSpecification());
        assertNotEquals("询价物料名", item.getProductName());
        assertEquals(created, response.getCreateTime());
        assertEquals(updated, response.getUpdateTime());
        assertTrue(response.getCanQuote());
        assertTrue(response.getCanEdit());
        assertTrue(response.getCanSubmit());
        assertFalse(response.getCanWithdraw());
        assertFalse(response.getCanMessage());
        assertFalse(response.getCanAccept());
        assertFalse(response.getCanConvert());

        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        var submitted = service.get(8L, 50L);
        assertFalse(submitted.getCanEdit());
        assertFalse(submitted.getCanSubmit());
        assertTrue(submitted.getCanWithdraw());
        assertTrue(submitted.getCanMessage());

        quote.setStatus(PurchaseInquiryMarketService.QUOTE_REJECTED);
        var rejected = service.get(8L, 50L);
        assertTrue(rejected.getCanEdit());
        assertFalse(rejected.getCanMessage());
        verifyNoInteractions(skuMapper, spuMapper);
    }

    @Test
    void buyerCapabilitiesFollowQuoteAndAcceptedInquiryState() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setEnterpriseId(200L); quote.setShopId(20L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        quote.setDeliveryDate(System.currentTimeMillis() + 86_400_000L);
        quote.setValidUntil(System.currentTimeMillis() + 86_400_000L);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        inquiry.setDeadline(System.currentTimeMillis() - 1L); // 截止后仍允许采购方采纳有效报价
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(shopMapper.selectById(20L)).thenReturn(enabledShop(20L, 200L));
        when(enterpriseMapper.selectById(200L)).thenReturn(approvedEnterprise(200L));
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(quoteItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        var submitted = service.get(7L, 50L);
        assertFalse(submitted.getCanEdit());
        assertFalse(submitted.getCanSubmit());
        assertFalse(submitted.getCanWithdraw());
        assertFalse(submitted.getCanMessage());
        assertTrue(submitted.getCanAccept());
        assertFalse(submitted.getCanConvert());

        quote.setStatus(PurchaseInquiryMarketService.QUOTE_ACCEPTED);
        quote.setValidUntil(System.currentTimeMillis() - 1L);
        inquiry.setStatus(4); inquiry.setSelectedQuoteId(50L);
        var accepted = service.get(7L, 50L);
        assertFalse(accepted.getCanAccept());
        assertTrue(accepted.getCanMessage());
        assertTrue(accepted.getCanConvert());
    }

    @Test
    void messageRetryIsIdempotentAndKeyCannotBeReusedForDifferentContent() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L); quote.setEnterpriseId(200L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        inquiry.setDeadline(System.currentTimeMillis() + 86_400_000L);
        InquiryQuoteMessageDO existing = new InquiryQuoteMessageDO();
        existing.setId(99L); existing.setQuoteId(50L); existing.setSenderMemberId(7L);
        existing.setClientMessageId("message-key-001"); existing.setContent("请确认交期");
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(enterpriseMapper.selectById(200L)).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectById(20L)).thenReturn(enabledShop(20L, 200L));
        when(messageMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        InquiryQuoteMessageSendReqVO request = message(50L, "message-key-001", "请确认交期");

        assertEquals(99L, service.sendMessage(7L, request));
        request.setContent("改成其他内容");
        assertThrows(ServiceException.class, () -> service.sendMessage(7L, request));
        verify(messageMapper, never()).insert(any(InquiryQuoteMessageDO.class));
    }

    @Test
    void disabledLegacySupplierCanReadHistoryButCannotSeeOrUseWriteCapabilities() {
        when(legacyMemberSalesProperties.isEnabled()).thenReturn(false);
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L); quote.setEnterpriseId(200L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L); inquiry.setStatus(1);
        inquiry.setDeadline(System.currentTimeMillis() + 86_400_000L);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(membership.requireSupplier(8L)).thenReturn(supplier(200L));
        when(shopMapper.selectById(20L)).thenReturn(enabledShop(20L, 200L));
        when(enterpriseMapper.selectById(200L)).thenReturn(approvedEnterprise(200L));
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(quoteItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        var response = service.get(8L, 50L);
        assertFalse(response.getCanQuote());
        assertFalse(response.getCanEdit());
        assertFalse(response.getCanSubmit());
        assertFalse(response.getCanWithdraw());
        assertFalse(response.getCanMessage());
        assertTrue(response.getLegacySalesReadOnly());
        assertTrue(response.getSalesWriteNotice().contains("后台管理的销售中心"));
        assertThrows(ServiceException.class, () -> service.sendMessage(8L,
                message(50L, "legacy-supplier-message", "旧入口不应发送")));
        verifyNoInteractions(messageMapper);
    }

    @Test
    void rejectedQuoteMessagesRemainReadableButCannotBeSent() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L); quote.setEnterpriseId(200L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_REJECTED);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(enterpriseMapper.selectById(200L)).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectById(20L)).thenReturn(enabledShop(20L, 200L));
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertTrue(service.messages(7L, 50L, 0L, 50).isEmpty());
        assertThrows(ServiceException.class, () -> service.sendMessage(7L,
                message(50L, "message-key-read-only", "不应发送")));
        verify(messageMapper, never()).insert(any(InquiryQuoteMessageDO.class));
        verify(messageMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void expiredInquiryMessagesRemainReadableButCannotBeSent() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L); quote.setEnterpriseId(200L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L);
        inquiry.setStatus(1); inquiry.setDeadline(System.currentTimeMillis() - 1L);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        when(membership.requireBuyer(7L)).thenReturn(buyer(100L));
        when(enterpriseMapper.selectById(200L)).thenReturn(approvedEnterprise(200L));
        when(shopMapper.selectById(20L)).thenReturn(enabledShop(20L, 200L));
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertTrue(service.messages(7L, 50L, 0L, 50).isEmpty());
        assertThrows(ServiceException.class, () -> service.sendMessage(7L,
                message(50L, "message-key-expired", "不应发送")));
        verify(messageMapper, never()).selectOne(any(Wrapper.class));
        verify(messageMapper, never()).insert(any(InquiryQuoteMessageDO.class));
    }

    @Test
    void buyerQuoteAccessRejectsEnterpriseReassignment() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(membership.requireBuyerHistory(7L)).thenReturn(buyer(999L));

        assertThrows(ServiceException.class, () -> service.get(7L, 50L));
        assertThrows(ServiceException.class, () -> service.messages(7L, 50L, 0L, 50));
        assertThrows(ServiceException.class, () -> service.page(7L, 10L,
                new cn.iocoder.txgy.framework.common.pojo.PageParam()));
        verifyNoInteractions(quoteItemMapper, messageMapper);
    }

    @Test
    void nonPartyCannotReadOrSendQuoteMessages() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_SUBMITTED);
        InquiryDO inquiry = new InquiryDO(); inquiry.setId(10L); inquiry.setMemberId(7L);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        when(inquiryMapper.selectOne(any(Wrapper.class))).thenReturn(inquiry);
        when(quoteMapper.selectOne(any(Wrapper.class))).thenReturn(quote);
        doThrow(new ServiceException(403, "无权访问")).when(membership)
                .requireSupplierShopHistory(8L, null, 20L);

        assertThrows(ServiceException.class, () -> service.messages(8L, 50L, 0L, 50));
        assertThrows(ServiceException.class, () -> service.sendMessage(8L,
                message(50L, "message-key-002", "越权消息")));
        verifyNoInteractions(messageMapper);
    }

    @Test
    void foreignSupplierCannotReadAnotherShopsQuote() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L);
        InquiryDO inquiry = new InquiryDO(); inquiry.setId(10L); inquiry.setMemberId(7L);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        doThrow(new ServiceException(403, "无权访问")).when(membership)
                .requireSupplierShopHistory(8L, null, 20L);

        assertThrows(ServiceException.class, () -> service.get(8L, 50L));
        verifyNoInteractions(quoteItemMapper);
    }

    @Test
    void buyerCannotReadOrMessageAnUnsubmittedSupplierDraft() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setShopId(20L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_DRAFT);
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L);
        when(quoteMapper.selectById(50L)).thenReturn(quote);
        when(inquiryMapper.selectById(10L)).thenReturn(inquiry);
        assertThrows(ServiceException.class, () -> service.get(7L, 50L));
        assertThrows(ServiceException.class, () -> service.messages(7L, 50L, 0L, 50));
        verifyNoInteractions(quoteItemMapper, messageMapper);
    }

    private static EnterpriseApplicationDO buyer(Long enterpriseId) {
        EnterpriseApplicationDO buyer = new EnterpriseApplicationDO();
        buyer.setEnterpriseId(enterpriseId); buyer.setIdentity("buyer"); buyer.setStatus(1); return buyer;
    }

    private static InquiryDO publishedInquiry(Long id, Long enterpriseId, String visibility) {
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(id); inquiry.setEnterpriseId(enterpriseId); inquiry.setVisibility(visibility);
        inquiry.setStatus(1); inquiry.setNo("XJ-" + id);
        inquiry.setDeadline(System.currentTimeMillis() + 86_400_000L);
        inquiry.setTitle("防腐保温材料询价");
        return inquiry;
    }

    private static InquiryCrossTenantMapper.OpportunityRef opportunityRef(Long inquiryId,
                                                                           Long inquiryTenantId,
                                                                           String visibility) {
        InquiryCrossTenantMapper.OpportunityRef ref = new InquiryCrossTenantMapper.OpportunityRef();
        ref.setInquiryId(inquiryId);
        ref.setInquiryTenantId(inquiryTenantId);
        ref.setVisibility(visibility);
        return ref;
    }

    private static EnterpriseApplicationDO supplier(Long enterpriseId) {
        EnterpriseApplicationDO supplier = new EnterpriseApplicationDO();
        supplier.setEnterpriseId(enterpriseId); supplier.setIdentity("supplier"); supplier.setStatus(1); return supplier;
    }

    private static ProductEnterpriseDO approvedEnterprise(Long id) {
        ProductEnterpriseDO enterprise = new ProductEnterpriseDO();
        enterprise.setId(id); enterprise.setAuditStatus(1); return enterprise;
    }

    private static ProductShopDO enabledShop(Long id, Long enterpriseId) {
        ProductShopDO shop = new ProductShopDO();
        shop.setId(id); shop.setEnterpriseId(enterpriseId); shop.setStatus(0); return shop;
    }

    private static InquiryQuoteDO acceptedQuote() {
        InquiryQuoteDO quote = new InquiryQuoteDO();
        quote.setId(50L); quote.setInquiryId(10L); quote.setEnterpriseId(200L); quote.setShopId(20L);
        quote.setStatus(PurchaseInquiryMarketService.QUOTE_ACCEPTED);
        quote.setValidUntil(System.currentTimeMillis() + 86_400_000L);
        return quote;
    }

    private static InquiryDO acceptedInquiry() {
        InquiryDO inquiry = new InquiryDO();
        inquiry.setId(10L); inquiry.setMemberId(7L); inquiry.setEnterpriseId(100L);
        inquiry.setStatus(4); inquiry.setSelectedQuoteId(50L); return inquiry;
    }

    private static InquiryQuoteMessageSendReqVO message(Long quoteId, String clientMessageId, String content) {
        InquiryQuoteMessageSendReqVO request = new InquiryQuoteMessageSendReqVO();
        request.setQuoteId(quoteId); request.setClientMessageId(clientMessageId); request.setContent(content);
        return request;
    }

    private void stubStoredQuoteLine(InquiryQuoteDO quote, Long currentSpuVersion) {
        quote.setGoodsPrice(2_000L); quote.setFreightPrice(100); quote.setTotalPrice(2_100L);
        InquiryItemDO requested = new InquiryItemDO();
        requested.setId(11L); requested.setInquiryId(quote.getInquiryId()); requested.setQuantity(BigDecimal.TWO);
        InquiryQuoteItemDO item = new InquiryQuoteItemDO();
        item.setQuoteId(quote.getId()); item.setInquiryItemId(11L); item.setSkuId(31L);
        item.setProductName("商城螺纹钢"); item.setSkuName("商城螺纹钢 直径：20mm");
        item.setSkuSpecification("直径：20mm"); item.setSpuContentVersion(3L);
        item.setCount(2); item.setUnitPrice(1_000); item.setTotalPrice(2_000L);
        ProductSkuDO sku = new ProductSkuDO(); sku.setId(31L); sku.setSpuId(41L);
        sku.setProperties(List.of(new ProductSkuDO.Property(1L, "直径", 2L, "20mm")));
        ProductSpuDO spu = new ProductSpuDO();
        spu.setId(41L); spu.setShopId(quote.getShopId()); spu.setName("商城螺纹钢");
        spu.setStatus(1); spu.setReviewStatus(20);
        spu.setContentVersion(currentSpuVersion); spu.setApprovedVersion(currentSpuVersion);
        when(quoteItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(inquiryItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(requested));
        when(skuMapper.selectById(31L)).thenReturn(sku);
        when(spuMapper.selectById(41L)).thenReturn(spu);
    }
}
