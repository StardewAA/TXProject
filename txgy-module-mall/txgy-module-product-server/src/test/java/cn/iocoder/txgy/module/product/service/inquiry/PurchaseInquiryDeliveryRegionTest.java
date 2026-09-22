package cn.iocoder.txgy.module.product.service.inquiry;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.EnterpriseApplicationDO;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.InquiryInvitedScopeMapper;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.InquiryInvitedShopMapper;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.InquiryItemMapper;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.InquiryMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseInquiryDeliveryRegionTest {

    @InjectMocks private PurchaseInquiryService service;
    @Mock private InquiryMapper mapper;
    @Mock private InquiryItemMapper itemMapper;
    @Mock private InquiryInvitedShopMapper invitedShopMapper;
    @Mock private InquiryInvitedScopeMapper invitedScopeMapper;
    @Mock private EnterpriseMembershipService membership;
    @Mock private ProductSkuMapper skuMapper;
    @Mock private ProductSpuMapper spuMapper;
    @Mock private ProductShopMapper shopMapper;
    @Mock private ProductEnterpriseMapper enterpriseMapper;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        EnterpriseApplicationDO buyer = new EnterpriseApplicationDO();
        buyer.setEnterpriseId(100L);
        buyer.setIdentity("buyer");
        buyer.setStatus(1);
        when(membership.requireBuyer(7L)).thenReturn(buyer);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void platformPublicRequiresStructuredDistrictInsteadOfParsingAddressText() {
        InquirySaveReqVO request = request(null);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.save(7L, request));

        assertTrue(error.getMessage().contains("交付区县"));
        verify(mapper, never()).insert(any(InquiryDO.class));
    }

    @Test
    void platformPublicPersistsValidatedAreaHierarchyForSupplyMatching() {
        InquirySaveReqVO request = request(110105);
        doAnswer(invocation -> {
            invocation.<InquiryDO>getArgument(0).setId(99L);
            return 1;
        }).when(mapper).insert(any(InquiryDO.class));

        assertEquals(99L, service.save(7L, request));

        ArgumentCaptor<InquiryDO> captor = ArgumentCaptor.forClass(InquiryDO.class);
        verify(mapper).insert(captor.capture());
        InquiryDO row = captor.getValue();
        assertEquals(110105, row.getDeliveryAreaId());
        assertEquals(110100, row.getDeliveryCityId());
        assertEquals(110000, row.getDeliveryProvinceId());
    }

    @Test
    void legacyPlatformPublicDraftWithoutStructuredRegionCannotBypassSaveAndSubmitDirectly() {
        InquiryDO row = new InquiryDO();
        row.setId(99L);
        row.setMemberId(7L);
        row.setEnterpriseId(100L);
        row.setStatus(0);
        row.setDeadline(System.currentTimeMillis() + 86_400_000L);
        row.setVisibility(PurchaseInquiryService.VISIBILITY_PLATFORM_PUBLIC);
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.submit(7L, 99L));

        assertTrue(error.getMessage().contains("交付区县"));
        verify(mapper, never()).updateById(any(InquiryDO.class));
        verifyNoInteractions(itemMapper);
    }

    private static InquirySaveReqVO request(Integer deliveryAreaId) {
        InquirySaveReqVO request = new InquirySaveReqVO();
        request.setRequestKey("delivery-region-0001");
        request.setTitle("防腐材料采购");
        request.setContactName("采购员");
        request.setContactMobile("13800138000");
        request.setAddress("北京市朝阳区测试地址");
        request.setDeliveryAreaId(deliveryAreaId);
        request.setDeadline(System.currentTimeMillis() + 86_400_000L);
        request.setVisibility(PurchaseInquiryService.VISIBILITY_PLATFORM_PUBLIC);
        InquirySaveReqVO.Item item = new InquirySaveReqVO.Item();
        item.setName("防腐涂料");
        item.setCategory("防腐材料");
        item.setQuantity(BigDecimal.ONE);
        item.setUnit("桶");
        request.setItems(List.of(item));
        return request;
    }
}
