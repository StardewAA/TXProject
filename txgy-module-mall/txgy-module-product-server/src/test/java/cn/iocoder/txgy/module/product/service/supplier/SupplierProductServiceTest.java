package cn.iocoder.txgy.module.product.service.supplier;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.controller.app.supplier.SupplierProductSaveReqVO;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.service.spu.ProductSpuService;
import cn.iocoder.txgy.module.product.service.category.ProductCategoryService;
import cn.iocoder.txgy.module.product.service.brand.ProductBrandService;
import cn.iocoder.txgy.module.trade.api.delivery.DeliveryTemplateApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupplierProductServiceTest {
    SupplierProductService service;
    EnterpriseMembershipService membership;
    ProductSpuMapper spus;
    ProductSkuMapper skus;
    ProductSpuService spuService;
    ProductCategoryService categories;
    DeliveryTemplateApi delivery;
    cn.iocoder.txgy.module.product.service.property.ProductPropertyService properties;
    cn.iocoder.txgy.module.product.service.property.ProductPropertyValueService values;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        service = new SupplierProductService();
        membership = mock(EnterpriseMembershipService.class); spus = mock(ProductSpuMapper.class);
        skus = mock(ProductSkuMapper.class); spuService = mock(ProductSpuService.class);
        categories = mock(ProductCategoryService.class); delivery = mock(DeliveryTemplateApi.class);
        ReflectionTestUtils.setField(service, "membership", membership);
        ReflectionTestUtils.setField(service, "spuMapper", spus);
        ReflectionTestUtils.setField(service, "skuMapper", skus);
        ReflectionTestUtils.setField(service, "spuService", spuService);
        ReflectionTestUtils.setField(service, "reviewService", mock(cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService.class));
        ReflectionTestUtils.setField(service, "categoryService", categories);
        ReflectionTestUtils.setField(service, "brandService", mock(ProductBrandService.class));
        ReflectionTestUtils.setField(service, "deliveryApi", delivery);
        properties=mock(cn.iocoder.txgy.module.product.service.property.ProductPropertyService.class);
        values=mock(cn.iocoder.txgy.module.product.service.property.ProductPropertyValueService.class);
        ReflectionTestUtils.setField(service,"propertyService",properties);ReflectionTestUtils.setField(service,"valueService",values);
        when(categories.getCategoryLevel(anyLong())).thenReturn(3);
        when(delivery.list()).thenReturn(CommonResult.success(List.of(new DeliveryTemplateApi.Template(1L, "按件", 1))));
    }
    @AfterEach void clearTenantContext() {
        TenantContextHolder.clear();
    }
    SupplierProductSaveReqVO request() {
        return new SupplierProductSaveReqVO().setShopId(10L).setName("商品").setKeyword("材料")
                .setIntroduction("简介").setDescriptionText("<script>alert(1)</script>").setCategoryId(2L)
                .setBrandId(3L).setPicUrl("https://example.com/product.png").setDeliveryTemplateId(1L)
                .setSkus(List.of(new SupplierProductSaveReqVO.Sku().setPrice(123).setMarketPrice(150).setWeight(1D).setVolume(1D)));
    }
    void owned() {
        when(spus.selectOne(any())).thenReturn(new ProductSpuDO().setId(20L).setShopId(10L).setStatus(0));
        when(skus.selectListBySpuId(20L)).thenReturn(List.of(new ProductSkuDO().setId(30L).setSpuId(20L).setStock(9)));
    }
    @Test void deniedShopStopsBeforeDataRead() {
        doThrow(new ServiceException(403, "无权")).when(membership).requireShop(1L,10L);
        assertThrows(ServiceException.class, () -> service.page(1L,10L,new PageParam(),null,null,null));
        verifyNoInteractions(spus);
    }
    @Test void foreignProductCannotBeRead() {
        assertThrows(ServiceException.class, () -> service.detail(1L,10L,999L));
        verifyNoInteractions(skus);
    }
    @Test void quoteSkuOptionsExposeAllEligibleSkuSpecificationsWithoutSpuDetailFanOut() {
        ProductSpuDO product = new ProductSpuDO().setId(20L).setShopId(10L).setName("耐高温保温板")
                .setPicUrl("https://example.com/product.png").setStatus(1).setReviewStatus(20)
                .setContentVersion(3L).setApprovedVersion(3L);
        ProductSkuDO first = new ProductSkuDO().setId(31L).setSpuId(20L).setPrice(8800)
                .setBarCode("SKU-31").setProperties(List.of(
                        new ProductSkuDO.Property(5L, "厚度", 6L, "50mm")));
        ProductSkuDO second = new ProductSkuDO().setId(32L).setSpuId(20L).setPrice(12800)
                .setBarCode("SKU-32").setPicUrl("https://example.com/sku-32.png");
        when(spus.selectList(any())).thenReturn(List.of(product));
        when(skus.selectListBySpuId(anyCollection())).thenReturn(List.of(first, second));

        var result = service.quoteSkuOptions(1L, 10L);

        verify(membership).requireShop(1L, 10L);
        assertEquals(2, result.size());
        assertEquals("耐高温保温板", result.getFirst().productName());
        assertEquals("厚度：50mm", result.getFirst().specification());
        assertEquals("默认规格", result.get(1).specification());
        assertEquals("https://example.com/sku-32.png", result.get(1).picUrl());
        verify(skus).selectListBySpuId(anyCollection());
    }
    @Test void unknownSkuCannotBeWritten() {
        owned(); var req=request().setId(20L); req.getSkus().getFirst().setId(999L);
        assertThrows(ServiceException.class, () -> service.save(1L,req));
        verify(skus,never()).updateById(any(ProductSkuDO.class));
    }
    @Test void existingStockOverwriteRejected() {
        owned(); var req=request().setId(20L); req.getSkus().getFirst().setId(30L).setInitialStock(100);
        assertThrows(ServiceException.class, () -> service.save(1L,req));
        verify(skus,never()).updateById(any(ProductSkuDO.class));
    }
    @Test void updateDoesNotWriteInventoryMappingOrCounters() {
        owned(); var req=request().setId(20L); req.getSkus().getFirst().setId(30L);
        assertEquals(20L,service.save(1L,req));
        var sku=ArgumentCaptor.forClass(ProductSkuDO.class); verify(skus).updateById(sku.capture());
        assertNull(sku.getValue().getStock()); assertNull(sku.getValue().getWmsSkuId());
        assertNull(sku.getValue().getWmsWarehouseId()); assertNull(sku.getValue().getCostPrice());
        assertNull(sku.getValue().getFirstBrokeragePrice());
        assertNull(sku.getValue().getPicUrl()); // 旧客户端不传则不改图
        var spu=ArgumentCaptor.forClass(ProductSpuDO.class); verify(spus).updateById(spu.capture());
        assertNull(spu.getValue().getStock()); assertNull(spu.getValue().getShopId());
        assertNull(spu.getValue().getSalesCount()); assertNull(spu.getValue().getBrowseCount());
        assertNull(spu.getValue().getSliderPicUrls()); // 旧客户端未传，不覆盖轮播图
        assertTrue(spu.getValue().getDescription().startsWith("&lt;script&gt;"));
    }
    @Test void createUsesLocalSingleSkuAndStartsOffline() {
        when(spuService.createSpu(any())).thenReturn(20L);
        assertEquals(20L,service.save(1L,request()));
        var create=ArgumentCaptor.forClass(ProductSpuSaveReqVO.class); verify(spuService).createSpu(create.capture());
        assertEquals(10L,create.getValue().getShopId()); assertFalse(create.getValue().getSpecType());
        assertNull(create.getValue().getSkus().getFirst().getWmsSkuId());
        var patch=ArgumentCaptor.forClass(ProductSpuDO.class); verify(spus).updateById(patch.capture());
        assertEquals(0,patch.getValue().getStatus());
    }
    @Test void invalidTemplateRejected() {
        assertThrows(ServiceException.class, () -> service.save(1L,request().setDeliveryTemplateId(99L)));
        verifyNoInteractions(spuService);
    }
    SupplierProductSaveReqVO multi(){
        when(properties.getPropertyList()).thenReturn(List.of(new cn.iocoder.txgy.module.product.dal.dataobject.property.ProductPropertyDO().setId(5L).setName("厚度")));
        when(values.getPropertyValueListByPropertyId(any())).thenReturn(List.of(new cn.iocoder.txgy.module.product.dal.dataobject.property.ProductPropertyValueDO().setId(6L).setPropertyId(5L).setName("10mm"),new cn.iocoder.txgy.module.product.dal.dataobject.property.ProductPropertyValueDO().setId(7L).setPropertyId(5L).setName("20mm")));
        var req=request().setSpecType(true);var first=req.getSkus().getFirst().setProperties(List.of(new SupplierProductSaveReqVO.Property().setPropertyId(5L).setValueId(6L)));
        var second=request().getSkus().getFirst().setProperties(List.of(new SupplierProductSaveReqVO.Property().setPropertyId(5L).setValueId(7L))).setInitialStock(9);
        req.setSkus(List.of(first,second));return req;
    }
    @Test void multiCreatesRealPropertiesAndIndependentLocalStocks(){
        var req=multi();when(spuService.createSpu(any())).thenReturn(20L);service.save(1L,req);
        verify(spuService).createSpu(argThat(r->Boolean.TRUE.equals(r.getSpecType())&&r.getSkus().size()==2&&r.getSkus().get(1).getStock()==9&&r.getSkus().getFirst().getProperties().getFirst().getPropertyName().equals("厚度")&&r.getSkus().getFirst().getWmsSkuId()==null));
    }
    @Test void independentSkuPicturesOnCreate(){var req=multi();req.getSkus().getFirst().setPicUrl("https://example.com/sku.png");when(spuService.createSpu(any())).thenReturn(20L);service.save(1L,req);verify(spuService).createSpu(argThat(r->r.getSkus().getFirst().getPicUrl().equals("https://example.com/sku.png")&&r.getSkus().get(1).getPicUrl().equals(req.getPicUrl())));}
    @Test void editingPicturePreservesInventoryAndProperties(){owned();var req=request().setId(20L);req.getSkus().getFirst().setId(30L).setPicUrl("https://example.com/sku.png");service.save(1L,req);verify(skus).updateById(argThat((ProductSkuDO s)->s.getPicUrl().equals("https://example.com/sku.png")&&s.getStock()==null&&s.getProperties()==null&&s.getWmsSkuId()==null&&s.getSalesCount()==null));}
    @Test void emptyPictureRestoresCurrentMain(){owned();var req=request().setId(20L);req.getSkus().getFirst().setId(30L).setPicUrl("");service.save(1L,req);verify(skus).updateById(argThat((ProductSkuDO s)->s.getPicUrl().equals(req.getPicUrl())));}
    @Test void unsafeSkuImageRejected(){var req=request();req.getSkus().getFirst().setPicUrl("javascript:alert(1)");assertThrows(ServiceException.class,()->service.save(1L,req));verifyNoInteractions(spuService);}
    @Test void duplicateCombinationDenied(){var req=multi();req.getSkus().get(1).getProperties().getFirst().setValueId(6L);assertThrows(ServiceException.class,()->service.save(1L,req));verifyNoInteractions(spuService);}
    @Test void mismatchedOrMissingPropertyDenied(){var req=multi();req.getSkus().getFirst().getProperties().getFirst().setValueId(999L);assertThrows(ServiceException.class,()->service.save(1L,req));req.getSkus().getFirst().setProperties(null);assertThrows(ServiceException.class,()->service.save(1L,req));verifyNoInteractions(spuService);}
    @Test void editingCannotReplaceProperties(){owned();var req=request().setId(20L);req.getSkus().getFirst().setId(30L).setProperties(List.of(new SupplierProductSaveReqVO.Property().setPropertyId(5L).setValueId(6L)));assertThrows(ServiceException.class,()->service.save(1L,req));verify(skus,never()).updateById(any(ProductSkuDO.class));}
    @Test void galleryValidatesMainDuplicateAndUnsafeUrl() {
        var req=request();
        for(var images:List.of(List.<String>of(),List.of("https://example.com/other.png"),List.of(req.getPicUrl(),req.getPicUrl()),List.of(req.getPicUrl(),"javascript:alert(1)"))){
            req.setSliderPicUrls(images);assertThrows(ServiceException.class,()->service.save(1L,req));
        }
        verifyNoInteractions(spuService);
    }
    @Test void createsGalleryInRequestedOrder() {
        var req=request().setSliderPicUrls(List.of("https://example.com/product.png","https://example.com/second.png"));
        when(spuService.createSpu(any())).thenReturn(20L);service.save(1L,req);
        verify(spuService).createSpu(argThat(r->r.getSliderPicUrls().equals(req.getSliderPicUrls())));
    }
    @Test void updatesGalleryWithoutChangingStock() {
        owned();var req=request().setId(20L).setSliderPicUrls(List.of("https://example.com/product.png","https://example.com/second.png"));req.getSkus().getFirst().setId(30L);
        service.save(1L,req);
        verify(spus).updateById(argThat((ProductSpuDO p)->p.getSliderPicUrls().equals(req.getSliderPicUrls())&&p.getStock()==null));
    }
    @Test void weightedTemplateRequiresWeight() {
        when(delivery.list()).thenReturn(CommonResult.success(List.of(new DeliveryTemplateApi.Template(1L,"按重",2))));
        var req=request(); req.getSkus().getFirst().setWeight(0D);
        assertThrows(ServiceException.class, () -> service.save(1L,req));
    }
    @Test void unsupportedStateAndUnsafeImageRejected() {
        assertThrows(ServiceException.class, () -> service.status(1L,10L,20L,-1,0L));
        assertThrows(ServiceException.class, () -> SupplierProductService.validateImageUrl("javascript:alert(1)"));
        assertThrows(ServiceException.class, () -> SupplierProductService.validateImageUrl("data:image/svg+xml,x"));
    }
}
