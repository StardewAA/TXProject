package cn.iocoder.txgy.module.trade.service.cart;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.api.sku.ProductSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.*;
import cn.iocoder.txgy.module.trade.convert.cart.TradeCartConvert;
import cn.iocoder.txgy.module.trade.dal.dataobject.cart.CartDO;
import cn.iocoder.txgy.module.trade.dal.mysql.cart.CartMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CartProductReviewTest {
    private final CartMapper carts = mock(CartMapper.class);
    private final ProductSkuApi skus = mock(ProductSkuApi.class);
    private final ProductSpuApi spus = mock(ProductSpuApi.class);
    private final CartServiceImpl service = new CartServiceImpl();
    private final ProductSkuRespDTO sku = new ProductSkuRespDTO().setId(2L).setSpuId(1L).setStock(10);
    private final ProductSpuRespDTO spu = new ProductSpuRespDTO().setId(1L).setName("未审核的新名称")
            .setStatus(1).setStock(10).setSaleable(true);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "cartMapper", carts);
        ReflectionTestUtils.setField(service, "productSkuApi", skus);
        ReflectionTestUtils.setField(service, "productSpuApi", spus);
        when(skus.getSku(2L)).thenReturn(sku);
        when(spus.validateSpuList(List.of(1L))).thenReturn(CommonResult.success(List.of(spu)));
    }

    @Test void approvedProductCanBeAdded() {
        when(carts.insert(any(CartDO.class))).thenAnswer(call -> { ((CartDO)call.getArgument(0)).setId(9L); return 1; });
        assertEquals(9L, service.addCart(3L, new AppCartAddReqVO().setSkuId(2L).setCount(1)));
        verify(spus).validateSpuList(List.of(1L));
    }

    @Test void pendingProductCannotBeAddedEvenWhenSkuExistsAndStatusWasTampered() {
        spu.setSaleable(false);
        assertThrows(RuntimeException.class, () -> service.addCart(3L, new AppCartAddReqVO().setSkuId(2L).setCount(1)));
        verify(carts, never()).insert(any(CartDO.class));
    }

    @Test void oldOrUnknownProductRpcCannotSilentlyAllowPurchase() {
        spu.setSaleable(null);
        assertThrows(RuntimeException.class, () -> service.addCart(3L, new AppCartAddReqVO().setSkuId(2L).setCount(1)));
        verify(carts, never()).insert(any(CartDO.class));
    }

    @Test void productServiceFailureNeverWritesCart() {
        when(spus.validateSpuList(any())).thenReturn(CommonResult.error(400, "商品审核版本不可售"));
        assertThrows(RuntimeException.class, () -> service.addCart(3L, new AppCartAddReqVO().setSkuId(2L).setCount(1)));
        verify(carts, never()).insert(any(CartDO.class));
    }

    @Test void changingQuantityRevalidatesCurrentProductReview() {
        when(carts.selectById(9L, 3L)).thenReturn(new CartDO().setId(9L).setSpuId(1L).setSkuId(2L));
        spu.setSaleable(false);
        assertThrows(RuntimeException.class, () -> service.updateCartCount(3L, new AppCartUpdateCountReqVO().setId(9L).setCount(2)));
        verify(carts, never()).updateById(any(CartDO.class));
    }

    @Test void invalidCartHidesUnreviewedContentAndPreservesDeleteIdentity() {
        spu.setSaleable(false);
        sku.setPrice(987654).setPicUrl("https://invalid.test/private-draft.png");
        var result = TradeCartConvert.INSTANCE.convertList(List.of(new CartDO().setId(9L).setSpuId(1L)
                .setSkuId(2L).setCount(1).setSelected(true)), List.of(spu), List.of(sku));
        assertTrue(result.getValidList().isEmpty());
        var row = result.getInvalidList().getFirst();
        assertEquals(9L, row.getId());
        assertEquals("商品暂不可购买", row.getSpu().getName());
        assertNull(row.getSku().getPicUrl());
        assertEquals(0, row.getSku().getPrice());
        assertEquals(2L, row.getSku().getId());
    }

    @Test void approvedButInsufficientSelectedSkuIsInvalid() {
        sku.setStock(1);
        var result = TradeCartConvert.INSTANCE.convertList(List.of(new CartDO().setId(9L).setSpuId(1L)
                .setSkuId(2L).setCount(2)), List.of(spu), List.of(sku));
        assertTrue(result.getValidList().isEmpty());
        assertEquals(1, result.getInvalidList().size());
    }
}
