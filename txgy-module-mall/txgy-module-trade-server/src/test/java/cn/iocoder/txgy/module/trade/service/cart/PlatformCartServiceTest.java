package cn.iocoder.txgy.module.trade.service.cart;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.member.api.platform.MemberPlatformAccountApi;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.product.api.sku.ProductPublicSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartAddReqVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartUpdateCountReqVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartUpdateSelectedReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.cart.PlatformCartDO;
import cn.iocoder.txgy.module.trade.dal.mysql.cart.PlatformCartMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformCartServiceTest {

    private PlatformCartMapper mapper;
    private ProductPublicSkuApi productApi;
    private MemberPlatformAccountApi accountApi;
    private PlatformCartService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PlatformCartMapper.class);
        productApi = mock(ProductPublicSkuApi.class);
        accountApi = mock(MemberPlatformAccountApi.class);
        service = new PlatformCartService(mapper, productApi, accountApi);
        when(accountApi.resolve(316L, 2009L)).thenReturn(CommonResult.success(
                new MemberPlatformAccountRespDTO().setPlatformAccountId(9001L)
                        .setLegacyMemberId(316L).setSourceTenantId(2009L)));
    }

    @Test
    void addPersistsOnlyServerResolvedOwnershipAndPlatformAccount() {
        ProductPublicSkuRespDTO sku = validSku(601L, 2001L, 301L, 401L, 501L);
        when(productApi.getOwnershipList(List.of(601L))).thenReturn(List.of(sku));
        when(mapper.selectByMemberAndSkuId(9001L, 601L)).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<PlatformCartDO>getArgument(0).setId(701L);
            return 1;
        }).when(mapper).insert(any(PlatformCartDO.class));

        Long id = service.add(316L, 2009L,
                new AppPlatformCartAddReqVO().setSkuId(601L).setQuantity(2));

        assertEquals(701L, id);
        ArgumentCaptor<PlatformCartDO> inserted = ArgumentCaptor.forClass(PlatformCartDO.class);
        verify(mapper).insert(inserted.capture());
        assertEquals(9001L, inserted.getValue().getPlatformMemberId());
        assertEquals(2001L, inserted.getValue().getTenantId());
        assertEquals(301L, inserted.getValue().getEnterpriseId());
        assertEquals(401L, inserted.getValue().getShopId());
        assertEquals(501L, inserted.getValue().getSpuId());
    }

    @Test
    void addRejectsClosedOrPublicTenantSkuWithoutWriting() {
        when(productApi.getOwnershipList(List.of(601L))).thenReturn(List.of());

        assertThrows(ServiceException.class, () -> service.add(316L, 2009L,
                new AppPlatformCartAddReqVO().setSkuId(601L).setQuantity(1)));

        verify(mapper, never()).insert(any(PlatformCartDO.class));
    }

    @Test
    void addExistingUsesBoundedAtomicIncrement() {
        ProductPublicSkuRespDTO sku = validSku(601L, 2001L, 301L, 401L, 501L).setStock(5);
        PlatformCartDO existing = cart(8L, 2001L, 301L, 401L, 501L, 601L, 2);
        when(productApi.getOwnershipList(List.of(601L))).thenReturn(List.of(sku));
        when(mapper.selectByMemberAndSkuId(9001L, 601L)).thenReturn(existing);
        when(mapper.incrementQuantityAndSelect(8L, 9001L, 2, 5)).thenReturn(1);

        Long id = service.add(316L, 2009L,
                new AppPlatformCartAddReqVO().setSkuId(601L).setQuantity(2));

        assertEquals(8L, id);
        verify(mapper).incrementQuantityAndSelect(8L, 9001L, 2, 5);
        verify(mapper, never()).insert(any(PlatformCartDO.class));
    }

    @Test
    void listGroupsByTenantThenShopAndPreservesInvalidRows() {
        PlatformCartDO valid = cart(1L, 2001L, 301L, 401L, 501L, 601L, 2);
        PlatformCartDO invalid = cart(2L, 2002L, 302L, 402L, 502L, 602L, 3);
        when(mapper.selectListByMember(9001L)).thenReturn(List.of(valid, invalid));
        when(productApi.getOwnershipList(List.of(601L, 602L)))
                .thenReturn(List.of(validSku(601L, 2001L, 301L, 401L, 501L)));
        when(mapper.updateInvalidReason(anyLong(), anyLong(), any())).thenReturn(1);

        var result = service.list(316L, 2009L);

        assertEquals(2, result.getTenantGroups().size());
        assertEquals(5, result.getTotalQuantity());
        assertEquals(2, result.getSelectedQuantity());
        assertTrue(result.getTenantGroups().getFirst().getShops().getFirst().getItems().getFirst().getValid());
        var invalidItem = result.getTenantGroups().get(1).getShops().getFirst().getItems().getFirst();
        assertFalse(invalidItem.getValid());
        assertEquals("所属租户未开放或商品不存在", invalidItem.getInvalidReason());
        verify(mapper).updateInvalidReason(2L, 9001L, "所属租户未开放或商品不存在");
        verify(mapper, never()).deleteByIdsAndMember(any(), anyLong());
    }

    @Test
    void listNeverExposesCurrentFieldsFromAnInvalidUnreviewedSku() {
        PlatformCartDO cart = cart(1L, 2001L, 301L, 401L, 501L, 601L, 2);
        ProductPublicSkuRespDTO draft = validSku(601L, 2001L, 301L, 401L, 501L)
                .setSaleable(false).setInvalidReason("商品审核未通过或已变更")
                .setSpuName("未审核的新名称").setPicUrl("https://private.invalid/draft.png")
                .setPrice(987654);
        when(mapper.selectListByMember(9001L)).thenReturn(List.of(cart));
        when(productApi.getOwnershipList(List.of(601L))).thenReturn(List.of(draft));
        when(mapper.updateInvalidReason(1L, 9001L, "商品审核未通过或已变更")).thenReturn(1);

        var item = service.list(316L, 2009L).getTenantGroups().getFirst()
                .getShops().getFirst().getItems().getFirst();

        assertFalse(item.getValid());
        assertNull(item.getSpuName());
        assertNull(item.getPicUrl());
        assertNull(item.getPrice());
        assertTrue(item.getProperties().isEmpty());
    }

    @Test
    void updateCannotTouchAnotherPlatformAccountsItem() {
        when(mapper.selectByIdAndMember(99L, 9001L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.updateCount(316L, 2009L,
                new AppPlatformCartUpdateCountReqVO().setId(99L).setQuantity(2)));

        verify(productApi, never()).getOwnershipList(any());
        verify(mapper, never()).updateQuantity(anyLong(), anyLong(), any());
    }

    @Test
    void updateCountDoesNotImplicitlySelectTheItem() {
        PlatformCartDO cart = cart(9L, 2001L, 301L, 401L, 501L, 601L, 2).setSelected(false);
        when(mapper.selectByIdAndMember(9L, 9001L)).thenReturn(cart);
        when(productApi.getOwnershipList(List.of(601L)))
                .thenReturn(List.of(validSku(601L, 2001L, 301L, 401L, 501L)));
        when(mapper.updateQuantity(9L, 9001L, 4)).thenReturn(1);

        service.updateCount(316L, 2009L,
                new AppPlatformCartUpdateCountReqVO().setId(9L).setQuantity(4));

        verify(mapper).updateQuantity(9L, 9001L, 4);
        verify(mapper, never()).incrementQuantityAndSelect(anyLong(), anyLong(), any(), any());
    }

    @Test
    void platformAccountResolverMismatchFailsClosedBeforeProductLookup() {
        when(accountApi.resolve(316L, 2009L)).thenReturn(CommonResult.success(
                new MemberPlatformAccountRespDTO().setPlatformAccountId(9001L)
                        .setLegacyMemberId(316L).setSourceTenantId(1L)));

        assertThrows(ServiceException.class, () -> service.add(316L, 2009L,
                new AppPlatformCartAddReqVO().setSkuId(601L).setQuantity(1)));

        verify(productApi, never()).getOwnershipList(any());
        verify(mapper, never()).insert(any(PlatformCartDO.class));
    }

    @Test
    void selectingInvalidItemRecordsReasonAndDoesNotSelectAnything() {
        PlatformCartDO invalid = cart(2L, 2002L, 302L, 402L, 502L, 602L, 3);
        when(mapper.selectListByIdsAndMember(java.util.Set.of(2L), 9001L)).thenReturn(List.of(invalid));
        when(productApi.getOwnershipList(List.of(602L))).thenReturn(List.of());
        when(mapper.updateInvalidReason(2L, 9001L, "所属租户未开放或商品不存在")).thenReturn(1);

        assertThrows(ServiceException.class, () -> service.updateSelected(316L, 2009L,
                new AppPlatformCartUpdateSelectedReqVO().setIds(List.of(2L)).setSelected(true)));

        verify(mapper).updateInvalidReason(2L, 9001L, "所属租户未开放或商品不存在");
        verify(mapper, never()).updateSelected(anyLong(), any(), any());
    }

    private static PlatformCartDO cart(Long id, Long tenantId, Long enterpriseId, Long shopId,
                                       Long spuId, Long skuId, int quantity) {
        return new PlatformCartDO().setId(id).setPlatformMemberId(9001L).setTenantId(tenantId)
                .setEnterpriseId(enterpriseId).setShopId(shopId).setSpuId(spuId).setSkuId(skuId)
                .setQuantity(quantity).setSelected(true);
    }

    private static ProductPublicSkuRespDTO validSku(Long skuId, Long tenantId, Long enterpriseId,
                                                     Long shopId, Long spuId) {
        return new ProductPublicSkuRespDTO().setSkuId(skuId).setTenantId(tenantId)
                .setTenantName("租户 " + tenantId).setEnterpriseId(enterpriseId)
                .setEnterpriseName("企业 " + enterpriseId).setShopId(shopId)
                .setShopName("店铺 " + shopId).setSpuId(spuId).setSpuName("商品 " + spuId)
                .setPrice(1200).setStock(100).setSaleable(true).setProperties(List.of());
    }
}
