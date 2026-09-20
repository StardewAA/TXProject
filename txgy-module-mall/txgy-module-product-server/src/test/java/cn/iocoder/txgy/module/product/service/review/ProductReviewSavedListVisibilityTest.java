package cn.iocoder.txgy.module.product.service.review;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.product.controller.app.favorite.AppFavoriteController;
import cn.iocoder.txgy.module.product.controller.app.favorite.vo.AppFavoritePageReqVO;
import cn.iocoder.txgy.module.product.controller.app.history.AppProductBrowseHistoryController;
import cn.iocoder.txgy.module.product.controller.app.history.vo.AppProductBrowseHistoryPageReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.favorite.ProductFavoriteDO;
import cn.iocoder.txgy.module.product.dal.dataobject.history.ProductBrowseHistoryDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.service.favorite.ProductFavoriteService;
import cn.iocoder.txgy.module.product.service.history.ProductBrowseHistoryService;
import cn.iocoder.txgy.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductReviewSavedListVisibilityTest {
    ProductSpuService products; ProductSpuReviewService review; ProductSpuDO draft,live;
    @BeforeEach void setup(){
        products=mock(ProductSpuService.class);review=mock(ProductSpuReviewService.class);
        draft=new ProductSpuDO().setId(10L).setName("未审核秘密新名").setPrice(999999).setPicUrl("https://private/new.png");
        live=new ProductSpuDO().setId(20L).setName("已审核商品").setPrice(100).setPicUrl("https://public/old.png");
        when(review.saleable(live)).thenReturn(true);
        when(products.getSpuList(anyCollection())).thenReturn(Arrays.asList(draft,live,null));
    }
    @Test void favoritesKeepRelationAndTotalButNeverShowUnreviewedOrDeletedContent(){
        var service=mock(ProductFavoriteService.class);
        when(service.getFavoritePage(any(),any(AppFavoritePageReqVO.class))).thenReturn(new PageResult<>(List.of(
                new ProductFavoriteDO().setId(1L).setSpuId(10L),new ProductFavoriteDO().setId(2L).setSpuId(20L),new ProductFavoriteDO().setId(3L).setSpuId(30L)),8L));
        var controller=new AppFavoriteController();ReflectionTestUtils.setField(controller,"productFavoriteService",service);
        ReflectionTestUtils.setField(controller,"productSpuService",products);ReflectionTestUtils.setField(controller,"reviewService",review);
        var page=controller.getFavoritePage(new AppFavoritePageReqVO()).getCheckedData();assertEquals(8L,page.getTotal());assertEquals(3,page.getList().size());
        var first=page.getList().getFirst();assertEquals(1L,first.getId());assertEquals(10L,first.getSpuId());assertFalse(first.getSaleable());
        assertEquals("商品暂不可购买",first.getSpuName());assertNull(first.getPrice());assertEquals("",first.getPicUrl());
        assertEquals("已审核商品",page.getList().get(1).getSpuName());assertEquals(100,page.getList().get(1).getPrice());
        assertEquals(30L,page.getList().get(2).getSpuId());assertFalse(page.getList().get(2).getSaleable());
        verify(service,never()).deleteFavorite(any(),any());
    }
    @Test void browsingHistoryKeepsRelationAndTotalWithSafeUnavailablePlaceholders(){
        var history=mock(ProductBrowseHistoryService.class);
        when(history.getBrowseHistoryPage(any())).thenReturn(new PageResult<>(List.of(
                new ProductBrowseHistoryDO().setId(1L).setSpuId(10L),new ProductBrowseHistoryDO().setId(2L).setSpuId(20L),new ProductBrowseHistoryDO().setId(3L).setSpuId(30L)),9L));
        var controller=new AppProductBrowseHistoryController();ReflectionTestUtils.setField(controller,"productBrowseHistoryService",history);
        ReflectionTestUtils.setField(controller,"productSpuService",products);ReflectionTestUtils.setField(controller,"reviewService",review);
        var page=controller.getBrowseHistoryPage(new AppProductBrowseHistoryPageReqVO()).getCheckedData();assertEquals(9L,page.getTotal());assertEquals(3,page.getList().size());
        var first=page.getList().getFirst();assertEquals(1L,first.getId());assertEquals(10L,first.getSpuId());assertFalse(first.getSaleable());
        assertEquals("商品暂不可购买",first.getSpuName());assertNull(first.getPrice());assertNull(first.getStock());assertEquals("",first.getPicUrl());
        assertTrue(page.getList().get(1).getSaleable());assertEquals("已审核商品",page.getList().get(1).getSpuName());
        assertEquals(30L,page.getList().get(2).getSpuId());assertFalse(page.getList().get(2).getSaleable());
        verify(history,never()).hideUserBrowseHistory(any(),any());
    }
}
