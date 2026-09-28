package cn.iocoder.txgy.module.promotion.service.kefu.chat;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.txgy.module.trade.api.order.TradeOrderApi;
import cn.iocoder.txgy.module.trade.api.order.dto.TradeOrderServiceCardRespDTO;
import org.junit.jupiter.api.*;
import java.util.List;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeFuChatCardServiceTest {
    ProductSpuApi products = mock(ProductSpuApi.class);
    ProductShopApi shops = mock(ProductShopApi.class);
    TradeOrderApi orders = mock(TradeOrderApi.class);
    KeFuChatCardService service = new KeFuChatCardService(products, shops, orders);
    Actor buyer = new Actor(11, 1);
    Session session = new Session("session", 1, "SHOP", 101, 11, "WAITING", null, null, 0, null, null);
    ProductSpuRespDTO product = new ProductSpuRespDTO().setId(9L).setShopId(101L).setName("保温板")
            .setSaleable(true).setStatus(1).setPrice(1200).setCostPrice(800).setPicUrl("https://example.test/a.jpg");
    TradeOrderServiceCardRespDTO order = new TradeOrderServiceCardRespDTO().setId(8L).setTenantId(1L)
            .setUserId(11L).setShopId(101L).setNo("T8").setPayPrice(2400).setStatus(20);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(products.validateSpuList(List.of(9L))).thenReturn(CommonResult.success(List.of(product)));
        when(shops.getShop(101L)).thenReturn(CommonResult.success(new ProductShopRespDTO().setId(101L).setStatus(0)));
        when(orders.getServiceCard(8L,11L,1L)).thenReturn(CommonResult.success(order));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void productUsesOnlyPublicServerFields() {
        var card=service.build(session,buyer,"PRODUCT",9L);
        assertEquals("保温板",card.title());assertEquals(1200,card.amount());assertEquals(9L,card.spuId());assertNull(card.orderId());
        assertFalse(card.toString().contains("800"));verifyNoInteractions(orders);
    }
    @Test void orderSnapshotRequiresOwnerAndDoesNotExposePrivateFields() {
        var card=service.build(session,buyer,"ORDER",8L);
        assertEquals("订单 T8",card.title());assertEquals(2400,card.amount());assertNull(card.picUrl());assertNull(card.spuId());
        assertEquals(8L,card.orderId());verifyNoInteractions(products,shops);
    }
    @Test void actorAndTenantRejectedBeforeAnyRpc() {
        assertThrows(ServiceException.class,()->service.build(session,new Actor(11,2),"ORDER",8L));
        assertThrows(ServiceException.class,()->service.build(session,new Actor(22,1),"ORDER",8L));
        TenantContextHolder.setTenantId(2L);assertThrows(ServiceException.class,()->service.build(session,buyer,"ORDER",8L));
        TenantContextHolder.setTenantId(1L);TenantContextHolder.setIgnore(true);assertThrows(ServiceException.class,()->service.build(session,buyer,"ORDER",8L));
        verifyNoInteractions(products,shops,orders);
    }
    @Test void invalidTypeIdAndSessionScopeRejected() {
        for(String type:List.of("AFTERSALE","order","URL"))assertThrows(ServiceException.class,()->service.build(session,buyer,type,8L));
        assertThrows(ServiceException.class,()->service.build(session,buyer,"ORDER",0L));
        assertThrows(ServiceException.class,()->service.build(session,buyer,"ORDER",null));
        assertThrows(ServiceException.class,()->service.build(session,buyer,"ORDER",Long.MAX_VALUE));
        var bad=new Session("session",1,"PLATFORM",101,11,"WAITING",null,null,0,null,null);
        assertThrows(ServiceException.class,()->service.build(bad,buyer,"ORDER",8L));verifyNoInteractions(products,shops,orders);
    }
    @Test void crossShopProductAndOrderDenied() {
        product.setShopId(102L);assertThrows(ServiceException.class,()->service.build(session,buyer,"PRODUCT",9L));
        order.setShopId(102L);assertThrows(ServiceException.class,()->service.build(session,buyer,"ORDER",8L));
    }
    @Test void foreignTenantOwnerOrWrongOrderIdDeniedEvenIfRpcLeaks() {
        order.setTenantId(2L);assertThrows(ServiceException.class,()->service.build(session,buyer,"ORDER",8L));
        order.setTenantId(1L).setUserId(22L);assertThrows(ServiceException.class,()->service.build(session,buyer,"ORDER",8L));
        order.setUserId(11L).setId(7L);assertThrows(ServiceException.class,()->service.build(session,buyer,"ORDER",8L));
    }
    @Test void missingUnapprovedOffShelfProductDenied() {
        product.setSaleable(false);assertThrows(ServiceException.class,()->service.build(session,buyer,"PRODUCT",9L));
        product.setSaleable(true).setStatus(0);assertThrows(ServiceException.class,()->service.build(session,buyer,"PRODUCT",9L));
        when(products.validateSpuList(List.of(9L))).thenReturn(CommonResult.success(List.of()));
        assertThrows(ServiceException.class,()->service.build(session,buyer,"PRODUCT",9L));
    }
    @Test void closedShopProductDeniedButPlatformOwnHistoricalOrderAllowed() {
        when(shops.getShop(101L)).thenReturn(CommonResult.success(new ProductShopRespDTO().setId(101L).setStatus(1)));
        assertThrows(ServiceException.class,()->service.build(session,buyer,"PRODUCT",9L));
        var platform=new Session("session",1,"PLATFORM",0,11,"WAITING",null,null,0,null,null);
        assertEquals(101,service.build(platform,buyer,"ORDER",8L).shopId());
    }
    @Test void unsafeOrCredentialedImageCannotBecomeExecutableContent() {
        for(String image:List.of("javascript:alert(1)","data:image/svg+xml,x","//example.test/a","https://u:p@example.test/a","http://","x")) {
            product.setPicUrl(image);assertNull(service.build(session,buyer,"PRODUCT",9L).picUrl());
        }
        assertEquals("http://localhost:9000/p.jpg",KeFuChatCardService.safeImage("http://localhost:9000/p.jpg"));
    }
    @Test void negativeAmountIsUnknownNotFree() {
        product.setPrice(-1);assertNull(service.build(session,buyer,"PRODUCT",9L).amount());
        order.setPayPrice(-1);assertNull(service.build(session,buyer,"ORDER",8L).amount());
    }
}
